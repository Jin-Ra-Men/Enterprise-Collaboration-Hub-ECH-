package com.ech.backend.api.user.dto;

/**
 * 조직도 팝업 상단 회사 셀렉트 옵션.
 *
 * @param label             셀렉트 표시문구 (org_groups.display_name)
 * @param companyGroupCode {@code GET /organization?companyGroupCode=}에 넣을 값.
 *                          전체 옵션(ORGROOT)은 null.
 */
public record OrganizationCompanyFilterOption(String label, String companyGroupCode) {}
