package com.ech.backend.api.aigateway.dto;

/** Policy snapshot for clients (no secrets). */
public record AiGatewayStatusResponse(
        boolean externalLlmAllowed,
        String policyVersion,
        String defaultPolicySummary,
        int chatMaxRequestsPerMinute,
        int chatMaxRequestsPerHour,
        boolean llmHttpConfigured,
        /** Effective max Unicode code points forwarded to LLM after masking (see {@code ai.gateway.llm-max-input-chars}). */
        int llmMaxInputChars,
        /** Current user's preference: master toggle for all AI assistant features. */
        boolean aiAssistantEnabled
) {
}
