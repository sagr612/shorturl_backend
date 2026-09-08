package com.greatest.shortUrl.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greatest.shortUrl.config.JwtUtils;
import com.greatest.shortUrl.config.RateLimitFilter;
import com.greatest.shortUrl.entity.RefreshToken;
import com.greatest.shortUrl.entity.User;
import com.greatest.shortUrl.model.SignUpResponse;
import com.greatest.shortUrl.services.AuthService;
import com.greatest.shortUrl.services.RefreshTokenService;
import com.greatest.shortUrl.services.UserDetailsImpl;
import com.greatest.shortUrl.services.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for AuthController.
 * Security is excluded — we test HTTP status codes, JSON shape, and bean validation only.
 */
@WebMvcTest(
        controllers = AuthController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = RateLimitFilter.class
        )
)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // Spring Boot 3.4+ — use @MockitoBean (replaces deprecated @MockBean)
    @MockitoBean JwtUtils jwtUtils;
    @MockitoBean RefreshTokenService refreshTokenService;
    @MockitoBean UserDetailsServiceImpl userDetailsService;
    @MockitoBean AuthenticationManager authenticationManager;
    @MockitoBean AuthService authService;

    // ─── POST /api/v1/auth/signup ─────────────────────────────────────

    @Test
    void signup_validRequest_returns201WithAccessAndRefreshTokens() throws Exception {
        User user = User.builder().id("u1").email("alice@test.com").name("Alice").password("hash").build();
        SignUpResponse signUpResponse = SignUpResponse.builder().isSignedUp(true).user(user).build();
        RefreshToken refreshToken = buildRefreshToken();

        when(userDetailsService.signupUser(any())).thenReturn(signUpResponse);
        when(refreshTokenService.createRefreshToken("alice@test.com")).thenReturn(refreshToken);
        when(jwtUtils.generateJwtToken(any())).thenReturn("access-token-xyz");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@test.com","password":"Secret123!","name":"Alice"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token-xyz"))
                .andExpect(jsonPath("$.token").value(refreshToken.getToken()));
    }

    @Test
    void signup_duplicateEmail_returns409Conflict() throws Exception {
        SignUpResponse signUpResponse = SignUpResponse.builder().isSignedUp(false).build();
        when(userDetailsService.signupUser(any())).thenReturn(signUpResponse);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@test.com","password":"Secret123!","name":"Alice"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void signup_blankEmail_returns400ValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":"Secret123!","name":"Alice"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /api/v1/auth/login ──────────────────────────────────────

    @Test
    void login_validCredentials_returns200WithTokens() throws Exception {
        User user = User.builder().id("u1").email("alice@test.com").name("Alice").password("hash").build();
        UserDetailsImpl principal = UserDetailsImpl.build(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        RefreshToken refreshToken = buildRefreshToken();

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtUtils.generateJwtToken(principal)).thenReturn("access-token-xyz");
        when(refreshTokenService.createRefreshToken("alice@test.com")).thenReturn(refreshToken);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@test.com","password":"Secret123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-xyz"));
    }

    @Test
    void login_blankEmail_returns400ValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":"Secret123!"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /api/v1/auth/forgot-password ───────────────────────────

    @Test
    void forgotPassword_anyEmail_returns200WithoutRevealingIfUserExists() throws Exception {
        // authService.forgotPassword silently does nothing if user not found
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"unknown@test.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("Password reset email sent"));
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private RefreshToken buildRefreshToken() {
        return RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusSeconds(604800))
                .build();
    }
}
