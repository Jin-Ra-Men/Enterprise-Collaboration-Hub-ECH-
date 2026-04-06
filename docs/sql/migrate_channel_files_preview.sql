-- 채널 첨부: 서버에 원본(storage_key) + 미리보기 압축본(preview_storage_key) 분리 저장
-- 적용 후 백엔드 기동. 기존 행은 preview_* NULL (기존 동작: 한 파일만 존재 = 원본으로 간주)

ALTER TABLE channel_files ADD COLUMN IF NOT EXISTS preview_storage_key VARCHAR(1024);
ALTER TABLE channel_files ADD COLUMN IF NOT EXISTS preview_size_bytes BIGINT;
