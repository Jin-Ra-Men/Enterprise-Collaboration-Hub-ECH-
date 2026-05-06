package com.ech.backend.api.work;

import com.ech.backend.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("업무 마감·우선순위 API")
class WorkItemDuePriorityApiTest extends BaseIntegrationTest {

    @Test
    @DisplayName("채널 업무 생성 시 dueAt·priority 저장 및 목록·수정 반영")
    void create_list_update_due_and_priority() throws Exception {
        String createChannel = """
                {
                  "name": "업무우선순위테스트",
                  "workspaceKey": "WS_WI_DUE",
                  "channelType": "PUBLIC",
                  "createdByEmployeeNo": "%s"
                }
                """.formatted(adminEmployeeNo);

        String chResp = mockMvc.perform(post("/api/channels")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createChannel))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long channelId = objectMapper.readTree(chResp).path("data").path("channelId").asLong();

        String createWork = """
                {
                  "createdByEmployeeNo": "%s",
                  "title": "마감 테스트",
                  "description": null,
                  "status": "OPEN",
                  "dueAt": "2026-06-15T09:00:00Z",
                  "priority": "HIGH"
                }
                """.formatted(adminEmployeeNo);

        String wiResp = mockMvc.perform(post("/api/channels/" + channelId + "/work-items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createWork))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.dueAt").exists())
                .andReturn().getResponse().getContentAsString();
        long workId = objectMapper.readTree(wiResp).path("data").path("id").asLong();

        mockMvc.perform(get("/api/channels/" + channelId + "/work-items")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("employeeNo", adminEmployeeNo)
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value((int) workId))
                .andExpect(jsonPath("$.data[0].priority").value("HIGH"));

        String clearDue = """
                {
                  "actorEmployeeNo": "%s",
                  "clearDueAt": true,
                  "priority": "LOW"
                }
                """.formatted(adminEmployeeNo);

        mockMvc.perform(put("/api/work-items/" + workId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clearDue))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priority").value("LOW"))
                .andExpect(jsonPath("$.data.dueAt", nullValue()));
    }
}
