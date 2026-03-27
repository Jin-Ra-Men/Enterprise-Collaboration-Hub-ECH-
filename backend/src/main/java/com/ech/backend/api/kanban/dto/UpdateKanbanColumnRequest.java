package com.ech.backend.api.kanban.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateKanbanColumnRequest(
        @NotBlank @Size(max = 50) String actorEmployeeNo,
        @NotBlank @Size(max = 200) String name,
        int sortOrder
) {
}
