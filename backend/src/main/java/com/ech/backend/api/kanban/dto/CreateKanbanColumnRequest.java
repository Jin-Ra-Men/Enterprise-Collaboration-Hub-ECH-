package com.ech.backend.api.kanban.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateKanbanColumnRequest(
        @NotBlank @Size(max = 200) String name,
        Integer sortOrder
) {
}
