-- org_group_members (유저-조직 매핑) 테이블 생성 (PostgreSQL)
-- 실행 예:
--   psql -h localhost -U ech_user -d ech -f docs/sql/create_org_group_members.sql
--
-- 목적:
--   - 유저를 TEAM / JOB_LEVEL / DUTY_TITLE 등 group_type 별로 매핑
--   - 조직도 트리는 TEAM만 사용하며, JOB_LEVEL / DUTY_TITLE은 멤버 속성용으로만 사용

CREATE TABLE IF NOT EXISTS org_group_members (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_id BIGINT NOT NULL REFERENCES org_groups(id) ON DELETE CASCADE,

    -- 유저가 속한 타입(매핑 용도).
    -- 예: 'TEAM', 'JOB_LEVEL', 'DUTY_TITLE'
    member_group_type VARCHAR(30) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_org_group_members_user_type UNIQUE (user_id, member_group_type)
);

CREATE INDEX IF NOT EXISTS idx_org_group_members_user ON org_group_members(user_id);
CREATE INDEX IF NOT EXISTS idx_org_group_members_group ON org_group_members(group_id);
CREATE INDEX IF NOT EXISTS idx_org_group_members_type ON org_group_members(member_group_type);

