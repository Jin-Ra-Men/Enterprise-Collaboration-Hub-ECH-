package com.ech.backend.api.user.dto;

public record UserProfileResponse(
        Long userId,
        String employeeNo,
        String name,
        String email,
        String department,
        String jobLevel,
        String jobPosition,
        String jobTitle,
        String role,
        String status
) {
}
