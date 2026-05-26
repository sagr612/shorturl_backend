package com.greatest.shortUrl.controller;

import com.greatest.shortUrl.model.AdminStatsDto;
import com.greatest.shortUrl.model.AdminUserDto;
import com.greatest.shortUrl.model.PagedResult;
import com.greatest.shortUrl.model.ShortUrlDto;
import com.greatest.shortUrl.services.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/urls")
    public ResponseEntity<PagedResult<ShortUrlDto>> allUrls(

            @RequestParam(defaultValue = "1")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            @RequestParam(required = false)
            String search,

            @RequestParam(required = false)
            Boolean isPrivate,

            @RequestParam(defaultValue = "createdAt")
            String sortBy,

            @RequestParam(defaultValue = "desc")
            String direction
    ) {

        return ResponseEntity.ok(
                adminService.getAllUrls(
                        page,
                        size,
                        search,
                        isPrivate,
                        sortBy,
                        direction
                )
        );
    }

    @DeleteMapping("/delete-urls")
    public ResponseEntity<String> deleteUrls(
            @RequestParam List<String> ids
    ) {

        adminService.deleteUrls(ids);

        return ResponseEntity.ok(
                "URLs deleted successfully"
        );
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserDto>> allUsers() {

        return ResponseEntity.ok(   
                adminService.getAllUsers()
        );
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsDto> stats() {

        return ResponseEntity.ok(
                adminService.getStats()
        );
    }
}