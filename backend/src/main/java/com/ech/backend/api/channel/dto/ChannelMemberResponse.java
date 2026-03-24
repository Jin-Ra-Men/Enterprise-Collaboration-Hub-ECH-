package com.ech.backend.api.channel.dto;

import java.time.OffsetDateTime;

public record ChannelMemberResponse(
        Long userId,
        String memberRole,
        OffsetDateTime joinedAt
) {
}
