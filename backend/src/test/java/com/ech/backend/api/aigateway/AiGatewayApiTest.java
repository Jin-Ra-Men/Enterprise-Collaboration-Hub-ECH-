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
                .andExpect(jsonPath("$.data.defaultPolicySummary").exists());
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
}
