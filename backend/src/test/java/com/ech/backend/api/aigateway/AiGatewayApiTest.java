package com.ech.backend.api.aigateway;

import com.ech.backend.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AI 게이트웨이 API")
class AiGatewayApiTest extends BaseIntegrationTest {

    @Test
    @DisplayName("상태 조회에 정책 버전과 비허용 플래그가 포함된다")
    void status_includes_policy_defaults() throws Exception {
        mockMvc.perform(get("/api/ai/gateway/status").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.externalLlmAllowed").value(false))
                .andExpect(jsonPath("$.data.policyVersion").exists())
                .andExpect(jsonPath("$.data.defaultPolicySummary").exists())
                .andExpect(jsonPath("$.data.chatMaxRequestsPerMinute").value(30))
                .andExpect(jsonPath("$.data.chatMaxRequestsPerHour").value(300))
                .andExpect(jsonPath("$.data.llmHttpConfigured").value(false))
                .andExpect(jsonPath("$.data.llmMaxInputChars").value(8000));
    }

    @Test
    @DisplayName("chat는 기본 정책에서 403 AI_GATEWAY_BLOCKED")
    void chat_blocked_by_default() throws Exception {
        String body = """
                {
                  "purpose": "test-purpose",
                  "employeeNo": "%s",
                  "channelId": null,
                  "prompt": "hello"
                }
                """.formatted(adminEmployeeNo);

        mockMvc.perform(post("/api/ai/gateway/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AI_GATEWAY_BLOCKED"));
    }

    @Test
    @DisplayName("근거 message_id만 있고 channelId가 없으면 400")
    void chat_rejects_citations_without_channel() throws Exception {
        String body = """
                {
                  "purpose": "reply-draft",
                  "employeeNo": "%s",
                  "prompt": "draft",
                  "citedMessageIds": [1]
                }
                """.formatted(adminEmployeeNo);

        mockMvc.perform(post("/api/ai/gateway/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("유효한 근거 인용이어도 기본 정책이면 403 유지")
    void chat_with_valid_citations_still_blocked_by_policy() throws Exception {
        String createBody = """
                {
                  "name": "ai-gateway-evidence-ch",
                  "workspaceKey": "WS_AI_GW_EV",
                  "channelType": "PUBLIC",
                  "createdByEmployeeNo": "%s"
                }
                """.formatted(adminEmployeeNo);

        String createResp = mockMvc.perform(post("/api/channels")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long channelId = objectMapper.readTree(createResp).path("data").path("channelId").asLong();

        String msgBody = """
                { "senderId": "%s", "text": "evidence-root" }
                """.formatted(adminEmployeeNo);
        String msgResp = mockMvc.perform(post("/api/channels/" + channelId + "/messages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(msgBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = objectMapper.readTree(msgResp).path("data").path("messageId").asLong();

        String gwBody = """
                {
                  "purpose": "reply-draft",
                  "employeeNo": "%s",
                  "channelId": %d,
                  "prompt": "suggested reply",
                  "citedMessageIds": [%d]
                }
                """.formatted(adminEmployeeNo, channelId, messageId);

        mockMvc.perform(post("/api/ai/gateway/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gwBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AI_GATEWAY_BLOCKED"));
    }

    @Test
    @DisplayName("채널과 맞지 않는 근거 message_id는 400")
    void chat_rejects_unknown_message_in_channel() throws Exception {
        String createBody = """
                {
                  "name": "ai-gateway-bogus-ch",
                  "workspaceKey": "WS_AI_GW_BG",
                  "channelType": "PUBLIC",
                  "createdByEmployeeNo": "%s"
                }
                """.formatted(adminEmployeeNo);

        String createResp = mockMvc.perform(post("/api/channels")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long channelId = objectMapper.readTree(createResp).path("data").path("channelId").asLong();

        String gwBody = """
                {
                  "purpose": "reply-draft",
                  "employeeNo": "%s",
                  "channelId": %d,
                  "prompt": "x",
                  "citedMessageIds": [999999999]
                }
                """.formatted(adminEmployeeNo, channelId);

        mockMvc.perform(post("/api/ai/gateway/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gwBody))
                .andExpect(status().isBadRequest());
    }
}
