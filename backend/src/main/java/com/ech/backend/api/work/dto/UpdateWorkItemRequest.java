package com.ech.backend.api.work.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateWorkItemRequest(
        @NotBlank @Size(max = 50) String actorEmployeeNo,
        @Size(max = 500) String title,
        @Size(max = 8000) String description,
        @Size(max = 50) String status
) {
}
