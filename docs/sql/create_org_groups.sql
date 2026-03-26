-- org_groups (조직 룩업/계층) 테이블 생성 (PostgreSQL)
-- 실행 예:
--   psql -h localhost -U ech_user -d ech -f docs/sql/create_org_groups.sql
--
-- 목적:
--   - 회사(COMPANY) / 본부(DIVISION) / 부서(TEAM)를 parent_group_id 로 계층화
--   - 잡 레벨(JOB_LEVEL) / 잡 타이틀(DUTY_TITLE)은 lookup 용으로만 보관(트리에는 미표시)

CREATE TABLE IF NOT EXISTS org_groups (
    id BIGSERIAL PRIMARY KEY,

    -- COMPANY, DIVISION, TEAM, JOB_LEVEL, DUTY_TITLE 등
    group_type VARCHAR(30) NOT NULL,

    -- md5 기반 코드(uniqueness의 핵심). VARCHAR(32) (md5 hex length)
    group_code VARCHAR(32) NOT NULL,

    -- 조직도 표시명(요청사항: DisplayName)
    display_name VARCHAR(200) NOT NULL,

    parent_group_id BIGINT NULL REFERENCES org_groups(id) ON DELETE CASCADE,

    -- DIVISION/TEAM이 소속 회사(COMPANY)를 빠르게 찾기 위한 컬럼
    company_group_id BIGINT NULL REFERENCES org_groups(id) ON DELETE CASCADE,

    -- 회사/본부/팀에 대해서만 의미: COMPANYCODE;DIVCODE;TEAMCODE
    group_path VARCHAR(500) NULL,

    sort_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_org_groups_type_code UNIQUE (group_type, group_code)
);

CREATE INDEX IF NOT EXISTS idx_org_groups_type_parent ON org_groups(group_type, parent_group_id);
CREATE INDEX IF NOT EXISTS idx_org_groups_type_company ON org_groups(group_type, company_group_id);
CREATE INDEX IF NOT EXISTS idx_org_groups_group_code ON org_groups(group_code);

