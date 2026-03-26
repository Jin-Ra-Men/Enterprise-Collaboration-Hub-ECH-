package com.ech.backend.api.user.dto;

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
        String status
) {
}
