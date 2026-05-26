package com.greatest.shortUrl.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final RedissonClient redissonClient;

    // Each unique key gets its own RRateLimiter in Redis
    public boolean tryConsume(String key, long capacity, long windowSeconds) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);

        // trySetRate only applies if limiter doesn't exist yet
        // OVERALL = fixed window, PER_CLIENT = per client id
        rateLimiter.trySetRate(
                RateType.OVERALL,
                capacity,
                windowSeconds,
                RateIntervalUnit.SECONDS
        );

        return rateLimiter.tryAcquire(1);
    }

    public long getAvailableTokens(String key) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        RateLimiterConfig config = rateLimiter.getConfig();
        if (config == null) return 0;
        return rateLimiter.availablePermits();
    }
}