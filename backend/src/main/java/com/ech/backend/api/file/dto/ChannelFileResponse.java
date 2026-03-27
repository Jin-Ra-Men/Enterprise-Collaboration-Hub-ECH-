package com.ech.backend.api.file.dto;

import java.time.OffsetDateTime;

public record ChannelFileResponse(
        Long id,
        Long channelId,
        String uploadedByEmployeeNo,
        String uploaderName,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String storageKey,
        OffsetDateTime createdAt
) {
}
