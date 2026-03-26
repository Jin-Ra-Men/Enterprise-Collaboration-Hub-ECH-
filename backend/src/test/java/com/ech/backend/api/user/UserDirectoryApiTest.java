package com.ech.backend.api.user;

import com.ech.backend.BaseIntegrationTest;
import com.ech.backend.domain.org.OrgGroup;
import com.ech.backend.domain.org.OrgGroupCodes;
import com.ech.backend.domain.org.OrgGroupMemberRepository;
import com.ech.backend.domain.org.OrgGroupRepository;
import com.ech.backend.domain.org.OrgGroupMember;
import com.ech.backend.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("사용자 조직도 API (user-directory)")
class UserDirectoryApiTest extends BaseIntegrationTest {

    @BeforeEach
    void seedOrgGroupsForTestData() {
        orgGroupMemberRepository.deleteAll();
        orgGroupRepository.deleteAll();

        // org_groups/org_group_members는 H2에서 Postgres upsert 로직(ON CONFLICT)을 그대로 돌리기 어렵기 때문에,
        // users 시드 데이터를 기반으로 테스트용 최소 조직 매핑을 직접 생성한다.
        for (User u : userRepository.findAll()) {
            if (!"ACTIVE".equalsIgnoreCase(u.getStatus())) {
                continue;
            }

            String companyCodeNormalized = safeCompanyCode(u.getCompanyCode());
            String companyDisplayName = resolveCompanyDisplayName(u.getCompanyName(), companyCodeNormalized);
            String companyFp = OrgGroupCodes.fingerprintCompany(companyCodeNormalized, companyDisplayName);
            String companyGroupCode = OrgGroupCodes.prettyCompany(companyCodeNormalized, companyFp);

            OrgGroup company = findOrCreateCompany(companyGroupCode, companyDisplayName);

            String divisionDisplayName = resolveOrDefault(u.getDivisionName(), "미지정 본부");
            String divisionFp = OrgGroupCodes.fingerprintDivision(companyFp, divisionDisplayName);
            String divisionGroupCode = OrgGroupCodes.prettyDivision(companyFp, divisionFp);
            OrgGroup division = findOrCreateDivision(company, divisionGroupCode, divisionDisplayName);

            String teamDisplayName = resolveOrDefault(u.getTeamName(), "미지정 팀");
            String teamFp = OrgGroupCodes.fingerprintTeam(divisionFp, teamDisplayName);
            String teamGroupCode = OrgGroupCodes.prettyTeam(divisionFp, teamFp);
            OrgGroup team = findOrCreateTeam(company, division, teamGroupCode, teamDisplayName);

            orgGroupMemberRepository.findByUser_IdAndMemberGroupType(u.getId(), "TEAM").ifPresentOrElse(
                    existing -> {
                        if (!existing.getGroup().getId().equals(team.getId())) {
                            existing.setGroup(team);
                            orgGroupMemberRepository.save(existing);
                        }
                    },
                    () -> orgGroupMemberRepository.save(new OrgGroupMember(u, team, "TEAM"))
            );
        }
    }

    @Autowired
    private OrgGroupRepository orgGroupRepository;

    @Autowired
    private OrgGroupMemberRepository orgGroupMemberRepository;

    private OrgGroup findOrCreateCompany(String companyCode, String companyDisplayName) {
        return orgGroupRepository.findByGroupTypeAndGroupCode("COMPANY", companyCode)
                .orElseGet(() -> orgGroupRepository.save(new OrgGroup(
                        "COMPANY",
                        companyCode,
                        companyDisplayName,
                        null,
                        companyCode
                )));
    }

    private OrgGroup findOrCreateDivision(OrgGroup company, String divisionCode, String divisionDisplayName) {
        return orgGroupRepository.findByGroupTypeAndGroupCode("DIVISION", divisionCode)
                .orElseGet(() -> orgGroupRepository.save(new OrgGroup(
                        "DIVISION",
                        divisionCode,
                        divisionDisplayName,
                        company.getGroupCode(),
                        company.getGroupCode() + ";" + divisionCode
                )));
    }

    private OrgGroup findOrCreateTeam(OrgGroup company, OrgGroup division, String teamCode, String teamDisplayName) {
        return orgGroupRepository.findByGroupTypeAndGroupCode("TEAM", teamCode)
                .orElseGet(() -> orgGroupRepository.save(new OrgGroup(
                        "TEAM",
                        teamCode,
                        teamDisplayName,
                        division.getGroupCode(),
                        company.getGroupCode() + ";" + division.getGroupCode() + ";" + teamCode
                )));
    }

    private static String safeCompanyCode(String companyCode) {
        if (companyCode == null || companyCode.isBlank()) {
            return "GENERAL";
        }
        return companyCode.trim().toUpperCase();
    }

    private static String resolveCompanyDisplayName(String companyName, String companyCodeNormalized) {
        String cn = companyName == null ? null : companyName.trim();
        if (cn != null && !cn.isEmpty()) {
            return cn;
        }
        return switch (companyCodeNormalized) {
            case "EXTERNAL" -> "외부인력";
            case "COVIM365" -> "M365";
            default -> "내부";
        };
    }

    private static String resolveOrDefault(String value, String defaultValue) {
        String v = value == null ? null : value.trim();
        return (v == null || v.isEmpty()) ? defaultValue : v;
    }

    @Test
    @DisplayName("JWT로 GET /api/user-directory/organization 호출 시 200")
    void organization_ok() throws Exception {
        mockMvc.perform(get("/api/user-directory/organization")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companies").isArray())
                .andExpect(jsonPath("$.data.companies", hasSize(greaterThan(0))));
    }

    @Test
    @DisplayName("organization 에 companyGroupCode 쿼리 시 200")
    void organization_with_company_group_code() throws Exception {
        mockMvc.perform(get("/api/user-directory/organization")
                        .param("companyGroupCode", "NON_EXISTENT_CODE")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companies").isArray())
                .andExpect(jsonPath("$.data.companies", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/user-directory/organization-filters 200 및 전체 옵션(null 키)")
    void organization_filters_ok() throws Exception {
        mockMvc.perform(get("/api/user-directory/organization-filters")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.options").isArray())
                .andExpect(jsonPath("$.data.options[0].label").value("전체 (그룹사 공용)"))
                .andExpect(jsonPath("$.data.options[0].companyGroupCode").value(nullValue()))
                .andExpect(jsonPath("$.data.options[1].companyGroupCode").value(notNullValue()));
    }

    @Test
    @DisplayName("토큰 없이 organization 호출 시 401")
    void organization_without_token() throws Exception {
        mockMvc.perform(get("/api/user-directory/organization"))
                .andExpect(status().isUnauthorized());
    }
}
