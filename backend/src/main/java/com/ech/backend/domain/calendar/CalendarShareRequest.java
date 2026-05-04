package com.ech.backend.domain.calendar;

import com.ech.backend.domain.channel.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "calendar_share_requests")
public class CalendarShareRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_employee_no", nullable = false, length = 50)
    private String senderEmployeeNo;

    @Column(name = "recipient_employee_no", nullable = false, length = 50)
    private String recipientEmployeeNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_channel_id", nullable = false)
    private Channel originChannel;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CalendarShareStatus status = CalendarShareStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "accepted_event_id")
    private Long acceptedEventId;

    /** When the share was created from an existing event on the sender's calendar (audit / UI). */
    @Column(name = "source_event_id")
    private Long sourceEventId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected CalendarShareRequest() {
    }

    public CalendarShareRequest(
            String senderEmployeeNo,
            String recipientEmployeeNo,
            Channel originChannel,
            String title,
            String description,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            OffsetDateTime expiresAt,
            Long sourceEventId
    ) {
        this.senderEmployeeNo = senderEmployeeNo;
        this.recipientEmployeeNo = recipientEmployeeNo;
        this.originChannel = originChannel;
        this.title = title;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.expiresAt = expiresAt;
        this.sourceEventId = sourceEventId;
        this.status = CalendarShareStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public String getSenderEmployeeNo() {
        return senderEmployeeNo;
    }

    public String getRecipientEmployeeNo() {
        return recipientEmployeeNo;
    }

    public Channel getOriginChannel() {
        return originChannel;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public OffsetDateTime getEndsAt() {
        return endsAt;
    }

    public CalendarShareStatus getStatus() {
        return status;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public Long getAcceptedEventId() {
        return acceptedEventId;
    }

    public Long getSourceEventId() {
        return sourceEventId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void markAccepted(Long eventId) {
        this.status = CalendarShareStatus.ACCEPTED;
        this.acceptedEventId = eventId;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markDeclined() {
        this.status = CalendarShareStatus.DECLINED;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markExpired() {
        this.status = CalendarShareStatus.EXPIRED;
        this.updatedAt = OffsetDateTime.now();
    }
}
