package com.ech.backend.api.retention;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ech.backend.api.retention.dto.ArchiveRunResultResponse;

/**
 * 보존 정책 자동 실행 스케줄러.
 * 매일 새벽 2시에 활성화된 모든 보존 정책을 순서대로 실행한다.
 */
@Component
public class ArchivingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ArchivingScheduler.class);

    private final RetentionPolicyService retentionPolicyService;

    public ArchivingScheduler(RetentionPolicyService retentionPolicyService) {
        this.retentionPolicyService = retentionPolicyService;
    }

    /** 매일 02:00에 활성화된 보존 정책 자동 실행 */
    @Scheduled(cron = "0 0 2 * * *")
    public void runScheduled() {
        log.info("[ArchivingScheduler] 보존 정책 자동 아카이빙 시작");
        List<ArchiveRunResultResponse> results = retentionPolicyService.runArchiving();
        if (results.isEmpty()) {
            log.info("[ArchivingScheduler] 활성화된 보존 정책 없음 — 스킵");
            return;
        }
        results.forEach(r -> log.info("[ArchivingScheduler] {} → {} (skipped={}) {}",
                r.resourceType(), r.processedCount(), r.skipped(), r.message()));
        log.info("[ArchivingScheduler] 보존 정책 자동 아카이빙 완료");
    }
}
