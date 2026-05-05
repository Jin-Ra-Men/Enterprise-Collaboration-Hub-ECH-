package com.ech.backend.api.file.dto;

import java.time.OffsetDateTime;

public record ChannelLibraryFolderResponse(
        Long id,
        Long channelId,
        String name,
        int sortOrder,
        OffsetDateTime createdAt
) {
}
