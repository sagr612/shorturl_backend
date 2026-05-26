package com.greatest.shortUrl.repository;

import com.greatest.shortUrl.entitiy.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {
    Optional<PasswordResetToken> findByToken(String token);

    void deleteByUserId(String id);
    @Modifying
    @Query("""
            DELETE FROM PasswordResetToken prt 
            WHERE prt.expiresAt IS NOT NULL
            AND prt.expiresAt < :now
            """)
    int deleteExpiredTokens(@Param("now") Instant now);
}