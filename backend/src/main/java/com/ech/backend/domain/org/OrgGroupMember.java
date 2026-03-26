package com.ech.backend.domain.org;

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
@Table(name = "org_group_members")
public class OrgGroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_code", referencedColumnName = "group_code", nullable = false)
    private OrgGroup group;

    @Column(name = "member_group_type", nullable = false, length = 30)
    private String memberGroupType;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected OrgGroupMember() {}

    public OrgGroupMember(User user, OrgGroup group, String memberGroupType) {
        this.user = user;
        this.group = group;
        this.memberGroupType = memberGroupType;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public OrgGroup getGroup() {
        return group;
    }

    public String getMemberGroupType() {
        return memberGroupType;
    }

    public void setGroup(OrgGroup group) {
        this.group = group;
    }
}

