package com.ech.backend.domain.calendar;

import com.ech.backend.domain.channel.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import java.time.OffsetDateTime;

@Entity
@Table(name = "calendar_events")
public class CalendarEvent {

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

    /**
     * When this row was created by accepting a share: channel (or DM channel) where the share was initiated.
     * Null for self-created events.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_channel_id")
    private Channel originChannel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_from_share_id")
    private CalendarShareRequest sharedFromShare;

    @Column(name = "created_by_actor", nullable = false, length = 30)
    private String createdByActor = "USER";

    @ColumnDefault("true")
    @Column(name = "in_use", nullable = false)
    private boolean inUse = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected CalendarEvent() {
    }

    public CalendarEvent(
            String ownerEmployeeNo,
            String title,
            String description,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            Channel originChannel,
            CalendarShareRequest sharedFromShare,
            String createdByActor
    ) {
        this.ownerEmployeeNo = ownerEmployeeNo;
        this.title = title;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.originChannel = originChannel;
        this.sharedFromShare = sharedFromShare;
        this.createdByActor = createdByActor != null && !createdByActor.isBlank() ? createdByActor.trim() : "USER";
        this.inUse = true;
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

    public Channel getOriginChannel() {
        return originChannel;
    }

    public CalendarShareRequest getSharedFromShare() {
        return sharedFromShare;
    }

    public String getCreatedByActor() {
        return createdByActor;
    }

    public boolean isInUse() {
        return inUse;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void update(String title, String description, OffsetDateTime startsAt, OffsetDateTime endsAt) {
        if (title != null && !title.isBlank()) {
            this.title = title.trim();
        }
        if (description != null) {
            this.description = description.trim().isEmpty() ? null : description.trim();
        }
        if (startsAt != null) {
            this.startsAt = startsAt;
        }
        if (endsAt != null) {
            this.endsAt = endsAt;
        }
        this.updatedAt = OffsetDateTime.now();
    }

    public void setInUse(boolean inUse) {
        this.inUse = inUse;
        this.updatedAt = OffsetDateTime.now();
    }
}
