package com.ech.backend.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateThemePreferenceRequest(
        @NotBlank String theme
) {
}
