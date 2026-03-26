package com.ech.backend.api.user;

import com.ech.backend.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("사용자 조직도 API (user-directory)")
class UserDirectoryApiTest extends BaseIntegrationTest {

    @Test
    @DisplayName("JWT로 GET /api/user-directory/organization 호출 시 200")
    void organization_ok() throws Exception {
        mockMvc.perform(get("/api/user-directory/organization")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companies").isArray());
    }

    @Test
    @DisplayName("organization 에 companyKey 쿼리 시 200")
    void organization_with_company_key() throws Exception {
        mockMvc.perform(get("/api/user-directory/organization")
                        .param("companyKey", "GENERAL")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companies").isArray());
    }

    @Test
    @DisplayName("GET /api/user-directory/organization-filters 200 및 전체 옵션(null 키)")
    void organization_filters_ok() throws Exception {
        mockMvc.perform(get("/api/user-directory/organization-filters")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.options").isArray())
                .andExpect(jsonPath("$.data.options[0].label").value("전체 (그룹사 공용)"))
                .andExpect(jsonPath("$.data.options[0].companyKey").value(nullValue()))
                .andExpect(jsonPath("$.data.options[0].companyName").value(nullValue()));
    }

    @Test
    @DisplayName("토큰 없이 organization 호출 시 401")
    void organization_without_token() throws Exception {
        mockMvc.perform(get("/api/user-directory/organization"))
                .andExpect(status().isUnauthorized());
    }
}
