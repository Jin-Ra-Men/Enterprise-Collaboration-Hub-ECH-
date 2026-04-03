package com.ech.backend.api.user.dto;

import java.util.List;

/**
 * 회사(COMPANY) 응답. directMembers = 특정 본부/팀에 속하지 않고 회사에 직속된 사용자(대표이사 등).
 */
public record OrgCompanyResponse(
        String name,
        List<UserSearchResponse> directMembers,
        List<OrgDivisionResponse> divisions
) {}
