package com.ech.backend.api.user.dto;

import java.util.List;

/**
 * 본부(DIVISION) 응답. directMembers = 특정 팀에 속하지 않고 본부에 직속된 사용자(본부장 등).
 */
public record OrgDivisionResponse(
        String name,
        List<UserSearchResponse> directMembers,
        List<OrgTeamResponse> teams
) {}
