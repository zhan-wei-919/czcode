package com.czcode.projectservice.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBranchProtectionRuleRequest(
    @NotBlank @Size(max = 128) String branchPattern,
    @Min(1) @Max(4) Short minPushRole,
    @Min(1) @Max(4) Short minMergeRole,
    Boolean requirePr,
    Boolean allowForcePush,
    Boolean allowDeleteBranch
) {
}
