-- org_groups 백필 (users.company_code -> COMPANY/DIVISION/TEAM/JOB_LEVEL/DUTY_TITLE)
-- PostgreSQL
--
-- 실행 예:
--   psql -h localhost -U ech_user -d ech -f docs/sql/backfill_org_groups_from_users.sql
--
-- group_code 규칙 (ASCII 전용, 백엔드 OrgGroupCodes 와 동일):
--   COMPANY: COMP_{tenantSlug}_{FP8}
--   DIVISION: DIV_{FP8}_{FP8} (회사/본부 지문 앞 8자)
--   TEAM: TEAM_{FP8}_{FP8}
--   JOB_LEVEL: JOB_{FP12}
--   DUTY_TITLE: DUT_{FP12}
--   FP* 는 동일 시드 문자열에 대한 md5(hex) 접두부 (대문자)
--
DO $$
BEGIN

  /* COMPANY */
  INSERT INTO org_groups (
    group_type, group_code, display_name, member_of_group_code, group_path
  )
  SELECT
    'COMPANY' AS group_type,
    'COMP_' || COALESCE(
      NULLIF(
        upper(left(
          regexp_replace(upper(cc_n), '[^A-Z0-9]', '', 'g'),
          12
        )),
        ''
      ),
      'GEN'
    ) || '_' || upper(substring(md5('COMPANY;' || cc_n || ';' || cd_n) from 1 for 8)) AS group_code,
    cd_n AS display_name,
    NULL AS member_of_group_code,
    'COMP_' || COALESCE(
      NULLIF(
        upper(left(
          regexp_replace(upper(cc_n), '[^A-Z0-9]', '', 'g'),
          12
        )),
        ''
      ),
      'GEN'
    ) || '_' || upper(substring(md5('COMPANY;' || cc_n || ';' || cd_n) from 1 for 8)) AS group_path
  FROM (
    SELECT DISTINCT
      COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL') AS cc_n,
      CASE
        WHEN COALESCE(TRIM(u.company_name), '') <> '' THEN TRIM(u.company_name)
        WHEN COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL') = 'EXTERNAL' THEN '외부인력'
        WHEN COALESCE(NULLIF(TRIM(u.company_code), ''), 'GENERAL') = 'COVIM365' THEN 'M365'
        ELSE '내부'
      END AS cd_n
    FROM users u
    WHERE u.status = 'ACTIVE'
  ) x
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
    'DIV_' || upper(substring(c.company_fp from 1 for 8)) || '_' || upper(substring(c.division_fp from 1 for 8)) AS group_code,
    c.division_display_name AS display_name,
    c.company_pretty AS member_of_group_code,
    c.company_pretty || ';' || c.division_pretty AS group_path
  FROM (
    SELECT
      d.company_code_n,
      d.company_display_name,
      d.division_display_name,
      md5('COMPANY;' || d.company_code_n || ';' || d.company_display_name) AS company_fp,
      md5(
        'DIVISION;' ||
        md5('COMPANY;' || d.company_code_n || ';' || d.company_display_name) || ';' ||
        d.division_display_name
      ) AS division_fp,
      'COMP_' || COALESCE(
        NULLIF(
          upper(left(regexp_replace(upper(d.company_code_n), '[^A-Z0-9]', '', 'g'), 12)),
          ''
        ),
        'GEN'
      ) || '_' || upper(substring(md5('COMPANY;' || d.company_code_n || ';' || d.company_display_name) from 1 for 8)) AS company_pretty,
      'DIV_' || upper(substring(md5('COMPANY;' || d.company_code_n || ';' || d.company_display_name) from 1 for 8)) || '_' || upper(substring(
        md5(
          'DIVISION;' ||
          md5('COMPANY;' || d.company_code_n || ';' || d.company_display_name) || ';' ||
           d.division_display_name
        ) from 1 for 8
      )) AS division_pretty
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
    ) d
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
    'TEAM_' || upper(substring(t.division_fp from 1 for 8)) || '_' || upper(substring(t.team_fp from 1 for 8)) AS group_code,
    t.team_display_name AS display_name,
    t.division_pretty AS member_of_group_code,
    t.company_pretty || ';' || t.division_pretty || ';' || t.team_pretty AS group_path
  FROM (
    SELECT
      s.company_code_n,
      s.company_display_name,
      s.division_display_name,
      s.team_display_name,
      md5('COMPANY;' || s.company_code_n || ';' || s.company_display_name) AS company_fp,
      md5(
        'DIVISION;' ||
        md5('COMPANY;' || s.company_code_n || ';' || s.company_display_name) || ';' ||
        s.division_display_name
      ) AS division_fp,
      md5(
        'TEAM;' ||
        md5(
          'DIVISION;' ||
          md5('COMPANY;' || s.company_code_n || ';' || s.company_display_name) || ';' ||
          s.division_display_name
        ) || ';' || s.team_display_name
      ) AS team_fp,
      'COMP_' || COALESCE(
        NULLIF(
          upper(left(regexp_replace(upper(s.company_code_n), '[^A-Z0-9]', '', 'g'), 12)),
          ''
        ),
        'GEN'
      ) || '_' || upper(substring(md5('COMPANY;' || s.company_code_n || ';' || s.company_display_name) from 1 for 8)) AS company_pretty,
      'DIV_' || upper(substring(md5('COMPANY;' || s.company_code_n || ';' || s.company_display_name) from 1 for 8)) || '_' || upper(substring(
        md5(
          'DIVISION;' ||
          md5('COMPANY;' || s.company_code_n || ';' || s.company_display_name) || ';' ||
          s.division_display_name
        ) from 1 for 8
      )) AS division_pretty,
      'TEAM_' || upper(substring(
        md5(
          'DIVISION;' ||
          md5('COMPANY;' || s.company_code_n || ';' || s.company_display_name) || ';' ||
          s.division_display_name
        ) from 1 for 8
      )) || '_' || upper(substring(
        md5(
          'TEAM;' ||
          md5(
            'DIVISION;' ||
            md5('COMPANY;' || s.company_code_n || ';' || s.company_display_name) || ';' ||
            s.division_display_name
          ) || ';' || s.team_display_name
        ) from 1 for 8
      )) AS team_pretty
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
    ) s
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
    'JOB_' || upper(substring(md5('JOB_LEVEL;' || TRIM(u.job_rank)) from 1 for 12)) AS group_code,
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
    'DUT_' || upper(substring(md5('DUTY_TITLE;' || TRIM(u.duty_title)) from 1 for 12)) AS group_code,
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
