package com.ech.backend.api.channel.dto;

import com.ech.backend.domain.channel.ChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateChannelRequest(
        @NotBlank @Size(max = 100) String workspaceKey,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 2000) String description,
        @NotNull ChannelType channelType,
        @NotNull Long createdByUserId,
        List<Long> dmPeerUserIds
) {
    public CreateChannelRequest {
        dmPeerUserIds = dmPeerUserIds == null ? List.of() : List.copyOf(dmPeerUserIds);
    }
}
