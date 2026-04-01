package com.ech.backend.domain.channel;

import com.ech.backend.domain.user.User;
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
import java.util.Objects;

@Entity
@Table(name = "channels")
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_key", nullable = false, length = 100)
    private String workspaceKey;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 20)
    private ChannelType channelType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "employee_no", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected Channel() {
    }

    public Channel(String workspaceKey, String name, String description, ChannelType channelType, User createdBy) {
        this.workspaceKey = workspaceKey;
        this.name = name;
        this.description = description;
        this.channelType = channelType;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public String getWorkspaceKey() {
        return workspaceKey;
    }

    public String getName() {
        return name;
    }

    public void updateName(String name) {
        this.name = name;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getDescription() {
        return description;
    }

    public ChannelType getChannelType() {
        return channelType;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void updateDescription(String description) {
        this.description = description;
        this.updatedAt = OffsetDateTime.now();
    }

    public void transferManager(User nextManager) {
        this.createdBy = Objects.requireNonNull(nextManager, "nextManager");
        this.updatedAt = OffsetDateTime.now();
    }
}
