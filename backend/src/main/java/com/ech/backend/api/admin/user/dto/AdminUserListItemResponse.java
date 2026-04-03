package com.ech.backend.api.admin.user.dto;

import java.time.OffsetDateTime;

public record AdminUserListItemResponse(
        String employeeNo,
        String email,
        String name,
        String role,
        String status,
        String teamGroupCode,
        String teamDisplayName,
        String jobLevelGroupCode,
        String jobLevelDisplayName,
        String jobPositionGroupCode,
        String jobPositionDisplayName,
        String jobTitleGroupCode,
        String jobTitleDisplayName,
        OffsetDateTime createdAt
) {}
