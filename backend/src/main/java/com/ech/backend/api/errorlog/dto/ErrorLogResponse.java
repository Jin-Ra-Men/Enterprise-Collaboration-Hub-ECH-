package com.ech.backend.api.errorlog.dto;

import java.time.OffsetDateTime;

public record ErrorLogResponse(
        Long id,
        String errorCode,
        String errorClass,
        String message,
        String path,
        String httpMethod,
        Long actorUserId,
        String requestId,
        OffsetDateTime createdAt
) {
}
