package com.ech.backend.api.channel.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ChannelSummaryResponse(
        Long channelId,
        String workspaceKey,
        String name,
        String description,
        String channelType,
        int memberCount,
        OffsetDateTime createdAt,
        int unreadCount,
        List<String> dmPeerEmployeeNos,
        String dmSidebarLabel,
        OffsetDateTime lastMessageAt
) {
}
