package com.ech.backend.api.kanban.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateKanbanColumnRequest(
        @NotNull Long actorUserId,
        @NotBlank @Size(max = 200) String name,
        int sortOrder
) {
}
