package com.ech.backend.api.retention;

import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.api.retention.dto.ArchiveRunResultResponse;
import com.ech.backend.api.retention.dto.RetentionPolicyResponse;
import com.ech.backend.api.retention.dto.UpdateRetentionPolicyRequest;
import com.ech.backend.domain.audit.AuditEventType;
import com.ech.backend.domain.audit.AuditLogRepository;
import com.ech.backend.domain.error.ErrorLogRepository;
import com.ech.backend.domain.message.MessageRepository;
import com.ech.backend.domain.retention.RetentionPolicy;
import com.ech.backend.domain.retention.RetentionPolicyRepository;
import com.ech.backend.domain.retention.RetentionResourceType;
import com.ech.backend.domain.user.UserRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetentionPolicyService {

    private static final Logger log = LoggerFactory.getLogger(RetentionPolicyService.class);

    private final RetentionPolicyRepository policyRepository;
    private final MessageRepository messageRepository;
    private final AuditLogRepository auditLogRepository;
    private final ErrorLogRepository errorLogRepository;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    public RetentionPolicyService(
            RetentionPolicyRepository policyRepository,
            MessageRepository messageRepository,
            AuditLogRepository auditLogRepository,
            ErrorLogRepository errorLogRepository,
            AuditLogService auditLogService,
            UserRepository userRepository
    ) {
        this.policyRepository = policyRepository;
        this.messageRepository = messageRepository;
        this.auditLogRepository = auditLogRepository;
        this.errorLogRepository = errorLogRepository;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<RetentionPolicyResponse> listAll() {
        return policyRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public RetentionPolicyResponse updatePolicy(String resourceType, UpdateRetentionPolicyRequest request) {
        String normalizedType = resourceType.trim().toUpperCase();
        validateResourceType(normalizedType);

        RetentionPolicy policy = policyRepository.findByResourceType(normalizedType)
                .orElseThrow(() -> new IllegalArgumentException("보존 정책을 찾을 수 없습니다: " + normalizedType));

        Long updatedByUserId = request.updatedBy() == null
                ? null
                : userRepository.findByEmployeeNo(request.updatedBy())
                        .map(u -> u.getId())
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + request.updatedBy()));

        policy.update(request.retentionDays(), request.isEnabled(),
                request.description(), updatedByUserId);
        policyRepository.save(policy);

        auditLogService.safeRecord(
                AuditEventType.RETENTION_POLICY_UPDATED,
                updatedByUserId,
                "RETENTION_POLICY",
                policy.getId(),
                null,
                "resourceType=" + normalizedType + " retentionDays=" + request.retentionDays()
                        + " enabled=" + request.isEnabled(),
                null
        );
        return toResponse(policy);
    }

    /**
     * 활성화된 모든 정책에 대해 아카이빙을 실행한다.
     * 스케줄러 및 수동 트리거 모두 이 메서드를 호출한다.
     */
    @Transactional
    public List<ArchiveRunResultResponse> runArchiving() {
        List<RetentionPolicy> policies = policyRepository.findByIsEnabledTrue();
        List<ArchiveRunResultResponse> results = new ArrayList<>();

        for (RetentionPolicy policy : policies) {
            results.add(applyPolicy(policy));
        }
        return results;
    }

    /**
     * 특정 자원 유형에 대해 아카이빙을 수동 실행한다 (활성화 여부 무관).
     */
    @Transactional
    public ArchiveRunResultResponse runArchivingForType(String resourceType) {
        String normalizedType = resourceType.trim().toUpperCase();
        validateResourceType(normalizedType);

        RetentionPolicy policy = policyRepository.findByResourceType(normalizedType)
                .orElseThrow(() -> new IllegalArgumentException("보존 정책을 찾을 수 없습니다: " + normalizedType));

        return applyPolicy(policy);
    }

    private ArchiveRunResultResponse applyPolicy(RetentionPolicy policy) {
        String resourceType = policy.getResourceType();
        if (policy.getRetentionDays() <= 0) {
            return new ArchiveRunResultResponse(resourceType, 0, true, "영구 보관 정책 (0일 이하) — 스킵");
        }

        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(policy.getRetentionDays());
        OffsetDateTime now = OffsetDateTime.now();
        int count = 0;

        try {
            switch (RetentionResourceType.valueOf(resourceType)) {
                case MESSAGES -> count = messageRepository.archiveOlderThan(cutoff, now);
                case AUDIT_LOGS -> count = auditLogRepository.deleteOlderThan(cutoff);
                case ERROR_LOGS -> count = errorLogRepository.deleteOlderThan(cutoff);
            }
            log.info("[Archiving] {} → {} 건 처리 (cutoff={})", resourceType, count, cutoff);
            auditLogService.safeRecord(
                    AuditEventType.DATA_ARCHIVED,
                    null,
                    "RETENTION_POLICY",
                    policy.getId(),
                    null,
                    "resourceType=" + resourceType + " count=" + count + " cutoff=" + cutoff,
                    null
            );
            return new ArchiveRunResultResponse(resourceType, count, false, count + "건 처리 완료");
        } catch (Exception e) {
            log.error("[Archiving] {} 처리 중 오류: {}", resourceType, e.getMessage(), e);
            return new ArchiveRunResultResponse(resourceType, 0, true, "오류 발생: " + e.getMessage());
        }
    }

    private void validateResourceType(String resourceType) {
        try {
            RetentionResourceType.valueOf(resourceType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 자원 유형입니다: " + resourceType
                    + ". 허용 값: MESSAGES, AUDIT_LOGS, ERROR_LOGS");
        }
    }

    private RetentionPolicyResponse toResponse(RetentionPolicy p) {
        return new RetentionPolicyResponse(
                p.getId(),
                p.getResourceType(),
                p.getRetentionDays(),
                p.isEnabled(),
                p.getDescription(),
                p.getUpdatedBy() == null ? null : userRepository.findById(p.getUpdatedBy()).map(u -> u.getEmployeeNo()).orElse(null),
                p.getUpdatedAt()
        );
    }
}
