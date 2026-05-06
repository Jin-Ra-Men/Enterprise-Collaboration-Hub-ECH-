package com.ech.backend.api.aiassistant;

import com.ech.backend.api.aigateway.AiGatewayConfigurable;
import com.ech.backend.api.aigateway.AiGatewayPiiMasker;
import com.ech.backend.api.aigateway.llm.LlmCompletionResult;
import com.ech.backend.api.aigateway.llm.LlmInvocationPort;
import com.ech.backend.api.aiassistant.insight.ConversationInsightJsonSupport;
import com.ech.backend.api.aiassistant.insight.ConversationInsightKind;
import com.ech.backend.api.aiassistant.insight.ConversationInsightLlmResult;
import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.api.calendar.CalendarService;
import com.ech.backend.api.settings.AppSettingsService;
import com.ech.backend.common.exception.AiGatewayLlmUpstreamException;
import com.ech.backend.domain.aiassistant.AiSuggestionInboxRepository;
import com.ech.backend.domain.aiassistant.AiSuggestionKind;
import com.ech.backend.domain.aiassistant.ChannelAiAssistantPreference;
import com.ech.backend.domain.aiassistant.ChannelAiAssistantPreferenceRepository;
import com.ech.backend.domain.audit.AuditEventType;
import com.ech.backend.domain.calendar.CalendarSuggestionRepository;
import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.channel.ChannelType;
import com.ech.backend.domain.message.Message;
import com.ech.backend.domain.message.MessageRepository;
import com.ech.backend.domain.settings.AppSettingKey;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 프로액티브 LLM 인사이트: 옵트인 채널의 최근 루트 텍스트 메시지를 분석해 일정 제안 또는 워크플로 힌트를 적재한다.
 *
 * <p>호출 전제: 기초설정 {@link AppSettingKey#AI_PROACTIVE_LLM_CONVERSATION_INSIGHT_ENABLED},
 * {@link AppSettingKey#AI_PROACTIVE_JOBS_ENABLED}, 외부 LLM 허용·HTTP LLM 구성.
 */
@Component
public class ProactiveConversationInsightScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProactiveConversationInsightScheduler.class);

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

    private static final int DEFAULT_LOOKBACK_HOURS = 2;
    private static final int DEFAULT_MAX_MESSAGES_PAGE = 28;
    private static final int DEFAULT_CONTEXT_MESSAGES = 14;
    private static final int DEFAULT_MAX_LLM_CALLS_PER_CHANNEL_PER_HOUR = 8;
    private static final double DEFAULT_CONFIDENCE_MIN = 0.55;
    private static final int TITLE_LINE_MAX_CODE_POINTS = 480;

    /** JVM 내 채널별 롤링 1시간 LLM 호출 시각(비용 상한 근사). */
    private final ConcurrentHashMap<Long, ArrayDeque<Long>> llmCallsByChannelMillis = new ConcurrentHashMap<>();

    private final AiAssistantService aiAssistantService;
    private final AppSettingsService appSettingsService;
    private final AiGatewayConfigurable gatewaySettings;
    private final LlmInvocationPort llmInvocationPort;
    private final CalendarService calendarService;
    private final CalendarSuggestionRepository calendarSuggestionRepository;
    private final AiSuggestionInboxRepository inboxRepository;
    private final ChannelAiAssistantPreferenceRepository channelAiAssistantPreferenceRepository;
    private final ChannelRepository channelRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public ProactiveConversationInsightScheduler(
            AiAssistantService aiAssistantService,
            AppSettingsService appSettingsService,
            AiGatewayConfigurable gatewaySettings,
            LlmInvocationPort llmInvocationPort,
            CalendarService calendarService,
            CalendarSuggestionRepository calendarSuggestionRepository,
            AiSuggestionInboxRepository inboxRepository,
            ChannelAiAssistantPreferenceRepository channelAiAssistantPreferenceRepository,
            ChannelRepository channelRepository,
            MessageRepository messageRepository,
            UserRepository userRepository,
            AuditLogService auditLogService
    ) {
        this.aiAssistantService = aiAssistantService;
        this.appSettingsService = appSettingsService;
        this.gatewaySettings = gatewaySettings;
        this.llmInvocationPort = llmInvocationPort;
        this.calendarService = calendarService;
        this.calendarSuggestionRepository = calendarSuggestionRepository;
        this.inboxRepository = inboxRepository;
        this.channelAiAssistantPreferenceRepository = channelAiAssistantPreferenceRepository;
        this.channelRepository = channelRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    private boolean proactiveJobsEnabled() {
        String v = appSettingsService.get(AppSettingKey.AI_PROACTIVE_JOBS_ENABLED, "true");
        return Boolean.parseBoolean(v != null ? v.trim() : "true");
    }

    private boolean llmInsightEnabled() {
        String v = appSettingsService.get(AppSettingKey.AI_PROACTIVE_LLM_CONVERSATION_INSIGHT_ENABLED, "false");
        return Boolean.parseBoolean(v != null ? v.trim() : "false");
    }

    private double confidenceMin() {
        String raw = appSettingsService.get(
                AppSettingKey.AI_PROACTIVE_LLM_CONVERSATION_CONFIDENCE_MIN,
                Double.toString(DEFAULT_CONFIDENCE_MIN));
        try {
            double d = Double.parseDouble(raw.trim());
            if (Double.isNaN(d) || d < 0 || d > 1) {
                return DEFAULT_CONFIDENCE_MIN;
            }
            return d;
        } catch (Exception e) {
            return DEFAULT_CONFIDENCE_MIN;
        }
    }

    private int maxLlmCallsPerChannelHour() {
        String raw = appSettingsService.get(
                AppSettingKey.AI_PROACTIVE_LLM_CONVERSATION_MAX_LLM_CALLS_PER_CHANNEL_PER_HOUR,
                Integer.toString(DEFAULT_MAX_LLM_CALLS_PER_CHANNEL_PER_HOUR));
        try {
            int n = Integer.parseInt(raw.trim());
            return Math.max(1, Math.min(120, n));
        } catch (Exception e) {
            return DEFAULT_MAX_LLM_CALLS_PER_CHANNEL_PER_HOUR;
        }
    }

    private int lookbackHours() {
        return DEFAULT_LOOKBACK_HOURS;
    }

    /**
     * 매시 23분 — 활동 힌트(07분)와 분산. 채널·메시지 단위로 LLM 호출 상한을 둔다.
     */
    @Scheduled(cron = "0 23 * * * *")
    public void hourlyConversationInsight() {
        if (!proactiveJobsEnabled() || !llmInsightEnabled()) {
            return;
        }
        if (!gatewaySettings.isAllowExternalLlm() || !llmInvocationPort.isConfigured()) {
            return;
        }

        double confMin = confidenceMin();
        int llmCap = maxLlmCallsPerChannelHour();
        OffsetDateTime since = OffsetDateTime.now().minusHours(lookbackHours());
        int pageSize = DEFAULT_MAX_MESSAGES_PAGE;
        OffsetDateTime dupWorkflowSince = OffsetDateTime.now().minusDays(14);

        List<ChannelAiAssistantPreference> prefs = channelAiAssistantPreferenceRepository.findByProactiveOptInTrue();
        for (ChannelAiAssistantPreference pref : prefs) {
            Long cid = pref.getChannelId();
            try {
                Optional<Channel> chOpt = channelRepository.findById(cid);
                if (chOpt.isEmpty() || chOpt.get().getChannelType() == ChannelType.DM) {
                    continue;
                }
                List<Message> msgs = messageRepository.findRecentRootTextMessagesForProactiveInsight(
                        cid, since, PageRequest.of(0, pageSize));
                if (msgs.isEmpty()) {
                    continue;
                }
                for (int i = 0; i < msgs.size(); i++) {
                    Message focus = msgs.get(i);
                    User sender = focus.getSender();
                    String emp = sender.getEmployeeNo();
                    if (!aiAssistantService.isAiAssistantEnabled(emp)) {
                        continue;
                    }
                    if (inboxRepository.countRecentWorkItemHintForSourceMessage(emp, cid, focus.getId(), dupWorkflowSince)
                            > 0) {
                        continue;
                    }
                    String exactJson = "[" + focus.getId() + "]";
                    if (calendarSuggestionRepository.countPendingAiTouchingOriginMessage(emp, exactJson, focus.getId())
                            > 0) {
                        continue;
                    }
                    if (!reserveLlmCallSlot(cid, llmCap)) {
                        break;
                    }
                    Optional<ConversationInsightLlmResult> parsed =
                            invokeLlmAndParse(cid, focus, msgs, i);
                    if (parsed.isEmpty()) {
                        continue;
                    }
                    ConversationInsightLlmResult r = parsed.get();
                    applyValidatedResult(r, confMin, emp, cid, focus);
                }
            } catch (Exception e) {
                log.debug("[ProactiveLlmInsight] skip channelId={}: {}", cid, e.getMessage());
            }
        }
    }

    private boolean reserveLlmCallSlot(long channelId, int maxPerHour) {
        long now = System.currentTimeMillis();
        long hourMs = 60L * 60L * 1000L;
        ArrayDeque<Long> dq = llmCallsByChannelMillis.computeIfAbsent(channelId, k -> new ArrayDeque<>());
        synchronized (dq) {
            while (!dq.isEmpty() && now - dq.peekFirst() > hourMs) {
                dq.pollFirst();
            }
            if (dq.size() >= maxPerHour) {
                return false;
            }
            dq.addLast(now);
            return true;
        }
    }

    private Optional<ConversationInsightLlmResult> invokeLlmAndParse(
            long channelId,
            Message focus,
            List<Message> msgsDesc,
            int focusIndex
    ) {
        int end = Math.min(focusIndex + DEFAULT_CONTEXT_MESSAGES, msgsDesc.size());
        List<Message> chunk = new ArrayList<>(msgsDesc.subList(focusIndex, end));
        Collections.reverse(chunk);

        ZonedDateTime anchorSeoul = ZonedDateTime.now(SEOUL);
        String transcript = buildTranscript(chunk, channelId, focus.getId(), anchorSeoul);
        AiGatewayPiiMasker.MaskResult masked = AiGatewayPiiMasker.mask(transcript);
        int maxCp = gatewaySettings.getLlmMaxInputChars();
        String maskedText = masked.maskedText();
        boolean tooLong = maskedText.codePointCount(0, maskedText.length()) > maxCp;
        String llmPrompt = tooLong
                ? ConversationInsightJsonSupport.truncateToMaxCodePoints(maskedText, maxCp)
                : maskedText;

        Long actorUserId = userRepository.findByEmployeeNo(focus.getSender().getEmployeeNo())
                .map(User::getId)
                .orElse(null);
        String auditDetail = "purpose=PROACTIVE_CONVERSATION_INSIGHT,channelId=" + channelId
                + ",focusMessageId=" + focus.getId()
                + ",promptChars=" + llmPrompt.codePointCount(0, llmPrompt.length())
                + ",piiRedactions=" + masked.redactionCount();

        try {
            Optional<LlmCompletionResult> out = llmInvocationPort.complete(llmPrompt, "PROACTIVE_CONVERSATION_INSIGHT");
            if (out.isEmpty()) {
                auditLogService.safeRecord(
                        AuditEventType.AI_GATEWAY_LLM_FAILED,
                        actorUserId,
                        "AI_GATEWAY",
                        null,
                        null,
                        auditDetail + ",reason=no_choice",
                        null);
                return Optional.empty();
            }
            LlmCompletionResult r = out.get();
            auditLogService.safeRecord(
                    AuditEventType.AI_GATEWAY_LLM_SUCCEEDED,
                    actorUserId,
                    "AI_GATEWAY",
                    null,
                    null,
                    auditDetail + ",model=" + truncateAudit(r.model(), 80),
                    null);
            return ConversationInsightJsonSupport.parseLlmJson(r.replyText());
        } catch (AiGatewayLlmUpstreamException ex) {
            auditLogService.safeRecord(
                    AuditEventType.AI_GATEWAY_LLM_FAILED,
                    actorUserId,
                    "AI_GATEWAY",
                    null,
                    null,
                    auditDetail + ",reason=upstream",
                    null);
            return Optional.empty();
        }
    }

    private static String truncateAudit(String s, int max) {
        if (s == null || s.isBlank()) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private void applyValidatedResult(
            ConversationInsightLlmResult r,
            double confidenceMin,
            String recipientEmp,
            long channelId,
            Message focus
    ) {
        if (r.kind() == ConversationInsightKind.NONE) {
            return;
        }
        if (r.ambiguous()) {
            return;
        }
        if (Double.isNaN(r.confidence()) || r.confidence() < confidenceMin) {
            return;
        }

        if (r.kind() == ConversationInsightKind.CALENDAR) {
            Optional<CalendarTimes> times = validateCalendarTimes(r);
            if (times.isEmpty()) {
                return;
            }
            CalendarTimes t = times.get();
            String title = r.title() != null && !r.title().isBlank() ? r.title() : "일정 제안";
            try {
                calendarService.createAiAssistantSuggestionFromProactivePipeline(
                        recipientEmp,
                        channelId,
                        focus.getId(),
                        title,
                        r.description(),
                        t.startsAt(),
                        t.endsAt());
            } catch (RuntimeException ex) {
                log.debug("[ProactiveLlmInsight] calendar skip emp={} channel={}: {}",
                        recipientEmp, channelId, ex.getMessage());
            }
            return;
        }

        if (r.kind() == ConversationInsightKind.WORKFLOW) {
            String title = r.title();
            if (title == null || title.isBlank()) {
                title = r.workflowReason() != null && !r.workflowReason().isBlank()
                        ? r.workflowReason()
                        : "워크플로 제안";
            }
            String summary = r.workflowReason() != null && !r.workflowReason().isBlank()
                    ? r.workflowReason()
                    : "대화 맥락을 바탕으로 업무·칸반으로 정리할 만한 내용이 있습니다.";
            String payload = "{\"deepLink\":\"workHub\",\"channelId\":" + channelId
                    + ",\"sourceMessageId\":" + focus.getId() + "}";
            try {
                aiAssistantService.enqueueSuggestion(
                        recipientEmp,
                        AiSuggestionKind.WORK_ITEM_HINT,
                        channelId,
                        title,
                        summary,
                        payload,
                        r.confidence());
            } catch (RuntimeException ex) {
                log.debug("[ProactiveLlmInsight] enqueue skip emp={} channel={}: {}",
                        recipientEmp, channelId, ex.getMessage());
            }
        }
    }

    /**
     * 서버 검증: 구간·모호 시간 거부·일정 길이 상한.
     */
    private Optional<CalendarTimes> validateCalendarTimes(ConversationInsightLlmResult r) {
        OffsetDateTime start = r.startsAt();
        if (start == null) {
            return Optional.empty();
        }
        OffsetDateTime end = r.endsAt();
        if (end == null) {
            end = start.plusHours(1);
        }
        if (!end.isAfter(start)) {
            return Optional.empty();
        }
        long minutes = ChronoUnit.MINUTES.between(start, end);
        if (minutes > 14L * 24 * 60 || minutes < 5) {
            return Optional.empty();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (start.isBefore(now.minusHours(6)) || start.isAfter(now.plusDays(120))) {
            return Optional.empty();
        }
        if (!start.getOffset().equals(SEOUL_OFFSET) || !end.getOffset().equals(SEOUL_OFFSET)) {
            return Optional.empty();
        }
        return Optional.of(new CalendarTimes(start, end));
    }

    private String buildTranscript(List<Message> chronological, long channelId, long focusMessageId, ZonedDateTime nowSeoul) {
        StringBuilder sb = new StringBuilder();
        sb.append("You classify a CSTalk-style channel snippet for proactive assistant suggestions.\n");
        sb.append("Reply with ONLY valid JSON (no markdown fence). Fields:\n");
        sb.append("- suggestionKind: NONE | CALENDAR | WORKFLOW\n");
        sb.append("- confidence: number 0..1\n");
        sb.append("- ambiguous: boolean — true if dates/times are unclear or contradictory; then server rejects auto-actions.\n");
        sb.append("- title, description: optional strings\n");
        sb.append("- startsAt, endsAt: ISO-8601 with explicit numeric offset +09:00 for Asia/Seoul wall time when CALENDAR\n");
        sb.append("- workflowReason: optional for WORKFLOW\n");
        sb.append("Example: {\"suggestionKind\":\"NONE\",\"confidence\":0.2,\"ambiguous\":false,\"title\":null,\"description\":null,\"startsAt\":null,\"endsAt\":null,\"workflowReason\":null}\n\n");
        sb.append("NOW_ANCHOR_ASIA_SEOUL: ")
                .append(nowSeoul.toOffsetDateTime().toString())
                .append('\n');
        sb.append("CHANNEL_ID: ").append(channelId).append('\n');
        sb.append("FOCUS_MESSAGE_ID: ").append(focusMessageId).append('\n');
        sb.append("TRANSCRIPT_OLDEST_FIRST:\n");
        for (Message m : chronological) {
            sb.append("- msgId=").append(m.getId());
            sb.append(" sender=").append(m.getSender().getEmployeeNo());
            sb.append(" at=").append(m.getCreatedAt().toString());
            sb.append(" body=");
            String body = m.getBody() == null ? "" : m.getBody();
            body = ConversationInsightJsonSupport.truncateToMaxCodePoints(body, TITLE_LINE_MAX_CODE_POINTS)
                    .replace('\n', ' ')
                    .trim();
            sb.append(body);
            sb.append('\n');
        }
        return sb.toString();
    }

    private record CalendarTimes(OffsetDateTime startsAt, OffsetDateTime endsAt) {}
}
