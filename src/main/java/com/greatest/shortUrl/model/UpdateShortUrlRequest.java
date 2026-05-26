package com.greatest.shortUrl.model;

import jakarta.validation.constraints.NotBlank;

public record UpdateShortUrlRequest(

        @NotBlank(message = "Original URL is required")
        String originalUrl

) {
}