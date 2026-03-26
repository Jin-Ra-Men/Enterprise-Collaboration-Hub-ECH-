package com.ech.backend.domain.user;

import com.ech.backend.api.user.dto.UserSearchResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    @Query("""
            SELECT new com.ech.backend.api.user.dto.UserSearchResponse(
                u.id,
                u.employeeNo,
                u.name,
                u.email,
                teamGroup.displayName,
                jlGroup.displayName,
                jpGroup.displayName,
                jtGroup.displayName,
                u.role,
                u.status
            )
            FROM User u
            JOIN OrgGroupMember mTeam ON mTeam.user = u AND mTeam.memberGroupType = 'TEAM'
            JOIN mTeam.group teamGroup
            LEFT JOIN OrgGroupMember mJl ON mJl.user = u AND mJl.memberGroupType = 'JOB_LEVEL'
            LEFT JOIN mJl.group jlGroup
            LEFT JOIN OrgGroupMember mJp ON mJp.user = u AND mJp.memberGroupType = 'JOB_POSITION'
            LEFT JOIN mJp.group jpGroup
            LEFT JOIN OrgGroupMember mJt ON mJt.user = u AND mJt.memberGroupType = 'JOB_TITLE'
            LEFT JOIN mJt.group jtGroup
            WHERE (:department IS NULL OR teamGroup.displayName = :department)
              AND (
                :keyword IS NULL
                OR LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.employeeNo) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(teamGroup.displayName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR (:idMatch IS NOT NULL AND u.id = :idMatch)
              )
            ORDER BY u.name ASC
            """)
    List<UserSearchResponse> searchUsers(
            @Param("keyword") String keyword,
            @Param("department") String department,
            @Param("idMatch") Long idMatch
    );

    Optional<User> findByEmployeeNo(String employeeNo);

    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.passwordHash IS NULL")
    List<User> findUsersWithoutPassword();

    @Modifying
    @Query(value = """
            INSERT INTO users (employee_no, email, name, role, status, created_at, updated_at)
            VALUES (:employeeNo, :email, :name, :role, :status, NOW(), NOW())
            ON CONFLICT (employee_no) DO UPDATE SET
                email = EXCLUDED.email,
                name = EXCLUDED.name,
                role = EXCLUDED.role,
                status = EXCLUDED.status,
                updated_at = NOW()
            """, nativeQuery = true)
    void upsertByEmployeeNo(
            @Param("employeeNo") String employeeNo,
            @Param("email") String email,
            @Param("name") String name,
            @Param("role") String role,
            @Param("status") String status
    );

    @Modifying
    @Query("""
            UPDATE User u
            SET u.status = :status
            WHERE u.employeeNo = :employeeNo
            """)
    int updateStatusByEmployeeNo(
            @Param("employeeNo") String employeeNo,
            @Param("status") String status
    );

    @Modifying
    @Query("""
            UPDATE User u
            SET u.themePreference = :themePreference
            WHERE u.id = :userId
            """)
    int updateThemePreferenceById(
            @Param("userId") Long userId,
            @Param("themePreference") String themePreference
    );
}
