package com.ech.backend.api.aigateway.llm;

import java.util.Optional;

public interface LlmInvocationPort {

    boolean isConfigured();

    /**
     * Sends {@code maskedUserPrompt} only — caller must apply {@link com.ech.backend.api.aigateway.AiGatewayPiiMasker} first.
     */
    Optional<LlmCompletionResult> complete(String maskedUserPrompt, String purpose);
}
