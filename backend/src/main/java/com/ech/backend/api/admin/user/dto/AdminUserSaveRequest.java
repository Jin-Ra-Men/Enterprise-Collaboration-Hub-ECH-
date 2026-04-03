package com.ech.backend.api.admin.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminUserSaveRequest(
        @NotBlank String employeeNo,
        @NotBlank @Email String email,
        @NotBlank String name,
        String role,
        String status,
        String teamGroupCode,
        String jobLevelGroupCode,
        String jobPositionGroupCode,
        String jobTitleGroupCode
) {}
