-- 채널 자료실 Phase 1 — 기존 DB에 Hibernate ddl-auto 없이 반영할 때 사용.
-- 실행 전 백업 권장.

CREATE TABLE IF NOT EXISTS channel_library_folders (
    id BIGSERIAL PRIMARY KEY,
    channel_id BIGINT NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_channel_library_folders_channel ON channel_library_folders(channel_id);

ALTER TABLE channel_files ADD COLUMN IF NOT EXISTS library_folder_id BIGINT REFERENCES channel_library_folders(id) ON DELETE SET NULL;
ALTER TABLE channel_files ADD COLUMN IF NOT EXISTS library_pinned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE channel_files ADD COLUMN IF NOT EXISTS library_caption TEXT;
ALTER TABLE channel_files ADD COLUMN IF NOT EXISTS library_tags VARCHAR(500);
ALTER TABLE channel_files ADD COLUMN IF NOT EXISTS attachment_message_id BIGINT REFERENCES messages(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_channel_files_library_folder ON channel_files(library_folder_id) WHERE library_folder_id IS NOT NULL;
