package com.ech.backend.domain.org;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** 관리자용: 다수 사원번호에 대해 모든 그룹 타입 멤버를 한 번에 조회 */
    @Query("""
            SELECT m
            FROM OrgGroupMember m
            JOIN FETCH m.user u
            JOIN FETCH m.group g
            WHERE u.employeeNo IN :employeeNos
            """)
    List<OrgGroupMember> findAllByEmployeeNosWithGroupAndUser(
            @Param("employeeNos") Collection<String> employeeNos
    );

    /** 조직 그룹 삭제 전 연관 멤버 일괄 삭제 */
    @Modifying
    @Query("DELETE FROM OrgGroupMember m WHERE m.group.groupCode = :groupCode")
    void deleteAllByGroupCode(@Param("groupCode") String groupCode);

    /** 그룹코드 변경 시 org_group_members.group_code 일괄 갱신 (네이티브) */
    @Modifying
    @Query(value = "UPDATE org_group_members SET group_code = :newCode WHERE group_code = :oldCode", nativeQuery = true)
    void updateGroupCode(@Param("oldCode") String oldCode, @Param("newCode") String newCode);
}
