package com.ech.backend.api.calendar.dto;

import java.util.List;

public record CalendarConflictCheckResponse(boolean hasConflict, List<CalendarEventOverlapRow> overlappingEvents) {
}
