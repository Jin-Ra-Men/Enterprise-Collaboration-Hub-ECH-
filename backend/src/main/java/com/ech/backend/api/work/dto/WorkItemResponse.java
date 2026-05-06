package com.ech.backend.api.work.dto;

import java.time.OffsetDateTime;

public record WorkItemResponse(
        Long id,
        String title,
        String description,
        String status,
        boolean inUse,
        Long sourceMessageId,
        Long sourceChannelId,
        String createdByEmployeeNo,
        OffsetDateTime dueAt,
        String priority,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
