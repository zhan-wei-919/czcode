package com.czcode.projectservice.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;

public record UpsertProjectMemberRequest(
    @Min(1) @Max(4) short role,
    UUID inviterUserId,
    @Min(1) @Max(2) Short status
) {
}
