package com.ech.backend.api.user.dto;

import java.util.List;

public record DepartmentGroupResponse(
        String department,
        List<UserSearchResponse> users
) {
}
