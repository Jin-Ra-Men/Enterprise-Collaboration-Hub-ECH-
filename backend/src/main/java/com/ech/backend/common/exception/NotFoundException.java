package com.ech.backend.common.exception;

/**
 * 요청한 리소스가 존재하지 않을 때 사용하는 예외.
 * GlobalExceptionHandler에 의해 HTTP 404 Not Found로 변환된다.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
