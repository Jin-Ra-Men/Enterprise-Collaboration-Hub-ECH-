package com.ech.backend.api.aiassistant;

import com.ech.backend.api.settings.AppSettingsService;
import com.ech.backend.domain.aiassistant.AiSuggestionDigestMode;
import com.ech.backend.domain.aiassistant.AiSuggestionInboxRepository;
import com.ech.backend.domain.aiassistant.AiSuggestionKind;
import com.ech.backend.domain.aiassistant.ChannelAiAssistantPreference;
import com.ech.backend.domain.aiassistant.ChannelAiAssistantPreferenceRepository;
import com.ech.backend.domain.aiassistant.UserAiAssistantPreference;
import com.ech.backend.domain.aiassistant.UserAiAssistantPreferenceRepository;
import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.channel.ChannelMember;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.channel.ChannelMemberRole;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.channel.ChannelType;
import com.ech.backend.domain.message.MessageRepository;
import com.ech.backend.domain.settings.AppSettingKey;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 프로액티브 제안 적재 트리거(규칙·스케줄). LLM 호출 없음.
 *
 * <ul>
 *   <li>매시간: 옵트인 채널의 최근 1시간 타임라인 활동이 기준 이상이면 REALTIME 다이제스트 사용자인 채널 관리자에게 {@link AiSuggestionKind#WORK_ITEM_HINT}</li>
 *   <li>매일 09:15(Asia/Seoul): DAILY 또는 (월요일만) WEEKLY 다이제스트 사용자에게 옵트인 채널 소속 시 {@link AiSuggestionKind#DIGEST_SUMMARY}</li>
 * </ul>
 */
@Component
public class ProactiveAiSuggestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProactiveAiSuggestionScheduler.class);

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static final int DEFAULT_ACTIVITY_MIN = 5;

    private final AiAssistantService aiAssistantService;
    private final AppSettingsService appSettingsService;
    private final ChannelAiAssistantPreferenceRepository channelAiAssistantPreferenceRepository;
    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final MessageRepository messageRepository;
    private final UserAiAssistantPreferenceRepository userAiAssistantPreferenceRepository;
    private final AiSuggestionInboxRepository inboxRepository;

    public ProactiveAiSuggestionScheduler(
            AiAssistantService aiAssistantService,
            AppSettingsService appSettingsService,
            ChannelAiAssistantPreferenceRepository channelAiAssistantPreferenceRepository,
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            MessageRepository messageRepository,
            UserAiAssistantPreferenceRepository userAiAssistantPreferenceRepository,
            AiSuggestionInboxRepository inboxRepository
    ) {
        this.aiAssistantService = aiAssistantService;
        this.appSettingsService = appSettingsService;
        this.channelAiAssistantPreferenceRepository = channelAiAssistantPreferenceRepository;
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.messageRepository = messageRepository;
        this.userAiAssistantPreferenceRepository = userAiAssistantPreferenceRepository;
        this.inboxRepository = inboxRepository;
    }

    private boolean jobsEnabled() {
        String v = appSettingsService.get(AppSettingKey.AI_PROACTIVE_JOBS_ENABLED, "true");
        return Boolean.parseBoolean(v != null ? v.trim() : "true");
    }

    private int activityMinMessagesPerHour() {
        String raw = appSettingsService.get(
                AppSettingKey.AI_PROACTIVE_ACTIVITY_MIN_MESSAGES_PER_HOUR,
                Integer.toString(DEFAULT_ACTIVITY_MIN));
        try {
            int n = Integer.parseInt(raw.trim());
            return Math.max(1, Math.min(500, n));
        } catch (Exception e) {
            return DEFAULT_ACTIVITY_MIN;
        }
    }

    /** 매시 정각 7분 — 서버 로컬 크론(활동 창은 UTC 오프셋 기준 롤링 1시간). */
    @Scheduled(cron = "0 7 * * * *")
    public void hourlyActivityHints() {
        if (!jobsEnabled()) {
            return;
        }
        int minMsgs = activityMinMessagesPerHour();
        OffsetDateTime since = OffsetDateTime.now().minusHours(1);
        List<ChannelAiAssistantPreference> prefs = channelAiAssistantPreferenceRepository.findByProactiveOptInTrue();
        for (ChannelAiAssistantPreference pref : prefs) {
            Long cid = pref.getChannelId();
            try {
                Optional<Channel> chOpt = channelRepository.findById(cid);
                if (chOpt.isEmpty() || chOpt.get().getChannelType() == ChannelType.DM) {
                    continue;
                }
                long activity = messageRepository.countTimelineActivitySince(cid, since);
                if (activity < minMsgs) {
                    continue;
                }
                List<ChannelMember> members = channelMemberRepository.findByChannelIdFetchUsers(cid);
                OffsetDateTime dedupeSince = OffsetDateTime.now().minusHours(24);
                String payload = "{\"deepLink\":\"workHub\",\"channelId\":" + cid + "}";
                for (ChannelMember cm : members) {
                    if (cm.getMemberRole() != ChannelMemberRole.MANAGER) {
                        continue;
                    }
                    String emp = cm.getUser().getEmployeeNo();
                    UserAiAssistantPreference up = userAiAssistantPreferenceRepository.findById(emp).orElse(null);
                    if (up != null && !up.isAiAssistantEnabled()) {
                        continue;
                    }
                    AiSuggestionDigestMode mode =
                            up != null ? up.getDigestMode() : AiSuggestionDigestMode.REALTIME;
                    if (mode != AiSuggestionDigestMode.REALTIME) {
                        continue;
                    }
                    long dup = inboxRepository.countByRecipientEmployeeNoAndChannel_IdAndSuggestionKindAndCreatedAtAfter(
                            emp, cid, AiSuggestionKind.WORK_ITEM_HINT, dedupeSince);
                    if (dup > 0) {
                        continue;
                    }
                    aiAssistantService.enqueueSuggestion(
                            emp,
                            AiSuggestionKind.WORK_ITEM_HINT,
                            cid,
                            "채널 활동이 활발합니다",
                            "업무로 정리하거나 칸반에 연결할 만한 논의가 있는지 확인해 보세요.",
                            payload,
                            null);
                }
            } catch (Exception e) {
                log.debug("[ProactiveAi] hourly skip channelId={}: {}", cid, e.getMessage());
            }
        }
    }

    /** 매일 09:15 Asia/Seoul — 다이제스트 모드 사용자에게 제안함 배치 알림(동일 현지일 1건 디듀프). */
    @Scheduled(cron = "0 15 9 * * *", zone = "Asia/Seoul")
    public void digestBatchAsiaSeoul() {
        if (!jobsEnabled()) {
            return;
        }
        ZonedDateTime nowSeoul = ZonedDateTime.now(SEOUL);
        OffsetDateTime startOfDaySeoul = nowSeoul.toLocalDate().atStartOfDay(SEOUL).toOffsetDateTime();

        List<UserAiAssistantPreference> digestUsers = userAiAssistantPreferenceRepository.findByDigestModeIn(
                List.of(AiSuggestionDigestMode.DAILY, AiSuggestionDigestMode.WEEKLY));

        for (UserAiAssistantPreference up : digestUsers) {
            try {
                if (!up.isAiAssistantEnabled()) {
                    continue;
                }
                if (up.getDigestMode() == AiSuggestionDigestMode.WEEKLY && nowSeoul.getDayOfWeek() != DayOfWeek.MONDAY) {
                    continue;
                }
                String emp = up.getEmployeeNo();
                if (!employeeSharesAnyOptInNonDmChannel(emp)) {
                    continue;
                }
                long already = inboxRepository.countByRecipientEmployeeNoAndSuggestionKindAndCreatedAtAfter(
                        emp, AiSuggestionKind.DIGEST_SUMMARY, startOfDaySeoul);
                if (already > 0) {
                    continue;
                }
                String payload = "{\"deepLink\":\"aiInbox\",\"digest\":true}";
                aiAssistantService.enqueueSuggestion(
                        emp,
                        AiSuggestionKind.DIGEST_SUMMARY,
                        null,
                        "오늘의 제안함 확인",
                        "프로액티브 알림을 한 번에 확인해 보세요.",
                        payload,
                        null);
            } catch (Exception e) {
                log.debug("[ProactiveAi] digest skip emp={}: {}", up.getEmployeeNo(), e.getMessage());
            }
        }
    }

    private boolean employeeSharesAnyOptInNonDmChannel(String employeeNo) {
        for (ChannelAiAssistantPreference p : channelAiAssistantPreferenceRepository.findByProactiveOptInTrue()) {
            Long cid = p.getChannelId();
            Optional<Channel> ch = channelRepository.findById(cid);
            if (ch.isEmpty() || ch.get().getChannelType() == ChannelType.DM) {
                continue;
            }
            if (channelMemberRepository.existsByChannelIdAndUserEmployeeNo(cid, employeeNo)) {
                return true;
            }
        }
        return false;
    }
}
