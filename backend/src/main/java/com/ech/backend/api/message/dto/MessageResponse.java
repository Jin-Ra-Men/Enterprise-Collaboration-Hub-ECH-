package com.ech.backend.api.message.dto;

import java.time.OffsetDateTime;

public record MessageResponse(
        Long messageId,
        Long channelId,
        String senderId,
        String senderName,
        Long parentMessageId,
        String text,
        OffsetDateTime createdAt,
        String messageType
) {
}
