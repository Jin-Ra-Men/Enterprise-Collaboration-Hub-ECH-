package com.ech.backend.api.release.dto;

public record ActivateReleaseRequest(
        Long actorUserId,
        String note
) {
}
