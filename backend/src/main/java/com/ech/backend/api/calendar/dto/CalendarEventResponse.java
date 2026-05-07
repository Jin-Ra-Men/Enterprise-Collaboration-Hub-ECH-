package com.ech.backend.api.calendar.dto;

import java.time.OffsetDateTime;
import java.util.List;

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
        Long originDmChannelId,
        String originDmChannelName,
        String originDmChannelType,
        List<Long> originMessageIds,
        Long sharedFromShareId,
        String createdByActor,
        boolean inUse,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<CalendarEventAttendeeResponse> attendees
) {
}
