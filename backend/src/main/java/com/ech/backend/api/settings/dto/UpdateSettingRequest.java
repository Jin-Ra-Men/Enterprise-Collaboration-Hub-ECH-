package com.ech.backend.api.settings.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateSettingRequest(
        @NotNull String value,
        String description,
        Long updatedBy
) {
}
