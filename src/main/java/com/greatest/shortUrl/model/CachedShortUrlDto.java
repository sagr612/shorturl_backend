package com.greatest.shortUrl.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Setter
@Builder
public class CachedShortUrlDto implements Serializable {
    String id;

    String shortKey;

    String originalUrl;

    Boolean isPrivate;

    Instant expiresAt;

    UrlStatus status;

    Long clickCount;

    String createdById;
}