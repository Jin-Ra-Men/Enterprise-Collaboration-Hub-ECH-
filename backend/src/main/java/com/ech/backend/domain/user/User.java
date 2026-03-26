package com.ech.backend.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_no", nullable = false, unique = true, length = 50)
    private String employeeNo;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String department;

    /** 최상위 회사명(조직도 루트). */
    @Column(name = "company_name", length = 120)
    private String companyName;

    /** 본부 단위. */
    @Column(name = "division_name", length = 120)
    private String divisionName;

    /** 팀 단위. */
    @Column(name = "team_name", length = 120)
    private String teamName;

    /**
     * 그룹사/테넌트 구분 키(조직도 상단 필터). 예: ORGROOT(전체), GENERAL, EXTERNAL, COVIM365.
     * null 은 레거시 호환으로 조직도에서 GENERAL 과 동일하게 취급할 수 있다.
     */
    @Column(name = "company_key", length = 40)
    private String companyKey;

    /** 조직 직위(예: 대리, 과장). UI는 값이 없으면 `-` 등으로 표시. */
    @Column(name = "job_rank", length = 100)
    private String jobRank;

    /** 조직 직책(예: 팀장, PM). 없으면 API는 null, UI에서는 행 자체를 숨김. */
    @Column(name = "duty_title", length = 100)
    private String dutyTitle;

    @Column(nullable = false, length = 30)
    private String role = "MEMBER";

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    /** BCrypt 해시 저장. 그룹웨어 연동 시 외부 인증을 사용하므로 NULL 허용. */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    /** JPA 프록시 생성용 기본 생성자 (직접 호출 금지) */
    protected User() {}

    /**
     * 테스트 및 신규 사용자 생성용 생성자.
     */
    public User(String employeeNo, String email, String name, String department, String role) {
        this.employeeNo = employeeNo;
        this.email = email;
        this.name = name;
        this.department = department;
        this.role = role != null ? role : "MEMBER";
    }

    public Long getId() {
        return id;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getDepartment() {
        return department;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getDivisionName() {
        return divisionName;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getCompanyKey() {
        return companyKey;
    }

    public void setCompanyKey(String companyKey) {
        this.companyKey = companyKey;
    }

    public String getJobRank() {
        return jobRank;
    }

    public String getDutyTitle() {
        return dutyTitle;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
