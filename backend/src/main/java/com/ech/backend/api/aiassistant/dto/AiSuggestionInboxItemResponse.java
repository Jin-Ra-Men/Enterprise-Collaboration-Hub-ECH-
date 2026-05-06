package com.ech.backend.api.aiassistant.dto;

import java.time.OffsetDateTime;

public record AiSuggestionInboxItemResponse(
        Long id,
        String suggestionKind,
        String status,
        Long channelId,
        String title,
        String summary,
        String payloadJson,
        OffsetDateTime createdAt
) {
}
