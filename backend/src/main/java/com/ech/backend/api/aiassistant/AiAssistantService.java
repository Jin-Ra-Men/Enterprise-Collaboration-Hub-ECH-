package com.ech.backend.api.aiassistant;

import com.ech.backend.api.aiassistant.dto.AiSuggestionInboxItemResponse;
import com.ech.backend.api.aiassistant.dto.ChannelAiAssistantPreferenceResponse;
import com.ech.backend.api.aiassistant.dto.UpdateChannelAiAssistantPreferenceRequest;
import com.ech.backend.api.aiassistant.dto.UpdateUserAiAssistantPreferenceRequest;
import com.ech.backend.api.aiassistant.dto.UserAiAssistantPreferenceResponse;
import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.api.settings.AppSettingsService;
import com.ech.backend.common.exception.ForbiddenException;
import com.ech.backend.common.security.UserPrincipal;
import com.ech.backend.domain.aiassistant.AiSuggestionInboxItem;
import com.ech.backend.domain.aiassistant.AiSuggestionInboxRepository;
import com.ech.backend.domain.aiassistant.AiSuggestionInboxStatus;
import com.ech.backend.domain.aiassistant.AiSuggestionKind;
import com.ech.backend.domain.aiassistant.ChannelAiAssistantPreference;
import com.ech.backend.domain.aiassistant.ChannelAiAssistantPreferenceRepository;
import com.ech.backend.domain.aiassistant.UserAiAssistantPreference;
import com.ech.backend.domain.aiassistant.UserAiAssistantPreferenceRepository;
import com.ech.backend.domain.audit.AuditEventType;
import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.channel.ChannelMember;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.channel.ChannelMemberRole;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.channel.ChannelType;
import com.ech.backend.domain.settings.AppSettingKey;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiAssistantService {

    private static final int DEFAULT_DISMISS_COOLDOWN_HOURS = 24;
    private static final int DEFAULT_MAX_PER_CHANNEL_PER_HOUR = 30;

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final ChannelAiAssistantPreferenceRepository channelAiPreferenceRepository;
    private final UserAiAssistantPreferenceRepository userAiPreferenceRepository;
    private final AiSuggestionInboxRepository inboxRepository;
    private final AppSettingsService appSettingsService;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    public AiAssistantService(
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            ChannelAiAssistantPreferenceRepository channelAiPreferenceRepository,
            UserAiAssistantPreferenceRepository userAiPreferenceRepository,
            AiSuggestionInboxRepository inboxRepository,
            AppSettingsService appSettingsService,
            AuditLogService auditLogService,
            UserRepository userRepository
    ) {
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.channelAiPreferenceRepository = channelAiPreferenceRepository;
        this.userAiPreferenceRepository = userAiPreferenceRepository;
        this.inboxRepository = inboxRepository;
        this.appSettingsService = appSettingsService;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ChannelAiAssistantPreferenceResponse getChannelPreference(UserPrincipal principal, Long channelId) {
        String emp = requireEmployee(principal);
        Channel channel = loadChannelAndRequireMembership(channelId, emp);
        boolean dm = channel.getChannelType() == ChannelType.DM;
        boolean optIn = false;
        if (!dm) {
            optIn = channelAiPreferenceRepository.findByChannelId(channelId).map(ChannelAiAssistantPreference::isProactiveOptIn).orElse(false);
        }
        return new ChannelAiAssistantPreferenceResponse(channelId, optIn, dm);
    }

    @Transactional
    public ChannelAiAssistantPreferenceResponse updateChannelPreference(
            UserPrincipal principal,
            Long channelId,
            UpdateChannelAiAssistantPreferenceRequest body
    ) {
        String emp = requireEmployee(principal);
        Channel channel = loadChannelAndRequireMembership(channelId, emp);
        if (channel.getChannelType() == ChannelType.DM) {
            throw new IllegalArgumentException("DM 채널에서는 프로액티브 AI 관찰을 활성화할 수 없습니다.");
        }
        ChannelMember me = channelMemberRepository.findByChannel_IdAndUser_EmployeeNo(channelId, emp).orElseThrow();
        if (me.getMemberRole() != ChannelMemberRole.MANAGER) {
            throw new ForbiddenException("채널 관리자만 프로액티브 비서 옵트인을 변경할 수 있습니다.");
        }
        boolean next = Boolean.TRUE.equals(body.proactiveOptIn());
        ChannelAiAssistantPreference pref =
                channelAiPreferenceRepository.findByChannelId(channelId).orElseGet(() -> new ChannelAiAssistantPreference(channelId, false));
        pref.setProactiveOptIn(next);
        channelAiPreferenceRepository.save(pref);
        auditLogService.safeRecord(
                AuditEventType.AI_ASSISTANT_CHANNEL_PREF_UPDATED,
                userId(emp),
                "CHANNEL_AI_ASSISTANT",
                channelId,
                channel.getWorkspaceKey(),
                "proactiveOptIn=" + next,
                null
        );
        return new ChannelAiAssistantPreferenceResponse(channelId, next, false);
    }

    @Transactional(readOnly = true)
    public UserAiAssistantPreferenceResponse getUserPreference(UserPrincipal principal, String employeeNo) {
        String emp = resolveSelfEmployeeNo(principal, employeeNo);
        UserAiAssistantPreference row = userAiPreferenceRepository.findById(emp).orElse(null);
        boolean cooldown = false;
        if (row != null && row.getProactiveCooldownUntil() != null) {
            cooldown = OffsetDateTime.now().isBefore(row.getProactiveCooldownUntil());
        }
        if (row == null) {
            return new UserAiAssistantPreferenceResponse(
                    com.ech.backend.domain.aiassistant.AiAssistantTone.BALANCED.name(),
                    com.ech.backend.domain.aiassistant.AiSuggestionDigestMode.REALTIME.name(),
                    cooldown
            );
        }
        return new UserAiAssistantPreferenceResponse(
                row.getProactiveTone().name(),
                row.getDigestMode().name(),
                cooldown
        );
    }

    @Transactional
    public UserAiAssistantPreferenceResponse updateUserPreference(
            UserPrincipal principal,
            String employeeNo,
            UpdateUserAiAssistantPreferenceRequest body
    ) {
        String emp = resolveSelfEmployeeNo(principal, employeeNo);
        UserAiAssistantPreference row =
                userAiPreferenceRepository.findById(emp).orElseGet(() -> userAiPreferenceRepository.save(new UserAiAssistantPreference(emp)));
        if (body.proactiveTone() != null) {
            row.setProactiveTone(body.proactiveTone());
        }
        if (body.digestMode() != null) {
            row.setDigestMode(body.digestMode());
        }
        userAiPreferenceRepository.save(row);
        auditLogService.safeRecord(
                AuditEventType.AI_ASSISTANT_USER_PREF_UPDATED,
                userId(emp),
                "USER_AI_ASSISTANT",
                null,
                null,
                "tone=" + row.getProactiveTone() + ",digest=" + row.getDigestMode(),
                null
        );
        return getUserPreference(principal, emp);
    }

    @Transactional(readOnly = true)
    public List<AiSuggestionInboxItemResponse> listInbox(UserPrincipal principal, String employeeNo, AiSuggestionInboxStatus status) {
        String emp = resolveSelfEmployeeNo(principal, employeeNo);
        AiSuggestionInboxStatus st = status != null ? status : AiSuggestionInboxStatus.PENDING;
        return inboxRepository.findTop50ByRecipientEmployeeNoAndStatusOrderByCreatedAtDesc(emp, st).stream()
                .map(this::toInboxResponse)
                .toList();
    }

    @Transactional
    public void dismissInboxItem(UserPrincipal principal, Long suggestionId, String employeeNo) {
        String emp = resolveSelfEmployeeNo(principal, employeeNo);
        AiSuggestionInboxItem item = inboxRepository.findByIdAndRecipientEmployeeNo(suggestionId, emp)
                .orElseThrow(() -> new IllegalArgumentException("제안을 찾을 수 없습니다."));
        if (item.getStatus() != AiSuggestionInboxStatus.PENDING) {
            return;
        }
        item.setStatus(AiSuggestionInboxStatus.DISMISSED);
        inboxRepository.save(item);

        int coolHours = parsePositiveInt(
                appSettingsService.get(
                        AppSettingKey.AI_PROACTIVE_DISMISS_COOLDOWN_HOURS,
                        Integer.toString(DEFAULT_DISMISS_COOLDOWN_HOURS)),
                DEFAULT_DISMISS_COOLDOWN_HOURS,
                1,
                168
        );
        UserAiAssistantPreference row =
                userAiPreferenceRepository.findById(emp).orElseGet(() -> userAiPreferenceRepository.save(new UserAiAssistantPreference(emp)));
        row.setProactiveCooldownUntil(OffsetDateTime.now().plusHours(coolHours));
        userAiPreferenceRepository.save(row);

        auditLogService.safeRecord(
                AuditEventType.AI_SUGGESTION_INBOX_DISMISSED,
                userId(emp),
                "AI_SUGGESTION_INBOX",
                suggestionId,
                null,
                "cooldownHours=" + coolHours,
                null
        );
    }

    /**
     * Marks a suggestion handled without executing mutating APIs here (Phase 7-3-3 defers real mutations to domain flows).
     */
    @Transactional
    public void acknowledgeInboxItem(UserPrincipal principal, Long suggestionId, String employeeNo) {
        String emp = resolveSelfEmployeeNo(principal, employeeNo);
        AiSuggestionInboxItem item = inboxRepository.findByIdAndRecipientEmployeeNo(suggestionId, emp)
                .orElseThrow(() -> new IllegalArgumentException("제안을 찾을 수 없습니다."));
        if (item.getStatus() != AiSuggestionInboxStatus.PENDING) {
            return;
        }
        item.setStatus(AiSuggestionInboxStatus.ACTED);
        inboxRepository.save(item);
        auditLogService.safeRecord(
                AuditEventType.AI_SUGGESTION_INBOX_ACTED,
                userId(emp),
                "AI_SUGGESTION_INBOX",
                suggestionId,
                null,
                "kind=" + item.getSuggestionKind(),
                null
        );
    }

    /**
     * Internal enqueue guard for future proactive jobs (rate limit + DM exclusion + membership + dismiss cooldown).
     */
    @Transactional
    public AiSuggestionInboxItem enqueueSuggestion(
            String recipientEmployeeNo,
            AiSuggestionKind kind,
            Long channelIdOrNull,
            String title,
            String summary,
            String payloadJson,
            Double confidence
    ) {
        String recipient = Objects.requireNonNull(recipientEmployeeNo, "recipient").trim();
        if (recipient.isEmpty()) {
            throw new IllegalArgumentException("수신자 사번이 필요합니다.");
        }
        UserAiAssistantPreference up = userAiPreferenceRepository.findById(recipient).orElse(null);
        if (up != null && up.getProactiveCooldownUntil() != null && OffsetDateTime.now().isBefore(up.getProactiveCooldownUntil())) {
            throw new IllegalStateException("사용자 프로액티브 거절 쿨다운 중입니다.");
        }

        Channel channel = null;
        if (channelIdOrNull != null) {
            channel = channelRepository.findById(channelIdOrNull).orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
            if (channel.getChannelType() == ChannelType.DM) {
                throw new IllegalArgumentException("DM 채널에는 프로액티브 제안을 적재할 수 없습니다.");
            }
            boolean optedIn = channelAiPreferenceRepository.findByChannelId(channelIdOrNull)
                    .map(ChannelAiAssistantPreference::isProactiveOptIn)
                    .orElse(false);
            if (!optedIn) {
                throw new IllegalStateException("채널이 프로액티브 비서 옵트인 상태가 아닙니다.");
            }
            if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channelIdOrNull, recipient)) {
                throw new IllegalArgumentException("수신자가 해당 채널 멤버가 아닙니다.");
            }
            int maxPerHour = parsePositiveInt(
                    appSettingsService.get(
                            AppSettingKey.AI_PROACTIVE_MAX_PER_CHANNEL_PER_HOUR,
                            Integer.toString(DEFAULT_MAX_PER_CHANNEL_PER_HOUR)),
                    DEFAULT_MAX_PER_CHANNEL_PER_HOUR,
                    1,
                    500
            );
            OffsetDateTime since = OffsetDateTime.now().minusHours(1);
            long recent = inboxRepository.countCreatedAfterForChannel(channelIdOrNull, since);
            if (recent >= maxPerHour) {
                throw new IllegalStateException("채널별 시간당 프로액티브 제안 상한을 초과했습니다.");
            }
        }

        AiSuggestionInboxItem saved = inboxRepository.save(new AiSuggestionInboxItem(
                recipient,
                kind,
                channel,
                trimTitle(title),
                trimSummary(summary),
                payloadJson,
                confidence
        ));
        auditLogService.safeRecord(
                AuditEventType.AI_SUGGESTION_INBOX_CREATED,
                userId(recipient),
                "AI_SUGGESTION_INBOX",
                saved.getId(),
                channel != null ? channel.getWorkspaceKey() : null,
                "kind=" + kind + ",channelId=" + channelIdOrNull,
                null
        );
        return saved;
    }

    private AiSuggestionInboxItemResponse toInboxResponse(AiSuggestionInboxItem it) {
        Long chId = it.getChannel() != null ? it.getChannel().getId() : null;
        return new AiSuggestionInboxItemResponse(
                it.getId(),
                it.getSuggestionKind().name(),
                it.getStatus().name(),
                chId,
                it.getTitle(),
                it.getSummary(),
                it.getPayloadJson(),
                it.getCreatedAt()
        );
    }

    private Channel loadChannelAndRequireMembership(Long channelId, String employeeNo) {
        Channel channel = channelRepository.findById(channelId).orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channelId, employeeNo)) {
            throw new ForbiddenException("채널 멤버만 AI 비서 설정을 조회할 수 있습니다.");
        }
        return channel;
    }

    private static String requireEmployee(UserPrincipal principal) {
        if (principal == null || principal.employeeNo() == null || principal.employeeNo().isBlank()) {
            throw new IllegalArgumentException("인증이 필요합니다.");
        }
        return principal.employeeNo().trim();
    }

    private static String resolveSelfEmployeeNo(UserPrincipal principal, String employeeNo) {
        if (principal == null) {
            throw new IllegalArgumentException("인증이 필요합니다.");
        }
        if (employeeNo == null || employeeNo.isBlank()) {
            return principal.employeeNo().trim();
        }
        if (!principal.employeeNo().trim().equals(employeeNo.trim())) {
            throw new IllegalArgumentException("다른 사용자의 AI 비서 설정에 접근할 수 없습니다.");
        }
        return employeeNo.trim();
    }

    private Long userId(String employeeNo) {
        Optional<User> u = userRepository.findByEmployeeNo(employeeNo);
        return u.map(User::getId).orElse(null);
    }

    private static String trimTitle(String t) {
        if (t == null || t.isBlank()) {
            return "(제목 없음)";
        }
        String s = t.trim();
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private static String trimSummary(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private static int parsePositiveInt(String raw, int fallback, int min, int max) {
        try {
            int v = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, v));
        } catch (Exception e) {
            return Math.max(min, Math.min(max, fallback));
        }
    }
}
