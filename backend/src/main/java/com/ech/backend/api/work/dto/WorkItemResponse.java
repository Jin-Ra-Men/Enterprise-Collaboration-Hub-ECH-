package com.ech.backend.api.work.dto;

import java.time.OffsetDateTime;

public record WorkItemResponse(
        Long id,
        String title,
        String description,
        String status,
        Long sourceMessageId,
        Long sourceChannelId,
        String createdByEmployeeNo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
