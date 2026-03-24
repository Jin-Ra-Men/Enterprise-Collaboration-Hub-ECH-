package com.ech.backend.api.channel.dto;

import com.ech.backend.domain.channel.ChannelMemberRole;
import jakarta.validation.constraints.NotNull;

public record JoinChannelRequest(
        @NotNull Long userId,
        @NotNull ChannelMemberRole memberRole
) {
}
