package com.czcode.authservice.security;

import java.util.UUID;

public record AuthPrincipal(
    UUID accountId,
    UUID userId,
    String email
) {
}
