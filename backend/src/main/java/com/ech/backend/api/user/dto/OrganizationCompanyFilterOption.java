package com.ech.backend.api.user.dto;

/**
 * 조직도 팝업 상단 회사(테넌트) 셀렉트 옵션.
 *
 * @param filterValue API {@code /organization?companyKey=} 에 넣을 값. {@code ORGROOT} 는 전체.
 * @param label       DB의 회사 표시명 등 UI 라벨
 */
public record OrganizationCompanyFilterOption(String filterValue, String label) {}
