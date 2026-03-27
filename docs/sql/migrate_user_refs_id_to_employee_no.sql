-- users.id 기반 참조 컬럼을 users.employee_no 기반으로 전환
-- 대상: channels/channel_members/channel_read_states/messages/channel_files
--      kanban_boards/kanban_card_assignees/kanban_card_events/work_items
--
-- 주의:
-- 1) 실행 전 백업 필수
-- 2) users.employee_no는 NOT NULL + UNIQUE여야 함

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE employee_no IS NULL OR employee_no = '') THEN
        RAISE EXCEPTION 'users.employee_no is null/blank. migration aborted.';
    END IF;
END $$;

-- 1) channels.created_by
ALTER TABLE channels ADD COLUMN IF NOT EXISTS created_by_emp VARCHAR(50);
UPDATE channels c
SET created_by_emp = u.employee_no
FROM users u
WHERE c.created_by_emp IS NULL
  AND c.created_by IS NOT NULL
  AND u.id = c.created_by;
ALTER TABLE channels DROP COLUMN created_by;
ALTER TABLE channels RENAME COLUMN created_by_emp TO created_by;
ALTER TABLE channels ALTER COLUMN created_by SET NOT NULL;
ALTER TABLE channels
    ADD CONSTRAINT channels_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES users(employee_no) ON DELETE RESTRICT;

-- 2) channel_members.user_id
ALTER TABLE channel_members ADD COLUMN IF NOT EXISTS user_id_emp VARCHAR(50);
UPDATE channel_members cm
SET user_id_emp = u.employee_no
FROM users u
WHERE cm.user_id_emp IS NULL
  AND cm.user_id IS NOT NULL
  AND u.id = cm.user_id;
ALTER TABLE channel_members DROP COLUMN user_id;
ALTER TABLE channel_members RENAME COLUMN user_id_emp TO user_id;
ALTER TABLE channel_members ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE channel_members
    ADD CONSTRAINT channel_members_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(employee_no) ON DELETE CASCADE;
CREATE UNIQUE INDEX IF NOT EXISTS uq_channel_members_channel_user
    ON channel_members(channel_id, user_id);
CREATE INDEX IF NOT EXISTS idx_channel_members_user_id ON channel_members(user_id);

-- 3) channel_read_states.user_id
ALTER TABLE channel_read_states ADD COLUMN IF NOT EXISTS user_id_emp VARCHAR(50);
UPDATE channel_read_states rs
SET user_id_emp = u.employee_no
FROM users u
WHERE rs.user_id_emp IS NULL
  AND rs.user_id IS NOT NULL
  AND u.id = rs.user_id;
ALTER TABLE channel_read_states DROP COLUMN user_id;
ALTER TABLE channel_read_states RENAME COLUMN user_id_emp TO user_id;
ALTER TABLE channel_read_states ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE channel_read_states
    ADD CONSTRAINT channel_read_states_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(employee_no) ON DELETE CASCADE;
CREATE UNIQUE INDEX IF NOT EXISTS uq_channel_read_states_channel_user
    ON channel_read_states(channel_id, user_id);

-- 4) messages.sender_id
ALTER TABLE messages ADD COLUMN IF NOT EXISTS sender_id_emp VARCHAR(50);
UPDATE messages m
SET sender_id_emp = u.employee_no
FROM users u
WHERE m.sender_id_emp IS NULL
  AND m.sender_id IS NOT NULL
  AND u.id = m.sender_id;
ALTER TABLE messages DROP COLUMN sender_id;
ALTER TABLE messages RENAME COLUMN sender_id_emp TO sender_id;
ALTER TABLE messages ALTER COLUMN sender_id SET NOT NULL;
ALTER TABLE messages
    ADD CONSTRAINT messages_sender_id_fkey
    FOREIGN KEY (sender_id) REFERENCES users(employee_no) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON messages(sender_id);

-- 5) channel_files.uploaded_by
ALTER TABLE channel_files ADD COLUMN IF NOT EXISTS uploaded_by_emp VARCHAR(50);
UPDATE channel_files f
SET uploaded_by_emp = u.employee_no
FROM users u
WHERE f.uploaded_by_emp IS NULL
  AND f.uploaded_by IS NOT NULL
  AND u.id = f.uploaded_by;
