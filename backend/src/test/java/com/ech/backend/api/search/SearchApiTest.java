package com.ech.backend.api.search;

import com.ech.backend.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("통합 검색 API 통합 테스트")
class SearchApiTest extends BaseIntegrationTest {

    @Test
    @DisplayName("키워드 검색 성공 - 결과 없어도 200 반환")
    void search_returns_200() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "테스트검색어없는것")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.query").value("테스트검색어없는것"))
                .andExpect(jsonPath("$.data.totalCount").value(0));
    }

    @Test
    @DisplayName("JWT 없이 검색 시 401 반환")
    void search_without_token_returns_401() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("키워드 미입력 시 400 반환")
    void search_empty_keyword_returns_400() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", " ")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("타입 필터(MESSAGES) 지정 검색")
    void search_with_type_filter() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "hello")
                        .param("type", "MESSAGES")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("MESSAGES"));
    }

    @Test
    @DisplayName("limit 파라미터 적용 확인")
    void search_with_limit() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "test")
                        .param("limit", "5")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }
}
