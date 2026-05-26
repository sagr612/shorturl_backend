package com.greatest.shortUrl.model;


public record AdminStatsDto(

        Long totalUsers,

        Long totalUrls,

        Long publicUrls,

        Long privateUrls,

        Long totalClicks

) {
}