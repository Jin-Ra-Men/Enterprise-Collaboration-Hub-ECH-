package com.ech.backend.api.kanban.dto;

import java.time.OffsetDateTime;

public record AssignedKanbanCardSummaryResponse(
        Long cardId,
        String title,
        Long channelId,
        String channelName,
        String channelType,
        Long boardId,
        Long columnId,
        String columnName,
        OffsetDateTime updatedAt
) {
}
