package com.ech.backend.api.aiassistant.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateChannelAiAssistantPreferenceRequest(@NotNull Boolean proactiveOptIn) {
}
