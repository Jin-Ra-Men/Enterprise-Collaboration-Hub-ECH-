package com.ech.backend.api.aigateway;

import com.ech.backend.api.aigateway.dto.AiGatewayChatRequest;
import com.ech.backend.api.aigateway.dto.AiGatewayChatResponse;
import com.ech.backend.api.aigateway.dto.AiGatewayStatusResponse;
import com.ech.backend.api.aigateway.llm.LlmCompletionResult;
import com.ech.backend.api.aigateway.llm.LlmInvocationPort;
import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.exception.AiGatewayLlmUpstreamException;
import com.ech.backend.common.security.UserPrincipal;
import com.ech.backend.domain.audit.AuditEventType;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.message.MessageRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class AiGatewayService {

    private static final String DEFAULT_POLICY_SUMMARY_KO =
            "협업 원문(채널·DM·첨부)은 기본적으로 공용 인터넷 LLM으로 전송하지 않습니다. 예외는 전용망·계약 경로만 법무·보안 합의 후 검토합니다.";

    private final AiGatewayConfigurable gatewaySettings;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final MessageRepository messageRepository;
    private final AiGatewayRateLimiter rateLimiter;
    private final LlmInvocationPort llmInvocationPort;

    public AiGatewayService(
            AiGatewayConfigurable gatewaySettings,
            AuditLogService auditLogService,
            UserRepository userRepository,
            ChannelMemberRepository channelMemberRepository,
            MessageRepository messageRepository,
            AiGatewayRateLimiter rateLimiter,
            LlmInvocationPort llmInvocationPort
    ) {
        this.gatewaySettings = gatewaySettings;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.messageRepository = messageRepository;
        this.rateLimiter = rateLimiter;
        this.llmInvocationPort = llmInvocationPort;
    }

    public AiGatewayStatusResponse statusSnapshot() {
        String ver = gatewaySettings.getPolicyVersion();
        if (ver == null || ver.isBlank()) {
            ver = "unknown";
        }
        return new AiGatewayStatusResponse(
                gatewaySettings.isAllowExternalLlm(),
                ver,
                DEFAULT_POLICY_SUMMARY_KO,
                gatewaySettings.getChatMaxRequestsPerMinute(),
                gatewaySettings.getChatMaxRequestsPerHour(),
                llmInvocationPort.isConfigured()
        );
    }

    public ResponseEntity<ApiResponse<?>> chat(
            UserPrincipal principal,
            AiGatewayChatRequest request,
            HttpServletRequest httpRequest
    ) {
        String actorEmp = resolveSelfEmployeeNo(principal, request.employeeNo());
        rateLimiter.checkChatOrThrow(actorEmp, gatewaySettings);
        Long actorUserId = userRepository.findByEmployeeNo(actorEmp).map(User::getId).orElse(null);
        List<Long> citedNorm = normalizeCitedIds(request.citedMessageIds());
        validateCitations(request.channelId(), actorEmp, citedNorm);
        int piiRedactions = AiGatewayPiiMasker.mask(request.prompt()).redactionCount();
        String detail = buildAuditDetail(request, piiRedactions, citedNorm.size());
        String requestId = httpRequest != null ? httpRequest.getHeader("X-Request-Id") : null;

        if (!gatewaySettings.isAllowExternalLlm()) {
            auditLogService.safeRecord(
                    AuditEventType.AI_GATEWAY_POLICY_BLOCKED,
                    actorUserId,
                    "AI_GATEWAY",
                    null,
                    null,
                    detail,
                    requestId
            );
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.fail(
                            "AI_GATEWAY_BLOCKED",
                            "AI 게이트웨이 정책상 외부 LLM 전송이 비활성화되어 있습니다(기초설정 "
                                    + "`ai.gateway.allow-external-llm` 또는 app.ai). "
                                    + "전용망·승인 제공자 연동 및 운영 설정 변경은 보안 절차 후 진행합니다."
                    ));
        }

        if (!llmInvocationPort.isConfigured()) {
            auditLogService.safeRecord(
                    AuditEventType.AI_GATEWAY_PROVIDER_NOT_CONFIGURED,
                    actorUserId,
                    "AI_GATEWAY",
                    null,
                    null,
                    detail + ",phase=stub",
                    requestId
            );
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(ApiResponse.fail(
                            "AI_GATEWAY_NOT_CONFIGURED",
                            "외부 LLM 허용 플래그는 켜져 있으나 제공자(http base-url·api-key·llm.http-enabled)가 구성되지 않았습니다."
                    ));
        }

        String maskedPrompt = AiGatewayPiiMasker.mask(request.prompt()).maskedText();
        try {
            Optional<LlmCompletionResult> out = llmInvocationPort.complete(maskedPrompt, request.purpose());
            if (out.isEmpty()) {
                auditLogService.safeRecord(
                        AuditEventType.AI_GATEWAY_LLM_FAILED,
                        actorUserId,
                        "AI_GATEWAY",
                        null,
                        null,
                        detail + ",reason=no_choice",
                        requestId
                );
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(ApiResponse.fail(
                                "AI_GATEWAY_LLM_EMPTY",
                                "모델이 유효한 응답을 생성하지 않았습니다."
                        ));
            }
            LlmCompletionResult r = out.get();
            auditLogService.safeRecord(
                    AuditEventType.AI_GATEWAY_LLM_SUCCEEDED,
                    actorUserId,
                    "AI_GATEWAY",
                    null,
                    null,
                    detail + ",model=" + truncateAudit(r.model(), 80),
                    requestId
            );
            return ResponseEntity.ok(ApiResponse.success(new AiGatewayChatResponse(
                    r.replyText(),
                    r.model(),
                    r.totalTokens()
            )));
        } catch (AiGatewayLlmUpstreamException ex) {
            auditLogService.safeRecord(
                    AuditEventType.AI_GATEWAY_LLM_FAILED,
                    actorUserId,
                    "AI_GATEWAY",
                    null,
                    null,
                    detail + ",reason=upstream",
                    requestId
            );
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.fail("AI_GATEWAY_LLM_UPSTREAM_ERROR", ex.getMessage()));
        }
    }

    private static String truncateAudit(String s, int max) {
        if (s == null || s.isBlank()) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static String resolveSelfEmployeeNo(UserPrincipal principal, String employeeNo) {
        if (principal == null) {
            throw new IllegalArgumentException("인증이 필요합니다.");
        }
        if (employeeNo == null || employeeNo.isBlank()) {
            return principal.employeeNo();
        }
        if (!principal.employeeNo().equals(employeeNo.trim())) {
            throw new IllegalArgumentException("다른 사용자로 AI 게이트웨이를 호출할 수 없습니다.");
        }
        return employeeNo.trim();
    }

    private static List<Long> normalizeCitedIds(List<Long> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> dedup = new LinkedHashSet<>();
        for (Long id : raw) {
            if (id != null) {
                dedup.add(id);
            }
        }
        if (dedup.size() > 20) {
            throw new IllegalArgumentException("근거 메시지는 최대 20개까지 지정할 수 있습니다.");
        }
        return new ArrayList<>(dedup);
    }

    private void validateCitations(Long channelId, String actorEmployeeNo, List<Long> citedIds) {
        if (citedIds.isEmpty()) {
            return;
        }
        if (channelId == null) {
            throw new IllegalArgumentException("근거 message_id를 지정하면 channelId가 필요합니다.");
        }
        long cid = channelId;
        if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(cid, actorEmployeeNo)) {
            throw new IllegalArgumentException("해당 채널의 멤버만 근거 메시지를 인용할 수 있습니다.");
        }
        for (Long mid : citedIds) {
            messageRepository.findByIdAndChannel_Id(mid, cid)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "근거 메시지를 찾을 수 없거나 채널과 일치하지 않습니다."));
        }
    }

    private static String buildAuditDetail(AiGatewayChatRequest req, int piiRedactions, int citedDistinctCount) {
        int len = req.prompt() != null ? req.prompt().length() : 0;
        String purpose = req.purpose() == null ? "" : req.purpose().trim();
        if (purpose.length() > 64) {
            purpose = purpose.substring(0, 64);
        }
        return "purpose="
                + purpose
                + ",promptChars="
                + len
                + ",channelId="
                + req.channelId()
                + ",citedDistinct="
                + citedDistinctCount
                + ",piiRedactions="
                + piiRedactions;
    }
}
