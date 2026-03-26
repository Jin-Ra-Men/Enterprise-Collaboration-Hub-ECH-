-- org_group_members 백필 (users -> org_groups 매핑) (PostgreSQL)
-- 실행 예:
--   psql -h localhost -U ech_user -d ech -f docs/sql/backfill_org_group_members_from_users.sql
--
-- 전제:
--   - docs/sql/create_org_groups.sql
--   - docs/sql/backfill_org_groups_from_users.sql
--   가 먼저 실행되어 org_groups에 COMPANY/DIVISION/TEAM/JOB_LEVEL/DUTY_TITLE 그룹이 존재해야 한다.
--
-- 매핑 규칙:
--   - TEAM: users.company_key + company_name + division_name + team_name 으로 TEAM group_code를 계산 후 TEAM group에 매핑
--   - JOB_LEVEL: users.job_rank 값으로 JOB_LEVEL 그룹에 매핑
--   - DUTY_TITLE: users.duty_title 값으로 DUTY_TITLE 그룹에 매핑
--
-- UPSERT:
--   - 유니크키 (user_id, member_group_type) 기준으로 갱신

-- TEAM 멤버 매핑
WITH scoped AS (
  SELECT
    u.id AS user_id,
    COALESCE(NULLIF(TRIM(u.company_key), ''), 'GENERAL') AS company_key_n,
    TRIM(COALESCE(NULLIF(u.company_name, ''), '')) AS company_name_n,
    COALESCE(NULLIF(TRIM(u.division_name), ''), '미지정 본부') AS division_display_name,
    COALESCE(NULLIF(TRIM(u.team_name), ''), '미지정 팀') AS team_display_name,
    u.status,
    u.updated_at
  FROM users u
  WHERE u.status = 'ACTIVE'
),
named AS (
  SELECT
    s.*,
    CASE
      WHEN s.company_name_n <> '' THEN s.company_name_n
      WHEN s.company_key_n = 'EXTERNAL' THEN '외부인력'
      WHEN s.company_key_n = 'COVIM365' THEN 'M365'
      ELSE '내부'
    END AS company_display_name
  FROM scoped s
),
codes AS (
  SELECT
    n.user_id,
    md5('COMPANY;' || n.company_key_n || ';' || n.company_display_name) AS company_code,
    n.division_display_name,
    md5('DIVISION;' || md5('COMPANY;' || n.company_key_n || ';' || n.company_display_name) || ';' || n.division_display_name) AS division_code,
    n.team_display_name,
    md5(
      'TEAM;' ||
      md5('DIVISION;' ||
        md5('COMPANY;' || n.company_key_n || ';' || n.company_display_name) ||
        ';' || n.division_display_name
      ) ||
      ';' || n.team_display_name
    ) AS team_code
  FROM named n
)
INSERT INTO org_group_members (user_id, group_id, member_group_type, created_at, updated_at)
SELECT
  c.user_id,
  team_g.id AS group_id,
  'TEAM' AS member_group_type,
  NOW(),
  NOW()
FROM codes c
JOIN org_groups company_g
  ON company_g.group_type='COMPANY'
 AND company_g.group_code=c.company_code
JOIN org_groups division_g
  ON division_g.group_type='DIVISION'
 AND division_g.group_code=c.division_code
JOIN org_groups team_g
  ON team_g.group_type='TEAM'
 AND team_g.group_code=c.team_code
WHERE team_g.is_active = TRUE
ON CONFLICT (user_id, member_group_type) DO UPDATE
SET group_id = EXCLUDED.group_id,
    updated_at = NOW();

-- JOB_LEVEL 멤버 매핑
INSERT INTO org_group_members (user_id, group_id, member_group_type, created_at, updated_at)
SELECT
  u.id AS user_id,
  job_g.id AS group_id,
  'JOB_LEVEL' AS member_group_type,
  NOW(),
  NOW()
FROM users u
JOIN org_groups job_g
  ON job_g.group_type='JOB_LEVEL'
 AND job_g.group_code = md5('JOB_LEVEL;' || TRIM(u.job_rank))
WHERE u.status='ACTIVE'
  AND u.job_rank IS NOT NULL
  AND TRIM(u.job_rank) <> ''
ON CONFLICT (user_id, member_group_type) DO UPDATE
SET group_id = EXCLUDED.group_id,
    updated_at = NOW();

-- DUTY_TITLE 멤버 매핑
INSERT INTO org_group_members (user_id, group_id, member_group_type, created_at, updated_at)
SELECT
  u.id AS user_id,
  duty_g.id AS group_id,
  'DUTY_TITLE' AS member_group_type,
  NOW(),
  NOW()
FROM users u
JOIN org_groups duty_g
  ON duty_g.group_type='DUTY_TITLE'
 AND duty_g.group_code = md5('DUTY_TITLE;' || TRIM(u.duty_title))
WHERE u.status='ACTIVE'
  AND u.duty_title IS NOT NULL
  AND TRIM(u.duty_title) <> ''
ON CONFLICT (user_id, member_group_type) DO UPDATE
SET group_id = EXCLUDED.group_id,
    updated_at = NOW();

