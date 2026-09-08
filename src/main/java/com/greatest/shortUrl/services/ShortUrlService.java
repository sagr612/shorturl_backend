package com.greatest.shortUrl.services;

import com.greatest.shortUrl.ApplicationProperties;
import com.greatest.shortUrl.entity.ShortUrl;
import com.greatest.shortUrl.exceptions.ShortUrlNotFoundException;
import com.greatest.shortUrl.exceptions.UnauthorizedException;
import com.greatest.shortUrl.model.*;
import com.greatest.shortUrl.repository.ShortUrlRepo;
import com.greatest.shortUrl.repository.UserRepo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.greatest.shortUrl.services.RandomUtils.generateRandomShortKey;
import static java.time.temporal.ChronoUnit.DAYS;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShortUrlService {
    private final ShortUrlRepo shortUrlRepo;
    private final EntityMapper entityMapper;
    private final ApplicationProperties properties;
    private final UserRepo userRepo;
    private final CachedShortUrlService cachedShortUrlService;


    private Pageable getPageable(int page, int size) {
        page = page > 1 ? page - 1 : 0;
        return PageRequest.of(page, size, Sort.Direction.DESC, "createdAt");
    }
    private String normalizeUrl(String url) {
        return url.trim().replaceAll("#+$", "");
    }

    /**
     * Creates a short URL.
     *
     * Key design: we no longer do a SELECT-before-INSERT (TOCTOU race condition).
     * Instead we attempt the INSERT directly and retry on a unique-key collision.
     *
     * With a 6-char base62 keyspace (62^6 ≈ 56 billion combinations) the collision
     * probability per attempt is negligible, so MAX_RETRIES=3 is extremely conservative.
     * The DB unique constraint (short_key UNIQUE) is the single source of truth.
     */
    private static final int MAX_KEY_GENERATION_RETRIES = 3;

    @Transactional
    public ShortUrlDto createShortUrl(@Valid CreateShortUrl createShortUrl, String userId) {
        if (properties.validateOriginalUrl()) {
            boolean urlExists = UrlExistenceValidator.isValid(normalizeUrl(createShortUrl.originalUrl()));
            if (!urlExists) {
                throw new ShortUrlNotFoundException("Invalid URL " + createShortUrl.originalUrl());
            }
        }

        for (int attempt = 1; attempt <= MAX_KEY_GENERATION_RETRIES; attempt++) {
            try {
                var shortUrl = new ShortUrl();
                shortUrl.setOriginalUrl(createShortUrl.originalUrl());
                shortUrl.setShortKey(generateRandomShortKey()); // no SELECT — just try it
                if (userId == null) {
                    shortUrl.setCreatedBy(null);
                    shortUrl.setIsPrivate(false);
                    shortUrl.setExpiresAt(Instant.now().plus(properties.defaultExpiryInDays(), DAYS));
                } else {
                    shortUrl.setCreatedBy(userRepo.findById(userId).orElseThrow());
                    shortUrl.setIsPrivate(createShortUrl.isPrivate() != null && createShortUrl.isPrivate());
                    shortUrl.setExpiresAt(createShortUrl.expirationInDays() != null ? Instant.now().plus(createShortUrl.expirationInDays(), DAYS) : null);
                }
                shortUrl.setClickCount(0L);
                shortUrl.setCreatedAt(Instant.now());
                shortUrl.setStatus(UrlStatus.ACTIVE);
                shortUrlRepo.saveAndFlush(shortUrl); // flush immediately so constraint fires here
                ShortUrlDto dto = entityMapper.toShortUrlDto(shortUrl);
                return cachedShortUrlService.save(dto);
            } catch (DataIntegrityViolationException ex) {
                // short_key collision — astronomically rare with 56B keyspace but handled correctly
                log.warn("Short key collision on attempt {}/{}, retrying", attempt, MAX_KEY_GENERATION_RETRIES);
                if (attempt == MAX_KEY_GENERATION_RETRIES) {
                    throw new IllegalStateException("Failed to generate unique short key after " + MAX_KEY_GENERATION_RETRIES + " attempts", ex);
                }
            }
        }
        throw new IllegalStateException("Unreachable");
    }


    @Transactional
    public Optional<ShortUrlDto> accessShortUrl(String shortKey, String userId) {
        ShortUrlDto shortUrl = cachedShortUrlService.getShortUrl(shortKey);
        if (shortUrl == null) {
            return Optional.empty();
        }

        if (shortUrl.getStatus() != UrlStatus.ACTIVE) {
            return Optional.empty();
        }
        if (shortUrl.getExpiresAt() != null && shortUrl.getExpiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        if (Boolean.TRUE.equals(shortUrl.getIsPrivate())) {

            // must have owner
            if (shortUrl.getCreatedBy() == null) {
                return Optional.empty();
            }

            // must match user
            if (!shortUrl.getCreatedBy().getId().equals(userId)) {
                return Optional.empty();
            }
        }
        shortUrlRepo.incrementClickCount(shortUrl.getId());

//        shortUrl.setClickCount(shortUrl.getClickCount() + 1);
//        shortUrlRepo.save(shortUrl);
        return Optional.ofNullable(shortUrl);

    }


    private Pageable getPageable(int page, int size, String sortBy, String direction) {
        page = Math.max(page - 1, 0);
        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        return PageRequest.of(page, size, sort);
    }

    public PagedResult<ShortUrlDto> getUserShortUrls(String userId, int page, int pageSize, String search, Boolean isPrivate, String sortBy, String direction) {

        Pageable pageable = getPageable(page, pageSize, sortBy, direction);

        var shortUrlsPage = shortUrlRepo.findUserUrls(userId, search, isPrivate, pageable).map(entityMapper::toShortUrlDto);

        return PagedResult.from(shortUrlsPage);
    }

    @Transactional
    public void deleteUserShortUrls(List<String> ids, String currentUserId) {

        if (ids != null && !ids.isEmpty() && currentUserId != null) {
            ids.forEach(id -> {
                shortUrlRepo.findById(id).ifPresent(url ->
                        cachedShortUrlService.evict(url.getShortKey())
                );
            });
            shortUrlRepo.softDeleteUserUrls(ids, currentUserId);
        }
    }


    public Optional<ShortUrlDto> accessPublicShortUrl(String shortKey) {
        ShortUrlDto shortUrl = cachedShortUrlService.getShortUrl(shortKey);
        if (shortUrl == null) {
            return Optional.empty();
        }


        if (shortUrl.getStatus() != UrlStatus.ACTIVE) {
            return Optional.empty();
        }
        if (shortUrl.getExpiresAt() != null && shortUrl.getExpiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        if (Boolean.TRUE.equals(shortUrl.getIsPrivate())) {

            return Optional.empty();
        }
        shortUrlRepo.incrementClickCount(shortUrl.getId());
//        shortUrl.setClickCount(shortUrl.getClickCount() + 1);
//        shortUrlRepo.save(shortUrl);
        return Optional.ofNullable(shortUrl);
    }


    @Transactional
    public ShortUrlDto updateShortUrl(String shortKey, String originalUrl, String currentUserId) {
//        cachedShortUrlService.evict(shortKey);
        ShortUrl shortUrl = shortUrlRepo.findByShortKey(shortKey).orElseThrow(() -> new ShortUrlNotFoundException("Short URL not found"));

        // ownership check
        if (shortUrl.getCreatedBy() == null || !shortUrl.getCreatedBy().getId().equals(currentUserId)) {
            throw new UnauthorizedException("You are not allowed to update this URL");
        }
        if (properties.validateOriginalUrl()) {
            boolean urlExists = UrlExistenceValidator.isValid(originalUrl);
            if (!urlExists) {
                throw new ShortUrlNotFoundException("Invalid URL " + originalUrl);
            }
        }
        shortUrl.setOriginalUrl(normalizeUrl(originalUrl));
        shortUrlRepo.save(shortUrl);
//        return entityMapper.toShortUrlDto(shortUrl);
        ShortUrlDto dto = entityMapper.toShortUrlDto(shortUrl);
        return cachedShortUrlService.update(dto);
    }


    public PagedResult<ShortUrlDto> findAllPublicShortUrls(Integer page, Integer size, String search, String sortBy, String direction) {
        Pageable pageable = getPageable(page, size, sortBy, direction);
        var shortUrlsPage = shortUrlRepo.findPublicUrls(search, UrlStatus.ACTIVE, pageable).map(entityMapper::toShortUrlDto);

        return PagedResult.from(shortUrlsPage);
    }
}
