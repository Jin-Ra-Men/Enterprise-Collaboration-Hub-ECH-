-- **[2025-03] 레거시]** `users.company_name` 등 컬럼이 제거된 스키마에서는 사용하지 않습니다. OrgSync 사용.
--
-- users.company_name / division_name / team_name 을 `seed_test_users.sql` 과 동일 값으로 갱신 (PostgreSQL)
-- 다른 컬럼(이메일, department, job_rank 등)은 변경하지 않습니다.
-- 컬럼이 없으면 먼저 `migrate_users_add_org_columns.sql` 을 실행하세요.
--
-- 실행 예:
--   psql -h localhost -U ech_user -d ech -f docs/sql/update_users_org_from_seed.sql

UPDATE users AS u
SET
    company_name = v.company_name,
    division_name = v.division_name,
    team_name = v.team_name,
    updated_at = NOW()
FROM (
    VALUES
        -- seed_test_users.sql 과 동일 순서·값
        ('ECH-ADM-001', 'ECH 주식회사'::VARCHAR(120), '운영본부'::VARCHAR(120), 'IT운영팀'::VARCHAR(120)),
        ('ECH-TST-001', 'ECH 주식회사', '품질본부', '테스트팀'),
        ('ECH-TST-002', 'ECH 주식회사', '품질본부', '테스트팀'),
        ('ECH-TST-003', 'ECH 주식회사', '품질본부', '테스트팀'),
        ('ECH-DEV-001', 'ECH 주식회사', '기술본부', '개발1팀'),
        ('ECH-DEV-002', 'ECH 주식회사', '기술본부', '개발1팀'),
        ('ECH-DEV-003', 'ECH 주식회사', '기술본부', '개발1팀'),
        ('ECH-DEV-004', 'ECH 주식회사', '기술본부', '개발2팀'),
        ('ECH-DEV-005', 'ECH 주식회사', '기술본부', '개발2팀'),
        ('ECH-HR-001', 'ECH 주식회사', '경영지원본부', '인사총무팀'),
        ('ECH-SAL-001', 'ECH 주식회사', '영업본부', '영업1팀'),
        ('ECH-PLN-001', 'ECH 주식회사', '기획본부', '기획전략팀'),
        ('ECH-SEC-001', 'ECH 주식회사', '감사본부', '보안감사팀'),
        ('ECH-EXT-001', NULL::VARCHAR(120), NULL::VARCHAR(120), NULL::VARCHAR(120)),
        ('ECH-INA-001', 'ECH 주식회사', '경영지원본부', '인사총무팀')
) AS v(employee_no, company_name, division_name, team_name)
WHERE u.employee_no = v.employee_no;
