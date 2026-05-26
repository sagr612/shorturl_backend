package com.greatest.shortUrl.controller;

import com.greatest.shortUrl.ApplicationProperties;
import com.greatest.shortUrl.exceptions.ShortUrlNotFoundException;
import com.greatest.shortUrl.model.CachedShortUrlDto;
import com.greatest.shortUrl.model.ShortUrlDto;
import com.greatest.shortUrl.services.SecurityUtils;
import com.greatest.shortUrl.services.ShortUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class RedirectController {
    private final ShortUrlService shortUrlService;
    private final SecurityUtils securityUtils;
    private final ApplicationProperties properties;
    @GetMapping("/s/{shortKey}")
    ResponseEntity<String> redirectToOriginalUrl(@PathVariable String shortKey) {
        String userId = securityUtils.getCurrentUserId();
        Optional<ShortUrlDto> shortUrlDtoOptional = shortUrlService.accessShortUrl(shortKey, userId);
        if (shortUrlDtoOptional.isEmpty()) {
            throw new ShortUrlNotFoundException("Invalid short key: " + shortKey);
        }
        ShortUrlDto shortUrlDto = shortUrlDtoOptional.get();
        return ResponseEntity.status(HttpStatus.OK).body(shortUrlDto.getOriginalUrl());
    }

    @GetMapping("/s/public/{shortKey}")
    ResponseEntity<Void> publicRedirectToOriginalUrl(@PathVariable String shortKey) {
        Optional<ShortUrlDto> shortUrlDtoOptional = shortUrlService.accessPublicShortUrl(shortKey);
        if (shortUrlDtoOptional.isEmpty()) {
            throw new ShortUrlNotFoundException("Invalid short key: " + shortKey);
        }
        ShortUrlDto shortUrlDto = shortUrlDtoOptional.get();
        URI uri = UriComponentsBuilder
                .fromUriString(shortUrlDto.getOriginalUrl())
                .build(true)
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(uri).build();
    }

}
