-- 사용자별 조직도 노출 순번(관리자 수동 정렬) 추가
ALTER TABLE users
ADD COLUMN IF NOT EXISTS directory_sort_order INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN users.directory_sort_order IS '관리자 사용자 관리에서 조정하는 조직도 사용자 정렬 순번(오름차순)';
