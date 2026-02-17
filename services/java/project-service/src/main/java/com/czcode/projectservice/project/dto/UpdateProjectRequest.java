package com.czcode.projectservice.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
    @Size(max = 128) String name,
    String description,
    @Min(1) @Max(3) Short visibility,
    @Min(1) @Max(2) Short status
) {
}
