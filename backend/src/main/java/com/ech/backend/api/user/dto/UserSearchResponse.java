package com.ech.backend.api.user.dto;

public record UserSearchResponse(
        Long userId,
        String employeeNo,
        String name,
        String email,
        String department,
        String role,
        String status
) {
}
