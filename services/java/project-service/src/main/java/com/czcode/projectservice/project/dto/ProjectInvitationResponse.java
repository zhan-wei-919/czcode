package com.czcode.projectservice.project.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectInvitationResponse(
    UUID id,
    UUID projectId,
    String inviteeEmail,
    short role,
    String token,
    short status,
    Instant expiredAt,
    Instant acceptedAt,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt
) {
}
