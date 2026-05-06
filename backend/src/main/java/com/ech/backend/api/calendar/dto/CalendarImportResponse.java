package com.ech.backend.api.calendar.dto;

/** Result of Phase 6-5 bulk ICS import (per-event failures increment skipped only). */
public record CalendarImportResponse(int importedCount, int skippedCount) {
}
