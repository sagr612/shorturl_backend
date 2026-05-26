package com.greatest.shortUrl.services;

import com.greatest.shortUrl.entitiy.ShortUrl;
import com.greatest.shortUrl.model.CachedShortUrlDto;
import com.greatest.shortUrl.model.ShortUrlDto;
import com.greatest.shortUrl.repository.ShortUrlRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CachedShortUrlService {

    private final ShortUrlRepo shortUrlRepo;
    private final EntityMapper entityMapper;

    @Cacheable(value = "USERS_DATA", key = "#shortKey")
    public ShortUrlDto getShortUrl(String shortKey) {
        log.debug("Cache MISS for shortKey: {}", shortKey);
        ShortUrl shortUrl = shortUrlRepo.findByShortKey(shortKey).orElse(null);
        if (shortUrl == null) return null;
        return entityMapper.toShortUrlDto(shortUrl);
    }

    @CachePut(value = "USERS_DATA", key = "#shortUrlDto.shortKey")
    public ShortUrlDto save(ShortUrlDto shortUrlDto) {
        // No logic needed — Spring takes the return value and puts it in cache
        log.debug("Cache PUT for shortKey: {}", shortUrlDto.getShortKey());
        return shortUrlDto;
    }

    @CachePut(value = "USERS_DATA", key = "#shortUrlDto.shortKey")
    public ShortUrlDto update(ShortUrlDto shortUrlDto) {
        log.debug("Cache UPDATE for shortKey: {}", shortUrlDto.getShortKey());
        return shortUrlDto;
    }

    @CacheEvict(value = "USERS_DATA", key = "#shortKey")
    public void evict(String shortKey) {
        log.debug("Cache EVICT for shortKey: {}", shortKey);
    }
}