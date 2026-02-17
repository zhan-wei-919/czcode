package com.czcode.projectservice.project.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateFileNodeRequest(
    UUID parentId,
    @Size(max = 128) String name,
    UUID collabDocId
) {
}
