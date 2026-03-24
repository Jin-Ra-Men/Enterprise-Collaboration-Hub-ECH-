package com.ech.backend.api.auth.dto;

public record LoginResponse(
        String token,
        Long userId,
        String employeeNo,
        String email,
        String name,
        String department,
        String role
) {
}
