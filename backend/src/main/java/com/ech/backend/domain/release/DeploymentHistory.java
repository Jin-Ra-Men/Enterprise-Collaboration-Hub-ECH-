package com.ech.backend.domain.release;

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
@Table(name = "deployment_history")
public class DeploymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "release_id", nullable = false)
    private ReleaseVersion release;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeploymentAction action;

    @Column(name = "from_version", length = 50)
    private String fromVersion;

    @Column(name = "to_version", nullable = false, length = 50)
    private String toVersion;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected DeploymentHistory() {
    }

    public DeploymentHistory(ReleaseVersion release, DeploymentAction action,
                              String fromVersion, String toVersion,
                              Long actorUserId, String note) {
        this.release = release;
        this.action = action;
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.actorUserId = actorUserId;
        this.note = note;
    }

    public Long getId() { return id; }
    public ReleaseVersion getRelease() { return release; }
    public DeploymentAction getAction() { return action; }
    public String getFromVersion() { return fromVersion; }
    public String getToVersion() { return toVersion; }
    public Long getActorUserId() { return actorUserId; }
    public String getNote() { return note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
