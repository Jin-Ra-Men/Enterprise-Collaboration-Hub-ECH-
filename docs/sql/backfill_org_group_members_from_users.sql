-- org_group_members 백필 (users -> org_groups 매핑) (PostgreSQL)
--
-- **[2025-03] 사용 중단 안내**: `users.user_id` 기반 및 `users` 조직 컬럼 기반 백필은
-- 스키마 변경(`org_group_members.employee_no`, users 슬림화) 이후 적용하지 않습니다.
-- `employee_no` FK 및 OrgSync 흐름을 사용하세요.
--
-- 실행 예:
--   psql -h localhost -U ech_user -d ech -f docs/sql/backfill_org_group_members_from_users.sql
--
-- 전제:
--   - docs/sql/create_org_groups.sql
--   - docs/sql/backfill_org_groups_from_users.sql
--   가 먼저 실행되어 org_groups.group_code가 생성되어 있어야 한다.
--
-- group_code 는 OrgGroupCodes 규칙(ASCII)과 동일한 pretty 코드를 사용한다.

/* TEAM 멤버 매핑 */
WITH scoped AS (
  SELECT
    u.id AS user_id,
    COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL') AS company_code_n,
    TRIM(COALESCE(NULLIF(u.company_name, ''), '')) AS company_name_n,
    COALESCE(NULLIF(TRIM(u.division_name), ''), '미지정 본부') AS division_display_name,
    COALESCE(NULLIF(TRIM(u.team_name), ''), '미지정 팀') AS team_display_name
  FROM users u
  WHERE u.status = 'ACTIVE'
),
named AS (
  SELECT
    s.*,
    CASE
      WHEN s.company_name_n <> '' THEN s.company_name_n
      WHEN s.company_code_n = 'EXTERNAL' THEN '외부인력'
      WHEN s.company_code_n = 'COVIM365' THEN 'M365'
      ELSE '내부'
    END AS company_display_name
  FROM scoped s
),
codes AS (
  SELECT
    n.user_id,
    md5('COMPANY;' || n.company_code_n || ';' || n.company_display_name) AS company_fp,
    md5(
      'DIVISION;' ||
      md5('COMPANY;' || n.company_code_n || ';' || n.company_display_name) || ';' ||
      n.division_display_name
    ) AS division_fp,
    md5(
      'TEAM;' ||
      md5(
        'DIVISION;' ||
        md5('COMPANY;' || n.company_code_n || ';' || n.company_display_name) || ';' ||
        n.division_display_name
      ) || ';' || n.team_display_name
    ) AS team_fp
  FROM named n
),
pretty AS (
  SELECT
    c.user_id,
    'TEAM_' || upper(substring(c.division_fp from 1 for 8)) || '_' || upper(substring(c.team_fp from 1 for 8)) AS team_code
  FROM codes c
)
INSERT INTO org_group_members (user_id, group_code, member_group_type, created_at, updated_at)
SELECT
  p.user_id,
  p.team_code AS group_code,
  'TEAM' AS member_group_type,
  NOW(),
  NOW()
FROM pretty p
ON CONFLICT (user_id, member_group_type) DO UPDATE
SET
  group_code = EXCLUDED.group_code,
  updated_at = NOW();

/* JOB_LEVEL 멤버 매핑 */
INSERT INTO org_group_members (user_id, group_code, member_group_type, created_at, updated_at)
SELECT
  u.id AS user_id,
  'JOB_' || upper(substring(md5('JOB_LEVEL;' || TRIM(u.job_rank)) from 1 for 12)) AS group_code,
  'JOB_LEVEL' AS member_group_type,
  NOW(),
  NOW()
FROM users u
WHERE u.status = 'ACTIVE'
  AND u.job_rank IS NOT NULL
  AND TRIM(u.job_rank) <> ''
ON CONFLICT (user_id, member_group_type) DO UPDATE
SET
  group_code = EXCLUDED.group_code,
  updated_at = NOW();

/* DUTY_TITLE 멤버 매핑 */
INSERT INTO org_group_members (user_id, group_code, member_group_type, created_at, updated_at)
SELECT
  u.id AS user_id,
  'DUT_' || upper(substring(md5('DUTY_TITLE;' || TRIM(u.duty_title)) from 1 for 12)) AS group_code,
  'DUTY_TITLE' AS member_group_type,
  NOW(),
  NOW()
FROM users u
WHERE u.status = 'ACTIVE'
  AND u.duty_title IS NOT NULL
  AND TRIM(u.duty_title) <> ''
ON CONFLICT (user_id, member_group_type) DO UPDATE
SET
  group_code = EXCLUDED.group_code,
  updated_at = NOW();
