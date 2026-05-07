package com.ech.backend.api.calendar.dto;

import jakarta.validation.Valid;
import java.util.List;

public record ReplaceCalendarEventAttendeesRequest(@Valid List<CalendarAttendeeInput> attendees) {
}
