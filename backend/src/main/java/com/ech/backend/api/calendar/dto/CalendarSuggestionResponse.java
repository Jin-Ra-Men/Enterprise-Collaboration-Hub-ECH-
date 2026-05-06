package com.ech.backend.api.calendar.dto;

import com.ech.backend.domain.calendar.CalendarSuggestionStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record CalendarSuggestionResponse(
        Long id,
        String ownerEmployeeNo,
        String title,
        String description,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        CalendarSuggestionStatus status,
        Long originChannelId,
        String originChannelName,
        String originChannelType,
        Long originDmChannelId,
        String originDmChannelName,
        String originDmChannelType,
        List<Long> originMessageIds,
        String createdByActor,
        Long confirmedEventId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
