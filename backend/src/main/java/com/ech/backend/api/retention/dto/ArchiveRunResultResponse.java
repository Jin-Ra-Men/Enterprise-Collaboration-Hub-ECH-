package com.ech.backend.api.retention.dto;

public record ArchiveRunResultResponse(
        String resourceType,
        int processedCount,
        boolean skipped,
        String message
) {
}
