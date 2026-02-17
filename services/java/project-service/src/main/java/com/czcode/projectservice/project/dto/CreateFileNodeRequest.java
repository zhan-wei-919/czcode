package com.czcode.projectservice.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateFileNodeRequest(
    UUID parentId,
    @NotBlank @Size(max = 128) String name,
    boolean isDirectory,
    UUID collabDocId
) {
}
