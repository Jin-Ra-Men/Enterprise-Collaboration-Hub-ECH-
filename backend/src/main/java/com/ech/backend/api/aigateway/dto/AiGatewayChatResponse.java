package com.ech.backend.api.aigateway.dto;

public record AiGatewayChatResponse(String replyText, String model, Integer totalTokens) {
}
