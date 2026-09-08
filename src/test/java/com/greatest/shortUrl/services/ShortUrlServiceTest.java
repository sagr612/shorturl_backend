package com.greatest.shortUrl.services;

import com.greatest.shortUrl.ApplicationProperties;
import com.greatest.shortUrl.entity.ShortUrl;
import com.greatest.shortUrl.entity.User;
import com.greatest.shortUrl.exceptions.ShortUrlNotFoundException;
import com.greatest.shortUrl.exceptions.UnauthorizedException;
import com.greatest.shortUrl.model.*;
import com.greatest.shortUrl.repository.ShortUrlRepo;
import com.greatest.shortUrl.repository.UserRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShortUrlServiceTest {

    @Mock ShortUrlRepo shortUrlRepo;
    @Mock EntityMapper entityMapper;
    @Mock ApplicationProperties properties;
    @Mock UserRepo userRepo;
    @Mock CachedShortUrlService cachedShortUrlService;

    @InjectMocks ShortUrlService shortUrlService;

    // ─── createShortUrl ───────────────────────────────────────────────

    @Test
    void createShortUrl_whenUrlValidationEnabledAndUrlInvalid_throwsShortUrlNotFoundException() {
        CreateShortUrl request = new CreateShortUrl("not-a-real-url", false, null);
        when(properties.validateOriginalUrl()).thenReturn(true);

        try (MockedStatic<UrlExistenceValidator> validator = mockStatic(UrlExistenceValidator.class)) {
            validator.when(() -> UrlExistenceValidator.isValid(anyString())).thenReturn(false);

            assertThatThrownBy(() -> shortUrlService.createShortUrl(request, "user-1"))
                    .isInstanceOf(ShortUrlNotFoundException.class)
                    .hasMessageContaining("Invalid URL");
        }
    }

    @Test
    void createShortUrl_anonymousUser_doesNotFetchUserAndUsesDefaultExpiry() {
        CreateShortUrl request = new CreateShortUrl("https://example.com", false, null);
        ShortUrlDto dto = buildDto("k1", "https://example.com", null);

        when(properties.validateOriginalUrl()).thenReturn(false);
        when(properties.defaultExpiryInDays()).thenReturn(30);
        // no existsByShortKey stub — new impl does saveAndFlush directly
        when(shortUrlRepo.saveAndFlush(any())).thenReturn(buildEntity("k1", "https://example.com", null));
        when(entityMapper.toShortUrlDto(any())).thenReturn(dto);
        when(cachedShortUrlService.save(any())).thenReturn(dto);

        ShortUrlDto result = shortUrlService.createShortUrl(request, null);

        assertThat(result.getShortKey()).isEqualTo("k1");
        verify(userRepo, never()).findById(anyString());
    }

    @Test
    void createShortUrl_authenticatedUser_fetchesUserFromRepo() {
        CreateShortUrl request = new CreateShortUrl("https://example.com", true, null);
        User user = buildUser("user-1");
        ShortUrlDto dto = buildDto("k1", "https://example.com", null);

        when(properties.validateOriginalUrl()).thenReturn(false);
        when(userRepo.findById("user-1")).thenReturn(Optional.of(user));
        // no existsByShortKey stub — new impl does saveAndFlush directly
        when(shortUrlRepo.saveAndFlush(any())).thenReturn(buildEntity("k1", "https://example.com", user));
        when(entityMapper.toShortUrlDto(any())).thenReturn(dto);
        when(cachedShortUrlService.save(any())).thenReturn(dto);

        shortUrlService.createShortUrl(request, "user-1");

        verify(userRepo).findById("user-1");
    }

    @Test
    void createShortUrl_retriesOnKeyCollision_untilUniqueKeyFound() {
        // Simulates the rare case: first saveAndFlush hits a DB unique constraint violation,
        // second attempt succeeds. This is the core of the new insert-then-retry pattern.
        CreateShortUrl request = new CreateShortUrl("https://example.com", false, null);
        ShortUrlDto dto = buildDto("k2", "https://example.com", null);

        when(properties.validateOriginalUrl()).thenReturn(false);
        when(properties.defaultExpiryInDays()).thenReturn(30);
        when(shortUrlRepo.saveAndFlush(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"))
                .thenReturn(buildEntity("k2", "https://example.com", null));
        when(entityMapper.toShortUrlDto(any())).thenReturn(dto);
        when(cachedShortUrlService.save(any())).thenReturn(dto);

        ShortUrlDto result = shortUrlService.createShortUrl(request, null);

        assertThat(result).isNotNull();
        // saveAndFlush called twice — once for the collision, once for the successful retry
        verify(shortUrlRepo, times(2)).saveAndFlush(any());
    }

    // ─── accessShortUrl ───────────────────────────────────────────────

    @Test
    void accessShortUrl_whenCacheMiss_returnsEmpty() {
        when(cachedShortUrlService.getShortUrl("abc")).thenReturn(null);

        assertThat(shortUrlService.accessShortUrl("abc", "user-1")).isEmpty();
    }

    @Test
    void accessShortUrl_whenUrlIsNotActive_returnsEmpty() {
        ShortUrlDto dto = buildDto("abc", "https://example.com", null);
        dto.setStatus(UrlStatus.DELETED);
        when(cachedShortUrlService.getShortUrl("abc")).thenReturn(dto);

        assertThat(shortUrlService.accessShortUrl("abc", "user-1")).isEmpty();
    }

    @Test
    void accessShortUrl_whenUrlIsExpired_returnsEmpty() {
        ShortUrlDto dto = buildDto("abc", "https://example.com", null);
        dto.setStatus(UrlStatus.ACTIVE);
        dto.setExpiresAt(Instant.now().minusSeconds(3600));
        when(cachedShortUrlService.getShortUrl("abc")).thenReturn(dto);

        assertThat(shortUrlService.accessShortUrl("abc", "user-1")).isEmpty();
    }

    @Test
    void accessShortUrl_whenPrivateAndWrongUser_returnsEmpty() {
        UserDto owner = UserDto.builder().id("owner-id").build();
        ShortUrlDto dto = buildDto("abc", "https://example.com", owner);
        dto.setStatus(UrlStatus.ACTIVE);
        dto.setIsPrivate(true);
        when(cachedShortUrlService.getShortUrl("abc")).thenReturn(dto);

        assertThat(shortUrlService.accessShortUrl("abc", "intruder")).isEmpty();
    }

    @Test
    void accessShortUrl_whenPrivateAndNullUser_returnsEmpty() {
        UserDto owner = UserDto.builder().id("owner-id").build();
        ShortUrlDto dto = buildDto("abc", "https://example.com", owner);
        dto.setStatus(UrlStatus.ACTIVE);
        dto.setIsPrivate(true);
        when(cachedShortUrlService.getShortUrl("abc")).thenReturn(dto);

        assertThat(shortUrlService.accessShortUrl("abc", null)).isEmpty();
    }

    @Test
    void accessShortUrl_whenPrivateAndCorrectOwner_returnsUrlAndIncrementsClickCount() {
        UserDto owner = UserDto.builder().id("owner-id").build();
        ShortUrlDto dto = buildDto("abc", "https://example.com", owner);
        dto.setStatus(UrlStatus.ACTIVE);
        dto.setIsPrivate(true);
        when(cachedShortUrlService.getShortUrl("abc")).thenReturn(dto);
        doNothing().when(shortUrlRepo).incrementClickCount(anyString());

        Optional<ShortUrlDto> result = shortUrlService.accessShortUrl("abc", "owner-id");

        assertThat(result).isPresent();
        assertThat(result.get().getOriginalUrl()).isEqualTo("https://example.com");
        verify(shortUrlRepo).incrementClickCount(dto.getId());
    }

    @Test
    void accessShortUrl_publicActiveUrl_returnsUrlAndIncrementsClickCount() {
        ShortUrlDto dto = buildDto("pub", "https://example.com", null);
        dto.setStatus(UrlStatus.ACTIVE);
        when(cachedShortUrlService.getShortUrl("pub")).thenReturn(dto);
        doNothing().when(shortUrlRepo).incrementClickCount(anyString());

        Optional<ShortUrlDto> result = shortUrlService.accessShortUrl("pub", null);

        assertThat(result).isPresent();
        verify(shortUrlRepo).incrementClickCount(dto.getId());
    }

    // ─── updateShortUrl ───────────────────────────────────────────────

    @Test
    void updateShortUrl_whenKeyNotFound_throwsShortUrlNotFoundException() {
        when(shortUrlRepo.findByShortKey("xyz")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shortUrlService.updateShortUrl("xyz", "https://new.com", "user-1"))
                .isInstanceOf(ShortUrlNotFoundException.class)
                .hasMessageContaining("Short URL not found");
    }

    @Test
    void updateShortUrl_whenCalledByNonOwner_throwsUnauthorizedException() {
        User owner = buildUser("owner-id");
        ShortUrl entity = buildEntity("xyz", "https://old.com", owner);
        when(shortUrlRepo.findByShortKey("xyz")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> shortUrlService.updateShortUrl("xyz", "https://new.com", "other-user"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void updateShortUrl_validOwner_savesEntityAndUpdatesCache() {
        User owner = buildUser("user-1");
        ShortUrl entity = buildEntity("xyz", "https://old.com", owner);
        ShortUrlDto updated = buildDto("xyz", "https://new.com", null);

        when(shortUrlRepo.findByShortKey("xyz")).thenReturn(Optional.of(entity));
        when(properties.validateOriginalUrl()).thenReturn(false);
        when(shortUrlRepo.save(entity)).thenReturn(entity);
        when(entityMapper.toShortUrlDto(entity)).thenReturn(updated);
        when(cachedShortUrlService.update(updated)).thenReturn(updated);

        ShortUrlDto result = shortUrlService.updateShortUrl("xyz", "https://new.com", "user-1");

        assertThat(result.getOriginalUrl()).isEqualTo("https://new.com");
        verify(shortUrlRepo).save(entity);
        verify(cachedShortUrlService).update(updated);
    }

    // ─── deleteUserShortUrls ──────────────────────────────────────────

    @Test
    void deleteUserShortUrls_evictsCacheForEachUrlThenSoftDeletesInBulk() {
        ShortUrl entity = buildEntity("k1", "https://example.com", null);
        when(shortUrlRepo.findById("id-k1")).thenReturn(Optional.of(entity));

        shortUrlService.deleteUserShortUrls(List.of("id-k1"), "user-1");

        verify(cachedShortUrlService).evict("k1");
        verify(shortUrlRepo).softDeleteUserUrls(List.of("id-k1"), "user-1");
    }

    @Test
    void deleteUserShortUrls_whenIdsIsEmpty_doesNothing() {
        shortUrlService.deleteUserShortUrls(List.of(), "user-1");

        verifyNoInteractions(shortUrlRepo, cachedShortUrlService);
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private User buildUser(String id) {
        return User.builder().id(id).email(id + "@test.com").name("Test").password("hash").build();
    }

    private ShortUrl buildEntity(String key, String url, User createdBy) {
        return ShortUrl.builder()
                .id("id-" + key).shortKey(key).originalUrl(url)
                .clickCount(0L).createdAt(Instant.now())
                .status(UrlStatus.ACTIVE).isPrivate(false).createdBy(createdBy)
                .build();
    }

    private ShortUrlDto buildDto(String key, String url, UserDto createdBy) {
        return ShortUrlDto.builder()
                .id("id-" + key).shortKey(key).originalUrl(url)
                .clickCount(0L).createdAt(Instant.now())
                .status(UrlStatus.ACTIVE).isPrivate(false).createdBy(createdBy)
                .build();
    }

    // ─── accessPublicShortUrl ─────────────────────────────────────────

    @Test
    void accessPublicShortUrl_whenValidAndActive_returnsAndIncrementsClick() {
        ShortUrlDto dto = buildDto("pub", "https://open.com", null);
        dto.setIsPrivate(false);

        when(cachedShortUrlService.getShortUrl("pub")).thenReturn(dto);
        doNothing().when(shortUrlRepo).incrementClickCount(any());

        Optional<ShortUrlDto> result = shortUrlService.accessPublicShortUrl("pub");

        assertThat(result).isPresent();
        verify(shortUrlRepo).incrementClickCount("id-pub");
    }

    @Test
    void accessPublicShortUrl_whenUrlIsPrivate_returnsEmpty() {
        ShortUrlDto dto = buildDto("prv", "https://secret.com", null);
        dto.setIsPrivate(true);

        when(cachedShortUrlService.getShortUrl("prv")).thenReturn(dto);

        Optional<ShortUrlDto> result = shortUrlService.accessPublicShortUrl("prv");

        assertThat(result).isEmpty();
        verify(shortUrlRepo, never()).incrementClickCount(any());
    }

    @Test
    void accessPublicShortUrl_whenCacheMiss_returnsEmpty() {
        when(cachedShortUrlService.getShortUrl("missing")).thenReturn(null);
        assertThat(shortUrlService.accessPublicShortUrl("missing")).isEmpty();
    }

    @Test
    void accessPublicShortUrl_whenExpired_returnsEmpty() {
        ShortUrlDto dto = buildDto("exp", "https://exp.com", null);
        dto.setIsPrivate(false);
        dto.setExpiresAt(Instant.now().minusSeconds(60));

        when(cachedShortUrlService.getShortUrl("exp")).thenReturn(dto);

        assertThat(shortUrlService.accessPublicShortUrl("exp")).isEmpty();
    }

    // ─── getUserShortUrls ─────────────────────────────────────────────

    @Test
    void getUserShortUrls_returnsMappedPagedResult() {
        ShortUrl entity = new ShortUrl();
        entity.setId("id-1");
        entity.setShortKey("u1");
        entity.setOriginalUrl("https://user.com");
        entity.setStatus(UrlStatus.ACTIVE);
        entity.setClickCount(0L);
        entity.setCreatedAt(Instant.now());
        entity.setIsPrivate(false);

        var page = new org.springframework.data.domain.PageImpl<>(List.of(entity));
        when(shortUrlRepo.findUserUrls(eq("user-1"), isNull(), isNull(), any()))
                .thenReturn(page);
        when(entityMapper.toShortUrlDto(entity)).thenReturn(buildDto("u1", "https://user.com", null));

        PagedResult<ShortUrlDto> result = shortUrlService.getUserShortUrls(
                "user-1", 1, 10, null, null, "createdAt", "desc");

        assertThat(result.data()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    // ─── findAllPublicShortUrls ───────────────────────────────────────

    @Test
    void findAllPublicShortUrls_returnsMappedPagedResult() {
        ShortUrl entity = new ShortUrl();
        entity.setId("id-p1");
        entity.setShortKey("p1");
        entity.setOriginalUrl("https://pub.com");
        entity.setStatus(UrlStatus.ACTIVE);
        entity.setClickCount(0L);
        entity.setCreatedAt(Instant.now());
        entity.setIsPrivate(false);

        var page = new org.springframework.data.domain.PageImpl<>(List.of(entity));
        when(shortUrlRepo.findPublicUrls(isNull(), eq(UrlStatus.ACTIVE), any()))
                .thenReturn(page);
        when(entityMapper.toShortUrlDto(entity)).thenReturn(buildDto("p1", "https://pub.com", null));

        PagedResult<ShortUrlDto> result = shortUrlService.findAllPublicShortUrls(
                1, 10, null, "createdAt", "desc");

        assertThat(result.data()).hasSize(1);
        assertThat(result.pageNumber()).isEqualTo(1);
    }
}