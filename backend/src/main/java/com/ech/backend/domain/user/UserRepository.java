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
              )
            ORDER BY u.name ASC
            """)
    List<User> searchUsers(
            @Param("keyword") String keyword,
            @Param("department") String department
    );

    Optional<User> findByEmployeeNo(String employeeNo);

    @Modifying
    @Query(value = """
            INSERT INTO users (employee_no, email, name, department, role, status, created_at, updated_at)
            VALUES (:employeeNo, :email, :name, :department, :role, :status, NOW(), NOW())
            ON CONFLICT (employee_no) DO UPDATE SET
                email = EXCLUDED.email,
                name = EXCLUDED.name,
                department = EXCLUDED.department,
                role = EXCLUDED.role,
                status = EXCLUDED.status,
                updated_at = NOW()
            """, nativeQuery = true)
    void upsertByEmployeeNo(
            @Param("employeeNo") String employeeNo,
            @Param("email") String email,
            @Param("name") String name,
            @Param("department") String department,
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
