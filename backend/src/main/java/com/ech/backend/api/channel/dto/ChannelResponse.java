package com.ech.backend.api.channel.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ChannelResponse(
        Long channelId,
        String workspaceKey,
        String name,
        String description,
        String channelType,
        String createdByEmployeeNo,
        OffsetDateTime createdAt,
        List<ChannelMemberResponse> members
) {
}
