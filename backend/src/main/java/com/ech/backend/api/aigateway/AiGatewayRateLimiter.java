package com.ech.backend.api.aigateway;

import com.ech.backend.common.exception.AiGatewayRateLimitedException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory sliding-window style limits per employee number (Phase 7-4 gateway slice).
 * Restart clears counters; cluster 환경에서는 Redis 등으로 교체 검토.
 */
@Component
public class AiGatewayRateLimiter {

    private final ConcurrentHashMap<String, MutableBuckets> buckets = new ConcurrentHashMap<>();

    public void checkChatOrThrow(String employeeNo, AiGatewayConfigurable gateway) {
        if (employeeNo == null || employeeNo.isBlank()) {
            return;
        }
        int perMin = Math.max(0, gateway.getChatMaxRequestsPerMinute());
        int perHour = Math.max(0, gateway.getChatMaxRequestsPerHour());
        if (perMin <= 0 && perHour <= 0) {
            return;
        }
        MutableBuckets b = buckets.computeIfAbsent(employeeNo.trim(), k -> new MutableBuckets());
        synchronized (b) {
            long now = System.currentTimeMillis();
            long minCut = now - 60_000L;
            long hourCut = now - 3_600_000L;
            b.minuteMarks.removeIf(t -> t < minCut);
            b.hourMarks.removeIf(t -> t < hourCut);
            if (perMin > 0 && b.minuteMarks.size() >= perMin) {
                throw new AiGatewayRateLimitedException("분당 AI 게이트웨이 호출 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.");
            }
            if (perHour > 0 && b.hourMarks.size() >= perHour) {
                throw new AiGatewayRateLimitedException("시간당 AI 게이트웨이 호출 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.");
            }
            b.minuteMarks.add(now);
            b.hourMarks.add(now);
        }
    }

    private static final class MutableBuckets {
        private final List<Long> minuteMarks = new ArrayList<>();
        private final List<Long> hourMarks = new ArrayList<>();
    }
}
