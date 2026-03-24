package com.ech.backend.api.kanban.dto;

import jakarta.validation.constraints.NotNull;

public record KanbanAssigneeMutationRequest(
        @NotNull Long actorUserId,
        @NotNull Long assigneeUserId
) {
}
