-- 사용자별 테마 설정 저장 컬럼 추가
-- 재실행 가능(idempotent)하도록 IF NOT EXISTS 사용

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS theme_preference VARCHAR(20);

-- 기존 데이터는 NULL 유지(앱에서 dark 기본값 처리)
