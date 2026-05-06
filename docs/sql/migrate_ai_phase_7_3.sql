-- Phase 7-3: AI 프로액티브 비서 — 채널 옵트인, 사용자 톤·다이제스트, 공통 제안함 큐
-- PostgreSQL. 운영 적용 전 백업 권장.

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
