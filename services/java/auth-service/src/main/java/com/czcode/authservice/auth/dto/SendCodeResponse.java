package com.czcode.authservice.auth.dto;

import java.time.Instant;

public record SendCodeResponse(
    String message,
    Instant expiresAt,
    String debugCode
) {
}
