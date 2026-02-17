package com.czcode.projectservice.project.dto;

import java.time.Instant;
import java.util.UUID;

public record BranchProtectionRuleResponse(
    UUID id,
    UUID projectId,
    String branchPattern,
    short minPushRole,
    short minMergeRole,
    boolean requirePr,
    boolean allowForcePush,
    boolean allowDeleteBranch,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt
) {
}
