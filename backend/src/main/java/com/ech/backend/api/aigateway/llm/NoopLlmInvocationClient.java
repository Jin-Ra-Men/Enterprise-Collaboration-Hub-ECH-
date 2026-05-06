package com.ech.backend.api.aigateway.llm;

import java.util.Optional;

public final class NoopLlmInvocationClient implements LlmInvocationPort {

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public Optional<LlmCompletionResult> complete(String maskedUserPrompt, String purpose) {
        return Optional.empty();
    }
}
