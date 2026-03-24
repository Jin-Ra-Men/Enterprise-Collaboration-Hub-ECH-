package com.ech.backend.api.channel.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateChannelReadStateRequest(
        @NotNull Long userId,
        @NotNull Long lastReadMessageId
) {
}
