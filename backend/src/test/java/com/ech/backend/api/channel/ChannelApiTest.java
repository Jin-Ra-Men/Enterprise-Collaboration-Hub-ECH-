package com.ech.backend.api.channel;

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

@DisplayName("채널 API 통합 테스트")
class ChannelApiTest extends BaseIntegrationTest {

    @Test
    @DisplayName("MANAGER 이상 권한으로 채널 생성 성공")
    void create_channel_as_admin() throws Exception {
        String body = """
                {
                  "name": "테스트채널",
                  "workspaceKey": "WS_TEST",
                  "channelType": "PUBLIC",
                  "createdByEmployeeNo": "%s"
                }
                """.formatted(adminEmployeeNo);

        mockMvc.perform(post("/api/channels")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("테스트채널"))
                .andExpect(jsonPath("$.data.channelId", notNullValue()))
                .andExpect(jsonPath("$.error", nullValue()));
    }

    @Test
    @DisplayName("MEMBER 권한으로 채널 생성 성공 (모든 사용자 채널 생성 허용)")
    void create_channel_as_member_success() throws Exception {
        String body = """
                {
                  "name": "멤버채널",
                  "workspaceKey": "WS_TEST",
                  "channelType": "PUBLIC",
                  "createdByEmployeeNo": "%s"
                }
                """.formatted(normalEmployeeNo);

        mockMvc.perform(post("/api/channels")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channelId", notNullValue()))
                .andExpect(jsonPath("$.error", nullValue()));
    }

    @Test
    @DisplayName("채널 단건 조회 성공")
    void get_channel() throws Exception {
        // 채널 생성 후 조회
        String createBody = """
                {
                  "name": "조회테스트채널",
                  "workspaceKey": "WS_GET",
                  "channelType": "PUBLIC",
                  "createdByEmployeeNo": "%s"
                }
                """.formatted(adminEmployeeNo);

        String createResp = mockMvc.perform(post("/api/channels")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long channelId = objectMapper.readTree(createResp).path("data").path("channelId").asLong();

        mockMvc.perform(get("/api/channels/" + channelId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channelId").value(channelId));
    }

    @Test
    @DisplayName("존재하지 않는 채널 조회 시 404 반환")
    void get_channel_not_found() throws Exception {
        mockMvc.perform(get("/api/channels/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
