package com.ech.backend.api.channel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameChannelRequest(
        @NotBlank @Size(max = 100) String name
) {
}
