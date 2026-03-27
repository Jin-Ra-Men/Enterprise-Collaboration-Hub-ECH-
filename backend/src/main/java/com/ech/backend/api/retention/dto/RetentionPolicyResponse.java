package com.ech.backend.api.retention.dto;

import java.time.OffsetDateTime;

public record RetentionPolicyResponse(
        Long id,
        String resourceType,
        int retentionDays,
        boolean isEnabled,
        String description,
        String updatedByEmployeeNo,
        OffsetDateTime updatedAt
) {
}
