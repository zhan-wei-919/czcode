package com.czcode.projectservice.project.dto;

import java.util.UUID;

public record CurrentUserResponse(
    UUID accountId,
    UUID userId,
    String email
) {
}
