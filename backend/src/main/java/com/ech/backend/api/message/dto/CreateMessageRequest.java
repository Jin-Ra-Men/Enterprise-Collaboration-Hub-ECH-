package com.ech.backend.api.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMessageRequest(
        @NotNull Long senderId,
        @NotBlank @Size(max = 4000) String text
) {
}
