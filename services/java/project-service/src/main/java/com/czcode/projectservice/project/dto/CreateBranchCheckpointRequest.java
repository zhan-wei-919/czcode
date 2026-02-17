package com.czcode.projectservice.project.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBranchCheckpointRequest(
    @NotBlank @Size(max = 200) String title,
    String description,
    @NotBlank @Size(max = 512) String snapshotRef,
    @NotNull @Min(0) Long snapshotSizeBytes,
    @NotNull @Min(0) Integer fileCount
) {
}
