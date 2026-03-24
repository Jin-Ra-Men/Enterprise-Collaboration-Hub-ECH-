package com.ech.backend.api.auditlog;

import com.ech.backend.api.auditlog.dto.AuditLogResponse;
import com.ech.backend.domain.audit.AuditEventType;
import com.ech.backend.domain.audit.AuditLog;
import com.ech.backend.domain.audit.AuditLogRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final int MAX_LIMIT = 500;
    private static final int DEFAULT_LIMIT = 100;
    private static final int DETAIL_MAX_LEN = 500;

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * 이벤트 기록. 호출 서비스의 트랜잭션과 분리(REQUIRES_NEW)해 로깅 실패가 비즈니스 흐름에 영향을 주지 않도록 한다.
     * 단, 동기 호출이므로 스택에서 예외가 발생해도 safeRecord 래퍼가 삼킨다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            AuditEventType eventType,
            Long actorUserId,
            String resourceType,
            Long resourceId,
            String workspaceKey,
            String detail,
            String requestId
    ) {
        String safeDetail = sanitize(detail);
        auditLogRepository.save(new AuditLog(
                eventType,
                actorUserId,
                resourceType,
                resourceId,
                workspaceKey,
                safeDetail,
                sanitize(requestId, 100)
        ));
    }

    /** 예외를 삼키는 안전 래퍼 — 감사 로깅 실패가 응답 흐름을 막지 않게 한다. */
    public void safeRecord(
            AuditEventType eventType,
            Long actorUserId,
            String resourceType,
            Long resourceId,
            String workspaceKey,
            String detail,
            String requestId
    ) {
        try {
            record(eventType, actorUserId, resourceType, resourceId, workspaceKey, detail, requestId);
        } catch (Exception e) {
            log.warn("감사 로그 기록 실패 (eventType={}, resourceType={}, resourceId={}): {}",
                    eventType, resourceType, resourceId, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> search(
            OffsetDateTime from,
            OffsetDateTime to,
            Long actorUserId,
            String eventTypeStr,
            String resourceType,
            String workspaceKey,
            Integer limit
    ) {
        int size = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        AuditEventType eventType = parseEventType(eventTypeStr);
        String normalizedResourceType = normalize(resourceType);
        String normalizedWorkspaceKey = normalize(workspaceKey);

        return auditLogRepository
                .search(from, to, actorUserId, eventType, normalizedResourceType, normalizedWorkspaceKey,
                        PageRequest.of(0, size))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AuditLogResponse toResponse(AuditLog a) {
        return new AuditLogResponse(
                a.getId(),
                a.getEventType().name(),
                a.getActorUserId(),
                a.getResourceType(),
                a.getResourceId(),
                a.getWorkspaceKey(),
                a.getDetail(),
                a.getRequestId(),
                a.getCreatedAt()
        );
    }

    private static AuditEventType parseEventType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AuditEventType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
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

    private static String sanitize(String raw) {
        return sanitize(raw, DETAIL_MAX_LEN);
    }

    private static String sanitize(String raw, int max) {
        if (raw == null) {
            return null;
        }
        String flattened = raw.replace('\n', ' ').replace('\r', ' ').trim();
        return flattened.length() > max ? flattened.substring(0, max) : flattened;
    }
}
