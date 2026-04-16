package com.ech.backend.api.admin.user.dto;

import java.time.OffsetDateTime;

public record AdminUserListItemResponse(
        String employeeNo,
        String email,
        String name,
        String role,
        String status,
        Integer directorySortOrder,
        String teamGroupCode,
        String teamDisplayName,
        String jobLevelGroupCode,
        String jobLevelDisplayName,
        Integer jobLevelSortOrder,
        String jobPositionGroupCode,
        String jobPositionDisplayName,
        String jobTitleGroupCode,
        String jobTitleDisplayName,
        Integer jobTitleSortOrder,
        OffsetDateTime createdAt,
        boolean profileImagePresent
) {}
