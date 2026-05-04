package com.ech.backend.api.calendar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record CreateCalendarShareRequest(
        @NotBlank @Size(max = 50) String senderEmployeeNo,
        @NotBlank @Size(max = 50) String recipientEmployeeNo,
        /** Required when {@code sourceEventId} is null; ignored when copying from source event. */
        @Size(max = 500) String title,
        @Size(max = 8000) String description,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        /** When set, title/description/start/end are taken from this event (must belong to sender). */
        Long sourceEventId,
        /** Optional; default 7 days from creation when null. */
        OffsetDateTime expiresAt
) {
}
