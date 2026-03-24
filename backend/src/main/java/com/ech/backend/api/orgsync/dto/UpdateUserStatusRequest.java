package com.ech.backend.api.orgsync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateUserStatusRequest(
        @NotBlank
        @Pattern(regexp = "ACTIVE|INACTIVE", message = "status는 ACTIVE 또는 INACTIVE여야 합니다.")
        String status
) {
}
