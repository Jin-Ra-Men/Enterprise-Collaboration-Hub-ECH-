package com.ech.backend.api.admin.org.dto;

public record OrgGroupResponse(
        Long id,
        String groupType,
        String groupCode,
        String displayName,
        String memberOfGroupCode,
        String groupPath,
        int sortOrder,
        boolean isActive
) {}
