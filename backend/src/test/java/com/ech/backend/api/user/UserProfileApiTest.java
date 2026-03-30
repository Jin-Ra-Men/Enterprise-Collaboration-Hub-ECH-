package com.ech.backend.api.user;

import com.ech.backend.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("사용자 프로필 API")
class UserProfileApiTest extends BaseIntegrationTest {

    @Test
    @DisplayName("JWT로 GET /api/users/profile?userId= 호출 시 프로필이 반환된다")
    void profile_by_query_ok() throws Exception {
        mockMvc.perform(get("/api/users/profile").param("userId", String.valueOf(normalUserId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(normalUserId.intValue()))
                .andExpect(jsonPath("$.data.email").value(TEST_USER_EMAIL));
    }

    @Test
    @DisplayName("JWT로 GET /api/users/{id}/profile(경로형)도 동작한다")
    void profile_by_path_ok() throws Exception {
        mockMvc.perform(get("/api/users/" + normalUserId + "/profile")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(normalUserId.intValue()));
    }

    @Test
    @DisplayName("JWT로 GET /api/users/profile?employeeNo= 호출 시 프로필이 반환된다")
    void profile_by_employee_no_ok() throws Exception {
        mockMvc.perform(get("/api/users/profile").param("employeeNo", normalEmployeeNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeNo").value(normalEmployeeNo))
                .andExpect(jsonPath("$.data.email").value(TEST_USER_EMAIL));
    }

    @Test
    @DisplayName("토큰 없이 프로필 호출 시 401")
    void profile_without_token() throws Exception {
        mockMvc.perform(get("/api/users/profile").param("userId", String.valueOf(normalUserId)))
                .andExpect(status().isUnauthorized());
    }
}
