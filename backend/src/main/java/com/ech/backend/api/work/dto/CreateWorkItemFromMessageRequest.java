package com.ech.backend.api.work.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record CreateWorkItemFromMessageRequest(
        @NotBlank @Size(max = 50) String createdByEmployeeNo,
        @Size(max = 500) String title,
        @Size(max = 8000) String description,
        @Size(max = 50) String status,
        OffsetDateTime dueAt,
        @Size(max = 20)
        @Pattern(regexp = "^(?i)(LOW|NORMAL|HIGH)$", message = "priority must be LOW, NORMAL, or HIGH")
        String priority
) {
}
