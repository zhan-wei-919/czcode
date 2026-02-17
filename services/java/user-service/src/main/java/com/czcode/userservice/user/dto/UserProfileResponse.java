package com.czcode.userservice.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String nickname,
    String avatarUrl,
    String bio,
    String timezone,
    String locale,
    short status,
    Instant createdAt,
    Instant updatedAt
) {
}
