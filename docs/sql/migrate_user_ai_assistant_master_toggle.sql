-- 사용자별 AI 비서 마스터 스위치 (테마 설정 등에서 관리)
-- PostgreSQL. 운영 적용 전 백업 권장.

ALTER TABLE user_ai_assistant_preferences
    ADD COLUMN IF NOT EXISTS ai_assistant_enabled BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE user_ai_assistant_preferences
SET ai_assistant_enabled = TRUE
WHERE ai_assistant_enabled IS NULL;
