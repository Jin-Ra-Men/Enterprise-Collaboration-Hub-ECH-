package com.ech.backend.common.exception;

/** POST /api/ai/gateway/chat 분·시간당 호출 한도 초과. */
public class AiGatewayRateLimitedException extends RuntimeException {

    public AiGatewayRateLimitedException(String message) {
        super(message);
    }
}
