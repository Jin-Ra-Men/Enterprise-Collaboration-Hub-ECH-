-- org_group_members (유저-조직 매핑) 테이블 생성 (PostgreSQL)
-- 실행 예:
--   psql -h localhost -U ech_user -d ech -f docs/sql/create_org_group_members.sql
--
-- 목적:
--   - 유저를 TEAM / JOB_LEVEL / JOB_POSITION / JOB_TITLE 등 group_type 별로 매핑
--   - 조직도 트리는 TEAM만 사용하며, 직급/직위/직책은 멤버 속성용으로만 사용
--   - users 와의 조인 키는 id 가 아니라 users.employee_no

CREATE TABLE IF NOT EXISTS org_group_members (
    id BIGSERIAL PRIMARY KEY,

    employee_no VARCHAR(50) NOT NULL REFERENCES users(employee_no) ON DELETE CASCADE,
    -- org_groups.group_code (조직 식별자)
    group_code VARCHAR(32) NOT NULL REFERENCES org_groups(group_code) ON DELETE CASCADE,

    -- 유저가 속한 타입(매핑 용도).
    -- 예: 'TEAM', 'JOB_LEVEL', 'JOB_POSITION', 'JOB_TITLE'
    member_group_type VARCHAR(30) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_org_group_members_emp_type UNIQUE (employee_no, member_group_type)
);

CREATE INDEX IF NOT EXISTS idx_org_group_members_employee_no ON org_group_members(employee_no);
CREATE INDEX IF NOT EXISTS idx_org_group_members_group ON org_group_members(group_code);
CREATE INDEX IF NOT EXISTS idx_org_group_members_type ON org_group_members(member_group_type);
