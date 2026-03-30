package com.ech.backend.common.exception;

import com.ech.backend.api.errorlog.ErrorLogService;
import com.ech.backend.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.hibernate.LazyInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorLogService errorLogService;
    private final boolean exposeErrorDetail;

    public GlobalExceptionHandler(
            ErrorLogService errorLogService,
            @Value("${app.expose-error-detail:false}") boolean exposeErrorDetail
    ) {
        this.errorLogService = errorLogService;
        this.exposeErrorDetail = exposeErrorDetail;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("NOT_FOUND", "요청한 경로를 찾을 수 없습니다: " + exception.getResourcePath()));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(NoHandlerFoundException exception) {
        // /favicon.ico 같은 프론트 쪽 404는 불필요하므로 error_logs에 남기지 않는다.
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("NOT_FOUND", "요청한 경로를 찾을 수 없습니다."));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException exception,
                                                                  HttpServletRequest request) {
        // 로그인 실패(잘못된 사원번호/이메일 또는 비밀번호)는 사용자 입력 오류이므로 error_logs에 남기지 않는다.
        String requestUri = request != null ? request.getRequestURI() : "";
        boolean isLoginRequest = "/api/auth/login".equals(requestUri);
        if (!isLoginRequest) {
            safeLog("UNAUTHORIZED", exception, request);
        }
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("UNAUTHORIZED", exception.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException exception, HttpServletRequest request) {
        safeLog("FORBIDDEN", exception, request);
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("FORBIDDEN", exception.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException exception,
                                                             HttpServletRequest request) {
        safeLog("NOT_FOUND", exception, request);
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException exception, HttpServletRequest request) {
        safeLog("BAD_REQUEST", exception, request);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("BAD_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiResponse<Void>> handleIo(IOException exception, HttpServletRequest request) {
        safeLog("FILE_IO_ERROR", exception, request);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("FILE_IO_ERROR", "파일을 읽는 중 오류가 발생했습니다."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        safeLog("DATA_INTEGRITY", exception, request);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(
                        "DATA_INTEGRITY",
                        "DB 제약 위반으로 저장할 수 없습니다. users.employee_no 기준 스키마·외래키 이관을 확인하세요."
                ));
    }

    @ExceptionHandler(LazyInitializationException.class)
    public ResponseEntity<ApiResponse<Void>> handleLazyInit(
            LazyInitializationException exception,
            HttpServletRequest request
    ) {
        safeLog("LAZY_INIT", exception, request);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(
                        "LAZY_INIT",
                        "연관 데이터 로드 오류가 발생했습니다. 잠시 후 다시 시도하거나 서버 로그를 확인하세요."
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        safeLog("BAD_JSON", exception, request);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("BAD_JSON", "요청 본문(JSON) 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("요청 값이 올바르지 않습니다.");
        safeLog("VALIDATION_ERROR", new IllegalArgumentException(message), request);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnhandled(Exception exception, HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : "";
        log.error("Unhandled exception {} {}", path, exception.getClass().getName(), exception);
        safeLog("INTERNAL_SERVER_ERROR", exception, request);
        String message = "서버 내부 오류가 발생했습니다.";
        if (exposeErrorDetail) {
            message += " [" + summarizeException(exception) + "]";
        }
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("INTERNAL_SERVER_ERROR", message));
    }

    /** 사용자 응답에 넣을 짧은 예외 요약(개발용). */
    private static String summarizeException(Throwable exception) {
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String cls = exception.getClass().getSimpleName();
        String msg = root.getMessage();
        if (msg == null || msg.isBlank()) {
            return cls;
        }
        String flat = msg.replace('\n', ' ').replace('\r', ' ').trim();
        String combined = cls + ": " + flat;
        return combined.length() > 400 ? combined.substring(0, 400) + "…" : combined;
    }

    private void safeLog(String code, Exception exception, HttpServletRequest request) {
        try {
            errorLogService.logException(code, exception, request);
        } catch (Exception ignored) {
            // 에러 로깅 실패가 실제 응답 흐름을 깨지 않도록 보호한다.
        }
    }
}
