package com.ech.backend.api.work.dto;

import java.time.OffsetDateTime;

/**
 * Sidebar rows for work todos (kanban-assigned, due buckets, mention-linked).
 * {@code sourceMessageId} is set when the work was created from a message (멘션 맥락 이동용).
 */
public record WorkItemSidebarResponse(
        Long workItemId,
        String title,
        Long channelId,
        String channelName,
        boolean inUse,
        OffsetDateTime dueAt,
        String priority,
        Long sourceMessageId,
        OffsetDateTime updatedAt
) {
}
