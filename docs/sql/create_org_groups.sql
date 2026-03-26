-- org_groups (조직 룩업/계층) 테이블 생성 (PostgreSQL)
-- 실행 예:
--   psql -h localhost -U ech_user -d ech -f docs/sql/create_org_groups.sql
--
    -- 목적:
    --   - 회사(COMPANY) / 본부(DIVISION) / 부서(TEAM)를 group_code 기반 memberOf로 계층화
    --   - 잡 레벨(JOB_LEVEL) / 잡 타이틀(DUTY_TITLE)은 lookup 용으로만 보관(트리에는 미표시)

CREATE TABLE IF NOT EXISTS org_groups (
    id BIGSERIAL PRIMARY KEY,

    -- COMPANY, DIVISION, TEAM, JOB_LEVEL, DUTY_TITLE 등
    group_type VARCHAR(30) NOT NULL,

    -- md5 기반 코드(uniqueness의 핵심). VARCHAR(32) (md5 hex length)
    group_code VARCHAR(32) NOT NULL,

    -- 조직도 표시명(요청사항: DisplayName)
    display_name VARCHAR(200) NOT NULL,

    -- 상위 조직을 식별하는 부모 group_code.
    -- COMPANY는 NULL
    -- DIVISION은 COMPANY group_code
    -- TEAM은 DIVISION group_code
    member_of_group_code VARCHAR(32) NULL REFERENCES org_groups(group_code) ON DELETE CASCADE,

    -- 회사/본부/팀에 대해서만 의미: COMPANYCODE;DIVCODE;TEAMCODE
    group_path VARCHAR(500) NULL,

    sort_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_org_groups_group_code UNIQUE (group_code)
);

CREATE INDEX IF NOT EXISTS idx_org_groups_type_member_of ON org_groups(group_type, member_of_group_code);
CREATE INDEX IF NOT EXISTS idx_org_groups_group_code ON org_groups(group_code);

