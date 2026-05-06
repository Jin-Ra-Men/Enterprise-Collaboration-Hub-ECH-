package com.ech.backend.api.aigateway;

import com.ech.backend.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AI 게이트웨이 레이트 리밋")
@TestPropertySource(
        properties = {
                "app.ai.chat-max-requests-per-minute=2",
                "app.ai.chat-max-requests-per-hour=0"
        })
class AiGatewayRateLimitApiTest extends BaseIntegrationTest {

    @Test
    @DisplayName("분당 한도 초과 시 429 AI_GATEWAY_RATE_LIMITED")
    void chat_returns_429_when_per_minute_limit_exceeded() throws Exception {
        String body = """
                {
                  "purpose": "rl-test",
                  "employeeNo": "%s",
                  "channelId": null,
                  "prompt": "ping"
                }
                """.formatted(adminEmployeeNo);

        mockMvc.perform(post("/api/ai/gateway/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/ai/gateway/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/ai/gateway/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AI_GATEWAY_RATE_LIMITED"));
    }
}
