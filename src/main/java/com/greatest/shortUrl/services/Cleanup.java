package com.greatest.shortUrl.services;

import com.greatest.shortUrl.repository.PasswordResetTokenRepository;
import com.greatest.shortUrl.repository.ShortUrlRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class Cleanup {
    private final PasswordResetTokenRepository passwordResetTokenRepository;

//    @Scheduled(cron = "0 0 2 * * ?")

    /// /    @Scheduled(fixedRate = 5000)

    @Scheduled(cron = "0 0 * * * *")
//    @Scheduled(fixedRate = 5000)
    @Transactional
    public void deleteExpiredTokens() {

        int delete = passwordResetTokenRepository.deleteExpiredTokens(Instant.now());

        log.info("Deleted  {} expired tokens", delete);
    }


}
