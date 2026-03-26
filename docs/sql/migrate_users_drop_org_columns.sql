-- 기존 PostgreSQL DB: users 테이블에서 조직/직무 중복 컬럼 제거
-- (값은 org_groups / org_group_members 로 이전한 뒤 실행)
--
--   psql ... -f docs/sql/migrate_users_drop_org_columns.sql

ALTER TABLE users DROP COLUMN IF EXISTS department;
ALTER TABLE users DROP COLUMN IF EXISTS company_name;
ALTER TABLE users DROP COLUMN IF EXISTS division_name;
ALTER TABLE users DROP COLUMN IF EXISTS team_name;
ALTER TABLE users DROP COLUMN IF EXISTS company_code;
ALTER TABLE users DROP COLUMN IF EXISTS job_rank;
ALTER TABLE users DROP COLUMN IF EXISTS duty_title;
