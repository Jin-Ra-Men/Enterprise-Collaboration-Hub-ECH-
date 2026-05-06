package com.ech.backend.api.work;

import com.ech.backend.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("내 할 일(my todos) API")
class WorkItemMyTodosApiTest extends BaseIntegrationTest {

    private String normalUserToken() {
        return userToken;
    }

    @Test
    @DisplayName("멘션 토큰이 포함된 메시지에서 만든 업무가 mentionLinked에 포함된다")
    void mention_linked_bucket_contains_work_from_mention_message() throws Exception {
        String createBody = """
                {
                  "name": "my-todos-mention",
                  "workspaceKey": "WS_MY_TODOS_M",
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
                { "employeeNo": "%s", "memberRole": "MEMBER" }
                """.formatted(normalEmployeeNo);
        mockMvc.perform(post("/api/channels/" + channelId + "/members")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBody))
                .andExpect(status().isOk());

        String mentionToken = "@{" + normalEmployeeNo + "|확인}";
        String msgJson = "{\"senderId\":\"" + adminEmployeeNo + "\",\"text\":\"" + mentionToken + " 할 일 검토 부탁\"}";

        String msgResp = mockMvc.perform(post("/api/channels/" + channelId + "/messages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(msgJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long messageId = objectMapper.readTree(msgResp).path("data").path("messageId").asLong();

        String wiBody = """
                {
                  "createdByEmployeeNo": "%s",
                  "title": "멘션 업무",
                  "status": "OPEN"
                }
                """.formatted(adminEmployeeNo);
        mockMvc.perform(post("/api/messages/" + messageId + "/work-items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wiBody))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/work-items/me/todos")
                        .header("Authorization", "Bearer " + normalUserToken())
                        .param("employeeNo", normalEmployeeNo)
                        .param("limitPerBucket", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mentionLinked", hasSize(1)))
                .andExpect(jsonPath("$.data.mentionLinked[0].title").value("멘션 업무"))
                .andExpect(jsonPath("$.data.mentionLinked[0].sourceMessageId").value((int) messageId));
    }

    @Test
    @DisplayName("마감이 과거인 미완료 업무는 overdue 버킷에 포함된다")
    void overdue_bucket_lists_open_work_with_past_due() throws Exception {
        String createBody = """
                {
                  "name": "my-todos-overdue",
                  "workspaceKey": "WS_MY_TODOS_O",
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

        String wiBody = """
                {
                  "createdByEmployeeNo": "%s",
                  "title": "지연 업무",
                  "status": "OPEN",
                  "dueAt": "2020-01-01T00:00:00Z"
                }
                """.formatted(adminEmployeeNo);

        mockMvc.perform(post("/api/channels/" + channelId + "/work-items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wiBody))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/work-items/me/todos")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("employeeNo", adminEmployeeNo)
                        .param("limitPerBucket", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overdue", hasSize(1)))
                .andExpect(jsonPath("$.data.overdue[0].title").value("지연 업무"));
    }

    @Test
    @DisplayName("워크스페이스 전체 내 할 일 응답 구조가 버킷별 배열을 포함한다")
    void todos_response_has_all_buckets() throws Exception {
        mockMvc.perform(get("/api/work-items/me/todos")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("employeeNo", adminEmployeeNo)
                        .param("limitPerBucket", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overdue").isArray())
                .andExpect(jsonPath("$.data.dueToday").isArray())
                .andExpect(jsonPath("$.data.mentionLinked").isArray())
                .andExpect(jsonPath("$.data.kanbanAssigned").isArray());
    }
}
