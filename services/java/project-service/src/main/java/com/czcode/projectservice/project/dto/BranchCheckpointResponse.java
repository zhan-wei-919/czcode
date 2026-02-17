package com.czcode.projectservice.project.dto;

import java.time.Instant;
import java.util.UUID;

public record BranchCheckpointResponse(
    UUID id,
    UUID projectId,
    UUID branchId,
    String title,
    String description,
    String snapshotRef,
    long snapshotSizeBytes,
    int fileCount,
    UUID createdBy,
    Instant createdAt
) {
}
