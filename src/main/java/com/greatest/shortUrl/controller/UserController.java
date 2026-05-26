package com.greatest.shortUrl.controller;

import com.greatest.shortUrl.model.UserDto;
import com.greatest.shortUrl.model.UserStatsDto;
import com.greatest.shortUrl.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserDto> currentUser() {

        return ResponseEntity.ok(
                userService.getCurrentUser()
        );
    }

    @GetMapping("/me/stats")
    public ResponseEntity<UserStatsDto> currentUserStats() {

        return ResponseEntity.ok(
                userService.getCurrentUserStats()
        );
    }
}