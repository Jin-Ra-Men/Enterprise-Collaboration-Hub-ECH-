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
@Table(name = "calendar_suggestions")
public class CalendarSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_employee_no", nullable = false, length = 50)
    private String ownerEmployeeNo;

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
    private CalendarSuggestionStatus status = CalendarSuggestionStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_channel_id")
    private Channel originChannel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_dm_channel_id")
    private Channel originDmChannel;

    @Column(name = "origin_message_ids", columnDefinition = "TEXT")
    private String originMessageIdsJson;

    @Column(name = "created_by_actor", nullable = false, length = 30)
    private String createdByActor = "USER";

    @Column(name = "confirmed_event_id")
    private Long confirmedEventId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected CalendarSuggestion() {
    }

    public CalendarSuggestion(
            String ownerEmployeeNo,
            String title,
            String description,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            Channel originChannel,
            Channel originDmChannel,
            String originMessageIdsJson,
            String createdByActor
    ) {
        this.ownerEmployeeNo = ownerEmployeeNo;
        this.title = title;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.originChannel = originChannel;
        this.originDmChannel = originDmChannel;
        this.originMessageIdsJson = originMessageIdsJson;
        this.createdByActor = createdByActor != null && !createdByActor.isBlank()
                ? createdByActor.trim()
                : "USER";
        this.status = CalendarSuggestionStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public String getOwnerEmployeeNo() {
        return ownerEmployeeNo;
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

    public CalendarSuggestionStatus getStatus() {
        return status;
    }

    public Channel getOriginChannel() {
        return originChannel;
    }

    public Channel getOriginDmChannel() {
        return originDmChannel;
    }

    public String getOriginMessageIdsJson() {
        return originMessageIdsJson;
    }

    public String getCreatedByActor() {
        return createdByActor;
    }

    public Long getConfirmedEventId() {
        return confirmedEventId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void markConfirmed(Long eventId) {
        this.status = CalendarSuggestionStatus.CONFIRMED;
        this.confirmedEventId = eventId;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markDismissed() {
        this.status = CalendarSuggestionStatus.DISMISSED;
        this.updatedAt = OffsetDateTime.now();
    }
}
