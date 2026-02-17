package com.czcode.projectservice.project.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
    UUID id,
    String projectKey,
    String name,
    String description,
    UUID ownerUserId,
    short visibility,
    short status,
    Instant createdAt,
    Instant updatedAt
) {
}
