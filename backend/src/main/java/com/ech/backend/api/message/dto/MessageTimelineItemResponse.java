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
        String replyToPreview,
        /** REPLY 전용: 답장 대상(부모) 메시지 작성자 표시명 */
        String replyToSenderName,

        /** ROOT 전용: 해당 원글 스레드의 댓글(COMMENT_*만, 타임라인 답글 REPLY_* 제외) 개수. REPLY 행은 null. */
        Integer threadCommentCount,
        /** ROOT 전용: 가장 최근 COMMENT 시각 */
        OffsetDateTime lastCommentAt,
        /** ROOT 전용: 가장 최근 COMMENT 작성자 표시명 */
        String lastCommentSenderName
) {
}

