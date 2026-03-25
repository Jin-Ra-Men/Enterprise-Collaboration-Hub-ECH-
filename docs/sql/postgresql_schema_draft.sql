-- ECH PostgreSQL Schema Draft (Phase 1)
-- 목적: 사용자/채널/메시지 도메인의 초기 스키마 정의

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    employee_no VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    department VARCHAR(100),
    company_name VARCHAR(120),
    division_name VARCHAR(120),
    team_name VARCHAR(120),
    job_rank VARCHAR(100),
    duty_title VARCHAR(100),
    role VARCHAR(30) NOT NULL DEFAULT 'MEMBER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    -- BCrypt 해시. 그룹웨어 연동 시 외부 인증 사용 → NULL 허용
    password_hash VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- 기존 테이블에 컬럼 추가 (재실행 시 오류 무시)
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS job_rank VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS duty_title VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS company_name VARCHAR(120);
ALTER TABLE users ADD COLUMN IF NOT EXISTS division_name VARCHAR(120);
ALTER TABLE users ADD COLUMN IF NOT EXISTS team_name VARCHAR(120);

CREATE TABLE IF NOT EXISTS channels (
    id BIGSERIAL PRIMARY KEY,
    workspace_key VARCHAR(100) NOT NULL DEFAULT 'default',
    name VARCHAR(100) NOT NULL,
    description TEXT,
    channel_type VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (workspace_key, name)
);

CREATE TABLE IF NOT EXISTS channel_members (
    id BIGSERIAL PRIMARY KEY,
    channel_id BIGINT NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    member_role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (channel_id, user_id)
);

