package com.ech.backend.api.aiassistant.dto;

import com.ech.backend.domain.aiassistant.AiAssistantTone;
import com.ech.backend.domain.aiassistant.AiSuggestionDigestMode;

public record UpdateUserAiAssistantPreferenceRequest(
        AiAssistantTone proactiveTone,
        AiSuggestionDigestMode digestMode,
        Boolean aiAssistantEnabled
) {
}
