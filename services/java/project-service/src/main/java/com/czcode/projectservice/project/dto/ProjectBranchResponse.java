package com.czcode.projectservice.project.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectBranchResponse(
    UUID id,
    UUID projectId,
    String name,
    short branchType,
    UUID basedOnBranchId,
    UUID headCheckpointId,
    short status,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt
) {
}
