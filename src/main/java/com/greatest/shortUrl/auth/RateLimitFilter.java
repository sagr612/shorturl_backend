package com.greatest.shortUrl.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greatest.shortUrl.services.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();
        String clientKey = resolveClientKey(request, path);

        // Pick limits based on endpoint
        long capacity;
        String bucketPrefix;

        if (path.startsWith("/s/")) {
            capacity = 30;
            bucketPrefix = "rl:redirect:";
        } else if (path.startsWith("/api/v1/auth/")) {
            capacity = 3;
            bucketPrefix = "rl:auth:";
        } else if (path.equals("/api/v1/shorten/") && method.equals("POST")) {
            capacity = 10;
            bucketPrefix = "rl:create:";
        } else if (
                path.matches("^/api/v1/shorten/[^/]+$")
                        && method.equals("PUT")
        ) {

            capacity = 20;
            bucketPrefix = "rl:update:";

        } else {
            capacity = 100;
            bucketPrefix = "rl:general:";
        }

        String redisKey = bucketPrefix + clientKey;
        boolean allowed = rateLimiterService.tryConsume(redisKey, capacity, 60);

        if (allowed) {
            long remaining = rateLimiterService.getAvailableTokens(redisKey);
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(remaining));
            filterChain.doFilter(request, response);
        } else {
            writeTooManyRequestsResponse(response);
        }
    }

    private String resolveClientKey(HttpServletRequest request, String path) {
        if (path.startsWith("/api/v1/auth/")) {
            return "ip:" + getClientIp(request);
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String userId = jwtUtils.getUserIdFromToken(authHeader.substring(7));
                if (userId != null) return "user:" + userId;
            } catch (Exception e) {
                log.debug("Could not extract userId for rate limiting: {}", e.getMessage());
            }
        }

        return "ip:" + getClientIp(request);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequestsResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "timestamp", Instant.now().toString(),
                "statusCode", 429,
                "message", "Too many requests. Please slow down.",
                "error", "Too Many Requests"
        ));
    }
}