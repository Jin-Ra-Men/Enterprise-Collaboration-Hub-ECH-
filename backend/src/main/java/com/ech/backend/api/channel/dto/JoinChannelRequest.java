package com.ech.backend.api.channel.dto;

import com.ech.backend.domain.channel.ChannelMemberRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JoinChannelRequest(
        @NotBlank @Size(max = 50) String employeeNo,
        @NotNull ChannelMemberRole memberRole
) {
}
