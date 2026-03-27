package com.ech.backend.api.settings.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateSettingRequest(
        @NotNull String value,
        String description,
        @Size(max = 50) String updatedBy
) {
}