ALTER TABLE channel_files DROP COLUMN uploaded_by;
ALTER TABLE channel_files RENAME COLUMN uploaded_by_emp TO uploaded_by;
ALTER TABLE channel_files ALTER COLUMN uploaded_by SET NOT NULL;
ALTER TABLE channel_files
    ADD CONSTRAINT channel_files_uploaded_by_fkey
    FOREIGN KEY (uploaded_by) REFERENCES users(employee_no) ON DELETE RESTRICT;

-- 6) kanban_boards.created_by
ALTER TABLE kanban_boards ADD COLUMN IF NOT EXISTS created_by_emp VARCHAR(50);
UPDATE kanban_boards b
SET created_by_emp = u.employee_no
FROM users u
WHERE b.created_by_emp IS NULL
  AND b.created_by IS NOT NULL
  AND u.id = b.created_by;
ALTER TABLE kanban_boards DROP COLUMN created_by;
ALTER TABLE kanban_boards RENAME COLUMN created_by_emp TO created_by;
ALTER TABLE kanban_boards ALTER COLUMN created_by SET NOT NULL;
ALTER TABLE kanban_boards
    ADD CONSTRAINT kanban_boards_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES users(employee_no) ON DELETE RESTRICT;

-- 7) kanban_card_assignees.user_id
ALTER TABLE kanban_card_assignees ADD COLUMN IF NOT EXISTS user_id_emp VARCHAR(50);
UPDATE kanban_card_assignees a
SET user_id_emp = u.employee_no
FROM users u
WHERE a.user_id_emp IS NULL
  AND a.user_id IS NOT NULL
  AND u.id = a.user_id;
ALTER TABLE kanban_card_assignees DROP COLUMN user_id;
ALTER TABLE kanban_card_assignees RENAME COLUMN user_id_emp TO user_id;
ALTER TABLE kanban_card_assignees ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE kanban_card_assignees
    ADD CONSTRAINT kanban_card_assignees_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(employee_no) ON DELETE CASCADE;
CREATE UNIQUE INDEX IF NOT EXISTS uq_kanban_card_assignees_card_user
    ON kanban_card_assignees(card_id, user_id);
CREATE INDEX IF NOT EXISTS idx_kanban_card_assignees_user_id ON kanban_card_assignees(user_id);

-- 8) kanban_card_events.actor_user_id
ALTER TABLE kanban_card_events ADD COLUMN IF NOT EXISTS actor_user_id_emp VARCHAR(50);
UPDATE kanban_card_events e
SET actor_user_id_emp = u.employee_no
FROM users u
WHERE e.actor_user_id_emp IS NULL
  AND e.actor_user_id IS NOT NULL
  AND u.id = e.actor_user_id;
ALTER TABLE kanban_card_events DROP COLUMN actor_user_id;
ALTER TABLE kanban_card_events RENAME COLUMN actor_user_id_emp TO actor_user_id;
ALTER TABLE kanban_card_events ALTER COLUMN actor_user_id SET NOT NULL;
ALTER TABLE kanban_card_events
    ADD CONSTRAINT kanban_card_events_actor_user_id_fkey
    FOREIGN KEY (actor_user_id) REFERENCES users(employee_no) ON DELETE RESTRICT;

-- 9) work_items.created_by
ALTER TABLE work_items ADD COLUMN IF NOT EXISTS created_by_emp VARCHAR(50);
UPDATE work_items w
SET created_by_emp = u.employee_no
FROM users u
WHERE w.created_by_emp IS NULL
  AND w.created_by IS NOT NULL
  AND u.id = w.created_by;
ALTER TABLE work_items DROP COLUMN created_by;
ALTER TABLE work_items RENAME COLUMN created_by_emp TO created_by;
ALTER TABLE work_items ALTER COLUMN created_by SET NOT NULL;
ALTER TABLE work_items
    ADD CONSTRAINT work_items_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES users(employee_no) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_work_items_created_by ON work_items(created_by);
