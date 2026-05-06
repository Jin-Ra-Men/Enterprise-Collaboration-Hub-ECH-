package com.ech.backend.api.aigateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ech.backend.api.aigateway.dto.AiGatewayChatRequest;
import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.security.UserPrincipal;
import com.ech.backend.domain.audit.AuditEventType;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiGatewayService 단위")
class AiGatewayServiceTest {

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private UserRepository userRepository;

    private AiGatewayProperties properties;
    private AiGatewayService service;

    @BeforeEach
    void setUp() {
        properties = new AiGatewayProperties();
        properties.setAllowExternalLlm(true);
        properties.setPolicyVersion("unit-test");
        service = new AiGatewayService(properties, auditLogService, userRepository);
    }

    @Test
    @DisplayName("외부 LLM 허용 시에는 스텁으로 501 및 PROVIDER_NOT_CONFIGURED 감사")
    void chat_when_allowed_returns_not_implemented_and_audits_stub() {
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

        AiGatewayChatRequest req = new AiGatewayChatRequest("p", null, 12L, "hi");
        MockHttpServletRequest http = new MockHttpServletRequest();

        ResponseEntity<?> res = service.chat(principal, req, http);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
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
}
