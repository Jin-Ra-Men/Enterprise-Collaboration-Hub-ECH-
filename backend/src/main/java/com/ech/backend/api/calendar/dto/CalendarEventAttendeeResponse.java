package com.ech.backend.api.calendar.dto;

public record CalendarEventAttendeeResponse(
        Long id,
        String attendeeType,
        String employeeNo,
        String displayName,
        String email,
        int sortOrder
) {
}
