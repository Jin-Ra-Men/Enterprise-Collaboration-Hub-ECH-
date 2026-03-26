-- org_group_members: user_id(users.id) → employee_no(users.employee_no) 전환
--
-- 사전 조건: users.employee_no 가 채워져 있고 NOT NULL UNIQUE
-- 실행 예:
--   psql ... -f docs/sql/migrate_org_group_members_user_id_to_employee_no.sql

ALTER TABLE org_group_members ADD COLUMN IF NOT EXISTS employee_no VARCHAR(50);

UPDATE org_group_members m
SET employee_no = u.employee_no
FROM users u
WHERE m.user_id IS NOT NULL AND u.id = m.user_id;

ALTER TABLE org_group_members DROP CONSTRAINT IF EXISTS org_group_members_user_id_fkey;
ALTER TABLE org_group_members DROP CONSTRAINT IF EXISTS uq_org_group_members_user_type;

DROP INDEX IF EXISTS idx_org_group_members_user;

ALTER TABLE org_group_members DROP COLUMN IF EXISTS user_id;

ALTER TABLE org_group_members ALTER COLUMN employee_no SET NOT NULL;

ALTER TABLE org_group_members ADD CONSTRAINT org_group_members_employee_no_fkey
    FOREIGN KEY (employee_no) REFERENCES users(employee_no) ON DELETE CASCADE;

ALTER TABLE org_group_members ADD CONSTRAINT uq_org_group_members_emp_type
    UNIQUE (employee_no, member_group_type);

CREATE INDEX IF NOT EXISTS idx_org_group_members_employee_no ON org_group_members(employee_no);
