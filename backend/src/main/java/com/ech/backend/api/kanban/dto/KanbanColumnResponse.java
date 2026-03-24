package com.ech.backend.api.kanban.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record KanbanColumnResponse(
        Long id,
        String name,
        int sortOrder,
        List<KanbanCardResponse> cards,
        OffsetDateTime createdAt
) {
}
