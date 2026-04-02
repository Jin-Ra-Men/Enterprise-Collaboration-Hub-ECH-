package com.ech.backend.api.work.dto;

import java.time.OffsetDateTime;

/** Sidebar: work items where the user is assignee on at least one linked kanban card. */
public record WorkItemSidebarResponse(
        Long workItemId,
        String title,
        Long channelId,
        String channelName,
        boolean inUse,
        OffsetDateTime updatedAt
) {
}
