package com.greatest.shortUrl.services;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final RedissonClient redissonClient;
    private final MeterRegistry meterRegistry;

    /**
     * Tries to consume one token from the rate limiter bucket identified by {@code key}.
     *
     * <p><b>TTL design:</b> Redisson's RRateLimiter never sets a Redis key expiry on its own.
     * Without a TTL, every unique IP/user-id creates a key that lives forever, causing unbounded
     * Redis memory growth.
     *
     * <p>{@code trySetRate()} returns {@code true} only the <em>first</em> time a key is created.
     * We use that signal to set the TTL exactly once — preserving any subsequent countdown instead
     * of resetting it on every request (which would make the key immortal again).
     *
     * <p>TTL = {@code windowSeconds * 2}: the key outlives one full rate-limit window, then
     * Redisson cleans it up automatically.
     *
     * @param key           unique Redis key, e.g. {@code rl:redirect:ip:1.2.3.4}
     * @param capacity      max tokens in the window
     * @param windowSeconds length of the rate-limit window in seconds
     * @return {@code true} if the request is allowed, {@code false} if rate-limited
     */
    public boolean tryConsume(String key, long capacity, long windowSeconds) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);

        // trySetRate returns true only when the key is NEWLY created (didn't exist before).
        // That is the only safe moment to set the TTL without resetting an existing countdown.
        boolean newlyCreated = rateLimiter.trySetRate(
                RateType.OVERALL,
                capacity,
                windowSeconds,
                RateIntervalUnit.SECONDS
        );

        if (newlyCreated) {
            // Key was just created — set its expiry so it is cleaned up after the window expires.
            // 2× window gives a small buffer for in-flight requests at boundary.
            rateLimiter.expire(Duration.ofSeconds(windowSeconds * 2));
            log.debug("Rate limiter key created with TTL={}s: {}", windowSeconds * 2, key);
        }

        boolean allowed = rateLimiter.tryAcquire(1);

        meterRegistry.counter("rate_limit.requests", "bucket", key.split(":")[1],   // e.g. "redirect", "auth"
                "result", allowed ? "allowed" : "rejected").increment();

        return allowed;
    }

    public long getAvailableTokens(String key) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        RateLimiterConfig config = rateLimiter.getConfig();
        if (config == null) return 0;
        return rateLimiter.availablePermits();
    }
}