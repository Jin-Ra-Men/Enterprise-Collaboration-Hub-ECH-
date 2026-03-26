package com.ech.backend.domain.org;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrgGroupMemberRepository extends JpaRepository<OrgGroupMember, Long> {

    java.util.Optional<OrgGroupMember> findByUser_IdAndMemberGroupType(Long userId, String memberGroupType);

    @Query("""
            SELECT m
            FROM OrgGroupMember m
            JOIN FETCH m.user u
            JOIN FETCH m.group g
            WHERE m.memberGroupType = :memberGroupType
              AND g.groupCode IN :groupCodes
            """)
    List<OrgGroupMember> findMembersByMemberGroupTypeAndGroupCodes(
            @Param("memberGroupType") String memberGroupType,
            @Param("groupCodes") Collection<String> groupCodes
    );

    @Query("""
            SELECT m
            FROM OrgGroupMember m
            JOIN FETCH m.user u
            JOIN FETCH m.group g
            WHERE m.memberGroupType = :memberGroupType
              AND u.id IN :userIds
            """)
    List<OrgGroupMember> findMembersByMemberGroupTypeAndUserIds(
            @Param("memberGroupType") String memberGroupType,
            @Param("userIds") Collection<Long> userIds
    );
}

