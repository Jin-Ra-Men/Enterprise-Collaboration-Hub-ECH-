package com.ech.backend.api.aiassistant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ech.backend.BaseIntegrationTest;
import com.ech.backend.domain.aiassistant.AiSuggestionKind;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@DisplayName("AI 프로액티브 비서 / 제안함 API (Phase 7-3)")
class AiAssistantApiTest extends BaseIntegrationTest {

    @Autowired
    private AiAssistantService aiAssistantService;

    @Test
    @DisplayName("채널 관리자만 프로액티브 옵트인 변경 가능하고 일반 멤버는 403")
    void channel_proactive_opt_in_manager_only() throws Exception {
        long cid = createPublicChannelAndJoinNormalAsMember();

        mockMvc.perform(get("/api/channels/" + cid + "/ai-assistant/preference")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proactiveOptIn").value(false))
                .andExpect(jsonPath("$.data.dmProactiveBlocked").value(false));

        mockMvc.perform(put("/api/channels/" + cid + "/ai-assistant/preference")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proactiveOptIn\":true}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/channels/" + cid + "/ai-assistant/preference")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proactiveOptIn\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proactiveOptIn").value(true));

        mockMvc.perform(get("/api/channels/" + cid + "/ai-assistant/preference")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proactiveOptIn").value(true));
    }

    @Test
    @DisplayName("DM 채널은 프로액티브 차단 플래그이며 옵트인 변경 불가")
    void dm_blocks_proactive_toggle() throws Exception {
        String createDmBody = """
                {
                  "name": "AI DM test",
                  "workspaceKey": "WS_AI_DM_%s",
                  "channelType": "DM",
                  "createdByEmployeeNo": "%s",
                  "dmPeerEmployeeNos": ["%s"]
                }
                """.formatted(System.nanoTime(), adminEmployeeNo, normalEmployeeNo);

        String resp = mockMvc.perform(post("/api/channels")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDmBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long dmId = objectMapper.readTree(resp).path("data").path("channelId").asLong();

        mockMvc.perform(get("/api/channels/" + dmId + "/ai-assistant/preference")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dmProactiveBlocked").value(true))
                .andExpect(jsonPath("$.data.proactiveOptIn").value(false));

        mockMvc.perform(put("/api/channels/" + dmId + "/ai-assistant/preference")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proactiveOptIn\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("사용자 톤 설정 및 제안함 거절 후 쿨다운으로 적재 거부")
    void user_prefs_and_inbox_dismiss_cooldown() throws Exception {
        mockMvc.perform(put("/api/me/ai-assistant/preferences")
                        .param("employeeNo", normalEmployeeNo)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proactiveTone\":\"QUIET\",\"digestMode\":\"DAILY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proactiveTone").value("QUIET"))
                .andExpect(jsonPath("$.data.digestMode").value("DAILY"))
                .andExpect(jsonPath("$.data.aiAssistantEnabled").value(true));

        mockMvc.perform(get("/api/me/ai-assistant/preferences")
                        .param("employeeNo", normalEmployeeNo)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proactiveTone").value("QUIET"));

        long cid = createPublicChannelAndJoinNormalAsMember();

        mockMvc.perform(put("/api/channels/" + cid + "/ai-assistant/preference")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proactiveOptIn\":true}"))
                .andExpect(status().isOk());

        aiAssistantService.enqueueSuggestion(
                normalEmployeeNo,
                AiSuggestionKind.GENERIC,
                cid,
                "테스트 제안",
                "요약",
                "{\"demo\":true}",
                0.9);

        mockMvc.perform(get("/api/me/ai-suggestions")
                        .param("employeeNo", normalEmployeeNo)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("테스트 제안"));

        String listResp = mockMvc.perform(get("/api/me/ai-suggestions")
                        .param("employeeNo", normalEmployeeNo)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(listResp);
        long sid = root.path("data").get(0).path("id").asLong();

        mockMvc.perform(post("/api/me/ai-suggestions/" + sid + "/dismiss")
                        .param("employeeNo", normalEmployeeNo)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/me/ai-assistant/preferences")
                        .param("employeeNo", normalEmployeeNo)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proactiveCooldownActive").value(true));

        assertThatThrownBy(() -> aiAssistantService.enqueueSuggestion(
                        normalEmployeeNo,
                        AiSuggestionKind.GENERIC,
                        cid,
                        "두 번째",
                        null,
                        "{}",
                        null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("쿨다운");
    }

    @Test
    @DisplayName("채널 프로액티브 미옵트인이면 적재 시 거부")
    void enqueue_blocked_without_channel_opt_in() throws Exception {
        long cid = createPublicChannelAndJoinNormalAsMember();
        assertThatThrownBy(() -> aiAssistantService.enqueueSuggestion(
                        normalEmployeeNo,
                        AiSuggestionKind.GENERIC,
                        cid,
                        "blocked",
                        null,
                        "{}",
                        null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("옵트인");
    }

    @Test
    @DisplayName("AI 비서 비활성화 시 제안함 목록·거절은 403, 설정 조회·변경은 허용")
    void master_toggle_blocks_inbox_but_allows_pref_updates() throws Exception {
        mockMvc.perform(put("/api/me/ai-assistant/preferences")
                        .param("employeeNo", normalEmployeeNo)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aiAssistantEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiAssistantEnabled").value(false));

        mockMvc.perform(get("/api/me/ai-assistant/preferences")
                        .param("employeeNo", normalEmployeeNo)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiAssistantEnabled").value(false));

        mockMvc.perform(get("/api/me/ai-suggestions")
                        .param("employeeNo", normalEmployeeNo)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/me/ai-assistant/preferences")
                        .param("employeeNo", normalEmployeeNo)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aiAssistantEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiAssistantEnabled").value(true));
    }

    @Test
    @DisplayName("수신자 AI 비활성화 시 프로액티브 적재 거부")
    void enqueue_blocked_when_recipient_ai_disabled() throws Exception {
        mockMvc.perform(put("/api/me/ai-assistant/preferences")
                        .param("employeeNo", normalEmployeeNo)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aiAssistantEnabled\":false}"))
                .andExpect(status().isOk());

        long cid = createPublicChannelAndJoinNormalAsMember();
        mockMvc.perform(put("/api/channels/" + cid + "/ai-assistant/preference")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proactiveOptIn\":true}"))
                .andExpect(status().isOk());

        assertThatThrownBy(() -> aiAssistantService.enqueueSuggestion(
                        normalEmployeeNo,
                        AiSuggestionKind.GENERIC,
                        cid,
                        "x",
                        null,
                        "{}",
                        null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("사용하지 않도록");

        mockMvc.perform(put("/api/me/ai-assistant/preferences")
                        .param("employeeNo", normalEmployeeNo)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aiAssistantEnabled\":true}"))
                .andExpect(status().isOk());
    }

    private long createPublicChannelAndJoinNormalAsMember() throws Exception {
        String ws = "WS_AI_AST_" + System.nanoTime();
        String body = """
                {
                  "name": "AI비서테스트채널",
                  "workspaceKey": "%s",
                  "channelType": "PUBLIC",
                  "createdByEmployeeNo": "%s"
                }
                """.formatted(ws, adminEmployeeNo);

        String resp = mockMvc.perform(post("/api/channels")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error", nullValue()))
                .andReturn().getResponse().getContentAsString();
        long cid = objectMapper.readTree(resp).path("data").path("channelId").asLong();

        String joinBody = """
                {"employeeNo":"%s","memberRole":"MEMBER"}
                """.formatted(normalEmployeeNo);
        mockMvc.perform(post("/api/channels/" + cid + "/members")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        return cid;
    }
}
