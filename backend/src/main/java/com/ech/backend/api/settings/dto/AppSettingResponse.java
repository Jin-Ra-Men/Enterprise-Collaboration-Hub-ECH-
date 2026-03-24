package com.ech.backend.api.settings.dto;

import java.time.OffsetDateTime;

public record AppSettingResponse(
        Long id,
        String key,
        String value,
        String description,
        Long updatedBy,
        OffsetDateTime updatedAt
) {
}
