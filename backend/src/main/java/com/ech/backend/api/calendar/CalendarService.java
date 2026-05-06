package com.ech.backend.api.calendar;

import com.ech.backend.api.aiassistant.AiAssistantService;
import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.api.calendar.dto.CalendarConflictCheckResponse;
import com.ech.backend.api.calendar.dto.CalendarEventOverlapRow;
import com.ech.backend.api.calendar.dto.CalendarEventResponse;
import com.ech.backend.api.calendar.dto.CalendarImportResponse;
import com.ech.backend.api.calendar.dto.CalendarShareResponse;
import com.ech.backend.api.calendar.dto.CalendarSuggestionResponse;
import com.ech.backend.api.calendar.dto.CreateCalendarEventRequest;
import com.ech.backend.api.calendar.dto.CreateCalendarShareRequest;
import com.ech.backend.api.calendar.dto.CreateCalendarSuggestionRequest;
import com.ech.backend.api.calendar.dto.UpdateCalendarEventRequest;
import com.ech.backend.common.exception.ForbiddenException;
import com.ech.backend.common.security.UserPrincipal;
import com.ech.backend.domain.audit.AuditEventType;
import com.ech.backend.domain.calendar.CalendarEvent;
import com.ech.backend.domain.calendar.CalendarEventRepository;
import com.ech.backend.domain.calendar.CalendarShareRequest;
import com.ech.backend.domain.calendar.CalendarShareRequestRepository;
import com.ech.backend.domain.calendar.CalendarShareStatus;
import com.ech.backend.domain.calendar.CalendarSuggestion;
import com.ech.backend.domain.calendar.CalendarSuggestionRepository;
import com.ech.backend.domain.calendar.CalendarSuggestionStatus;
import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarService {

    private static final int TITLE_MAX = 500;
    private static final int DESCRIPTION_MAX = 8000;
    private static final int ICS_IMPORT_MAX_BYTES = 512 * 1024;
    private static final int ICS_IMPORT_MAX_EVENTS = 64;

    private final CalendarEventRepository calendarEventRepository;
    private final CalendarShareRequestRepository calendarShareRequestRepository;
    private final CalendarSuggestionRepository calendarSuggestionRepository;
    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final AiAssistantService aiAssistantService;

    public CalendarService(
            CalendarEventRepository calendarEventRepository,
            CalendarShareRequestRepository calendarShareRequestRepository,
            CalendarSuggestionRepository calendarSuggestionRepository,
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            AiAssistantService aiAssistantService
    ) {
        this.calendarEventRepository = calendarEventRepository;
        this.calendarShareRequestRepository = calendarShareRequestRepository;
        this.calendarSuggestionRepository = calendarSuggestionRepository;
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.aiAssistantService = aiAssistantService;
    }

    @Transactional(readOnly = true)
    public List<CalendarEventResponse> listEvents(
            UserPrincipal principal,
            String ownerEmployeeNo,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        String owner = resolveSelfEmployeeNo(principal, ownerEmployeeNo);
        OffsetDateTime rangeStart = from != null ? from : OffsetDateTime.now().minusDays(30);
        OffsetDateTime rangeEnd = to != null ? to : OffsetDateTime.now().plusDays(180);
        if (!rangeEnd.isAfter(rangeStart)) {
            throw new IllegalArgumentException("조회 종료 시각은 시작 시각보다 이후여야 합니다.");
        }
        return calendarEventRepository.findActiveForOwnerInRange(owner, rangeStart, rangeEnd).stream()
                .map(this::toEventResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] exportIcs(
            UserPrincipal principal,
            String ownerEmployeeNo,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        String owner = resolveSelfEmployeeNo(principal, ownerEmployeeNo);
        OffsetDateTime rangeStart = from != null ? from : OffsetDateTime.now().minusDays(30);
        OffsetDateTime rangeEnd = to != null ? to : OffsetDateTime.now().plusDays(180);
        if (!rangeEnd.isAfter(rangeStart)) {
            throw new IllegalArgumentException("조회 종료 시각은 시작 시각보다 이후여야 합니다.");
        }
        List<CalendarEvent> events = calendarEventRepository.findActiveForOwnerInRange(owner, rangeStart, rangeEnd);
        return CalendarIcsCodec.buildUtf8(events).getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public CalendarImportResponse importIcs(UserPrincipal principal, String ownerEmployeeNo, byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new IllegalArgumentException("파일이 비어 있습니다.");
        }
        if (fileBytes.length > ICS_IMPORT_MAX_BYTES) {
            throw new IllegalArgumentException(
                    "iCal 파일이 너무 큽니다. 최대 " + (ICS_IMPORT_MAX_BYTES / 1024) + "KB까지 허용됩니다.");
        }
        String owner = resolveSelfEmployeeNo(principal, ownerEmployeeNo);
        List<CalendarIcsCodec.ParsedVEvent> parsed = CalendarIcsCodec.parse(fileBytes);
        if (parsed.size() > ICS_IMPORT_MAX_EVENTS) {
            throw new IllegalArgumentException(
                    "한 번에 가져올 수 있는 일정은 최대 " + ICS_IMPORT_MAX_EVENTS + "개입니다.");
        }
        int imported = 0;
        int skipped = 0;
        for (CalendarIcsCodec.ParsedVEvent pe : parsed) {
            try {
                String title = pe.summary() != null && !pe.summary().isBlank()
                        ? trimTitle(pe.summary())
                        : "(제목 없음)";
                String desc = trimDescription(pe.description());
                validateTimeRange(pe.startsAt(), pe.endsAt());
                CreateCalendarEventRequest req = new CreateCalendarEventRequest(
                        owner,
                        title,
                        desc,
                        pe.startsAt(),
                        pe.endsAt(),
                        null,
                        null,
                        null,
                        "USER"
                );
                createEvent(principal, req);
                imported++;
            } catch (IllegalArgumentException ex) {
                skipped++;
            }
        }
        return new CalendarImportResponse(imported, skipped);
    }

    @Transactional
    public CalendarEventResponse createEvent(UserPrincipal principal, CreateCalendarEventRequest request) {
        String owner = resolveSelfEmployeeNo(principal, request.ownerEmployeeNo());
        validateTimeRange(request.startsAt(), request.endsAt());
        String title = trimTitle(request.title());
        String description = trimDescription(request.description());
        ResolvedOrigin origin = resolveOrigins(owner, request.originChannelId(), request.originDmChannelId());
        String idsJson = CalendarOriginIdsJson.serialize(
                CalendarOriginIdsJson.normalizeIncoming(request.originMessageIds()));

        String directActor = request.createdByActor();
        if (directActor != null && !directActor.isBlank()) {
            String upper = directActor.trim().toUpperCase();
            if ("AI_ASSISTANT".equals(upper)) {
                throw new IllegalArgumentException(
                        "직접 일정 생성에서는 AI_ASSISTANT를 사용할 수 없습니다. 제안 API를 사용하세요.");
            }
            if (!"USER".equals(upper)) {
                throw new IllegalArgumentException("createdByActor는 USER만 허용됩니다.");
            }
        }

        CalendarEvent saved = calendarEventRepository.save(new CalendarEvent(
                owner,
                title,
                description,
                request.startsAt(),
                request.endsAt(),
                origin.originChannel(),
                origin.originDmChannel(),
                null,
                "USER",
                idsJson
        ));
        auditLogService.safeRecord(
                AuditEventType.CALENDAR_EVENT_CREATED,
                userId(owner),
                "CALENDAR_EVENT",
                saved.getId(),
                null,
                "owner=" + owner,
                null
        );
        return toEventResponse(saved);
    }

    @Transactional
    public CalendarEventResponse updateEvent(
            UserPrincipal principal,
            Long eventId,
            String actorEmployeeNo,
            UpdateCalendarEventRequest request
    ) {
        String actor = resolveSelfEmployeeNo(principal, actorEmployeeNo);
        CalendarEvent event = calendarEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("일정을 찾을 수 없습니다."));
        if (!event.getOwnerEmployeeNo().equals(actor)) {
            throw new IllegalArgumentException("본인 일정만 수정할 수 있습니다.");
        }
        if (!event.isInUse()) {
            throw new IllegalArgumentException("삭제된 일정입니다.");
        }
        OffsetDateTime starts = request.startsAt() != null ? request.startsAt() : event.getStartsAt();
        OffsetDateTime ends = request.endsAt() != null ? request.endsAt() : event.getEndsAt();
        validateTimeRange(starts, ends);
        event.update(
                request.title() != null ? request.title() : event.getTitle(),
                request.description() != null ? request.description() : event.getDescription(),
                request.startsAt(),
                request.endsAt()
        );
        auditLogService.safeRecord(
                AuditEventType.CALENDAR_EVENT_UPDATED,
                userId(actor),
                "CALENDAR_EVENT",
                event.getId(),
                null,
                "owner=" + actor,
                null
        );
        return toEventResponse(event);
    }

    @Transactional
    public void deleteEvent(UserPrincipal principal, Long eventId, String actorEmployeeNo) {
        String actor = resolveSelfEmployeeNo(principal, actorEmployeeNo);
        CalendarEvent event = calendarEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("일정을 찾을 수 없습니다."));
        if (!event.getOwnerEmployeeNo().equals(actor)) {
            throw new IllegalArgumentException("본인 일정만 삭제할 수 있습니다.");
        }
        if (!event.isInUse()) {
            return;
        }
        event.setInUse(false);
        auditLogService.safeRecord(
                AuditEventType.CALENDAR_EVENT_DELETED,
                userId(actor),
                "CALENDAR_EVENT",
                event.getId(),
                null,
                "owner=" + actor,
                null
        );
    }

    @Transactional
    public CalendarShareResponse createShare(
            UserPrincipal principal,
            Long channelId,
            CreateCalendarShareRequest request
    ) {
        expireStalePendingShares();
        String sender = resolveSelfEmployeeNo(principal, request.senderEmployeeNo());
        if (sender.equals(request.recipientEmployeeNo())) {
            throw new IllegalArgumentException("본인에게는 일정을 공유할 수 없습니다.");
        }
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channelId, sender)) {
            throw new IllegalArgumentException("채널 멤버만 일정을 공유할 수 있습니다.");
        }
        if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channelId, request.recipientEmployeeNo())) {
            throw new IllegalArgumentException("수신자가 해당 채널(또는 DM) 멤버가 아닙니다.");
        }
        userRepository.findByEmployeeNo(request.recipientEmployeeNo())
                .orElseThrow(() -> new IllegalArgumentException("수신 사용자를 찾을 수 없습니다."));

        String title;
        String description;
        OffsetDateTime startsAt;
        OffsetDateTime endsAt;
        Long sourceEventId = request.sourceEventId();

        if (sourceEventId != null) {
            CalendarEvent source = calendarEventRepository.findById(sourceEventId)
                    .orElseThrow(() -> new IllegalArgumentException("원본 일정을 찾을 수 없습니다."));
            if (!source.getOwnerEmployeeNo().equals(sender) || !source.isInUse()) {
                throw new IllegalArgumentException("본인의 활성 일정만 공유할 수 있습니다.");
            }
            title = source.getTitle();
            description = source.getDescription();
            startsAt = source.getStartsAt();
            endsAt = source.getEndsAt();
        } else {
            if (request.title() == null || request.title().isBlank()) {
                throw new IllegalArgumentException("제목이 필요합니다.");
            }
            if (request.startsAt() == null || request.endsAt() == null) {
                throw new IllegalArgumentException("시작·종료 시각이 필요합니다.");
            }
            title = trimTitle(request.title());
            description = trimDescription(request.description());
            startsAt = request.startsAt();
            endsAt = request.endsAt();
        }
        validateTimeRange(startsAt, endsAt);

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = request.expiresAt() != null ? request.expiresAt() : now.plusDays(7);
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("만료 시각은 현재보다 이후여야 합니다.");
        }

        CalendarShareRequest saved = calendarShareRequestRepository.save(new CalendarShareRequest(
                sender,
                request.recipientEmployeeNo(),
                channel,
                title,
                description,
                startsAt,
                endsAt,
                expiresAt,
                sourceEventId
        ));
        auditLogService.safeRecord(
                AuditEventType.CALENDAR_SHARE_CREATED,
                userId(sender),
                "CALENDAR_SHARE",
                saved.getId(),
                channel.getWorkspaceKey(),
                "originChannelId=" + channelId + ",recipient=" + request.recipientEmployeeNo(),
                null
        );
        return toShareResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CalendarShareResponse> listIncomingPending(UserPrincipal principal, String employeeNo) {
        String emp = resolveSelfEmployeeNo(principal, employeeNo);
        OffsetDateTime now = OffsetDateTime.now();
        return calendarShareRequestRepository
                .findByRecipientEmployeeNoAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                        emp, CalendarShareStatus.PENDING, now)
                .stream()
                .map(this::toShareResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CalendarShareResponse> listOutgoing(UserPrincipal principal, String employeeNo) {
        String emp = resolveSelfEmployeeNo(principal, employeeNo);
        return calendarShareRequestRepository.findBySenderEmployeeNoOrderByCreatedAtDesc(emp).stream()
                .map(this::toShareResponse)
                .toList();
    }

    @Transactional
    public CalendarEventResponse acceptShare(UserPrincipal principal, Long shareId, String recipientEmployeeNo) {
        expireStalePendingShares();
        String recipient = resolveSelfEmployeeNo(principal, recipientEmployeeNo);
        CalendarShareRequest share = calendarShareRequestRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("공유 요청을 찾을 수 없습니다."));
        if (!share.getRecipientEmployeeNo().equals(recipient)) {
            throw new IllegalArgumentException("수신자만 공유를 수락할 수 있습니다.");
        }
        if (share.getStatus() != CalendarShareStatus.PENDING) {
            throw new IllegalArgumentException("이미 처리되었거나 만료된 공유 요청입니다.");
        }
        if (OffsetDateTime.now().isAfter(share.getExpiresAt())) {
            share.markExpired();
            throw new IllegalArgumentException("만료된 공유 요청입니다.");
        }

        CalendarEvent event = calendarEventRepository.save(new CalendarEvent(
                recipient,
                share.getTitle(),
                share.getDescription(),
                share.getStartsAt(),
                share.getEndsAt(),
                share.getOriginChannel(),
                null,
                share,
                "USER",
                null
        ));
        share.markAccepted(event.getId());
        auditLogService.safeRecord(
                AuditEventType.CALENDAR_SHARE_ACCEPTED,
                userId(recipient),
                "CALENDAR_SHARE",
                share.getId(),
                share.getOriginChannel().getWorkspaceKey(),
                "eventId=" + event.getId(),
                null
        );
        return toEventResponse(event);
    }

    @Transactional
    public void declineShare(UserPrincipal principal, Long shareId, String recipientEmployeeNo) {
        expireStalePendingShares();
        String recipient = resolveSelfEmployeeNo(principal, recipientEmployeeNo);
        CalendarShareRequest share = calendarShareRequestRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("공유 요청을 찾을 수 없습니다."));
        if (!share.getRecipientEmployeeNo().equals(recipient)) {
            throw new IllegalArgumentException("수신자만 공유를 거절할 수 있습니다.");
        }
        if (share.getStatus() != CalendarShareStatus.PENDING) {
            throw new IllegalArgumentException("이미 처리되었거나 만료된 공유 요청입니다.");
        }
        share.markDeclined();
        auditLogService.safeRecord(
                AuditEventType.CALENDAR_SHARE_DECLINED,
                userId(recipient),
                "CALENDAR_SHARE",
                share.getId(),
                share.getOriginChannel().getWorkspaceKey(),
                null,
                null
        );
    }

    @Transactional(readOnly = true)
    public List<CalendarSuggestionResponse> listSuggestions(
            UserPrincipal principal,
            String employeeNo,
            CalendarSuggestionStatus status
    ) {
        String owner = resolveSelfEmployeeNo(principal, employeeNo);
        CalendarSuggestionStatus st = status != null ? status : CalendarSuggestionStatus.PENDING;
        boolean aiOn = aiAssistantService.isAiAssistantEnabled(owner);
        return calendarSuggestionRepository
                .findByOwnerEmployeeNoAndStatusOrderByCreatedAtDesc(owner, st)
                .stream()
                .filter(s -> aiOn || !"AI_ASSISTANT".equalsIgnoreCase(s.getCreatedByActor()))
                .map(this::toSuggestionResponse)
                .toList();
    }

    @Transactional
    public CalendarSuggestionResponse createSuggestion(UserPrincipal principal, CreateCalendarSuggestionRequest request) {
        String callerEmp = principal.employeeNo() != null ? principal.employeeNo().trim() : "";
        String owner = resolveSelfEmployeeNo(principal, request.ownerEmployeeNo());
        validateTimeRange(request.startsAt(), request.endsAt());
        String title = trimTitle(request.title());
        String description = trimDescription(request.description());
        ResolvedOrigin origin = resolveOrigins(owner, request.originChannelId(), request.originDmChannelId());
        String idsJson = CalendarOriginIdsJson.serialize(
                CalendarOriginIdsJson.normalizeIncoming(request.originMessageIds()));

        String actor = normalizeSuggestionActor(request.createdByActor());
        if ("AI_ASSISTANT".equals(actor) && !aiAssistantService.isAiAssistantEnabled(callerEmp)) {
            throw new ForbiddenException("AI 비서 기능을 사용하지 않도록 설정되어 있어 AI 출처 일정 제안을 만들 수 없습니다.");
        }

        CalendarSuggestion saved = calendarSuggestionRepository.save(new CalendarSuggestion(
                owner,
                title,
                description,
                request.startsAt(),
                request.endsAt(),
                origin.originChannel(),
                origin.originDmChannel(),
                idsJson,
                actor
        ));
        auditLogService.safeRecord(
                AuditEventType.CALENDAR_SUGGESTION_CREATED,
                userId(owner),
                "CALENDAR_SUGGESTION",
                saved.getId(),
                null,
                "owner=" + owner + ",actor=" + actor,
                null
        );
        return toSuggestionResponse(saved);
    }

    /**
     * 프로액티브 LLM 인사이트 전용: 발신자(제안 수신자) 본인에게 {@code AI_ASSISTANT} 출처 일정 제안을 생성한다.
     * 스케줄러 등 비웹 컨텍스트에서만 호출한다.
     */
    @Transactional
    public Optional<Long> createAiAssistantSuggestionFromProactivePipeline(
            String ownerEmployeeNo,
            long originChannelId,
            long originMessageId,
            String title,
            String description,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt
    ) {
        String owner = ownerEmployeeNo == null ? "" : ownerEmployeeNo.trim();
        if (owner.isEmpty()) {
            return Optional.empty();
        }
        if (!aiAssistantService.isAiAssistantEnabled(owner)) {
            return Optional.empty();
        }
        String exactJson = "[" + originMessageId + "]";
        if (calendarSuggestionRepository.countPendingAiTouchingOriginMessage(owner, exactJson, originMessageId) > 0) {
            return Optional.empty();
        }
        validateTimeRange(startsAt, endsAt);
        String safeTitle = title == null || title.isBlank() ? "일정 제안" : title;
        String t = trimTitle(safeTitle);
        String d = trimDescription(description);
        ResolvedOrigin origin = resolveOrigins(owner, originChannelId, null);
        String idsJson = CalendarOriginIdsJson.serialize(List.of(originMessageId));

        CalendarSuggestion saved = calendarSuggestionRepository.save(new CalendarSuggestion(
                owner,
                t,
                d,
                startsAt,
                endsAt,
                origin.originChannel(),
                origin.originDmChannel(),
                idsJson,
                "AI_ASSISTANT"
        ));
        auditLogService.safeRecord(
                AuditEventType.CALENDAR_SUGGESTION_CREATED,
                userId(owner),
                "CALENDAR_SUGGESTION",
                saved.getId(),
                null,
                "owner=" + owner + ",actor=AI_ASSISTANT,proactiveConversationInsight=1",
                null
        );
        return Optional.of(saved.getId());
    }

    /**
     * Confirms a suggestion by creating a calendar event on the owner's calendar via the same persistence path as
     * direct creation (always {@code createdByActor=USER} on the saved event).
     */
    @Transactional
    public CalendarEventResponse confirmSuggestion(UserPrincipal principal, Long suggestionId, String employeeNo) {
        String owner = resolveSelfEmployeeNo(principal, employeeNo);
        CalendarSuggestion s = calendarSuggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new IllegalArgumentException("일정 제안을 찾을 수 없습니다."));
        if (!s.getOwnerEmployeeNo().equals(owner)) {
            throw new IllegalArgumentException("본인에게 온 제안만 확정할 수 있습니다.");
        }
        if (s.getStatus() != CalendarSuggestionStatus.PENDING) {
            throw new IllegalArgumentException("이미 처리된 제안입니다.");
        }
        if ("AI_ASSISTANT".equalsIgnoreCase(s.getCreatedByActor()) && !aiAssistantService.isAiAssistantEnabled(owner)) {
            throw new ForbiddenException("AI 비서 기능을 사용하지 않도록 설정되어 있어 AI 출처 일정 제안을 확정할 수 없습니다.");
        }
        validateTimeRange(s.getStartsAt(), s.getEndsAt());

        CalendarEvent event = calendarEventRepository.save(new CalendarEvent(
                owner,
                s.getTitle(),
                s.getDescription(),
                s.getStartsAt(),
                s.getEndsAt(),
                s.getOriginChannel(),
                s.getOriginDmChannel(),
                null,
                "USER",
                s.getOriginMessageIdsJson()
        ));
        s.markConfirmed(event.getId());
        auditLogService.safeRecord(
                AuditEventType.CALENDAR_EVENT_CREATED,
                userId(owner),
                "CALENDAR_EVENT",
                event.getId(),
                null,
                "owner=" + owner + ",fromSuggestionId=" + suggestionId,
                null
        );
        auditLogService.safeRecord(
                AuditEventType.CALENDAR_SUGGESTION_CONFIRMED,
                userId(owner),
                "CALENDAR_SUGGESTION",
                suggestionId,
                null,
                "eventId=" + event.getId(),
                null
        );
        return toEventResponse(event);
    }

    @Transactional
    public void dismissSuggestion(UserPrincipal principal, Long suggestionId, String employeeNo) {
        String owner = resolveSelfEmployeeNo(principal, employeeNo);
        CalendarSuggestion s = calendarSuggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new IllegalArgumentException("일정 제안을 찾을 수 없습니다."));
        if (!s.getOwnerEmployeeNo().equals(owner)) {
            throw new IllegalArgumentException("본인에게 온 제안만 해제할 수 있습니다.");
        }
        if (s.getStatus() != CalendarSuggestionStatus.PENDING) {
            throw new IllegalArgumentException("이미 처리된 제안입니다.");
        }
        s.markDismissed();
        auditLogService.safeRecord(
                AuditEventType.CALENDAR_SUGGESTION_DISMISSED,
                userId(owner),
                "CALENDAR_SUGGESTION",
                suggestionId,
                null,
                null,
                null
        );
    }

    @Transactional(readOnly = true)
    public CalendarConflictCheckResponse checkConflicts(
            UserPrincipal principal,
            String employeeNo,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            Long excludeEventId
    ) {
        String owner = resolveSelfEmployeeNo(principal, employeeNo);
        validateTimeRange(startsAt, endsAt);
        List<CalendarEventOverlapRow> rows = calendarEventRepository
                .findActiveOverlappingForOwner(owner, startsAt, endsAt, excludeEventId)
                .stream()
                .map(ev -> new CalendarEventOverlapRow(ev.getId(), ev.getTitle(), ev.getStartsAt(), ev.getEndsAt()))
                .toList();
        return new CalendarConflictCheckResponse(!rows.isEmpty(), rows);
    }

    /** Marks overdue PENDING shares as EXPIRED (call from write paths only). */
    @Transactional
    public void expireStalePendingShares() {
        OffsetDateTime now = OffsetDateTime.now();
        calendarShareRequestRepository.expirePendingBefore(
                now,
                CalendarShareStatus.PENDING,
                CalendarShareStatus.EXPIRED,
                now
        );
    }

    private String resolveSelfEmployeeNo(UserPrincipal principal, String employeeNo) {
        if (principal == null) {
            throw new IllegalArgumentException("인증이 필요합니다.");
        }
        if (employeeNo == null || employeeNo.isBlank()) {
            return principal.employeeNo();
        }
        if (!principal.employeeNo().equals(employeeNo.trim())) {
            throw new IllegalArgumentException("다른 사용자의 일정에 접근할 수 없습니다.");
        }
        return employeeNo.trim();
    }

    private void validateTimeRange(OffsetDateTime startsAt, OffsetDateTime endsAt) {
        if (startsAt == null || endsAt == null) {
            throw new IllegalArgumentException("시작·종료 시각이 필요합니다.");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("종료 시각은 시작 시각보다 이후여야 합니다.");
        }
    }

    private String trimTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("제목이 필요합니다.");
        }
        String t = title.trim();
        if (t.length() > TITLE_MAX) {
            throw new IllegalArgumentException("제목은 " + TITLE_MAX + "자 이하여야 합니다.");
        }
        return t;
    }

    private String trimDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String d = description.trim();
        if (d.length() > DESCRIPTION_MAX) {
            throw new IllegalArgumentException("설명은 " + DESCRIPTION_MAX + "자 이하여야 합니다.");
        }
        return d;
    }

    private Long userId(String employeeNo) {
        return userRepository.findByEmployeeNo(employeeNo).map(User::getId).orElse(null);
    }

    private record ResolvedOrigin(Channel originChannel, Channel originDmChannel) {
    }

    private ResolvedOrigin resolveOrigins(String actorEmployeeNo, Long originChannelId, Long originDmChannelId) {
        Channel originChannel = null;
        Channel originDmChannel = null;
        if (originChannelId != null) {
            long cid = originChannelId;
            Channel ch = channelRepository.findById(cid)
                    .orElseThrow(() -> new IllegalArgumentException("출처 채널을 찾을 수 없습니다."));
            if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(cid, actorEmployeeNo)) {
                throw new IllegalArgumentException("출처 채널의 멤버만 해당 채널을 일정 출처로 지정할 수 있습니다.");
            }
            originChannel = ch;
        }
        if (originDmChannelId != null) {
            long did = originDmChannelId;
            Channel dm = channelRepository.findById(did)
                    .orElseThrow(() -> new IllegalArgumentException("출처 DM 채널을 찾을 수 없습니다."));
            if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(did, actorEmployeeNo)) {
                throw new IllegalArgumentException("해당 DM의 참여자만 DM 출처를 지정할 수 있습니다.");
            }
            originDmChannel = dm;
        }
        return new ResolvedOrigin(originChannel, originDmChannel);
    }

    private static String normalizeSuggestionActor(String raw) {
        String a = raw == null || raw.isBlank() ? "USER" : raw.trim().toUpperCase(Locale.ROOT);
        if (!"USER".equals(a) && !"AI_ASSISTANT".equals(a)) {
            throw new IllegalArgumentException("createdByActor는 USER 또는 AI_ASSISTANT만 허용됩니다.");
        }
        return a;
    }

    private CalendarEventResponse toEventResponse(CalendarEvent e) {
        Long originChannelId = null;
        String originChannelName = null;
        String originChannelType = null;
        if (e.getOriginChannel() != null) {
            Hibernate.initialize(e.getOriginChannel());
            originChannelId = e.getOriginChannel().getId();
            originChannelName = e.getOriginChannel().getName();
            originChannelType = e.getOriginChannel().getChannelType().name();
        }
        Long originDmChannelId = null;
        String originDmChannelName = null;
        String originDmChannelType = null;
        if (e.getOriginDmChannel() != null) {
            Hibernate.initialize(e.getOriginDmChannel());
            originDmChannelId = e.getOriginDmChannel().getId();
            originDmChannelName = e.getOriginDmChannel().getName();
            originDmChannelType = e.getOriginDmChannel().getChannelType().name();
        }
        Long shareId = e.getSharedFromShare() != null ? e.getSharedFromShare().getId() : null;
        return new CalendarEventResponse(
                e.getId(),
                e.getOwnerEmployeeNo(),
                e.getTitle(),
                e.getDescription(),
                e.getStartsAt(),
                e.getEndsAt(),
                originChannelId,
                originChannelName,
                originChannelType,
                originDmChannelId,
                originDmChannelName,
                originDmChannelType,
                CalendarOriginIdsJson.deserialize(e.getOriginMessageIdsJson()),
                shareId,
                e.getCreatedByActor(),
                e.isInUse(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private CalendarSuggestionResponse toSuggestionResponse(CalendarSuggestion s) {
        Long originChannelId = null;
        String originChannelName = null;
        String originChannelType = null;
        if (s.getOriginChannel() != null) {
            Hibernate.initialize(s.getOriginChannel());
            originChannelId = s.getOriginChannel().getId();
            originChannelName = s.getOriginChannel().getName();
            originChannelType = s.getOriginChannel().getChannelType().name();
        }
        Long originDmChannelId = null;
        String originDmChannelName = null;
        String originDmChannelType = null;
        if (s.getOriginDmChannel() != null) {
            Hibernate.initialize(s.getOriginDmChannel());
            originDmChannelId = s.getOriginDmChannel().getId();
            originDmChannelName = s.getOriginDmChannel().getName();
            originDmChannelType = s.getOriginDmChannel().getChannelType().name();
        }
        return new CalendarSuggestionResponse(
                s.getId(),
                s.getOwnerEmployeeNo(),
                s.getTitle(),
                s.getDescription(),
                s.getStartsAt(),
                s.getEndsAt(),
                s.getStatus(),
                originChannelId,
                originChannelName,
                originChannelType,
                originDmChannelId,
                originDmChannelName,
                originDmChannelType,
                CalendarOriginIdsJson.deserialize(s.getOriginMessageIdsJson()),
                s.getCreatedByActor(),
                s.getConfirmedEventId(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }

    private CalendarShareResponse toShareResponse(CalendarShareRequest s) {
        Hibernate.initialize(s.getOriginChannel());
        Channel ch = s.getOriginChannel();
        return new CalendarShareResponse(
                s.getId(),
                s.getSenderEmployeeNo(),
                s.getRecipientEmployeeNo(),
                ch.getId(),
                ch.getName(),
                ch.getChannelType().name(),
                s.getTitle(),
                s.getDescription(),
                s.getStartsAt(),
                s.getEndsAt(),
                s.getStatus(),
                s.getExpiresAt(),
                s.getAcceptedEventId(),
                s.getSourceEventId(),
                s.getCreatedAt()
        );
    }
}
