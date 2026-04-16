-- 사용자 프로필 사진 파일 경로(스토리지 루트 기준 상대 경로)
-- 예: user-profiles/E12345.jpg — 실제 바이너리는 FILE_STORAGE_DIR 하위 user-profiles/ 에 저장
-- 재실행 가능(idempotent): PostgreSQL ADD COLUMN IF NOT EXISTS

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS profile_image_relpath VARCHAR(512);

COMMENT ON COLUMN users.profile_image_relpath IS '프로필 이미지 상대 경로(예: user-profiles/{sanitizedEmp}.{ext}); NULL이면 이니셜 아바타';
