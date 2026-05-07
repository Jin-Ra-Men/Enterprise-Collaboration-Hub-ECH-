package com.ech.backend.api.calendar.dto;

import jakarta.validation.constraints.Size;

public record CalendarAttendeeInput(
        @Size(max = 20) String attendeeType,
        @Size(max = 50) String employeeNo,
        @Size(max = 200) String displayName,
        @Size(max = 320) String email
) {
}
