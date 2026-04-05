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
        String role,
        String status,
        /** 조직도 등 동일 직급 정렬(먼저 생성된 사용자 우선). */
        OffsetDateTime createdAt
) {
}
