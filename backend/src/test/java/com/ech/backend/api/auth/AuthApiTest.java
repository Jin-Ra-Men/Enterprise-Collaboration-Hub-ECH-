package com.ech.backend.api.auth;

import com.ech.backend.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("인증 API 통합 테스트")
class AuthApiTest extends BaseIntegrationTest {

    @Test
    @DisplayName("로그인 성공 시 JWT 토큰이 반환된다")
    void login_success() throws Exception {
        String body = """
                {"loginId":"%s","password":"%s"}
                """.formatted(TEST_ADMIN_EMAIL, TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token", notNullValue()))
                .andExpect(jsonPath("$.data.userId", notNullValue()))
                .andExpect(jsonPath("$.error", nullValue()));
    }

    @Test
    @DisplayName("잘못된 비밀번호로 로그인 시 401 반환")
    void login_wrong_password() throws Exception {
        String body = """
                {"loginId":"%s","password":"wrongpassword"}
                """.formatted(TEST_ADMIN_EMAIL);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("존재하지 않는 계정으로 로그인 시 401 반환")
    void login_not_found() throws Exception {
        String body = """
                {"loginId":"nobody@ech.com","password":"any1234"}
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("유효한 JWT로 /me 호출 시 사용자 정보 반환")
    void me_with_valid_token() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(TEST_ADMIN_EMAIL))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("토큰 없이 /me 호출 시 401 반환")
    void me_without_token() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("잘못된 JWT로 /me 호출 시 401 반환")
    void me_with_invalid_token() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer invalidtoken.abc.def"))
                .andExpect(status().isUnauthorized());
    }
}
