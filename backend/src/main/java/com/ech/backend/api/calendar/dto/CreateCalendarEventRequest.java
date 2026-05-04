package com.ech.backend.api.calendar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record CreateCalendarEventRequest(
        @Size(max = 50) String ownerEmployeeNo,
        @NotBlank @Size(max = 500) String title,
        @Size(max = 8000) String description,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt
) {
}
