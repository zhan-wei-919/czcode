package com.czcode.projectservice.security;

import java.util.UUID;

public record AuthPrincipal(
    UUID accountId,
    UUID userId,
    String email
) {
}
