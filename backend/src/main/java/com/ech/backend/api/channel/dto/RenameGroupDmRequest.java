package com.ech.backend.api.channel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameGroupDmRequest(
        @NotBlank @Size(max = 2000) String name
) {
}
