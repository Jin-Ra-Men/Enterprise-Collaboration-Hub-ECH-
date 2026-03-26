-- users 테이블에 조직도(회사→본부→팀)용 컬럼 추가 (PostgreSQL)
-- 기존 DB가 초안 스키마보다 오래된 경우 1회 실행합니다.
-- 이후 `docs/sql/seed_test_users.sql`(UPSERT) 또는 `backfill_users_org_hierarchy.sql`로 값을 채웁니다.
--
-- 실행 예:
--   psql -h localhost -U ech_user -d ech -f docs/sql/migrate_users_add_org_columns.sql

ALTER TABLE users ADD COLUMN IF NOT EXISTS company_name VARCHAR(120);
ALTER TABLE users ADD COLUMN IF NOT EXISTS division_name VARCHAR(120);
ALTER TABLE users ADD COLUMN IF NOT EXISTS team_name VARCHAR(120);
