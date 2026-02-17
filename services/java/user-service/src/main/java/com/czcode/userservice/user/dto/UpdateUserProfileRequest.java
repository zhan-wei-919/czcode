package com.czcode.userservice.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
    @Size(max = 64) String nickname,
    @Size(max = 255) String avatarUrl,
    @Size(max = 512) String bio,
    @Size(max = 64) String timezone,
    @Size(max = 32) String locale,
    @Min(1) @Max(2) Short status
) {
}
