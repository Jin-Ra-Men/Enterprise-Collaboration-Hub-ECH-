package com.ech.backend.api.user.dto;

public record UserProfileResponse(
        Long userId,
        String employeeNo,
        String name,
        String email,
        String department,
        String jobRank,
        String dutyTitle,
        String role,
        String status
) {
}
