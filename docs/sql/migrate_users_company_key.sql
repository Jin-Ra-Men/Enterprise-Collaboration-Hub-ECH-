-- users.company_code: 조직도 상단 회사 필터(ORGROOT/GENERAL/EXTERNAL/COVIM365 등)
-- PostgreSQL. 기존 DB에 컬럼만 추가할 때 1회 실행합니다.

ALTER TABLE users ADD COLUMN IF NOT EXISTS company_code VARCHAR(40);
