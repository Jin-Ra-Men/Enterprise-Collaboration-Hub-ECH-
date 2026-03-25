package com.ech.backend.api.channel.dto;

import java.time.OffsetDateTime;

public record ChannelSummaryResponse(
        Long channelId,
        String workspaceKey,
        String name,
        String description,
        String channelType,
        int memberCount,
        OffsetDateTime createdAt
) {
}
