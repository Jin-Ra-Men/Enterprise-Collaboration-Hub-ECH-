package com.ech.backend.api.work.dto;

/**
 * 마감 지연·임박 업무 건수(목록 상한과 무관한 전체 집계).
 * 임박은 서버 시각 기준 {@code dueAt ∈ (now, now + dueSoonHours]}.
 */
public record TodoBadgeCounts(long overdueTotal, long dueSoonTotal, int dueSoonHours) {
}
