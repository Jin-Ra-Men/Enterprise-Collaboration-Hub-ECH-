package com.ech.backend.api.kanban.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateKanbanCardRequest(
        @NotNull Long actorUserId,
        @Size(max = 500) String title,
        @Size(max = 8000) String description,
        Integer sortOrder,
        @Size(max = 50) String status,
        Long columnId
) {
}
