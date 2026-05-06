package com.ech.backend.api.aigateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ech.backend.api.aigateway.dto.AiGatewayChatRequest;
import com.ech.backend.api.aigateway.llm.LlmCompletionResult;
import com.ech.backend.api.aigateway.llm.LlmInvocationPort;
import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.security.UserPrincipal;
import com.ech.backend.domain.audit.AuditEventType;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.message.MessageRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AiGatewayService 단위")
class AiGatewayServiceTest {

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChannelMemberRepository channelMemberRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private AiGatewayRateLimiter rateLimiter;

    @Mock
    private LlmInvocationPort llmInvocationPort;

    @Mock
    private AiGatewayConfigurable gatewaySettings;

    private AiGatewayService service;

    @BeforeEach
    void setUp() {
        when(gatewaySettings.isAllowExternalLlm()).thenReturn(true);
        when(gatewaySettings.getChatMaxRequestsPerMinute()).thenReturn(30);
        when(gatewaySettings.getChatMaxRequestsPerHour()).thenReturn(300);
        when(gatewaySettings.getPolicyVersion()).thenReturn("unit-test");
        when(gatewaySettings.getLlmMaxInputChars()).thenReturn(8000);
        service = new AiGatewayService(
                gatewaySettings,
                auditLogService,
                userRepository,
                channelMemberRepository,
                messageRepository,
                rateLimiter,
                llmInvocationPort);
    }

    @Test
    @DisplayName("외부 LLM 허용이어도 HTTP 제공자 미구성이면 501 및 PROVIDER_NOT_CONFIGURED 감사")
    void chat_when_allowed_but_llm_not_configured_returns_not_implemented_and_audits() {
        UserPrincipal principal = new UserPrincipal(
                99L,
                "E001",
                "x@test.com",
                "Tester",
                "",
                AppRole.MEMBER);
        User userMock = org.mockito.Mockito.mock(User.class);
        when(userMock.getId()).thenReturn(99L);
        when(userRepository.findByEmployeeNo("E001")).thenReturn(Optional.of(userMock));
        when(llmInvocationPort.isConfigured()).thenReturn(false);

        AiGatewayChatRequest req = new AiGatewayChatRequest("p", null, 12L, "hi", List.of());
        MockHttpServletRequest http = new MockHttpServletRequest();

        ResponseEntity<ApiResponse<?>> res = service.chat(principal, req, http);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
        verify(rateLimiter).checkChatOrThrow(eq("E001"), eq(gatewaySettings));
        ArgumentCaptor<AuditEventType> cap = ArgumentCaptor.forClass(AuditEventType.class);
        verify(auditLogService).safeRecord(
                cap.capture(),
                eq(99L),
                eq("AI_GATEWAY"),
                eq(null),
                eq(null),
                any(),
                eq(null));
        assertThat(cap.getValue()).isEqualTo(AuditEventType.AI_GATEWAY_PROVIDER_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("HTTP LLM 구성 시 마스킹 프롬프트로 완료 호출·200·성공 감사")
    void chat_when_llm_configured_returns_ok_and_audits_success() {
        UserPrincipal principal = new UserPrincipal(
                99L,
                "E001",
                "x@test.com",
                "Tester",
                "",
                AppRole.MEMBER);
        User userMock = org.mockito.Mockito.mock(User.class);
        when(userMock.getId()).thenReturn(99L);
        when(userRepository.findByEmployeeNo("E001")).thenReturn(Optional.of(userMock));
        when(llmInvocationPort.isConfigured()).thenReturn(true);
        when(llmInvocationPort.complete(any(), eq("p")))
                .thenReturn(Optional.of(new LlmCompletionResult("reply", "m", 10)));

        AiGatewayChatRequest req = new AiGatewayChatRequest("p", null, 12L, "hello", List.of());
        MockHttpServletRequest http = new MockHttpServletRequest();

        ResponseEntity<ApiResponse<?>> res = service.chat(principal, req, http);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().success()).isTrue();
        verify(rateLimiter).checkChatOrThrow(eq("E001"), eq(gatewaySettings));
        ArgumentCaptor<AuditEventType> cap = ArgumentCaptor.forClass(AuditEventType.class);
        verify(auditLogService).safeRecord(
                cap.capture(),
                eq(99L),
                eq("AI_GATEWAY"),
                eq(null),
                eq(null),
                any(),
                eq(null));
        assertThat(cap.getValue()).isEqualTo(AuditEventType.AI_GATEWAY_LLM_SUCCEEDED);
    }

    @Test
    @DisplayName("llm-max-input-chars보다 긴 마스킹 프롬프트는 잘린 문자열로 LLM에 전달된다")
    void chat_truncates_masked_prompt_to_llm_max_input_chars() {
        UserPrincipal principal = new UserPrincipal(
                99L,
                "E001",
                "x@test.com",
                "Tester",
                "",
                AppRole.MEMBER);
        User userMock = org.mockito.Mockito.mock(User.class);
        when(userMock.getId()).thenReturn(99L);
        when(userRepository.findByEmployeeNo("E001")).thenReturn(Optional.of(userMock));
        when(llmInvocationPort.isConfigured()).thenReturn(true);
        when(gatewaySettings.getLlmMaxInputChars()).thenReturn(12);
        String longPrompt = "a".repeat(80);
        when(llmInvocationPort.complete(any(), eq("p")))
                .thenReturn(Optional.of(new LlmCompletionResult("reply", "m", 10)));

        AiGatewayChatRequest req = new AiGatewayChatRequest("p", null, 12L, longPrompt, List.of());
        MockHttpServletRequest http = new MockHttpServletRequest();

        ResponseEntity<ApiResponse<?>> res = service.chat(principal, req, http);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<String> promptCap = ArgumentCaptor.forClass(String.class);
        verify(llmInvocationPort).complete(promptCap.capture(), eq("p"));
        assertThat(promptCap.getValue()).isEqualTo("aaaaaaaaaaa…");
        assertThat(promptCap.getValue().codePointCount(0, promptCap.getValue().length())).isEqualTo(12);

        ArgumentCaptor<String> auditCap = ArgumentCaptor.forClass(String.class);
        verify(auditLogService)
                .safeRecord(
                        eq(AuditEventType.AI_GATEWAY_LLM_SUCCEEDED),
                        eq(99L),
                        eq("AI_GATEWAY"),
                        eq(null),
                        eq(null),
                        auditCap.capture(),
                        eq(null));
        assertThat(auditCap.getValue()).contains("inputTruncated=true");
    }

    @Test
    @DisplayName("근거 메시지가 있으면 channelId 없이 호출할 수 없다")
    void chat_rejects_citations_without_channel_id() {
        UserPrincipal principal = new UserPrincipal(
                99L,
                "E001",
                "x@test.com",
                "Tester",
                "",
                AppRole.MEMBER);
        User userMock = org.mockito.Mockito.mock(User.class);
        when(userMock.getId()).thenReturn(99L);
        when(userRepository.findByEmployeeNo("E001")).thenReturn(Optional.of(userMock));

        AiGatewayChatRequest req = new AiGatewayChatRequest("p", null, null, "x", List.of(1L));
        MockHttpServletRequest http = new MockHttpServletRequest();

        assertThrows(IllegalArgumentException.class, () -> service.chat(principal, req, http));
        verify(rateLimiter).checkChatOrThrow(eq("E001"), eq(gatewaySettings));
        verifyNoInteractions(channelMemberRepository);
        verifyNoInteractions(messageRepository);
        verifyNoInteractions(llmInvocationPort);
    }
}
