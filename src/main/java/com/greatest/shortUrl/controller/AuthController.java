package com.greatest.shortUrl.controller;

import com.greatest.shortUrl.auth.JwtUtils;
import com.greatest.shortUrl.entitiy.RefreshToken;
import com.greatest.shortUrl.exceptions.InvalidTokenException;
import com.greatest.shortUrl.exceptions.UserAlreadyExistsException;
import com.greatest.shortUrl.model.*;
import com.greatest.shortUrl.services.AuthService;
import com.greatest.shortUrl.services.RefreshTokenService;
import com.greatest.shortUrl.services.UserDetailsImpl;
import com.greatest.shortUrl.services.UserDetailsServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/auth")

public class AuthController {
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final UserDetailsServiceImpl userDetailsService;
    private final AuthenticationManager authenticationManager;
    private final AuthService authService;


    @PostMapping("/signup")
    public ResponseEntity<JwtResponseDTO> signUp(@Valid @RequestBody SignUpReq userInfoDto) {

        SignUpResponse response = userDetailsService.signupUser(userInfoDto);

        if (!response.getIsSignedUp()) {
            throw new UserAlreadyExistsException(
                    "User already exists with email: " + userInfoDto.email()
            );
        }


        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userInfoDto.email());
        UserDetailsImpl userDetails = UserDetailsImpl.build(response.getUser());
        String jwtToken = jwtUtils.generateJwtToken(userDetails);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(JwtResponseDTO.builder()
                        .accessToken(jwtToken)
                        .token(refreshToken.getToken())
                        .build());
    }


    @PostMapping("/login")
    public ResponseEntity<JwtResponseDTO> AuthenticateAndGetToken(@Valid @RequestBody AuthRequestDTO authRequestDTO) {
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(authRequestDTO.getEmail(), authRequestDTO.getPassword()));
        if (!authentication.isAuthenticated()) {
            throw new RuntimeException("Authentication failed");
        }
        UserDetailsImpl user = (UserDetailsImpl) authentication.getPrincipal();
        String jwtToken = jwtUtils.generateJwtToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(authRequestDTO.getEmail());
        return ResponseEntity.ok(JwtResponseDTO.builder()
                .accessToken(jwtToken)
                .token(refreshToken.getToken())
                .build());
    }

    @PostMapping("/refreshToken")
    public ResponseEntity<JwtResponseDTO> refreshToken(@Valid @RequestBody RefreshTokenRequestDTO request) {

        return ResponseEntity.ok(
                refreshTokenService.refreshToken(request.getToken())
        );
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {

        authService.forgotPassword(request.email());

        return ResponseEntity.ok("Password reset email sent");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {

        authService.resetPassword(request.token(), request.newPassword());

        return ResponseEntity.ok("Password updated successfully");
    }

}
