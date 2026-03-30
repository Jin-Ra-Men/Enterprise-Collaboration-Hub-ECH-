package com.ech.backend.api.message.dto;

import java.time.OffsetDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 채팅 타임라인 렌더용 항목.
 * - ROOT: parentMessageId == null 인 메시지(기존 타임라인)
 * - REPLY: messageType이 REPLY_* 인 메시지(메인 타임라인에 함께 표시)
 */
public record MessageTimelineItemResponse(
        Long messageId,
        Long channelId,
        String senderId,
        String senderName,
        Long parentMessageId,
        String text,
        OffsetDateTime createdAt,
        String messageType,

        // REPLY 전용 메타
        @JsonProperty("isReply") boolean isReply,
        Long replyToMessageId,
        String replyToKind, // "ROOT" | "COMMENT"
        Long replyToRootMessageId,
        String replyToPreview
) {
}

