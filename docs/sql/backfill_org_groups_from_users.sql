-- org_groups 백필 (users.company_code -> COMPANY/DIVISION/TEAM/JOB_LEVEL/DUTY_TITLE)
-- PostgreSQL
--
-- 실행 예:
--   psql -h localhost -U ech_user -d ech -f docs/sql/backfill_org_groups_from_users.sql
--
-- 기대:
--   - docs/sql/create_org_groups.sql 실행 후 사용
--   - org_groups.group_code 유니크 제약을 사용하므로 ON CONFLICT (group_code) 기반으로 갱신
--   - 계층:
--       COMPANY.member_of_group_code = NULL
--       DIVISION.member_of_group_code = COMPANY.group_code
--       TEAM.member_of_group_code = DIVISION.group_code
--
DO $$
BEGIN

  /* COMPANY */
  INSERT INTO org_groups (
    group_type, group_code, display_name, member_of_group_code, group_path
  )
  SELECT
    'COMPANY' AS group_type,
    md5('COMPANY;' || COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL') || ';' ||
        CASE
          WHEN COALESCE(TRIM(u.company_name), '') <> '' THEN TRIM(u.company_name)
          WHEN COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL') = 'EXTERNAL' THEN '외부인력'
          WHEN COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL') = 'COVIM365' THEN 'M365'
          ELSE '내부'
        END
    ) AS group_code,
    CASE
      WHEN COALESCE(TRIM(u.company_name), '') <> '' THEN TRIM(u.company_name)
      WHEN COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL') = 'EXTERNAL' THEN '외부인력'
      WHEN COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL') = 'COVIM365' THEN 'M365'
      ELSE '내부'
    END AS display_name,
    NULL AS member_of_group_code,
    md5('COMPANY;' || COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL') || ';' ||
        CASE
          WHEN COALESCE(TRIM(u.company_name), '') <> '' THEN TRIM(u.company_name)
          WHEN COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL') = 'EXTERNAL' THEN '외부인력'
          WHEN COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL') = 'COVIM365' THEN 'M365'
          ELSE '내부'
        END
    ) AS group_path
  FROM users u
  WHERE u.status = 'ACTIVE'
  GROUP BY
    COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL'),
    CASE
      WHEN COALESCE(TRIM(u.company_name), '') <> '' THEN TRIM(u.company_name)
      WHEN COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL') = 'EXTERNAL' THEN '외부인력'
      WHEN COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL') = 'COVIM365' THEN 'M365'
      ELSE '내부'
    END
  ON CONFLICT (group_code) DO UPDATE
  SET
    display_name = EXCLUDED.display_name,
    member_of_group_code = EXCLUDED.member_of_group_code,
    group_path = EXCLUDED.group_path,
    updated_at = NOW();

  /* DIVISION */
  INSERT INTO org_groups (
    group_type, group_code, display_name, member_of_group_code, group_path
  )
  SELECT
    'DIVISION' AS group_type,
    md5(
      'DIVISION;' ||
      md5('COMPANY;' || c.company_code_n || ';' || c.company_display_name) || ';' ||
      c.division_display_name
    ) AS group_code,
    c.division_display_name AS display_name,
    md5('COMPANY;' || c.company_code_n || ';' || c.company_display_name) AS member_of_group_code,
    md5('COMPANY;' || c.company_code_n || ';' || c.company_display_name) || ';' ||
    md5(
      'DIVISION;' ||
      md5('COMPANY;' || c.company_code_n || ';' || c.company_display_name) || ';' ||
      c.division_display_name
    ) AS group_path
  FROM (
    SELECT DISTINCT
      COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL') AS company_code_n,
      CASE
        WHEN COALESCE(TRIM(u.company_name), '') <> '' THEN TRIM(u.company_name)
        WHEN COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL') = 'EXTERNAL' THEN '외부인력'
        WHEN COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL') = 'COVIM365' THEN 'M365'
        ELSE '내부'
      END AS company_display_name,
      COALESCE(NULLIF(TRIM(u.division_name), ''), '미지정 본부') AS division_display_name
    FROM users u
    WHERE u.status = 'ACTIVE'
  ) c
  ON CONFLICT (group_code) DO UPDATE
  SET
    display_name = EXCLUDED.display_name,
    member_of_group_code = EXCLUDED.member_of_group_code,
    group_path = EXCLUDED.group_path,
    updated_at = NOW();

  /* TEAM */
  INSERT INTO org_groups (
    group_type, group_code, display_name, member_of_group_code, group_path
  )
  SELECT
    'TEAM' AS group_type,
    md5(
      'TEAM;' ||
      md5(
        'DIVISION;' ||
        md5('COMPANY;' || t.company_code_n || ';' || t.company_display_name) || ';' ||
        t.division_display_name
      ) || ';' ||
      t.team_display_name
    ) AS group_code,
    t.team_display_name AS display_name,
    md5(
      'DIVISION;' ||
      md5('COMPANY;' || t.company_code_n || ';' || t.company_display_name) || ';' ||
      t.division_display_name
    ) AS member_of_group_code,
    md5('COMPANY;' || t.company_code_n || ';' || t.company_display_name) || ';' ||
    md5(
      'DIVISION;' ||
      md5('COMPANY;' || t.company_code_n || ';' || t.company_display_name) || ';' ||
      t.division_display_name
    ) || ';' ||
    md5(
      'TEAM;' ||
      md5(
        'DIVISION;' ||
        md5('COMPANY;' || t.company_code_n || ';' || t.company_display_name) || ';' ||
        t.division_display_name
      ) || ';' ||
      t.team_display_name
    ) AS group_path
  FROM (
    SELECT DISTINCT
      COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL') AS company_code_n,
      CASE
        WHEN COALESCE(TRIM(u.company_name), '') <> '' THEN TRIM(u.company_name)
        WHEN COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL') = 'EXTERNAL' THEN '외부인력'
        WHEN COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL') = 'COVIM365' THEN 'M365'
        ELSE '내부'
      END AS company_display_name,
      COALESCE(NULLIF(TRIM(u.division_name), ''), '미지정 본부') AS division_display_name,
      COALESCE(NULLIF(TRIM(u.team_name), ''), '미지정 팀') AS team_display_name
    FROM users u
    WHERE u.status = 'ACTIVE'
  ) t
  ON CONFLICT (group_code) DO UPDATE
  SET
    display_name = EXCLUDED.display_name,
    member_of_group_code = EXCLUDED.member_of_group_code,
    group_path = EXCLUDED.group_path,
    updated_at = NOW();

  /* JOB_LEVEL */
  INSERT INTO org_groups (
    group_type, group_code, display_name, member_of_group_code, group_path
  )
  SELECT
    'JOB_LEVEL' AS group_type,
    md5('JOB_LEVEL;' || TRIM(u.job_rank)) AS group_code,
    TRIM(u.job_rank) AS display_name,
    NULL AS member_of_group_code,
    NULL AS group_path
  FROM users u
  WHERE u.status = 'ACTIVE'
    AND u.job_rank IS NOT NULL
    AND TRIM(u.job_rank) <> ''
  GROUP BY TRIM(u.job_rank)
  ON CONFLICT (group_code) DO UPDATE
  SET
    display_name = EXCLUDED.display_name,
    member_of_group_code = EXCLUDED.member_of_group_code,
    group_path = EXCLUDED.group_path,
    updated_at = NOW();

  /* DUTY_TITLE */
  INSERT INTO org_groups (
    group_type, group_code, display_name, member_of_group_code, group_path
  )
  SELECT
    'DUTY_TITLE' AS group_type,
    md5('DUTY_TITLE;' || TRIM(u.duty_title)) AS group_code,
    TRIM(u.duty_title) AS display_name,
    NULL AS member_of_group_code,
    NULL AS group_path
  FROM users u
  WHERE u.status = 'ACTIVE'
    AND u.duty_title IS NOT NULL
    AND TRIM(u.duty_title) <> ''
  GROUP BY TRIM(u.duty_title)
  ON CONFLICT (group_code) DO UPDATE
  SET
    display_name = EXCLUDED.display_name,
    member_of_group_code = EXCLUDED.member_of_group_code,
    group_path = EXCLUDED.group_path,
    updated_at = NOW();

END$$;

