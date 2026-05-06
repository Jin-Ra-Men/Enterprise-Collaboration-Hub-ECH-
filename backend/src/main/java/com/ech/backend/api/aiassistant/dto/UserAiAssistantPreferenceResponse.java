package com.ech.backend.api.aiassistant.dto;

public record UserAiAssistantPreferenceResponse(
        String proactiveTone,
        String digestMode,
        boolean proactiveCooldownActive,
        boolean aiAssistantEnabled
) {
}
