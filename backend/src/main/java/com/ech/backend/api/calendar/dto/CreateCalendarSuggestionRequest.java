package com.ech.backend.api.calendar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public record CreateCalendarSuggestionRequest(
        @Size(max = 50) String ownerEmployeeNo,
        @NotBlank @Size(max = 500) String title,
        @Size(max = 8000) String description,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        Long originChannelId,
        Long originDmChannelId,
        @Size(max = 20) List<Long> originMessageIds,
        @Pattern(regexp = "^(?i)(USER|AI_ASSISTANT)$", message = "createdByActor must be USER or AI_ASSISTANT")
        String createdByActor
) {
}
