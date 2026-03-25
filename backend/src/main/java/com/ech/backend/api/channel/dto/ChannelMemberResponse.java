package com.ech.backend.api.channel.dto;

import java.time.OffsetDateTime;

public record ChannelMemberResponse(
        Long userId,
        String name,
        String department,
        String jobRank,
        String dutyTitle,
        String memberRole,
        OffsetDateTime joinedAt
) {
}
