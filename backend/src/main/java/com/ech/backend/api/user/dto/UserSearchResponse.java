package com.ech.backend.api.user.dto;

import java.time.OffsetDateTime;

public record UserSearchResponse(
        Long userId,
        String employeeNo,
        String name,
        String email,
        /** TEAM 그룹 display_name (UI 호환용으로 `department` 유지). */
        String department,
        String jobLevel,
        String jobPosition,
        String jobTitle,
        /** {@link com.ech.backend.domain.org.OrgGroup#getSortOrder()} — 직급 그룹 정렬(관리자 조직 설정과 동일). */
        Integer jobLevelSortOrder,
        /** 직위 그룹 정렬. */
        Integer jobPositionSortOrder,
        String role,
        String status,
        /** 조직도 등 동일 직급 정렬(먼저 생성된 사용자 우선). */
        OffsetDateTime createdAt,
        boolean profileImagePresent,
        /** 프로필 이미지 캐시 무효화 등(사용자 정보 변경 시 갱신). */
        OffsetDateTime updatedAt
) {
}
