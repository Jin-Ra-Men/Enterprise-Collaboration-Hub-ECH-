package com.ech.backend.api.kanban.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record KanbanBoardDetailResponse(
        Long id,
        String workspaceKey,
        String name,
        String description,
        String createdByEmployeeNo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<KanbanColumnResponse> columns
) {
}
