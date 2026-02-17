package com.czcode.projectservice.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateProjectRequest(
    @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Z0-9_\\-]+") String projectKey,
    @NotBlank @Size(max = 128) String name,
    String description,
    @NotNull UUID ownerUserId,
    @Min(1) @Max(3) Short visibility
) {
}
