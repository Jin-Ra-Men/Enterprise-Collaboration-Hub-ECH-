package com.ech.backend.api.aigateway.llm;

/** Successful outbound completion metadata (no raw upstream payload). */
public record LlmCompletionResult(String replyText, String model, Integer totalTokens) {
}
