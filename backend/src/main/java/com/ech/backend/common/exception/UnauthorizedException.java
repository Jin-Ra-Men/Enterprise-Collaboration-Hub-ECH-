package com.ech.backend.common.exception;

/**
 * 인증 실패 또는 자격 증명 불일치 시 사용하는 예외.
 * GlobalExceptionHandler에 의해 HTTP 401 Unauthorized로 변환된다.
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
