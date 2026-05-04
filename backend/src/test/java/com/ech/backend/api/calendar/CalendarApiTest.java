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
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
