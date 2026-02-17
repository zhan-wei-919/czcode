package com.czcode.authservice.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Email @Size(max = 128) String email,
    @NotBlank @Size(min = 8, max = 72) String password,
    @Size(max = 64) String clientId,
    @Size(max = 255) String deviceInfo
) {
}
