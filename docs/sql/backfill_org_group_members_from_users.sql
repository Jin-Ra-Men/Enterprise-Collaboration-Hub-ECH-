-- org_group_members 백필 (users -> org_groups 매핑) (PostgreSQL)
-- 실행 예:
--   psql -h localhost -U ech_user -d ech -f docs/sql/backfill_org_group_members_from_users.sql
--
-- 전제:
--   - docs/sql/create_org_groups.sql
--   - docs/sql/backfill_org_groups_from_users.sql
--   가 먼저 실행되어 org_groups.group_code가 생성되어 있어야 한다.
--
-- 매핑 규칙:
--   - TEAM: org_group_members.member_group_type='TEAM' 으로 TEAM group_code에 매핑
--   - JOB_LEVEL: member_group_type='JOB_LEVEL' 으로 JOB_LEVEL group_code에 매핑
--   - DUTY_TITLE: member_group_type='DUTY_TITLE' 으로 DUTY_TITLE group_code에 매핑
--
-- UPSERT:
--   - 유니크키 (user_id, member_group_type) 기준으로 group_code 갱신

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
    md5('COMPANY;' || n.company_code_n || ';' || n.company_display_name) AS company_code,
    md5(
      'DIVISION;' ||
      md5('COMPANY;' || n.company_code_n || ';' || n.company_display_name) || ';' ||
      n.division_display_name
    ) AS division_code,
    md5(
      'TEAM;' ||
      md5(
        'DIVISION;' ||
        md5('COMPANY;' || n.company_code_n || ';' || n.company_display_name) || ';' ||
        n.division_display_name
      ) || ';' || n.team_display_name
    ) AS team_code
  FROM named n
)
INSERT INTO org_group_members (user_id, group_code, member_group_type, created_at, updated_at)
SELECT
  c.user_id,
  c.team_code AS group_code,
  'TEAM' AS member_group_type,
  NOW(),
  NOW()
FROM codes c
ON CONFLICT (user_id, member_group_type) DO UPDATE
SET
  group_code = EXCLUDED.group_code,
  updated_at = NOW();

/* JOB_LEVEL 멤버 매핑 */
INSERT INTO org_group_members (user_id, group_code, member_group_type, created_at, updated_at)
SELECT
  u.id AS user_id,
  md5('JOB_LEVEL;' || TRIM(u.job_rank)) AS group_code,
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
  md5('DUTY_TITLE;' || TRIM(u.duty_title)) AS group_code,
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

