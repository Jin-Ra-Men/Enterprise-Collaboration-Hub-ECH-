package com.ech.backend.domain.retention;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "retention_policies")
public class RetentionPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 자원 유형. RetentionResourceType enum 이름과 동일하게 저장. */
    @Column(name = "resource_type", nullable = false, unique = true, length = 40)
    private String resourceType;

    /** 보존 기간(일). 0 이하이면 영구 보관. */
    @Column(name = "retention_days", nullable = false)
    private int retentionDays = 365;

    /** 자동 아카이빙 활성 여부. */
    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled = false;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected RetentionPolicy() {
    }

    public RetentionPolicy(String resourceType, int retentionDays, boolean isEnabled, String description) {
        this.resourceType = resourceType;
        this.retentionDays = retentionDays;
        this.isEnabled = isEnabled;
        this.description = description;
    }

    public Long getId() { return id; }
    public String getResourceType() { return resourceType; }
    public int getRetentionDays() { return retentionDays; }
    public boolean isEnabled() { return isEnabled; }
    public String getDescription() { return description; }
    public Long getUpdatedBy() { return updatedBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void update(int retentionDays, boolean isEnabled, String description, Long updatedBy) {
        this.retentionDays = retentionDays;
        this.isEnabled = isEnabled;
        this.description = description;
        this.updatedBy = updatedBy;
        this.updatedAt = OffsetDateTime.now();
    }
}
