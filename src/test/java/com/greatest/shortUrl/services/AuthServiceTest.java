package com.greatest.shortUrl.services;

import com.greatest.shortUrl.entity.PasswordResetToken;
import com.greatest.shortUrl.entity.User;
import com.greatest.shortUrl.exceptions.InvalidTokenException;
import com.greatest.shortUrl.exceptions.TokenExpiredException;
import com.greatest.shortUrl.repository.PasswordResetTokenRepository;
import com.greatest.shortUrl.repository.RefreshTokenRepository;
import com.greatest.shortUrl.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock PasswordResetTokenRepository passwordResetTokenRepo;
    @Mock UserRepo userRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock EmailService emailService;

    @InjectMocks AuthService authService;

    @BeforeEach
    void setUp() {
        // @Value fields are not injected by MockitoExtension — use ReflectionTestUtils
        ReflectionTestUtils.setField(authService, "frontendUrl", "http://localhost:5173");
    }

    // ─── resetPassword ────────────────────────────────────────────────

    @Test
    void resetPassword_whenTokenNotFound_throwsInvalidTokenException() {
        when(passwordResetTokenRepo.findByToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("bad-token", "NewPass1!"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Invalid reset token");
    }

    @Test
    void resetPassword_whenTokenAlreadyUsed_throwsInvalidTokenException() {
        PasswordResetToken token = buildToken("tok-1", false);
        token.setUsed(true);
        when(passwordResetTokenRepo.findByToken("tok-1")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword("tok-1", "NewPass1!"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("already used");
    }

    @Test
    void resetPassword_whenTokenExpired_throwsTokenExpiredException() {
        PasswordResetToken token = buildToken("tok-2", false);
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        when(passwordResetTokenRepo.findByToken("tok-2")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword("tok-2", "NewPass1!"))
                .isInstanceOf(TokenExpiredException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void resetPassword_validToken_updatesPasswordMarksTokenUsedAndDeletesRefreshTokens() {
        User user = buildUser("user-1");
        PasswordResetToken token = buildToken("tok-3", false);
        token.setUser(user);
        token.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));

        when(passwordResetTokenRepo.findByToken("tok-3")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPass1!")).thenReturn("hashed-new-pass");
        when(userRepo.save(user)).thenReturn(user);
        when(passwordResetTokenRepo.save(token)).thenReturn(token);

        authService.resetPassword("tok-3", "NewPass1!");

        assertThat(user.getPassword()).isEqualTo("hashed-new-pass");
        assertThat(token.isUsed()).isTrue();
        verify(refreshTokenRepository).deleteByUserId("user-1");
    }

    // ─── forgotPassword ───────────────────────────────────────────────

    @Test
    void forgotPassword_whenUserDoesNotExist_doesNothingAndLeaksNoInfo() {
        when(userRepo.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        // Should not throw — security best practice: never reveal if user exists
        authService.forgotPassword("unknown@test.com");

        verifyNoInteractions(passwordResetTokenRepo, emailService);
    }

    @Test
    void forgotPassword_validUser_invalidatesOldTokensCreatesNewAndSendsEmail() {
        User user = buildUser("user-1");
        when(userRepo.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.forgotPassword("user@test.com");

        // Old tokens must be wiped first
        verify(passwordResetTokenRepo).deleteByUserId("user-1");

        // A new token must be persisted
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepo).save(tokenCaptor.capture());
        PasswordResetToken saved = tokenCaptor.getValue();
        assertThat(saved.getToken()).isNotBlank();
        assertThat(saved.isUsed()).isFalse();
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());

        // Email must be sent with the reset link
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(eq("user-1@test.com"), anyString(), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("http://localhost:5173/reset-password?token=");
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private User buildUser(String id) {
        return User.builder().id(id).email(id + "@test.com").name("Test").password("hash").build();
    }

    private PasswordResetToken buildToken(String tokenValue, boolean used) {
        PasswordResetToken t = new PasswordResetToken();
        t.setToken(tokenValue);
        t.setUsed(used);
        t.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        return t;
    }
}
