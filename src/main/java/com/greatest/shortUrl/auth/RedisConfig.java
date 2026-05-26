package com.greatest.shortUrl.auth;

import org.redisson.api.RedissonClient;
import org.redisson.spring.cache.CacheConfig;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public CacheManager cacheManager(RedissonClient redissonClient) {
        Map<String, CacheConfig> cacheConfigMap=new HashMap<>();
        cacheConfigMap.put("USERS_DATA", new CacheConfig(Duration.ofMinutes(100).toMillis(), Duration.ofMinutes(200).toMillis()));
        return new RedissonSpringCacheManager(redissonClient, cacheConfigMap);
    }
}