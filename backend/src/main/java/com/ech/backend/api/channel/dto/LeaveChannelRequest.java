package com.ech.backend.api.channel.dto;

import jakarta.validation.constraints.Size;

public record LeaveChannelRequest(
        @Size(max = 50) String delegateManagerEmployeeNo
) {
}
