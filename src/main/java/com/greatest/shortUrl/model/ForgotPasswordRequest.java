package com.greatest.shortUrl.model;

import jakarta.validation.constraints.Email;

public record ForgotPasswordRequest(
        @Email
        String email
) {}