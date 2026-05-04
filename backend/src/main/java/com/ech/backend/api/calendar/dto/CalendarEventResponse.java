package com.ech.backend.api.calendar.dto;

import java.time.OffsetDateTime;

public record CalendarEventResponse(
        Long id,
        String ownerEmployeeNo,
        String title,
        String description,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        Long originChannelId,
        String originChannelName,
        String originChannelType,
        Long sharedFromShareId,
        String createdByActor,
        boolean inUse,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
