package com.ech.backend.api.admin.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrgGroupSaveRequest(
        @NotBlank String groupType,
        @NotBlank @Size(max = 32) String groupCode,
        @NotBlank @Size(max = 200) String displayName,
        String memberOfGroupCode,
        Integer sortOrder,
        Boolean isActive
) {}
