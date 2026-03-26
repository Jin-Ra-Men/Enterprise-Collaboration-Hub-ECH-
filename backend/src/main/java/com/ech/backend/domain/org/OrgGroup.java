package com.ech.backend.domain.org;

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
@Table(name = "org_groups")
public class OrgGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_type", nullable = false, length = 30)
    private String groupType;

    @Column(name = "group_code", nullable = false, length = 32)
    private String groupCode;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_group_id")
    private OrgGroup parentGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_group_id")
    private OrgGroup companyGroup;

    @Column(name = "group_path", length = 500)
    private String groupPath;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected OrgGroup() {}

    public OrgGroup(
            String groupType,
            String groupCode,
            String displayName,
            OrgGroup parentGroup,
            OrgGroup companyGroup,
            String groupPath
    ) {
        this.groupType = groupType;
        this.groupCode = groupCode;
        this.displayName = displayName;
        this.parentGroup = parentGroup;
        this.companyGroup = companyGroup;
        this.groupPath = groupPath;
        this.sortOrder = 0;
        this.isActive = true;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setParentGroup(OrgGroup parentGroup) {
        this.parentGroup = parentGroup;
    }

    public void setCompanyGroup(OrgGroup companyGroup) {
        this.companyGroup = companyGroup;
    }

    public void setGroupPath(String groupPath) {
        this.groupPath = groupPath;
    }

    public Long getId() {
        return id;
    }

    public String getGroupType() {
        return groupType;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public OrgGroup getParentGroup() {
        return parentGroup;
    }

    public OrgGroup getCompanyGroup() {
        return companyGroup;
    }

    public String getGroupPath() {
        return groupPath;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return isActive;
    }
}

