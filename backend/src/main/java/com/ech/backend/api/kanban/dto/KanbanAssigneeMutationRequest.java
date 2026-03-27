package com.ech.backend.api.kanban.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KanbanAssigneeMutationRequest(
        @NotBlank @Size(max = 50) String actorEmployeeNo,
        @NotBlank @Size(max = 50) String assigneeEmployeeNo
) {
}
