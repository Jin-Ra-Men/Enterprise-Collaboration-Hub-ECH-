-- users.company_name / division_name / team_name 백필 (PostgreSQL)
-- `migrate_users_add_org_columns.sql` 실행 후, INSERT 없이 기존 행만 갱신할 때 사용합니다.
-- 내용은 `docs/sql/seed_test_users.sql`의 사번별 조직과 동일합니다.
--
-- 실행 예:
--   psql -h localhost -U ech_user -d ech -f docs/sql/backfill_users_org_hierarchy.sql
--
-- 참고: 로컬 개발 DB는 `seed_test_users.sql` 한 번으로도 동일 값이 UPSERT 됩니다.

UPDATE users AS u
SET
    company_name = v.company_name,
    division_name = v.division_name,
    team_name = v.team_name,
    updated_at = NOW()
FROM (
    VALUES
        ('ECH-ADM-001', 'ECH 주식회사', '운영본부', 'IT운영팀'),
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
        ('ECH-INA-001', 'ECH 주식회사', '경영지원본부', '인사총무팀')
) AS v(employee_no, company_name, division_name, team_name)
WHERE u.employee_no = v.employee_no;

-- 외부·조직 미연동 계정(시드 기준 null 유지)
UPDATE users
SET
    company_name = NULL,
    division_name = NULL,
    team_name = NULL,
    updated_at = NOW()
WHERE employee_no = 'ECH-EXT-001';

-- 시드에 없는 사번: 세 컬럼이 모두 비어 있을 때만 department 문자열로 보조 채움 (외부 계정 제외)
UPDATE users
SET
    company_name = 'ECH 주식회사',
    division_name = COALESCE(NULLIF(TRIM(department), ''), '미지정 본부'),
    team_name = COALESCE(NULLIF(TRIM(department), ''), '미지정 팀'),
    updated_at = NOW()
WHERE company_name IS NULL
  AND division_name IS NULL
  AND team_name IS NULL
  AND employee_no <> 'ECH-EXT-001';
