package com.ech.backend.api.calendar;

import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.api.calendar.dto.CalendarEventResponse;
import com.ech.backend.api.calendar.dto.CalendarShareResponse;
import com.ech.backend.api.calendar.dto.CreateCalendarEventRequest;
import com.ech.backend.api.calendar.dto.CreateCalendarShareRequest;
import com.ech.backend.api.calendar.dto.UpdateCalendarEventRequest;
import com.ech.backend.common.security.UserPrincipal;
import com.ech.backend.domain.audit.AuditEventType;
import com.ech.backend.domain.calendar.CalendarEvent;
import com.ech.backend.domain.calendar.CalendarEventRepository;
import com.ech.backend.domain.calendar.CalendarShareRequest;
import com.ech.backend.domain.calendar.CalendarShareRequestRepository;
import com.ech.backend.domain.calendar.CalendarShareStatus;
import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarService {

    private static final int TITLE_MAX = 500;
    private static final int DESCRIPTION_MAX = 8000;

    private final CalendarEventRepository calendarEventRepository;
    private final CalendarShareRequestRepository calendarShareRequestRepository;
    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public CalendarService(
            CalendarEventRepository calendarEventRepository,
            CalendarShareRequestRepository calendarShareRequestRepository,
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            UserRepository userRepository,
            AuditLogService auditLogService
    ) {
        this.calendarEventRepository = calendarEventRepository;
        this.calendarShareRequestRepository = calendarShareRequestRepository;
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
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

    @Transactional
    public CalendarEventResponse createEvent(UserPrincipal principal, CreateCalendarEventRequest request) {
        String owner = resolveSelfEmployeeNo(principal, request.ownerEmployeeNo());
        validateTimeRange(request.startsAt(), request.endsAt());
        String title = trimTitle(request.title());
        String description = trimDescription(request.description());

        CalendarEvent saved = calendarEventRepository.save(new CalendarEvent(
                owner,
                title,
                description,
                request.startsAt(),
                request.endsAt(),
                null,
                null,
                "USER"
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
                share,
                "USER"
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
                shareId,
                e.getCreatedByActor(),
                e.isInUse(),
                e.getCreatedAt(),
                e.getUpdatedAt()
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
