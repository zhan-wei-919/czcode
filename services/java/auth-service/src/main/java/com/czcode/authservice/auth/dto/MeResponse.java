package com.czcode.authservice.auth.dto;

import java.util.UUID;

public record MeResponse(
    UUID accountId,
    UUID userId,
    String email,
    short status
) {
}
