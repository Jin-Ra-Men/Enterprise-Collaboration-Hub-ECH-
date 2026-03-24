package com.ech.backend.api.message.dto;

import java.time.OffsetDateTime;

public record MessageResponse(
        Long messageId,
        Long channelId,
        Long senderId,
        Long parentMessageId,
        String text,
        OffsetDateTime createdAt
) {
}
