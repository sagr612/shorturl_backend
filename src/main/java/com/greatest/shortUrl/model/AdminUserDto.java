package com.greatest.shortUrl.model;


import java.time.Instant;

public record AdminUserDto(
        String id,
        String name,
        String email,
        String role,
        Instant createdAt,
        Long totalUrls,
        Long totalClicks) {
}