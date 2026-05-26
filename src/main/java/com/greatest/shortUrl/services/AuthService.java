package com.greatest.shortUrl.services;

import com.greatest.shortUrl.entitiy.PasswordResetToken;
import com.greatest.shortUrl.entitiy.User;
import com.greatest.shortUrl.repository.PasswordResetTokenRepository;
import com.greatest.shortUrl.repository.RefreshTokenRepository;
import com.greatest.shortUrl.repository.UserRepo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.hibernate.annotations.SecondaryRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final PasswordResetTokenRepository passwordResetTokenRepo;
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional
    public void resetPassword(String token, String newPassword) {

        PasswordResetToken resetToken = passwordResetTokenRepo.findByToken(token).orElseThrow(() -> new RuntimeException("Invalid reset token"));

        if (resetToken.isUsed()) {
            throw new RuntimeException("Reset token already used");
        }

        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new RuntimeException("Reset token expired");
        }

        User user = resetToken.getUser();

        user.setPassword(passwordEncoder.encode(newPassword));

        userRepo.save(user);

        resetToken.setUsed(true);

        passwordResetTokenRepo.save(resetToken);

        // optional:
        // logout from all devices
        refreshTokenRepository.deleteByUserId(user.getId());
    }
    @Transactional
    public void forgotPassword(String email) {

        Optional<User> userOptional =
                userRepo.findByEmail(email);

        // Never reveal whether user exists
        if (userOptional.isEmpty()) {
            return;
        }

        User user = userOptional.get();

        // invalidate old unused tokens if you want
        passwordResetTokenRepo
                .deleteByUserId(user.getId());

        String token =
                UUID.randomUUID().toString();

        PasswordResetToken resetToken =
                new PasswordResetToken();

        resetToken.setToken(token);

        resetToken.setUser(user);

        resetToken.setUsed(false);

        resetToken.setExpiresAt(
                Instant.now().plus(
                        15,
                        ChronoUnit.MINUTES
                )
        );

        passwordResetTokenRepo.save(resetToken);

        String resetLink =
                frontendUrl +
                        "/reset-password?token=" +
                        token;

        mailService.sendMail(
                user.getEmail(),
                "Reset Your Password",
                """
                Click the link below to reset your password:
    
                %s
    
                This link expires in 15 minutes.
                """.formatted(resetLink)
        );
    }

}
