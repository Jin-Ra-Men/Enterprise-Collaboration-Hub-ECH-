-- work_items: due date and priority (Phase 2-7-1-1)
-- Safe to run once on existing PostgreSQL DBs created before this migration.

ALTER TABLE work_items
    ADD COLUMN IF NOT EXISTS due_at TIMESTAMPTZ;

ALTER TABLE work_items
    ADD COLUMN IF NOT EXISTS priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL';
