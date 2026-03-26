package com.ech.backend.api.orgsync.dto;

public record ExternalOrgUser(
        String employeeNo,
        String email,
        String name,
        String companyName,
        String divisionName,
        String teamName,
        /** 직급(예: 사원, 대리). */
        String jobLevel,
        /** 직위(예: 대표이사, 사장, 부사장). */
        String jobPosition,
        /** 직책(예: 팀장, 팀원, PM). */
        String jobTitle,
        String role,
        String status,
        /** 조직도 회사 필터 코드. null 이면 동기화 시 GENERAL 로 저장. */
        String companyCode
) {
}
