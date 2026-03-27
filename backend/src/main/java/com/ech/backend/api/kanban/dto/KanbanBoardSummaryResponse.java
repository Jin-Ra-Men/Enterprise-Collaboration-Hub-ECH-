package com.ech.backend.api.kanban.dto;

import java.time.OffsetDateTime;

public record KanbanBoardSummaryResponse(
        Long id,
        String workspaceKey,
        String name,
        String description,
        String createdByEmployeeNo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
