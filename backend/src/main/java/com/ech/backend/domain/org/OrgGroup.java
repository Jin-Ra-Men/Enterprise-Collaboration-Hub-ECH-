package com.ech.backend.domain.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    @Column(name = "group_code", nullable = false, length = 32, unique = true)
    private String groupCode;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;
    
    /**
     * 상위 조직을 식별하는 부모 groupCode.
     * - COMPANY는 null
     * - DIVISION은 COMPANY의 groupCode
     * - TEAM은 DIVISION의 groupCode
     */
    @Column(name = "member_of_group_code", nullable = true, length = 32)
    private String memberOfGroupCode;

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
            String memberOfGroupCode,
            String groupPath
    ) {
        this.groupType = groupType;
        this.groupCode = groupCode;
        this.displayName = displayName;
        this.memberOfGroupCode = memberOfGroupCode;
        this.groupPath = groupPath;
        this.sortOrder = 0;
        this.isActive = true;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setMemberOfGroupCode(String memberOfGroupCode) {
        this.memberOfGroupCode = memberOfGroupCode;
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

    public String getMemberOfGroupCode() {
        return memberOfGroupCode;
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

