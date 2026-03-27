package com.ech.backend.api.kanban.dto;

import com.ech.backend.domain.kanban.KanbanCardEventType;
import java.time.OffsetDateTime;

public record KanbanCardEventResponse(
        Long id,
        KanbanCardEventType eventType,
        String actorEmployeeNo,
        String fromRef,
        String toRef,
        OffsetDateTime createdAt
) {
}
