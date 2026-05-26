package com.greatest.shortUrl.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateShortUrl(
        @NotBlank(message = "Original URL is required")
        String originalUrl,
        Boolean isPrivate,
        @Min(1)
        @Max(365)
        Integer expirationInDays
) {
}
