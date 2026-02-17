package com.czcode.projectservice.project.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectInvitationRequest(
    @NotBlank @Email @Size(max = 128) String inviteeEmail,
    @Min(2) @Max(4) short role,
    @Min(1) @Max(720) Integer expireHours
) {
}
