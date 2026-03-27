package com.ech.backend.api.kanban.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateKanbanBoardRequest(
        @NotBlank @Size(max = 100) String workspaceKey,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 50) String createdByEmployeeNo
) {
}
