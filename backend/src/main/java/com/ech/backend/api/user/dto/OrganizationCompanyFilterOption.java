package com.ech.backend.api.user.dto;

/**
 * 조직도 팝업 상단 회사 셀렉트 옵션. DB의 (company_key, company_name) 조합별로 한 줄씩.
 *
 * @param label       셀렉트 표시문구(보통 {@code company_name})
 * @param companyKey  {@code GET /organization} 의 {@code companyKey}. 전체 옵션은 null.
 * @param companyName {@code GET /organization} 의 {@code companyName}. null 이면 회사명으로 추가 필터 없음.
 *                    빈 문자열({@code ""})이면 {@code company_name} 이 비어 있는 행만.
 */
public record OrganizationCompanyFilterOption(String label, String companyKey, String companyName) {}
