package com.greatest.shortUrl.services;

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
public class ShortUrlCleanupService {
    private final ShortUrlRepo shortUrlRepo;

//    @Scheduled(cron = "0 0 2 * * ?")
////    @Scheduled(fixedRate = 5000)
//    @Transactional
//    public void deleteExpiredUrls(){
//        int deleted = shortUrlRepo.deleteExpired(Instant.now());
//        log.info("Deleted {} expired URLs", deleted);
////        shortUrlRepo.deleteByExpiresAtBefore(Instant.now());
//    }

    @Scheduled(cron = "0 0 * * * *")
//    @Scheduled(fixedRate = 5000)

    @Transactional
    public void expireUrls() {

        int updated = shortUrlRepo.markExpiredUrls(Instant.now());

        log.info("Expired {} URLs", updated);
    }


    @Scheduled(cron = "0 0 2 * * *")
//    @Scheduled(fixedRate = 5000)

    @Transactional
    public void cleanupOldExpiredUrls() {

        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);

        int deleted = shortUrlRepo.deleteExpiredUrlsOlderThan(cutoff);

        log.info("Deleted {} expired URLs", deleted);
    }
}
