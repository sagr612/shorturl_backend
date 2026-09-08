package com.greatest.shortUrl.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greatest.shortUrl.ApplicationProperties;
import com.greatest.shortUrl.config.RateLimitFilter;
import com.greatest.shortUrl.exceptions.GlobalExceptionHandler;
import com.greatest.shortUrl.exceptions.ShortUrlNotFoundException;
import com.greatest.shortUrl.exceptions.UnauthorizedException;
import com.greatest.shortUrl.model.*;
import com.greatest.shortUrl.services.SecurityUtils;
import com.greatest.shortUrl.services.ShortUrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for ShortUrlController.
 * Tests HTTP layer: request validation, response status codes, JSON shape, error handling.
 */
@WebMvcTest(
        controllers = {ShortUrlController.class, GlobalExceptionHandler.class},
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = RateLimitFilter.class
        )
)
class ShortUrlControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean ShortUrlService shortUrlService;
    @MockitoBean SecurityUtils securityUtils;
    @MockitoBean ApplicationProperties properties;

    // ─── GET /api/v1/shorten/ (public list) ──────────────────────────

    @Test
    void getAllPublicUrls_returns200WithPagedResult() throws Exception {
        PagedResult<ShortUrlDto> page = buildPage(
                List.of(buildDto("abc", "https://example.com"))
        );
        when(shortUrlService.findAllPublicShortUrls(1, 10, null, "createdAt", "desc"))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/shorten/")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].shortKey").value("abc"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getAllPublicUrls_withSearch_passesSearchToService() throws Exception {
        PagedResult<ShortUrlDto> page = buildPage(List.of());
        when(shortUrlService.findAllPublicShortUrls(anyInt(), anyInt(), eq("example"), anyString(), anyString()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/shorten/").param("search", "example"))
                .andExpect(status().isOk());

        verify(shortUrlService).findAllPublicShortUrls(anyInt(), anyInt(), eq("example"), anyString(), anyString());
    }

    // ─── POST /api/v1/shorten/ (create) ──────────────────────────────

    @Test
    void createShortUrl_validRequest_returns200WithDto() throws Exception {
        ShortUrlDto dto = buildDto("xyz", "https://google.com");
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(shortUrlService.createShortUrl(any(), eq("user-1"))).thenReturn(dto);

        mockMvc.perform(post("/api/v1/shorten/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://google.com","isPrivate":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortKey").value("xyz"))
                .andExpect(jsonPath("$.originalUrl").value("https://google.com"));
    }

    @Test
    void createShortUrl_blankUrl_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/shorten/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"","isPrivate":false}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createShortUrl_missingOriginalUrl_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/shorten/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isPrivate":false}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createShortUrl_anonymousUser_stillCreatesUrl() throws Exception {
        ShortUrlDto dto = buildDto("pub", "https://github.com");
        when(securityUtils.getCurrentUserId()).thenReturn(null); // anonymous
        when(shortUrlService.createShortUrl(any(), isNull())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/shorten/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://github.com","isPrivate":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortKey").value("pub"));
    }

    // ─── GET /api/v1/shorten/my-urls ──────────────────────────────────

    @Test
    void getMyUrls_returns200WithUserOwnedUrls() throws Exception {
        PagedResult<ShortUrlDto> page = buildPage(
                List.of(buildDto("abc", "https://myurl.com"))
        );
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(properties.pageSize()).thenReturn(10);
        when(shortUrlService.getUserShortUrls(eq("user-1"), anyInt(), anyInt(), any(), any(), anyString(), anyString()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/shorten/my-urls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].shortKey").value("abc"));
    }

    // ─── DELETE /api/v1/shorten/delete-urls ───────────────────────────

    @Test
    void deleteUrls_withValidIds_returns200() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        doNothing().when(shortUrlService).deleteUserShortUrls(anyList(), eq("user-1"));

        mockMvc.perform(delete("/api/v1/shorten/delete-urls")
                        .param("ids", "id-1", "id-2"))
                .andExpect(status().isOk())
                .andExpect(content().string("Selected URLs have been deleted successfully"));
    }

    @Test
    void deleteUrls_withNoIds_returns400() throws Exception {
        mockMvc.perform(delete("/api/v1/shorten/delete-urls"))
                .andExpect(status().isBadRequest());
    }

    // ─── PUT /api/v1/shorten/{shortKey} ───────────────────────────────

    @Test
    void updateShortUrl_validRequest_returns200WithUpdatedDto() throws Exception {
        ShortUrlDto updated = buildDto("abc", "https://updated.com");
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(shortUrlService.updateShortUrl(eq("abc"), eq("https://updated.com"), eq("user-1")))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/shorten/abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://updated.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl").value("https://updated.com"));
    }

    @Test
    void updateShortUrl_blankUrl_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/shorten/abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateShortUrl_unauthorized_returns403() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("other-user");
        when(shortUrlService.updateShortUrl(anyString(), anyString(), anyString()))
                .thenThrow(new UnauthorizedException("You are not allowed to update this URL"));

        mockMvc.perform(put("/api/v1/shorten/abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://updated.com"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateShortUrl_notFound_returns404() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(shortUrlService.updateShortUrl(eq("bad"), anyString(), anyString()))
                .thenThrow(new ShortUrlNotFoundException("Short URL not found"));

        mockMvc.perform(put("/api/v1/shorten/bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://updated.com"}
                                """))
                .andExpect(status().isNotFound());
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private ShortUrlDto buildDto(String key, String url) {
        return ShortUrlDto.builder()
                .id("id-" + key).shortKey(key).originalUrl(url)
                .clickCount(0L).createdAt(Instant.now())
                .status(UrlStatus.ACTIVE).isPrivate(false)
                .build();
    }

    @SuppressWarnings("unchecked")
    private <T> PagedResult<T> buildPage(List<T> items) {
        return new PagedResult<>(items, 1, 1, items.size(), true, true, false, false);
    }
}
