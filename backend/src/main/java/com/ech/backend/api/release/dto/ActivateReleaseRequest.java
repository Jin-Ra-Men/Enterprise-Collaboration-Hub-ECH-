package com.ech.backend.api.release.dto;

public record ActivateReleaseRequest(
        String actorEmployeeNo,
        String note
) {
}
