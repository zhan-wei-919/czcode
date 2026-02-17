package com.czcode.projectservice.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateProjectBranchRequest(
    @NotBlank @Size(max = 128) String name,
    @Min(1) @Max(5) Short branchType,
    UUID basedOnBranchId
) {
}
