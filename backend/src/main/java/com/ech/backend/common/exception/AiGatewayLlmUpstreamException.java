package com.ech.backend.common.exception;

/** 외부 LLM HTTP 호출 실패(네트워크·비정상 응답). 민감 정보를 메시지에 넣지 않는다. */
public class AiGatewayLlmUpstreamException extends RuntimeException {

    public AiGatewayLlmUpstreamException(String message) {
        super(message);
    }

    public AiGatewayLlmUpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
