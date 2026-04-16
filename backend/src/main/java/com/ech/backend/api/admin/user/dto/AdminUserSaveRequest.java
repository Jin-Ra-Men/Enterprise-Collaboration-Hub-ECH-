package com.ech.backend.api.admin.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AdminUserSaveRequest(
        @NotBlank String employeeNo,
        @NotBlank @Email String email,
        @NotBlank String name,
        String role,
        String status,
        @Min(0) Integer directorySortOrder,
        String teamGroupCode,
        String jobLevelGroupCode,
        String jobPositionGroupCode,
        String jobTitleGroupCode
) {}
