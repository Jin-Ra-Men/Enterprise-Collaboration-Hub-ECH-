package com.ech.backend.domain.release;

/**
 * 릴리즈 파일 상태.
 * <ul>
 *   <li>UPLOADED  — 업로드 완료, 미활성</li>
 *   <li>ACTIVE    — 현재 운영 버전</li>
 *   <li>PREVIOUS  — 이전 운영 버전 (롤백 대상)</li>
 *   <li>DEPRECATED — 폐기(삭제 가능)</li>
 * </ul>
 */
public enum ReleaseStatus {
    UPLOADED,
    ACTIVE,
    PREVIOUS,
    DEPRECATED
}
