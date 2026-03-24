package com.ech.backend.api.release.dto;

public record RollbackRequest(
        Long actorUserId,
        String note
) {
}
