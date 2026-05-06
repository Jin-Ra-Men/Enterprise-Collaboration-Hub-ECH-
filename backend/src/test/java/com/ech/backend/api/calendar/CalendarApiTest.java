package com.ech.backend.api.calendar;

import com.ech.backend.BaseIntegrationTest;
import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.channel.ChannelMember;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.channel.ChannelMemberRole;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.channel.ChannelType;
import com.ech.backend.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpHeaders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("캘린더 API 통합 테스트")
class CalendarApiTest extends BaseIntegrationTest {

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ChannelMemberRepository channelMemberRepository;

    @Test
    @DisplayName("직접 일정 생성 후 목록 조회")
    void create_and_list_events() throws Exception {
        String body = """
                {
                  "ownerEmployeeNo": "%s",
                  "title": "회의",
                  "description": "내용",
                  "startsAt": "2026-06-01T10:00:00+09:00",
                  "endsAt": "2026-06-01T11:00:00+09:00"
                }
                """.formatted(adminEmployeeNo);

        mockMvc.perform(post("/api/calendar/events")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("회의"))
                .andExpect(jsonPath("$.data.originChannelId").value(nullValue()));

        mockMvc.perform(get("/api/calendar/events")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("employeeNo", adminEmployeeNo)
                        .param("from", "2026-05-01T00:00:00+09:00")
                        .param("to", "2026-07-01T00:00:00+09:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("회의"));
    }

    @Test
    @DisplayName("채널에서 일정 공유 후 수신자 수락 시 출처 채널 표시")
    void share_accept_shows_origin_channel() throws Exception {
        User admin = userRepository.findByEmployeeNo(adminEmployeeNo).orElseThrow();
        User normal = userRepository.findByEmployeeNo(normalEmployeeNo).orElseThrow();
        Channel ch = channelRepository.saveAndFlush(
                new Channel("WS_CAL", "캘린더공유채널", null, ChannelType.PUBLIC, admin));
        channelMemberRepository.saveAndFlush(new ChannelMember(ch, admin, ChannelMemberRole.MANAGER));
        channelMemberRepository.saveAndFlush(new ChannelMember(ch, normal, ChannelMemberRole.MEMBER));

        String shareBody = """
                {
                  "senderEmployeeNo": "%s",
                  "recipientEmployeeNo": "%s",
                  "title": "팀 미팅",
                  "startsAt": "2026-06-02T14:00:00+09:00",
                  "endsAt": "2026-06-02T15:00:00+09:00"
                }
                """.formatted(adminEmployeeNo, normalEmployeeNo);

        String shareResp = mockMvc.perform(post("/api/channels/{channelId}/calendar/shares", ch.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shareBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long shareId = objectMapper.readTree(shareResp).path("data").path("id").asLong();

        mockMvc.perform(post("/api/calendar/shares/{shareId}/accept", shareId)
                        .header("Authorization", "Bearer " + userToken)
                        .param("employeeNo", normalEmployeeNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerEmployeeNo").value(normalEmployeeNo))
                .andExpect(jsonPath("$.data.originChannelId", equalTo(ch.getId().intValue())))
                .andExpect(jsonPath("$.data.originChannelName").value("캘린더공유채널"));
    }

    @Test
    @DisplayName("일정 제안(AI_ASSISTANT) 확정 시 이벤트는 USER로 저장")
    void suggestion_confirm_creates_user_event() throws Exception {
        String body = """
                {
                  "ownerEmployeeNo": "%s",
                  "title": "제안회의",
                  "startsAt": "2026-07-01T09:00:00+09:00",
                  "endsAt": "2026-07-01T10:00:00+09:00",
                  "createdByActor": "AI_ASSISTANT"
                }
                """.formatted(adminEmployeeNo);

        String resp = mockMvc.perform(post("/api/calendar/suggestions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long sid = objectMapper.readTree(resp).path("data").path("id").asLong();

        mockMvc.perform(post("/api/calendar/suggestions/{sid}/confirm", sid)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("employeeNo", adminEmployeeNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("제안회의"))
                .andExpect(jsonPath("$.data.createdByActor").value("USER"));

        mockMvc.perform(get("/api/calendar/suggestions")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("employeeNo", adminEmployeeNo)
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("직접 일정 생성 시 AI_ASSISTANT 출처는 거부")
    void direct_event_rejects_ai_assistant_actor() throws Exception {
        String body = """
                {
                  "ownerEmployeeNo": "%s",
                  "title": "불가",
                  "startsAt": "2026-09-01T10:00:00+09:00",
                  "endsAt": "2026-09-01T11:00:00+09:00",
                  "createdByActor": "AI_ASSISTANT"
                }
                """.formatted(adminEmployeeNo);
        mockMvc.perform(post("/api/calendar/events")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("충돌 검사 API가 겹치는 일정을 반환")
    void conflicts_returns_overlap() throws Exception {
        String ev = """
                {
                  "ownerEmployeeNo": "%s",
                  "title": "블록",
                  "startsAt": "2026-08-01T10:00:00+09:00",
                  "endsAt": "2026-08-01T12:00:00+09:00"
                }
                """.formatted(adminEmployeeNo);
        mockMvc.perform(post("/api/calendar/events")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ev))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/calendar/events/conflicts")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("employeeNo", adminEmployeeNo)
                        .param("startsAt", "2026-08-01T11:00:00+09:00")
                        .param("endsAt", "2026-08-01T13:00:00+09:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasConflict").value(true))
                .andExpect(jsonPath("$.data.overlappingEvents[0].title").value("블록"));
    }

    @Test
    @DisplayName("iCal 내보내기에 활성 일정이 포함된다")
    void export_ics_includes_active_events() throws Exception {
        String body = """
                {
                  "ownerEmployeeNo": "%s",
                  "title": "내보내기테스트",
                  "startsAt": "2030-01-15T10:00:00+09:00",
                  "endsAt": "2030-01-15T11:00:00+09:00"
                }
                """.formatted(adminEmployeeNo);
        mockMvc.perform(post("/api/calendar/events")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/calendar/export.ics")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("employeeNo", adminEmployeeNo)
                        .param("from", "2030-01-01T00:00:00+09:00")
                        .param("to", "2030-02-01T00:00:00+09:00"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(content().contentType("text/calendar;charset=UTF-8"))
                .andExpect(content().string(containsString("BEGIN:VCALENDAR")))
                .andExpect(content().string(containsString("내보내기테스트")));
    }

    @Test
    @DisplayName("iCal 가져오기로 일정을 추가한다")
    void import_ics_creates_events() throws Exception {
        String ics = """
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:-//Test//Test//EN
                BEGIN:VEVENT
                UID:import-test-1@test
                DTSTAMP:20300101T000000Z
                DTSTART:20301102T010000Z
                DTEND:20301102T020000Z
                SUMMARY:ICS 가져오기 회의
                END:VEVENT
                END:VCALENDAR
                """;

        mockMvc.perform(multipart("/api/calendar/import")
                        .file(new MockMultipartFile(
                                "file",
                                "meetings.ics",
                                "text/calendar",
                                ics.getBytes(StandardCharsets.UTF_8)))
                        .param("employeeNo", adminEmployeeNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importedCount").value(1))
                .andExpect(jsonPath("$.data.skippedCount").value(0));

        mockMvc.perform(get("/api/calendar/events")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("employeeNo", adminEmployeeNo)
                        .param("from", "2030-11-01T00:00:00+09:00")
                        .param("to", "2030-11-30T00:00:00+09:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("ICS 가져오기 회의"));
    }
}
