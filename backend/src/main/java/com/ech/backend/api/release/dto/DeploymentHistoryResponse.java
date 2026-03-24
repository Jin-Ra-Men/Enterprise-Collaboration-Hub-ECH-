package com.ech.backend.api.release.dto;

import java.time.OffsetDateTime;

public record DeploymentHistoryResponse(
        Long id,
        Long releaseId,
        String action,
        String fromVersion,
        String toVersion,
        Long actorUserId,
        String note,
        OffsetDateTime createdAt
) {
}
