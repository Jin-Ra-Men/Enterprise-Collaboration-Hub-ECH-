package com.ech.backend.api.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMessageRequest(
        @NotBlank @Size(max = 50) String senderId,
        @NotBlank @Size(max = 4000) String text
) {
}
