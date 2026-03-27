package com.ech.backend.api.channel.dto;

import java.time.OffsetDateTime;

public record ChannelReadStateResponse(
        Long channelId,
        String employeeNo,
        Long lastReadMessageId,
        OffsetDateTime updatedAt
) {
}
