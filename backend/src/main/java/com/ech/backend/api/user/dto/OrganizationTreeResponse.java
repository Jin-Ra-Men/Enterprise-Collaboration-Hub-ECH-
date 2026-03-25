package com.ech.backend.api.user.dto;

import java.util.List;

/** 회사 → 본부 → 팀 → 사용자 계층 (조직도 UI용). */
public record OrganizationTreeResponse(List<OrgCompanyResponse> companies) {}
