package com.ech.backend.api.channel.dto;

import java.time.OffsetDateTime;

public record ChannelMemberResponse(
        String employeeNo,
        String name,
        String department,
        String jobLevel,
        String jobPosition,
        String jobTitle,
        String memberRole,
        OffsetDateTime joinedAt,
        boolean profileImagePresent,
        long profileImageVersion
) {
}
