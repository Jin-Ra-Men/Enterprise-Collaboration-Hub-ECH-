package com.ech.backend.api.release.dto;

public record RollbackRequest(
        String actorEmployeeNo,
        String note
) {
}
