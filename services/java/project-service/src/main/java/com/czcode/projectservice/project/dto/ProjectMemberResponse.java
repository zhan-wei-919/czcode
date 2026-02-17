package com.czcode.projectservice.project.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectMemberResponse(
    UUID projectId,
    UUID userId,
    short role,
    short status,
    UUID inviterUserId,
    Instant joinedAt,
    Instant updatedAt
) {
}
