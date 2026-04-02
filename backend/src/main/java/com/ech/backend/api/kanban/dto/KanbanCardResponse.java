package com.ech.backend.api.kanban.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record KanbanCardResponse(
        Long id,
        Long columnId,
        Long workItemId,
        boolean workItemInUse,
        String title,
        String description,
        int sortOrder,
        String status,
        List<String> assigneeEmployeeNos,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
