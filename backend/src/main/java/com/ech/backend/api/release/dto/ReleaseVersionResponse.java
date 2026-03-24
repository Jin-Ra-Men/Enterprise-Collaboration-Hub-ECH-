package com.ech.backend.api.release.dto;

import java.time.OffsetDateTime;

public record ReleaseVersionResponse(
        Long id,
        String version,
        String fileName,
        long fileSize,
        String checksum,
        String status,
        String description,
        Long uploadedBy,
        OffsetDateTime uploadedAt,
        OffsetDateTime activatedAt
) {
}
