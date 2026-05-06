-- Phase 6-4: calendar provenance extension + calendar_suggestions (제안→확정=직접 일정과 동일 저장 경로)
-- PostgreSQL. 기존 calendar_events가 이미 있는 배포에서 실행.

ALTER TABLE calendar_events ADD COLUMN IF NOT EXISTS origin_dm_channel_id BIGINT REFERENCES channels(id) ON DELETE SET NULL;
ALTER TABLE calendar_events ADD COLUMN IF NOT EXISTS origin_message_ids TEXT;

CREATE TABLE IF NOT EXISTS calendar_suggestions (
    id BIGSERIAL PRIMARY KEY,
    owner_employee_no VARCHAR(50) NOT NULL REFERENCES users(employee_no),
    title VARCHAR(500) NOT NULL,
    description TEXT,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    origin_channel_id BIGINT REFERENCES channels(id) ON DELETE SET NULL,
    origin_dm_channel_id BIGINT REFERENCES channels(id) ON DELETE SET NULL,
    origin_message_ids TEXT,
    created_by_actor VARCHAR(30) NOT NULL DEFAULT 'USER',
    confirmed_event_id BIGINT REFERENCES calendar_events(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_calendar_suggestions_owner_status ON calendar_suggestions(owner_employee_no, status);
