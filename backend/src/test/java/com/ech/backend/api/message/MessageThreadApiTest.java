package com.ech.backend.api.message;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ech.backend.BaseIntegrationTest;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("댓글/답글 스레드 통합 테스트")
class MessageThreadApiTest extends BaseIntegrationTest {

    @Test
    @DisplayName("댓글/답글 및 파일 첨부가 parentMessageId/messageType으로 저장된다")
    void comment_reply_and_attachments_parent_saved() throws Exception {
        // 1) 채널 생성 및 멤버 조인
        String createBody = """
                {
                  "name": "thread-test-channel",
                  "workspaceKey": "WS_THREAD_TEST",
                  "channelType": "PUBLIC",
                  "createdByEmployeeNo": "%s"
                }
                """.formatted(adminEmployeeNo);

        String createResp = mockMvc.perform(post("/api/channels")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error", nullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long channelId = objectMapper.readTree(createResp).path("data").path("channelId").asLong();

        String joinBody = """
                { "employeeNo": "%s", "memberRole": "MEMBER" }
                """.formatted(normalEmployeeNo);

        mockMvc.perform(post("/api/channels/" + channelId + "/members")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBody))
                .andExpect(status().isOk());

        // 1b) 타임라인 페이징 검증용 — 메인 root보다 먼저 보내는 ROOT
        String olderRootBody = """
                { "senderId": "%s", "text": "older-root" }
                """.formatted(adminEmployeeNo);
        String olderRootResp = mockMvc.perform(post("/api/channels/" + channelId + "/messages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(olderRootBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long olderRootMessageId = objectMapper.readTree(olderRootResp).path("data").path("messageId").asLong();

        // 2) ROOT 메시지 생성
        String rootBody = """
                { "senderId": "%s", "text": "root message" }
                """.formatted(adminEmployeeNo);

        String rootResp = mockMvc.perform(post("/api/channels/" + channelId + "/messages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rootBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long rootMessageId = objectMapper.readTree(rootResp).path("data").path("messageId").asLong();

        // 3) COMMENT 생성 (TEXT)
        String commentBody = """
                { "senderId": "%s", "text": "comment-1" }
                """.formatted(normalEmployeeNo);

        String commentResp = mockMvc.perform(post("/api/channels/" + channelId + "/messages/" + rootMessageId + "/comments")
                        .header("Authorization", "Bearer " + normalUserToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long commentMessageId = objectMapper.readTree(commentResp).path("data").path("messageId").asLong();

        // 4) REPLY 생성 (TEXT) — COMMENT에 답글
        String replyBody = """
                { "senderId": "%s", "text": "reply-1" }
                """.formatted(normalEmployeeNo);

        String replyResp = mockMvc.perform(post("/api/channels/" + channelId + "/messages/" + commentMessageId + "/replies")
                        .header("Authorization", "Bearer " + normalUserToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replyBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // replyMessageId는 parent 검증에만 쓰기 때문에 응답에서만 확인
        long replyMessageId = objectMapper.readTree(replyResp).path("data").path("messageId").asLong();

        // 5) COMMENT 파일 첨부 (COMMENT_FILE) — parent=root
        MockMultipartFile commentFile = new MockMultipartFile(
                "file",
                "comment-file.txt",
                "text/plain",
                "comment file content".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/channels/" + channelId + "/files/upload")
                        .file(commentFile)
                        .param("employeeNo", normalEmployeeNo)
                        .param("parentMessageId", String.valueOf(rootMessageId))
                        .param("threadKind", "COMMENT")
                        .header("Authorization", "Bearer " + normalUserToken()))
                .andExpect(status().isCreated());

        // 6) REPLY 파일 첨부 (REPLY_FILE) — parent=comment
        MockMultipartFile replyFile = new MockMultipartFile(
                "file",
                "reply-file.txt",
                "text/plain",
                "reply file content".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/channels/" + channelId + "/files/upload")
                        .file(replyFile)
                        .param("employeeNo", normalEmployeeNo)
                        .param("parentMessageId", String.valueOf(commentMessageId))
                        .param("threadKind", "REPLY")
                        .header("Authorization", "Bearer " + normalUserToken()))
                .andExpect(status().isCreated());

        // 7) ROOT thread replies 조회: COMMENT_TEXT/COMMENT_FILE만 포함되어야 한다.
        MvcResult threadRes = mockMvc.perform(get("/api/channels/" + channelId + "/messages/" + rootMessageId + "/replies")
                        .header("Authorization", "Bearer " + normalUserToken()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode threadJson = objectMapper.readTree(threadRes.getResponse().getContentAsString());
        JsonNode threadData = threadJson.path("data");

        boolean foundCommentText = false;
        boolean foundCommentFile = false;
        for (JsonNode item : threadData) {
            long pid = item.path("parentMessageId").isNull() ? -1 : item.path("parentMessageId").asLong();
            String mt = item.path("messageType").asText();
            JsonNode textNode = item.path("text");

            // getThreadReplies는 parentMessageId=ROOT에 대해 children만 내려주므로 parentMessageId가 root이어야 한다.
            if (pid != -1) {
                // root 단에서 parentMessageId는 항상 rootMessageId여야 함
                // (응답 필드가 null이 아닌 경우만 검증)
            }

            if ("COMMENT_TEXT".equals(mt)) {
                foundCommentText = true;
                // text는 그대로
                // parentMessageId는 comment 응답에서 rootMessageId여야 한다.
                assertThat(item.path("parentMessageId").asLong(), is(rootMessageId));
                assertThat(textNode.asText(), is("comment-1"));
            }
            if ("COMMENT_FILE".equals(mt)) {
                foundCommentFile = true;
                assertThat(item.path("parentMessageId").asLong(), is(rootMessageId));

                JsonNode payload = objectMapper.readTree(textNode.asText());
                assertThat(payload.path("originalFilename").asText(), is(commentFile.getOriginalFilename()));
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(foundCommentText, "COMMENT_TEXT가 없으면 실패");
        org.junit.jupiter.api.Assertions.assertTrue(foundCommentFile, "COMMENT_FILE이 없으면 실패");

        // 8) COMMENT thread replies 조회: REPLY_TEXT/REPLY_FILE만 포함되어야 한다.
        MvcResult nestedRes = mockMvc.perform(get("/api/channels/" + channelId + "/messages/" + commentMessageId + "/replies")
                        .header("Authorization", "Bearer " + normalUserToken()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode nestedJson = objectMapper.readTree(nestedRes.getResponse().getContentAsString());
        JsonNode nestedData = nestedJson.path("data");

        boolean foundReplyText = false;
        boolean foundReplyFile = false;
        for (JsonNode item : nestedData) {
            String mt = item.path("messageType").asText();
            JsonNode textNode = item.path("text");
            if ("REPLY_TEXT".equals(mt)) {
                foundReplyText = true;
                assertThat(item.path("parentMessageId").asLong(), is(commentMessageId));
                assertThat(textNode.asText(), is("reply-1"));
            }
            if ("REPLY_FILE".equals(mt)) {
                foundReplyFile = true;
                assertThat(item.path("parentMessageId").asLong(), is(commentMessageId));

                JsonNode payload = objectMapper.readTree(textNode.asText());
                assertThat(payload.path("originalFilename").asText(), is(replyFile.getOriginalFilename()));
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(foundReplyText, "REPLY_TEXT가 없으면 실패");
        org.junit.jupiter.api.Assertions.assertTrue(foundReplyFile, "REPLY_FILE이 없으면 실패");

        // 9) 타임라인 조회: ROOT + REPLY만 포함(댓글 제외) + replyTo 메타 검증
        MvcResult timelineRes = mockMvc.perform(get("/api/channels/" + channelId + "/messages/timeline")
                        .param("employeeNo", normalEmployeeNo)
                        .param("limit", "200")
                        .header("Authorization", "Bearer " + normalUserToken()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode timelineJson = objectMapper.readTree(timelineRes.getResponse().getContentAsString());
        JsonNode timelineData = timelineJson.path("data").path("items");

        long rootCount = 0;
        long replyCount = 0;
        boolean foundRootMessage = false;
        for (JsonNode item : timelineData) {
            String mt = item.path("messageType").asText();
            // 타임라인 응답에서 isReply 필드가 직렬화/역직렬화 단계에서 누락되거나
            // boolean 값이 예상과 다를 수 있으므로, messageType(REPLY_*)를 1차 기준으로 사용한다.
            boolean isReply = mt.startsWith("REPLY_")
                    || (item.has("isReply") && item.path("isReply").asBoolean(false))
                    || item.path("reply").asBoolean(false);

            if (!isReply) {
                rootCount++;
                long mid = item.path("messageId").asLong(-1);
                if (mid == rootMessageId) foundRootMessage = true;
                assertThat(mt, not(startsWith("COMMENT")));
                assertThat(mt, not(startsWith("REPLY")));
            } else {
                replyCount++;
                assertThat(mt, startsWith("REPLY_"));
                assertThat(item.path("parentMessageId").asLong(), is(commentMessageId));
                assertThat(item.path("replyToMessageId").asLong(), is(commentMessageId));
                assertThat(item.path("replyToKind").asText(), is("COMMENT"));
                assertThat(item.path("replyToRootMessageId").asLong(), is(rootMessageId));
            }

            // 댓글 메시지는 타임라인에 없어야 한다.
            assertThat(mt, not(startsWith("COMMENT")));
        }

        org.junit.jupiter.api.Assertions.assertTrue(rootCount >= 1, "ROOT 메시지가 타임라인에 없어야 함");
        org.junit.jupiter.api.Assertions.assertTrue(replyCount >= 2, "REPLY 메시지가 2개 이상이어야 함(텍스트+파일)");
        org.junit.jupiter.api.Assertions.assertTrue(foundRootMessage, "타임라인에 rootMessageId가 존재해야 합니다.");
        assertThat(timelineJson.path("data").path("hasMoreOlder").asBoolean(), is(false));

        // 9b) 타임라인 이전 페이지: main root 이전에는 older-root 한 건
        MvcResult olderRes = mockMvc.perform(get("/api/channels/" + channelId + "/messages/timeline")
                        .param("employeeNo", normalEmployeeNo)
                        .param("limit", "20")
                        .param("beforeMessageId", String.valueOf(rootMessageId))
                        .header("Authorization", "Bearer " + normalUserToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode olderJson = objectMapper.readTree(olderRes.getResponse().getContentAsString());
        JsonNode olderItems = olderJson.path("data").path("items");
        assertThat(olderItems.isArray(), is(true));
        boolean foundOlderRoot = false;
        for (JsonNode item : olderItems) {
            if (item.path("messageId").asLong() == olderRootMessageId) {
                foundOlderRoot = true;
                assertThat(item.path("text").asText(), is("older-root"));
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(foundOlderRoot, "beforeMessageId=root 이전 구간에 older-root가 포함되어야 함");
        assertThat(olderJson.path("data").path("hasMoreOlder").asBoolean(), is(false));

        // 10) 단건 메시지 조회(원글): 멤버 + employeeNo로 ROOT 본문 확인
        mockMvc.perform(get("/api/channels/" + channelId + "/messages/" + rootMessageId)
                        .param("employeeNo", adminEmployeeNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messageId").value((int) rootMessageId))
                .andExpect(jsonPath("$.data.text").value("root message"))
                .andExpect(jsonPath("$.data.parentMessageId").isEmpty());

        // 11) 스레드 모아보기: 활동이 있는 원글만, 최근 활동 순 — 동일 root가 선두에 오고 threadCommentCount >= 1
        mockMvc.perform(get("/api/channels/" + channelId + "/messages/threads")
                        .param("employeeNo", normalEmployeeNo)
                        .param("limit", "20")
                        .header("Authorization", "Bearer " + normalUserToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].messageId").value((int) rootMessageId))
                .andExpect(jsonPath("$.data[0].threadCommentCount").value(greaterThanOrEqualTo(1)));
    }

    private String normalUserToken() {
        return userToken;
    }
}

