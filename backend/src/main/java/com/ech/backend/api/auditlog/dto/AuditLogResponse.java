package com.ech.backend.api.auditlog.dto;

import java.time.OffsetDateTime;

public record AuditLogResponse(
        Long id,
        String eventType,
        Long actorUserId,
        String resourceType,
        Long resourceId,
        String workspaceKey,
        String detail,
        String requestId,
        OffsetDateTime createdAt
) {
}
