package com.ech.backend.api.settings.dto;

import java.time.OffsetDateTime;

public record AppSettingResponse(
        Long id,
        String key,
        String value,
        String description,
        String updatedByEmployeeNo,
        OffsetDateTime updatedAt
) {
}
