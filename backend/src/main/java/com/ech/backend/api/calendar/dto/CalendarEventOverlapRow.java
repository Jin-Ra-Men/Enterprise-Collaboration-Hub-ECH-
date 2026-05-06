package com.ech.backend.api.calendar.dto;

import java.time.OffsetDateTime;

public record CalendarEventOverlapRow(Long id, String title, OffsetDateTime startsAt, OffsetDateTime endsAt) {
}
