package com.ech.backend.api.work.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWorkItemFromMessageRequest(
        @NotNull Long createdByUserId,
        @Size(max = 500) String title,
        @Size(max = 8000) String description,
        @Size(max = 50) String status
) {
}
