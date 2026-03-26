package com.ech.backend.api.orgsync.dto;

public record ExternalOrgUser(
        String employeeNo,
        String email,
        String name,
        String department,
        String companyName,
        String divisionName,
        String teamName,
        String jobRank,
        String dutyTitle,
        String role,
        String status,
        /** 조직도 회사 필터 코드. null 이면 동기화 시 GENERAL 로 저장. */
        String companyCode
) {
}
