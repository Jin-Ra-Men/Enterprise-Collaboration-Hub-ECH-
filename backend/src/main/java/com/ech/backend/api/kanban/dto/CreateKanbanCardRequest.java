package com.ech.backend.api.kanban.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateKanbanCardRequest(
        @NotNull Long actorUserId,
        @NotBlank @Size(max = 500) String title,
        @Size(max = 8000) String description,
        Integer sortOrder,
        @Size(max = 50) String status
) {
}
