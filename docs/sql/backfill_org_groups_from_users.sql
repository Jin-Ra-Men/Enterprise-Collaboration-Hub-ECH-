-- org_groups 백필 (users -> COMPANY/DIVISION/TEAM/JOB_LEVEL/DUTY_TITLE)
-- PostgreSQL
--
-- 실행 예:
--   psql -h localhost -U ech_user -d ech -f docs/sql/backfill_org_groups_from_users.sql
--
-- 주의:
--   - org_groups가 이미 존재하면 UPSERT 방식으로 갱신된다.
--   - display_name 계산:
--       - company_name/division_name/team_name이 비어 있으면 기본 라벨 사용
--       - EXTERNAL은 '외부인력', COVIM365는 'M365', GENERAL은 '내부' 라벨 사용
--       - JOB_LEVEL/DUTY_TITLE은 users의 컬럼 값을 그대로 trim하여 lookup 그룹을 만든다.

DO $$
BEGIN

  -- COMPANY 그룹
  WITH active_users AS (
    SELECT
      COALESCE(NULLIF(TRIM(u.company_key), ''), 'GENERAL') AS company_key_n,
      TRIM(COALESCE(NULLIF(u.company_name, ''), '')) AS company_name_n,
      u.status
    FROM users u
    WHERE u.status = 'ACTIVE'
  ),
  company_scoped AS (
    SELECT DISTINCT
      company_key_n,
      CASE
        WHEN company_name_n <> '' THEN company_name_n
        WHEN company_key_n = 'EXTERNAL' THEN '외부인력'
        WHEN company_key_n = 'COVIM365' THEN 'M365'
        ELSE '내부'
      END AS company_display_name
    FROM active_users
  )
  INSERT INTO org_groups (group_type, group_code, display_name, parent_group_id, company_group_id, group_path)
  SELECT
    'COMPANY' AS group_type,
    md5('COMPANY;' || company_key_n || ';' || company_display_name) AS group_code,
    company_display_name AS display_name,
    NULL AS parent_group_id,
    NULL AS company_group_id,
    md5('COMPANY;' || company_key_n || ';' || company_display_name) AS group_path
  FROM company_scoped
  ON CONFLICT (group_type, group_code) DO UPDATE
  SET
    display_name = EXCLUDED.display_name,
    updated_at = NOW();

  -- DIVISION 그룹
  WITH active_users AS (
    SELECT
      COALESCE(NULLIF(TRIM(u.company_key), ''), 'GENERAL') AS company_key_n,
      TRIM(COALESCE(NULLIF(u.company_name, ''), '')) AS company_name_n,
      COALESCE(NULLIF(TRIM(u.division_name), ''), '미지정 본부') AS division_display_name
    FROM users u
    WHERE u.status = 'ACTIVE'
  ),
  company_division_scoped AS (
    SELECT DISTINCT
      company_key_n,
      CASE
        WHEN company_name_n <> '' THEN company_name_n
        WHEN company_key_n = 'EXTERNAL' THEN '외부인력'
        WHEN company_key_n = 'COVIM365' THEN 'M365'
        ELSE '내부'
      END AS company_display_name,
      division_display_name
    FROM active_users
  ),
  company_codes AS (
    SELECT
      company_key_n,
      company_display_name,
      md5('COMPANY;' || company_key_n || ';' || company_display_name) AS company_code,
      division_display_name
    FROM company_division_scoped
  )
  INSERT INTO org_groups (group_type, group_code, display_name, parent_group_id, company_group_id, group_path)
  SELECT
    'DIVISION' AS group_type,
    md5('DIVISION;' || cc.company_code || ';' || cc.division_display_name) AS group_code,
    cc.division_display_name AS display_name,
    c.id AS parent_group_id,
    c.id AS company_group_id,
    cc.company_code || ';' || md5('DIVISION;' || cc.company_code || ';' || cc.division_display_name) AS group_path
  FROM company_codes cc
  JOIN org_groups c
    ON c.group_type = 'COMPANY'
   AND c.group_code = cc.company_code
  ON CONFLICT (group_type, group_code) DO UPDATE
  SET
    display_name = EXCLUDED.display_name,
    parent_group_id = EXCLUDED.parent_group_id,
    company_group_id = EXCLUDED.company_group_id,
    group_path = EXCLUDED.group_path,
    updated_at = NOW();

  -- TEAM 그룹
  WITH active_users AS (
    SELECT
      COALESCE(NULLIF(TRIM(u.company_key), ''), 'GENERAL') AS company_key_n,
      TRIM(COALESCE(NULLIF(u.company_name, ''), '')) AS company_name_n,
      COALESCE(NULLIF(TRIM(u.division_name), ''), '미지정 본부') AS division_display_name,
      COALESCE(NULLIF(TRIM(u.team_name), ''), '미지정 팀') AS team_display_name
    FROM users u
    WHERE u.status = 'ACTIVE'
  ),
  scoped AS (
    SELECT DISTINCT
      company_key_n,
      CASE
        WHEN company_name_n <> '' THEN company_name_n
        WHEN company_key_n = 'EXTERNAL' THEN '외부인력'
        WHEN company_key_n = 'COVIM365' THEN 'M365'
        ELSE '내부'
      END AS company_display_name,
      division_display_name,
      team_display_name
    FROM active_users
  ),
  codes AS (
    SELECT
      company_key_n,
      company_display_name,
      division_display_name,
      team_display_name,
      md5('COMPANY;' || company_key_n || ';' || company_display_name) AS company_code
    FROM scoped
  ),
  division_codes AS (
    SELECT
      c.*,
      md5('DIVISION;' || c.company_code || ';' || c.division_display_name) AS division_code
    FROM codes c
  )
  INSERT INTO org_groups (group_type, group_code, display_name, parent_group_id, company_group_id, group_path)
  SELECT
    'TEAM' AS group_type,
    md5('TEAM;' || dc.division_code || ';' || dc.team_display_name) AS group_code,
    dc.team_display_name AS display_name,
    d.id AS parent_group_id,
    co.id AS company_group_id,
    co.group_code || ';' ||
    dc.division_code || ';' ||
    md5('TEAM;' || dc.division_code || ';' || dc.team_display_name) AS group_path
  FROM division_codes dc
  JOIN org_groups d
    ON d.group_type='DIVISION'
   AND d.group_code=dc.division_code
  JOIN org_groups co
    ON co.group_type='COMPANY'
   AND co.id = d.company_group_id
  ON CONFLICT (group_type, group_code) DO UPDATE
  SET
    display_name = EXCLUDED.display_name,
    parent_group_id = EXCLUDED.parent_group_id,
    company_group_id = EXCLUDED.company_group_id,
    group_path = EXCLUDED.group_path,
    updated_at = NOW();

  -- JOB_LEVEL lookup 그룹
  INSERT INTO org_groups (group_type, group_code, display_name, parent_group_id, company_group_id, group_path)
  SELECT
    'JOB_LEVEL' AS group_type,
    md5('JOB_LEVEL;' || TRIM(u.job_rank)) AS group_code,
    TRIM(u.job_rank) AS display_name,
    NULL AS parent_group_id,
    NULL AS company_group_id,
    NULL AS group_path
  FROM users u
  WHERE u.status='ACTIVE'
    AND u.job_rank IS NOT NULL
    AND TRIM(u.job_rank) <> ''
  GROUP BY TRIM(u.job_rank)
  ON CONFLICT (group_type, group_code) DO UPDATE
  SET
    display_name = EXCLUDED.display_name,
    updated_at = NOW();

  -- DUTY_TITLE lookup 그룹
  INSERT INTO org_groups (group_type, group_code, display_name, parent_group_id, company_group_id, group_path)
  SELECT
    'DUTY_TITLE' AS group_type,
    md5('DUTY_TITLE;' || TRIM(u.duty_title)) AS group_code,
    TRIM(u.duty_title) AS display_name,
    NULL AS parent_group_id,
    NULL AS company_group_id,
    NULL AS group_path
  FROM users u
  WHERE u.status='ACTIVE'
    AND u.duty_title IS NOT NULL
    AND TRIM(u.duty_title) <> ''
  GROUP BY TRIM(u.duty_title)
  ON CONFLICT (group_type, group_code) DO UPDATE
  SET
    display_name = EXCLUDED.display_name,
    updated_at = NOW();

END$$;

