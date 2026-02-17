package com.czcode.projectservice.project.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TransferOwnerRequest(
    @NotNull UUID targetUserId
) {
}
