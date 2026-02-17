package com.czcode.authservice.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
    @NotBlank String refreshToken,
    @Size(max = 64) String clientId,
    @Size(max = 255) String deviceInfo
) {
}
