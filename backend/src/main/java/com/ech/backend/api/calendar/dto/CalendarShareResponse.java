package com.ech.backend.api.calendar.dto;

import com.ech.backend.domain.calendar.CalendarShareStatus;
import java.time.OffsetDateTime;

public record CalendarShareResponse(
        Long id,
        String senderEmployeeNo,
        String recipientEmployeeNo,
        Long originChannelId,
        String originChannelName,
        String originChannelType,
        String title,
        String description,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        CalendarShareStatus status,
        OffsetDateTime expiresAt,
        Long acceptedEventId,
        Long sourceEventId,
        OffsetDateTime createdAt
) {
}
