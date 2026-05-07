-- =============================================================================
-- 로컬·백필용 묶음 이관: 캘린더 제안(Phase 6-4) + AI 프로액티브·제안함(Phase 7-3)
-- + 사용자 AI 마스터 스위치 컬럼 보정
--
-- PostgreSQL, 대상 스키마는 보통 public.
--
-- 전제:
--   - 테이블 channels, users 가 존재해야 함.
--   - calendar_events 가 존재해야 첫 번째 블록의 ALTER 가 성공함.
--     없으면 백엔드 한 번 기동(JPA ddl-auto:update) 후 재실행하거나,
--     docs/sql/postgresql_schema_draft.sql 의 calendar_events 정의를 먼저 적용.
--
-- 원본 분리 스크립트(운영에서는 개별 검토 권장):
--   migrate_calendar_phase_6_4.sql
--   migrate_ai_phase_7_3.sql
--   migrate_user_ai_assistant_master_toggle.sql
-- =============================================================================

-- ----- Part 1: Phase 6-4 (calendar) -----
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

-- ----- Part 2: Phase 7-3 (AI proactive inbox) -----
CREATE TABLE IF NOT EXISTS channel_ai_assistant_preferences (
    channel_id      BIGINT PRIMARY KEY REFERENCES channels (id) ON DELETE CASCADE,
    proactive_opt_in BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS user_ai_assistant_preferences (
    employee_no               VARCHAR(50) PRIMARY KEY,
    proactive_tone            VARCHAR(20) NOT NULL DEFAULT 'BALANCED',
    digest_mode               VARCHAR(20) NOT NULL DEFAULT 'REALTIME',
    proactive_cooldown_until  TIMESTAMPTZ NULL,
    ai_assistant_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_suggestion_inbox (
    id                     BIGSERIAL PRIMARY KEY,
    recipient_employee_no  VARCHAR(50) NOT NULL,
    suggestion_kind        VARCHAR(40) NOT NULL,
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    channel_id             BIGINT NULL REFERENCES channels (id) ON DELETE SET NULL,
    title                  VARCHAR(500) NOT NULL,
    summary                TEXT NULL,
    payload_json           TEXT NULL,
    confidence             DOUBLE PRECISION NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_suggestion_inbox_recipient_status
    ON ai_suggestion_inbox (recipient_employee_no, status);

CREATE INDEX IF NOT EXISTS idx_ai_suggestion_inbox_channel_created
    ON ai_suggestion_inbox (channel_id, created_at DESC);

-- ----- Part 3: 레거시 DB용 마스터 스위치 컬럼 보정 -----
ALTER TABLE user_ai_assistant_preferences
    ADD COLUMN IF NOT EXISTS ai_assistant_enabled BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE user_ai_assistant_preferences
SET ai_assistant_enabled = TRUE
WHERE ai_assistant_enabled IS NULL;

-- ----- 검증(실패 시 위 전제·순서 확인) -----
SELECT table_name
FROM information_schema.tables
WHERE table_schema = current_schema()
  AND table_name IN (
      'calendar_suggestions',
      'channel_ai_assistant_preferences',
      'user_ai_assistant_preferences',
      'ai_suggestion_inbox'
  )
ORDER BY table_name;
