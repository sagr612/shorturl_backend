package com.greatest.shortUrl.controller;

import com.greatest.shortUrl.ApplicationProperties;
import com.greatest.shortUrl.entitiy.ShortUrl;
import com.greatest.shortUrl.exceptions.ShortUrlNotFoundException;
import com.greatest.shortUrl.model.*;
import com.greatest.shortUrl.services.SecurityUtils;
import com.greatest.shortUrl.services.ShortUrlService;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shorten/")
@Slf4j
public class ShortUrlController {
    private final ShortUrlService shortUrlService;
    private final SecurityUtils securityUtils;
    private final ApplicationProperties properties;

    @GetMapping()
    public ResponseEntity allPublicShortUrls(@RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer size, @RequestParam(required = false) String search, @RequestParam(defaultValue = "createdAt") String sortBy, @RequestParam(defaultValue = "desc") String direction) {
        log.info("GET /api/shorten/v1/ -  page: {}, size: {}", page, size);

        PagedResult<ShortUrlDto> shortUrls = shortUrlService.findAllPublicShortUrls(page, size, search, sortBy,direction );
        return ResponseEntity.ok(shortUrls);
    }

    @PostMapping()
    public ResponseEntity createShortUrls(@RequestBody @Valid CreateShortUrl createShortUrl) {
        var currentUserId = securityUtils.getCurrentUserId();

        ShortUrlDto shortUrlDto = shortUrlService.createShortUrl(createShortUrl, currentUserId);

        return ResponseEntity.ok(shortUrlDto);
    }


    @GetMapping("/my-urls")
    public ResponseEntity showUserUrls(@RequestParam(defaultValue = "1") int page, @RequestParam(required = false) String search, @RequestParam(required = false) Boolean isPrivate, @RequestParam(defaultValue = "createdAt") String sortBy, @RequestParam(defaultValue = "desc") String direction) {
        var currentUserId = securityUtils.getCurrentUserId();
//        PagedResult<ShortUrlDto> myUrls = shortUrlService.getUserShortUrls(currentUserId, page, properties.pageSize());
        PagedResult<ShortUrlDto> myUrls =
                shortUrlService.getUserShortUrls(
                        currentUserId,
                        page,
                        properties.pageSize(),
                        search,
                        isPrivate,
                        sortBy,
                        direction
                );
        return ResponseEntity.ok(myUrls);
    }

    @DeleteMapping("/delete-urls")
    public ResponseEntity deleteUrls(@RequestParam(value = "ids", required = false) List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(" No URLs selected for deletion");
        }
        try {
            var currentUserId = securityUtils.getCurrentUserId();
            shortUrlService.deleteUserShortUrls(ids, currentUserId);
            return ResponseEntity.ok().body("Selected URLs have been deleted successfully");

        } catch (Exception e) {
            log.error("Error deleting URLs: " + e.getMessage());
        }
        return ResponseEntity.internalServerError().build();
    }




    @PutMapping("/{shortKey}")
    public ResponseEntity<ShortUrlDto> updateShortUrl(@PathVariable String shortKey, @RequestBody @Valid UpdateShortUrlRequest request) {

        String currentUserId = securityUtils.getCurrentUserId();

        ShortUrlDto updatedUrl = shortUrlService.updateShortUrl(
                shortKey,
                request.originalUrl(),
                currentUserId
        );

        return ResponseEntity.ok(updatedUrl);
    }

}
