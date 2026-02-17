package com.czcode.authservice.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record AuthTokenResponse(
    String tokenType,
    String accessToken,
    Instant accessTokenExpiresAt,
    String refreshToken,
    UUID accountId,
    UUID userId,
    String email
) {
}
