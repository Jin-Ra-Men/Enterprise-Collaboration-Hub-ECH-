package com.ech.backend.api.calendar.dto;

public record CalendarShareDispatchResponse(
        int requestedInternalAttendees,
        int createdShareRequests,
        int skippedExistingPending
) {
}
