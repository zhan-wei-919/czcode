package com.czcode.projectservice.project.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectAuditLogResponse(
    UUID id,
    UUID projectId,
    UUID actorUserId,
    String action,
    String targetType,
    UUID targetId,
    String detailJson,
    Instant createdAt
) {
}
