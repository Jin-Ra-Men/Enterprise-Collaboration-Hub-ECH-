package com.ech.backend.api.file.dto;

import java.time.OffsetDateTime;

public record ChannelFileResponse(
        Long id,
        Long channelId,
        Long uploadedByUserId,
        String uploaderName,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String storageKey,
        OffsetDateTime createdAt
) {
}
