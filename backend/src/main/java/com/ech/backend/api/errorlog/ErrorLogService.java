package com.ech.backend.api.errorlog;

import com.ech.backend.api.errorlog.dto.ErrorLogResponse;
import com.ech.backend.domain.error.ErrorLog;
import com.ech.backend.domain.error.ErrorLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ErrorLogService {

    private static final int MAX_DEFAULT_LIMIT = 200;

    private final ErrorLogRepository errorLogRepository;

    public ErrorLogService(ErrorLogRepository errorLogRepository) {
        this.errorLogRepository = errorLogRepository;
    }

    @Transactional
    public void logException(String errorCode, Exception exception, HttpServletRequest request) {
        String safeMessage = sanitize(exception.getMessage(), 2000);
        ErrorLog row = new ErrorLog(
                errorCode,
                exception.getClass().getSimpleName(),
                safeMessage,
                request == null ? null : sanitize(request.getRequestURI(), 500),
                request == null ? null : sanitize(request.getMethod(), 20),
                parseLongHeader(request, "X-User-Id"),
                request == null ? null : sanitize(request.getHeader("X-Request-Id"), 100)
        );
        errorLogRepository.save(row);
    }

    public List<ErrorLogResponse> search(
            OffsetDateTime from,
            OffsetDateTime to,
            String errorCode,
            String pathKeyword,
            Integer limit
    ) {
        int size = limit == null ? 50 : Math.min(Math.max(limit, 1), MAX_DEFAULT_LIMIT);
        return errorLogRepository.search(
                        from,
                        to,
                        normalize(errorCode),
                        normalize(pathKeyword),
                        PageRequest.of(0, size)
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ErrorLogResponse toResponse(ErrorLog e) {
        return new ErrorLogResponse(
                e.getId(),
                e.getErrorCode(),
                e.getErrorClass(),
                e.getMessage(),
                e.getPath(),
                e.getHttpMethod(),
                e.getActorUserId(),
                e.getRequestId(),
                e.getCreatedAt()
        );
    }

    private static Long parseLongHeader(HttpServletRequest request, String key) {
        if (request == null) {
            return null;
        }
        String raw = request.getHeader(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String sanitize(String raw, int max) {
        if (raw == null) {
            return null;
        }
        // 대화 본문/민감정보 원문 저장을 피하기 위해 줄바꿈을 제거하고 길이를 제한한다.
        String flattened = raw.replace('\n', ' ').replace('\r', ' ').trim();
        return flattened.length() > max ? flattened.substring(0, max) : flattened;
    }
}
