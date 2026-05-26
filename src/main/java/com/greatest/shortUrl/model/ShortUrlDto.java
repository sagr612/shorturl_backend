package com.greatest.shortUrl.model;

import lombok.*;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortUrlDto implements Serializable {
    String id;
    String shortKey;
    String originalUrl;
    Boolean isPrivate;
    Instant expiresAt;
    UserDto createdBy;
    Long clickCount;
    Instant createdAt;
    UrlStatus status;
}