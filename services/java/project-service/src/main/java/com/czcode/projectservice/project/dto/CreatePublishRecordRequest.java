package com.czcode.projectservice.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreatePublishRecordRequest(
    @NotNull UUID sourceBranchId,
    @NotNull UUID targetBranchId,
    @NotNull UUID sourceCheckpointId,
    @Min(1) @Max(3) Short publishStatus,
    String conflictSummary
) {
}
