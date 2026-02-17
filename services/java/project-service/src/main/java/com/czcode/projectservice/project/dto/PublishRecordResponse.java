package com.czcode.projectservice.project.dto;

import java.time.Instant;
import java.util.UUID;

public record PublishRecordResponse(
    UUID id,
    UUID projectId,
    UUID sourceBranchId,
    UUID targetBranchId,
    UUID sourceCheckpointId,
    short publishStatus,
    String conflictSummary,
    UUID publishedBy,
    Instant publishedAt,
    Instant createdAt
) {
}
