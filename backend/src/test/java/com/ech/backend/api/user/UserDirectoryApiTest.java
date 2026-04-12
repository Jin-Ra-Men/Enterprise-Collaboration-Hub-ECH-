package com.ech.backend.api.user;

import com.ech.backend.BaseIntegrationTest;
import com.ech.backend.domain.org.OrgGroup;
import com.ech.backend.domain.org.OrgGroupCodes;
import com.ech.backend.domain.org.OrgGroupMember;
import com.ech.backend.domain.org.OrgGroupMemberRepository;
import com.ech.backend.domain.org.OrgGroupRepository;
import com.ech.backend.domain.user.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

    @Autowired
    private OrgGroupRepository orgGroupRepository;

    @Autowired
    private OrgGroupMemberRepository orgGroupMemberRepository;

    @BeforeEach
    void seedOrgGroupsForTestData() {
        orgGroupMemberRepository.deleteAll();
        orgGroupRepository.deleteAll();

        String companyCodeNormalized = "GENERAL";
        String companyDisplayName = "CSTalk 통합테스트";
        String companyGroupCode = OrgGroupCodes.companyCode(companyCodeNormalized);
        OrgGroup company = findOrCreateCompany(companyGroupCode, companyDisplayName);

        String divisionDisplayName = "통합테스트본부";
        String divisionGroupCode = OrgGroupCodes.divisionCode(companyCodeNormalized, divisionDisplayName);
        OrgGroup division = findOrCreateDivision(company, divisionGroupCode, divisionDisplayName);

        String teamDisplayName = "통합테스트팀";
        String teamGroupCode = OrgGroupCodes.teamCode(divisionGroupCode, teamDisplayName);
        OrgGroup team = findOrCreateTeam(company, division, teamGroupCode, teamDisplayName);

        for (User u : userRepository.findAll()) {
            if (!"ACTIVE".equalsIgnoreCase(u.getStatus())) {
                continue;
            }
            orgGroupMemberRepository.findByUser_EmployeeNoAndMemberGroupType(u.getEmployeeNo(), "TEAM")
                    .ifPresentOrElse(
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

    @Test
    @DisplayName("미사용(INACTIVE) 사용자는 GET /api/user-directory/organization 응답에 포함되지 않음")
    void organization_excludes_inactive_users() throws Exception {
        User inactiveUser = userRepository.findByEmployeeNo(normalEmployeeNo).orElseThrow();
        inactiveUser.setStatus("INACTIVE");
        userRepository.saveAndFlush(inactiveUser);

        String body = mockMvc.perform(get("/api/user-directory/organization")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assertions.assertFalse(
                body.contains("\"employeeNo\":\"" + normalEmployeeNo + "\""),
                "INACTIVE 사용자 사번이 조직도 JSON에 포함되면 안 됨");
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
