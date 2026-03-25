package com.ech.backend.domain.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    @Query("""
            SELECT u FROM User u
            WHERE (:department IS NULL OR u.department = :department)
              AND (
                :keyword IS NULL
                OR LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.employeeNo) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(u.department, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR (:idMatch IS NOT NULL AND u.id = :idMatch)
              )
            ORDER BY u.name ASC
            """)
    List<User> searchUsers(
            @Param("keyword") String keyword,
            @Param("department") String department,
            @Param("idMatch") Long idMatch
    );

    @Query("""
            SELECT u FROM User u
            WHERE u.status = 'ACTIVE'
            ORDER BY COALESCE(u.companyName, '') ASC,
                     COALESCE(u.divisionName, '') ASC,
                     COALESCE(u.teamName, '') ASC,
                     COALESCE(u.department, '') ASC,
                     u.name ASC
            """)
    List<User> findActiveUsersForOrganization();

    Optional<User> findByEmployeeNo(String employeeNo);

    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.passwordHash IS NULL")
    List<User> findUsersWithoutPassword();

    @Modifying
    @Query(value = """
            INSERT INTO users (employee_no, email, name, department, company_name, division_name, team_name,
                job_rank, duty_title, role, status, created_at, updated_at)
            VALUES (:employeeNo, :email, :name, :department, :companyName, :divisionName, :teamName,
                :jobRank, :dutyTitle, :role, :status, NOW(), NOW())
            ON CONFLICT (employee_no) DO UPDATE SET
                email = EXCLUDED.email,
                name = EXCLUDED.name,
                department = EXCLUDED.department,
                company_name = EXCLUDED.company_name,
                division_name = EXCLUDED.division_name,
                team_name = EXCLUDED.team_name,
                job_rank = EXCLUDED.job_rank,
                duty_title = EXCLUDED.duty_title,
                role = EXCLUDED.role,
                status = EXCLUDED.status,
                updated_at = NOW()
            """, nativeQuery = true)
    void upsertByEmployeeNo(
            @Param("employeeNo") String employeeNo,
            @Param("email") String email,
            @Param("name") String name,
            @Param("department") String department,
            @Param("companyName") String companyName,
            @Param("divisionName") String divisionName,
            @Param("teamName") String teamName,
            @Param("jobRank") String jobRank,
            @Param("dutyTitle") String dutyTitle,
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
}
