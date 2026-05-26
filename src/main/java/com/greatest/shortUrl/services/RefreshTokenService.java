package com.greatest.shortUrl.services;

import com.greatest.shortUrl.auth.JwtUtils;
import com.greatest.shortUrl.entitiy.RefreshToken;
import com.greatest.shortUrl.entitiy.User;
import com.greatest.shortUrl.exceptions.InvalidTokenException;
import com.greatest.shortUrl.exceptions.TokenExpiredException;
import com.greatest.shortUrl.model.JwtResponseDTO;
import com.greatest.shortUrl.repository.RefreshTokenRepository;
import com.greatest.shortUrl.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepo userRepository;
    private final JwtUtils jwtUtils;

    @Value("${jwt.refresh-expiration:604800000}")
    private Long refreshTokenExpiration;

    @Transactional
    public RefreshToken createRefreshToken(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));


        Optional<RefreshToken> existing = refreshTokenRepository.findByUser(user);

        if (existing.isPresent()) {
            RefreshToken token = existing.get();
            token.setToken(UUID.randomUUID().toString());
            token.setExpiryDate(Instant.now().plusMillis(refreshTokenExpiration));
            return refreshTokenRepository.save(token);
        }

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshTokenExpiration))
                .build();

        return refreshTokenRepository.save(refreshToken);
    }


    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    @Transactional
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new TokenExpiredException("Refresh token expired. Please login again.");
        }
        return token;
    }


    @Scheduled(cron = "0 0 2 * * ?") // Run daily at 2 AM
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteByExpiryDateBefore(Instant.now());
    }

    public JwtResponseDTO refreshToken(String token) {
        RefreshToken refreshToken = findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Not found"));

        verifyExpiration(refreshToken);

        User user = refreshToken.getUser();

        String accessToken = jwtUtils.generateJwtToken(
                UserDetailsImpl.build(user)
        );

        return JwtResponseDTO.builder()
                .accessToken(accessToken)
                .token(token)
                .build();
    }
}
