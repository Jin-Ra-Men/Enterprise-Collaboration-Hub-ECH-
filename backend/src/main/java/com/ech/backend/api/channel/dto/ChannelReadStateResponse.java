package com.ech.backend.api.channel.dto;

import java.time.OffsetDateTime;

public record ChannelReadStateResponse(
        Long channelId,
        Long userId,
        Long lastReadMessageId,
        OffsetDateTime updatedAt
) {
}
