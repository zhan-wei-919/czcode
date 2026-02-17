package com.czcode.projectservice.project.dto;

import java.time.Instant;
import java.util.UUID;

public record FileNodeResponse(
    UUID id,
    UUID projectId,
    UUID parentId,
    String name,
    boolean isDirectory,
    UUID collabDocId,
    String path,
    Instant createdAt,
    Instant updatedAt
) {
}
