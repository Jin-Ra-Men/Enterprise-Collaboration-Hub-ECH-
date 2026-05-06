package com.ech.backend.api.aigateway.dto;

/** Policy snapshot for clients (no secrets). */
public record AiGatewayStatusResponse(
        boolean externalLlmAllowed,
        String policyVersion,
        String defaultPolicySummary,
        int chatMaxRequestsPerMinute,
        int chatMaxRequestsPerHour,
        boolean llmHttpConfigured
) {
}
