package com.ech.backend.api.channel;

import com.ech.backend.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    @DisplayName("JWT만으로 채널 생성 성공(본문에 createdByEmployeeNo 없음)")
    void create_channel_without_body_creator_uses_jwt() throws Exception {
        String body = """
                {
                  "name": "jwt전용채널",
                  "workspaceKey": "WS_JWT_ONLY",
                  "channelType": "PUBLIC"
                }
                """;

        mockMvc.perform(post("/api/channels")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("jwt전용채널"))
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

    @Test
    @DisplayName("채널 개설자가 멤버 내보내기 성공")
    void remove_member_by_creator_success() throws Exception {
        String createBody = """
                {
                  "name": "킥테스트채널",
                  "workspaceKey": "WS_KICK",
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

        long channelId = objectMapper.readTree(createResp).path("data").path("channelId").asLong();

        String joinBody = """
                {"employeeNo":"%s","memberRole":"MEMBER"}
                """.formatted(normalEmployeeNo);
        mockMvc.perform(post("/api/channels/" + channelId + "/members")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/channels/" + channelId + "/members")
                        .param("targetEmployeeNo", normalEmployeeNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members", hasSize(1)));
    }

    @Test
    @DisplayName("비개설자는 멤버 내보내기 403")
    void remove_member_forbidden_for_non_creator() throws Exception {
        String createBody = """
                {
                  "name": "킥금지채널",
                  "workspaceKey": "WS_KICK403",
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

        long channelId = objectMapper.readTree(createResp).path("data").path("channelId").asLong();

        String joinBody = """
                {"employeeNo":"%s","memberRole":"MEMBER"}
                """.formatted(normalEmployeeNo);
        mockMvc.perform(post("/api/channels/" + channelId + "/members")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/channels/" + channelId + "/members")
                        .param("targetEmployeeNo", normalEmployeeNo)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("개설자 본인 내보내기는 400")
    void remove_creator_self_bad_request() throws Exception {
        String createBody = """
                {
                  "name": "셀프킥",
                  "workspaceKey": "WS_SELFKICK",
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

        long channelId = objectMapper.readTree(createResp).path("data").path("channelId").asLong();

        mockMvc.perform(delete("/api/channels/" + channelId + "/members")
                        .param("targetEmployeeNo", adminEmployeeNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }
}
