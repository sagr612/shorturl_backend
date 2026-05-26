package com.greatest.shortUrl.repository;

import com.greatest.shortUrl.entitiy.RefreshToken;
import com.greatest.shortUrl.entitiy.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);


    void deleteByExpiryDateBefore(Instant now);

    Optional<RefreshToken> findByUser(User user);

    void deleteByUserId(String id);
}