CREATE TABLE IF NOT EXISTS messages (
    id BIGSERIAL PRIMARY KEY,
    channel_id BIGINT NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    sender_id BIGINT NOT NULL REFERENCES users(id),
    parent_message_id BIGINT REFERENCES messages(id) ON DELETE SET NULL,
    body TEXT NOT NULL,
    message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    is_edited BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_channels_workspace_key ON channels(workspace_key);
CREATE INDEX IF NOT EXISTS idx_channel_members_channel_id ON channel_members(channel_id);
CREATE INDEX IF NOT EXISTS idx_channel_members_user_id ON channel_members(user_id);
CREATE INDEX IF NOT EXISTS idx_messages_channel_id_created_at ON messages(channel_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_messages_parent_message_id ON messages(parent_message_id);

-- 채널별 사용자 읽음 포인터 (마지막으로 읽은 메시지)
CREATE TABLE IF NOT EXISTS channel_read_states (
    id BIGSERIAL PRIMARY KEY,
    channel_id BIGINT NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_read_message_id BIGINT REFERENCES messages(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (channel_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_channel_read_states_channel_user ON channel_read_states(channel_id, user_id);

-- 채널 첨부 파일 메타데이터 (실제 바이너리는 스토리지/NAS 등 외부, DB는 메타만)
CREATE TABLE IF NOT EXISTS channel_files (
    id BIGSERIAL PRIMARY KEY,
    channel_id BIGINT NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    uploaded_by BIGINT NOT NULL REFERENCES users(id),
    original_filename VARCHAR(500) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(1024) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_channel_files_channel_id_created_at ON channel_files(channel_id, created_at DESC);

-- 칸반 보드 / 컬럼 / 카드 / 담당자 / 이벤트 이력
CREATE TABLE IF NOT EXISTS kanban_boards (
    id BIGSERIAL PRIMARY KEY,
    workspace_key VARCHAR(100) NOT NULL DEFAULT 'default',
    name VARCHAR(200) NOT NULL,
    description TEXT,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (workspace_key, name)
);

CREATE TABLE IF NOT EXISTS kanban_columns (
    id BIGSERIAL PRIMARY KEY,
    board_id BIGINT NOT NULL REFERENCES kanban_boards(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_kanban_columns_board_id ON kanban_columns(board_id);

CREATE TABLE IF NOT EXISTS kanban_cards (
    id BIGSERIAL PRIMARY KEY,
    column_id BIGINT NOT NULL REFERENCES kanban_columns(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_kanban_cards_column_id_sort ON kanban_cards(column_id, sort_order);

CREATE TABLE IF NOT EXISTS kanban_card_assignees (
    id BIGSERIAL PRIMARY KEY,
    card_id BIGINT NOT NULL REFERENCES kanban_cards(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE (card_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_kanban_card_assignees_user_id ON kanban_card_assignees(user_id);

CREATE TABLE IF NOT EXISTS kanban_card_events (
    id BIGSERIAL PRIMARY KEY,
    card_id BIGINT NOT NULL REFERENCES kanban_cards(id) ON DELETE CASCADE,
    actor_user_id BIGINT NOT NULL REFERENCES users(id),
    event_type VARCHAR(40) NOT NULL,
    from_ref VARCHAR(500),
    to_ref VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_kanban_card_events_card_created ON kanban_card_events(card_id, created_at DESC);

-- 메시지에서 파생된 업무 항목(채팅 → 업무 연계)
CREATE TABLE IF NOT EXISTS work_items (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    source_message_id BIGINT UNIQUE REFERENCES messages(id) ON DELETE SET NULL,
    source_channel_id BIGINT NOT NULL REFERENCES channels(id),
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_work_items_source_channel_id ON work_items(source_channel_id);
CREATE INDEX IF NOT EXISTS idx_work_items_created_by ON work_items(created_by);

-- 운영 오류 로그 (대화 본문/파일 원문 등 민감 데이터는 저장하지 않음)
CREATE TABLE IF NOT EXISTS error_logs (
    id BIGSERIAL PRIMARY KEY,
    error_code VARCHAR(50) NOT NULL,
    error_class VARCHAR(255) NOT NULL,
    message VARCHAR(2000),
    path VARCHAR(500),
    http_method VARCHAR(20),
    actor_user_id BIGINT,
    request_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_error_logs_created_at ON error_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_error_logs_error_code ON error_logs(error_code);

-- 감사 이벤트 로그 (채널/메시지/파일/업무 도메인 이벤트 기록, 대화 본문 미수집)
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(60) NOT NULL,
    actor_user_id BIGINT,
    resource_type VARCHAR(40) NOT NULL,
    resource_id BIGINT,
    workspace_key VARCHAR(100) NOT NULL DEFAULT 'default',
    detail VARCHAR(500),
    request_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_event_type ON audit_logs(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_user_id ON audit_logs(actor_user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_resource ON audit_logs(resource_type, resource_id);

-- ============================================================
-- 통합 검색 성능 튜닝 (Phase 3-6-2)
-- pg_trgm 확장을 통한 ILIKE 검색 GIN 인덱스 적용
-- 실행 전 pg_trgm 확장 활성화 필요: CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- ============================================================
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 메시지 본문 검색 (아카이브/삭제 제외)
CREATE INDEX IF NOT EXISTS idx_messages_body_trgm ON messages USING GIN (body gin_trgm_ops) WHERE archived_at IS NULL AND is_deleted = FALSE;
-- 파일명 검색
CREATE INDEX IF NOT EXISTS idx_channel_files_filename_trgm ON channel_files USING GIN (original_filename gin_trgm_ops);
-- 업무 항목 검색
CREATE INDEX IF NOT EXISTS idx_work_items_title_trgm ON work_items USING GIN (title gin_trgm_ops);
-- 칸반 카드 검색
CREATE INDEX IF NOT EXISTS idx_kanban_cards_title_trgm ON kanban_cards USING GIN (title gin_trgm_ops);

-- 앱 전역 설정 (관리자 UI 또는 환경 변수로 변경 가능)
CREATE TABLE IF NOT EXISTS app_settings (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value TEXT,
    description TEXT,
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_app_settings_key ON app_settings(setting_key);

-- 보존 정책 (resource_type 별 자동 아카이빙/삭제 설정)
CREATE TABLE IF NOT EXISTS retention_policies (
    id BIGSERIAL PRIMARY KEY,
    resource_type VARCHAR(40) NOT NULL UNIQUE,
    retention_days INT NOT NULL DEFAULT 365,  -- 0 이하 = 영구 보관
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    description TEXT,
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- messages 아카이빙 컬럼 (기존 테이블 확장)
ALTER TABLE messages ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_messages_archived_at ON messages(archived_at) WHERE archived_at IS NOT NULL;

-- 릴리즈 버전 (WAR/JAR 배포 파일 관리)
CREATE TABLE IF NOT EXISTS release_versions (
    id BIGSERIAL PRIMARY KEY,
    version VARCHAR(50) NOT NULL UNIQUE,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    checksum VARCHAR(64),                  -- SHA-256 hex
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',  -- UPLOADED/ACTIVE/PREVIOUS/DEPRECATED
    description TEXT,
    uploaded_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    activated_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_release_versions_status ON release_versions(status);

-- 배포 이력 (활성화/롤백 기록)
CREATE TABLE IF NOT EXISTS deployment_history (
    id BIGSERIAL PRIMARY KEY,
    release_id BIGINT NOT NULL REFERENCES release_versions(id),
    action VARCHAR(30) NOT NULL,           -- ACTIVATED/ROLLED_BACK
    from_version VARCHAR(50),
    to_version VARCHAR(50) NOT NULL,
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_deployment_history_created_at ON deployment_history(created_at DESC);
