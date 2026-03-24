package com.ech.backend.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60)
    private AuditEventType eventType;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "resource_type", nullable = false, length = 40)
    private String resourceType;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "workspace_key", nullable = false, length = 100)
    private String workspaceKey;

    /** 이벤트 부가 정보 (예: 채널명, 파일명 등). 대화 본문·민감 원문은 저장하지 않음. */
    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected AuditLog() {
    }

    public AuditLog(
            AuditEventType eventType,
            Long actorUserId,
            String resourceType,
            Long resourceId,
            String workspaceKey,
            String detail,
            String requestId
    ) {
        this.eventType = eventType;
        this.actorUserId = actorUserId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.workspaceKey = workspaceKey == null ? "default" : workspaceKey;
        this.detail = detail;
        this.requestId = requestId;
    }

    public Long getId() { return id; }
    public AuditEventType getEventType() { return eventType; }
    public Long getActorUserId() { return actorUserId; }
    public String getResourceType() { return resourceType; }
    public Long getResourceId() { return resourceId; }
    public String getWorkspaceKey() { return workspaceKey; }
    public String getDetail() { return detail; }
    public String getRequestId() { return requestId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
