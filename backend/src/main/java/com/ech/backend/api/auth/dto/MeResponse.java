package com.ech.backend.api.auth.dto;

public record MeResponse(
        Long userId,
        String employeeNo,
        String email,
        String name,
        String department,
        String role
) {
}
