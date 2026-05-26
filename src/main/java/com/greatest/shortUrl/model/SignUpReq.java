package com.greatest.shortUrl.model;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;


public record SignUpReq(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,
        @NotBlank(message = "Password is required")
        String password,
        @NotBlank(message = "Name is required")
        String name
) {
}