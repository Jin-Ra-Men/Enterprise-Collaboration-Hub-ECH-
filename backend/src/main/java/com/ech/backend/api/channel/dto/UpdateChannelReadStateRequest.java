package com.ech.backend.api.channel.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateChannelReadStateRequest(
        @NotBlank @Size(max = 50) String employeeNo,
        @NotNull Long lastReadMessageId
) {
}
