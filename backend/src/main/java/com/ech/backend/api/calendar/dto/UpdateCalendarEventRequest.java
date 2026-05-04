package com.ech.backend.api.calendar.dto;

import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record UpdateCalendarEventRequest(
        @Size(max = 500) String title,
        @Size(max = 8000) String description,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt
) {
}
