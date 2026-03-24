package com.ech.backend.api.kanban.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record KanbanCardResponse(
        Long id,
        Long columnId,
        String title,
        String description,
        int sortOrder,
        String status,
        List<Long> assigneeUserIds,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
