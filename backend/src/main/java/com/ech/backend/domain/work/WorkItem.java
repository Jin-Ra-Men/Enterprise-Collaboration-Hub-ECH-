package com.ech.backend.domain.work;

import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.message.Message;
import com.ech.backend.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "work_items")
public class WorkItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 50)
    private String status = "OPEN";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_message_id")
    private Message sourceMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_channel_id", nullable = false)
    private Channel sourceChannel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "employee_no", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected WorkItem() {
    }

    public WorkItem(
            String title,
            String description,
            String status,
            Message sourceMessage,
            Channel sourceChannel,
            User createdBy
    ) {
        this.title = title;
        this.description = description;
        this.status = status != null && !status.isBlank() ? status : "OPEN";
        this.sourceMessage = sourceMessage;
        this.sourceChannel = sourceChannel;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public Message getSourceMessage() {
        return sourceMessage;
    }

    public Channel getSourceChannel() {
        return sourceChannel;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void update(String title, String description, String status) {
        if (title != null && !title.isBlank()) {
            this.title = title.trim();
        }
        if (description != null) {
            this.description = description.trim().isEmpty() ? null : description.trim();
        }
        if (status != null && !status.isBlank()) {
            this.status = status.trim();
        }
        this.updatedAt = OffsetDateTime.now();
    }
}
