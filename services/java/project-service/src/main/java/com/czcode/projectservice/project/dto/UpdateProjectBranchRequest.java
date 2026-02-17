package com.czcode.projectservice.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateProjectBranchRequest(
    @Size(max = 128) String name,
    @Min(1) @Max(3) Short status
) {
}
