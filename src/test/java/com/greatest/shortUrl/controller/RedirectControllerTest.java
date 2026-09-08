package com.greatest.shortUrl.controller;

import com.greatest.shortUrl.ApplicationProperties;
import com.greatest.shortUrl.config.RateLimitFilter;
import com.greatest.shortUrl.exceptions.GlobalExceptionHandler;
import com.greatest.shortUrl.model.ShortUrlDto;
import com.greatest.shortUrl.model.UrlStatus;
import com.greatest.shortUrl.services.SecurityUtils;
import com.greatest.shortUrl.services.ShortUrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for RedirectController.
 *
 * Two endpoints:
 *   GET /s/{shortKey}        — private-aware: returns 200 + body (URL string)
 *   GET /s/public/{shortKey} — public only:  returns 302 Found + Location header
 */
@WebMvcTest(
        controllers = {RedirectController.class, GlobalExceptionHandler.class},
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = RateLimitFilter.class
        )
)
class RedirectControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean ShortUrlService shortUrlService;
    @MockitoBean SecurityUtils securityUtils;
    @MockitoBean ApplicationProperties properties;

    // ─── GET /s/{shortKey} (private-aware) ───────────────────────────

    @Test
    void accessShortUrl_validKey_returns200WithOriginalUrl() throws Exception {
        ShortUrlDto dto = buildDto("abc", "https://github.com", false);
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(shortUrlService.accessShortUrl("abc", "user-1")).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/s/abc"))
                .andExpect(status().isOk())
                .andExpect(content().string("https://github.com"));
    }

    @Test
    void accessShortUrl_privateUrlOwner_returns200WithOriginalUrl() throws Exception {
        ShortUrlDto dto = buildDto("prv", "https://secret.com", true);
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(shortUrlService.accessShortUrl("prv", "user-1")).thenReturn(Optional.of(dto));

        // Intentional design: private URLs return 200 + body so browser can handle it
        // rather than redirecting (which would silently fail for private URLs)
        mockMvc.perform(get("/s/prv"))
                .andExpect(status().isOk())
                .andExpect(content().string("https://secret.com"));
    }

    @Test
    void accessShortUrl_privateUrlWrongUser_returns404() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("other-user");
        when(shortUrlService.accessShortUrl("prv", "other-user")).thenReturn(Optional.empty());

        mockMvc.perform(get("/s/prv"))
                .andExpect(status().isNotFound());
    }

    @Test
    void accessShortUrl_unknownKey_returns404() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn(null);
        when(shortUrlService.accessShortUrl("badkey", null))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/s/badkey"))
                .andExpect(status().isNotFound());
    }

    @Test
    void accessShortUrl_expiredUrl_returns404() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        // service returns empty when url is expired
        when(shortUrlService.accessShortUrl("exp", "user-1")).thenReturn(Optional.empty());

        mockMvc.perform(get("/s/exp"))
                .andExpect(status().isNotFound());
    }

    // ─── GET /s/public/{shortKey} (public 302 redirect) ─────────────

    @Test
    void publicRedirect_validPublicKey_returns302WithLocationHeader() throws Exception {
        ShortUrlDto dto = buildDto("pub", "https://spring.io", false);
        when(shortUrlService.accessPublicShortUrl("pub")).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/s/public/pub"))
                .andExpect(status().isFound())                          // 302
                .andExpect(header().string("Location", "https://spring.io"));
    }

    @Test
    void publicRedirect_privateKey_returns404() throws Exception {
        // Service returns empty for private URLs on the public endpoint
        when(shortUrlService.accessPublicShortUrl("prv")).thenReturn(Optional.empty());

        mockMvc.perform(get("/s/public/prv"))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicRedirect_unknownKey_returns404() throws Exception {
        when(shortUrlService.accessPublicShortUrl("nope")).thenReturn(Optional.empty());

        mockMvc.perform(get("/s/public/nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicRedirect_encodesSpecialCharsInUrl() throws Exception {
        ShortUrlDto dto = buildDto("sp", "https://example.com/path?q=hello+world", false);
        when(shortUrlService.accessPublicShortUrl("sp")).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/s/public/sp"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("https://example.com/path")));
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private ShortUrlDto buildDto(String key, String url, boolean isPrivate) {
        return ShortUrlDto.builder()
                .id("id-" + key).shortKey(key).originalUrl(url)
                .clickCount(0L).createdAt(Instant.now())
                .status(UrlStatus.ACTIVE).isPrivate(isPrivate)
                .build();
    }
}
