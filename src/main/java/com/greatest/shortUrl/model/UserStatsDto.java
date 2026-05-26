package com.greatest.shortUrl.model;

public record UserStatsDto(

        Long totalUrls,

        Long publicUrls,

        Long privateUrls,

        Long totalClicks

) {
}