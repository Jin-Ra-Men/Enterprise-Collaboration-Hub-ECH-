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
        String status
) {
}
