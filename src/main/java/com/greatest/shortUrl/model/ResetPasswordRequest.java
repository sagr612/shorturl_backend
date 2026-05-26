package com.greatest.shortUrl.model;

import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        String token,

        @Size(min = 6)
        String newPassword
) {}