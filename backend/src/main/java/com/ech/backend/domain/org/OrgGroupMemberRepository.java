package com.ech.backend.domain.org;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrgGroupMemberRepository extends JpaRepository<OrgGroupMember, Long> {

    Optional<OrgGroupMember> findByUser_EmployeeNoAndMemberGroupType(String employeeNo, String memberGroupType);

    @Query("""
            SELECT m
            FROM OrgGroupMember m
            JOIN FETCH m.user u
            JOIN FETCH m.group g
            WHERE TRIM(LOWER(m.memberGroupType)) = TRIM(LOWER(:memberGroupType))
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
            WHERE TRIM(LOWER(m.memberGroupType)) = TRIM(LOWER(:memberGroupType))
              AND u.employeeNo IN :employeeNos
            """)
    List<OrgGroupMember> findMembersByMemberGroupTypeAndEmployeeNos(
            @Param("memberGroupType") String memberGroupType,
            @Param("employeeNos") Collection<String> employeeNos
    );
}
