package com.ech.backend.api.message.dto;

import java.util.List;

/**
 * 채팅 타임라인 페이지(최신 또는 {@code beforeMessageId} 이전 구간).
 * {@code hasMoreOlder}는 서버가 {@code limit+1}건 조회 후 판별한다.
 */
public record MessageTimelinePageResponse(
        List<MessageTimelineItemResponse> items,
        boolean hasMoreOlder
) {}
