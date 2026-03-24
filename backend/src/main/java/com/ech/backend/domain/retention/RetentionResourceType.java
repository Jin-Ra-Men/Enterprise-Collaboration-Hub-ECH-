package com.ech.backend.domain.retention;

/**
 * 보존 정책이 적용되는 자원 유형.
 * <ul>
 *   <li>MESSAGES — 채널 메시지 (archived_at 기반 소프트 아카이브)</li>
 *   <li>AUDIT_LOGS — 감사 이벤트 로그 (만료 시 물리 삭제)</li>
 *   <li>ERROR_LOGS — 운영 오류 로그 (만료 시 물리 삭제)</li>
 * </ul>
 */
public enum RetentionResourceType {
    MESSAGES,
    AUDIT_LOGS,
    ERROR_LOGS
}
