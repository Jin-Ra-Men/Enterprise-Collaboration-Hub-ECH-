# ECH (Enterprise Collaboration Hub) — 데이터베이스 설계 명세서

> 이 문서는 ECH 서비스의 PostgreSQL 데이터베이스 구조를 처음부터 끝까지 상세히 기술한다.  
> JPA 엔티티 기준으로 관리되며, `docs/sql/postgresql_schema_draft.sql`이 DDL의 원본이다.

---

## 목차

1. [DB 기본 정보](#1-db-기본-정보)
2. [확장 모듈](#2-확장-모듈)
3. [테이블 목록 (전체)](#3-테이블-목록-전체)
4. [테이블 상세 명세](#4-테이블-상세-명세)
   - 4-1. users
   - 4-2. channels
   - 4-3. channel_members
   - 4-4. messages
   - 4-5. channel_read_states
   - 4-6. channel_files
   - 4-7. kanban_boards
   - 4-8. kanban_columns
   - 4-9. kanban_cards
   - 4-10. kanban_card_assignees
   - 4-11. kanban_card_events
   - 4-12. work_items
   - 4-13. error_logs
   - 4-14. audit_logs
   - 4-15. app_settings
   - 4-16. retention_policies
   - 4-17. release_versions
   - 4-18. deployment_history
5. [인덱스 전체 목록](#5-인덱스-전체-목록)
6. [Enum 값 정의](#6-enum-값-정의)
7. [테이블 관계도 (ERD 텍스트)](#7-테이블-관계도-erd-텍스트)
8. [데이터 보존 정책](#8-데이터-보존-정책)
9. [초기 데이터 (시드)](#9-초기-데이터-시드)
10. [운영 주의 사항](#10-운영-주의-사항)

---

## 1. DB 기본 정보

| 항목 | 값 |
|------|----|
| DBMS | PostgreSQL 14 이상 |
| DB 이름 | `ech` |
| 기본 접속 사용자 | `ech_user` |
| 기본 비밀번호 | `ech_password` (운영 환경에서 반드시 변경) |
| 기본 포트 | `5432` |
| 문자셋 | UTF-8 |
| 타임존 컬럼 타입 | `TIMESTAMPTZ` (Timezone Aware) |

### DB/사용자 초기 생성 SQL

```sql
-- 관리자(postgres) 계정으로 실행
CREATE DATABASE ech
    ENCODING 'UTF8'
    LC_COLLATE 'C'
    LC_CTYPE 'C'
    TEMPLATE template0;

CREATE USER ech_user WITH PASSWORD 'ech_password';
GRANT ALL PRIVILEGES ON DATABASE ech TO ech_user;

-- ech DB로 접속 후
\c ech
GRANT ALL ON SCHEMA public TO ech_user;
```

### 환경 변수 설정 (`.env`)

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=ech
DB_USER=ech_user
DB_PASSWORD=ech_password
DB_POOL_MAX=10
DB_POOL_MIN=1
DB_POOL_IDLE_MS=30000
DB_POOL_CONNECT_TIMEOUT_MS=10000
```

---

## 2. 확장 모듈

```sql
-- 통합 검색 성능 향상 (trigram 기반 ILIKE/GIN 인덱스)
-- 반드시 superuser 또는 DB 소유자가 실행
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

`pg_trgm` 확장은 한국어를 포함한 문자열의 부분 일치 검색을 GIN 인덱스로 처리할 수 있게 한다.  
`ILIKE '%키워드%'` 쿼리의 성능이 Full Table Scan에서 GIN Index Scan으로 개선된다.

---

## 3. 테이블 목록 (전체)

| # | 테이블명 | 도메인 | 역할 요약 |
|---|---------|--------|-----------|
| 1 | `users` | 사용자 | 사용자 계정 (사번, 이메일, 역할, 비밀번호 해시) |
| 2 | `org_groups` | 조직도 | 조직 룩업/계층(회사→본부→팀, 잡 lookup) |
| 3 | `org_group_members` | 조직도 | 유저-조직 매핑 (TEAM/JOB_LEVEL/JOB_POSITION/JOB_TITLE, FK=`employee_no`) |
| 4 | `channels` | 채널 | 대화/협업 공간 (PUBLIC/PRIVATE/DM) |
| 5 | `channel_members` | 채널 | 채널-사용자 M:N 매핑 및 채널 내 역할 |
| 6 | `messages` | 메시지 | 채팅 메시지 및 스레드 답글 |
| 7 | `channel_read_states` | 메시지 | 사용자별 채널 읽음 포인터 |
| 8 | `channel_files` | 파일 | 채널 첨부파일 메타데이터 |
| 9 | `kanban_boards` | 칸반 | 칸반 보드 |
| 10 | `kanban_columns` | 칸반 | 칸반 컬럼 (단계) |
| 11 | `kanban_cards` | 칸반 | 칸반 카드 (업무 항목) |
| 12 | `kanban_card_assignees` | 칸반 | 카드-담당자 M:N 매핑 |
| 13 | `kanban_card_events` | 칸반 | 카드 상태 변경 이력 |
| 14 | `work_items` | 업무 | 채팅 메시지 → 업무 항목 연계 |
| 15 | `error_logs` | 운영 | 시스템 오류 로그 |
| 16 | `audit_logs` | 운영 | 도메인 이벤트 감사 로그 |
| 17 | `app_settings` | 운영 | 앱 전역 설정 (파일 경로 등) |
| 18 | `retention_policies` | 운영 | 데이터 보존 정책 |
| 19 | `release_versions` | 배포 | WAR/JAR 릴리즈 파일 관리 |
| 20 | `deployment_history` | 배포 | 배포 활성화/롤백 이력 |

---

## 4. 테이블 상세 명세

---

### 4-1. `users` — 사용자

**목적**: ECH 서비스 사용자 계정. 사번 기반이며, 그룹웨어 연동 전까지는 로컬 BCrypt 인증을 사용한다.

```sql
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    employee_no     VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    name            VARCHAR(100) NOT NULL,
    role            VARCHAR(30)  NOT NULL DEFAULT 'MEMBER',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    password_hash   VARCHAR(255),
    profile_image_relpath VARCHAR(512),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|--------|------|----------|--------|------|
| `id` | BIGSERIAL | ✅ | auto | PK, 자동 증가 |
| `employee_no` | VARCHAR(50) | ✅ | - | 사번 (사내 고유식별자). UNIQUE |
| `email` | VARCHAR(255) | ✅ | - | 이메일. 로그인 식별자. UNIQUE |
| `name` | VARCHAR(100) | ✅ | - | 표시 이름 |
| `role` | VARCHAR(30) | ✅ | `'MEMBER'` | 앱 역할. → [역할 Enum 참고](#roles) |
| `status` | VARCHAR(20) | ✅ | `'ACTIVE'` | 계정 상태. → [상태 Enum 참고](#user-status) |
| `password_hash` | VARCHAR(255) | ❌ | NULL | BCrypt 해시. 그룹웨어 연동 시 NULL 허용 |
| `profile_image_relpath` | VARCHAR(512) | ❌ | NULL | 프로필 사진 파일 상대 경로(스토리지 루트 기준, 예: `user-profiles/E12345.jpg`) |
| `created_at` | TIMESTAMPTZ | ✅ | NOW() | 계정 생성 시각 |
| `updated_at` | TIMESTAMPTZ | ✅ | NOW() | 최종 수정 시각 |

**제약조건**
- `UNIQUE(employee_no)`, `UNIQUE(email)`

**관련 Java Entity**: `com.ech.backend.domain.user.User`

---

### 4-1-1. `org_groups` — 조직 룩업/계층

**목적**: 조직도 트리를 구성하는 룩업/계층 데이터(회사→본부→팀, 그리고 직급·직위·직책 lookup)를 저장한다.  
**표시명**은 `display_name`이며, `group_code`는 **ASCII 전용**(대문자 영숫자·`_`) pretty 코드이며 내부적으로는 동일 시드에 대한 MD5 지문 접두부로 충돌을 피한다(백엔드 `OrgGroupCodes` 규칙과 동일).

```sql
CREATE TABLE IF NOT EXISTS org_groups (
    id BIGSERIAL PRIMARY KEY,
    group_type VARCHAR(30) NOT NULL,
    group_code VARCHAR(32) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    member_of_group_code VARCHAR(32) NULL REFERENCES org_groups(group_code) ON DELETE CASCADE,
    group_path VARCHAR(500) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_org_groups_group_code UNIQUE (group_code)
);
```

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `group_type` | VARCHAR(30) | `COMPANY`, `DIVISION`, `TEAM`, `JOB_LEVEL`, `JOB_POSITION`, `JOB_TITLE` (레거시 DB는 `DUTY_TITLE` 행을 `migrate_org_duty_title_to_job_title.sql`로 `JOB_TITLE`로 통일 가능) |
| `group_code` | VARCHAR(32) | 안정적 유니크 코드(ASCII pretty; 예: `COMP_GENERAL_XXXX`, `DIV_XXXX_YYYY`, `TEAM_XXXX_YYYY`, `JOB_...`, `JPOS_...`, `JTIT_...`) |
| `display_name` | VARCHAR(200) | UI 표시명 |
| `member_of_group_code` | VARCHAR(32) | 상위 조직 `group_code` (COMPANY는 NULL) |

---

### 4-1-2. `org_group_members` — 유저 매핑

**목적**: `users`와 `org_groups`를 연결해, 유저가 어느 `TEAM`/`JOB_LEVEL`/`JOB_POSITION`/`JOB_TITLE`에 속하는지 저장한다. **FK는 `users.id`가 아니라 `users.employee_no`** 이다.

```sql
CREATE TABLE IF NOT EXISTS org_group_members (
    id BIGSERIAL PRIMARY KEY,
    employee_no VARCHAR(50) NOT NULL REFERENCES users(employee_no) ON DELETE CASCADE,
    group_code VARCHAR(32) NOT NULL REFERENCES org_groups(group_code) ON DELETE CASCADE,
    member_group_type VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_org_group_members_emp_type UNIQUE (employee_no, member_group_type)
);
```

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `employee_no` | VARCHAR(50) | 사용자 사번 (`users.employee_no`) |
| `member_group_type` | VARCHAR(30) | TEAM/JOB_LEVEL/JOB_POSITION/JOB_TITLE |
| `group_code` | VARCHAR(32) | 해당 조직 식별자(`org_groups.group_code`) |

---

### 4-2. `channels` — 채널

**목적**: 대화/협업 공간. 워크스페이스 키로 논리적 공간을 구분한다.

```sql
CREATE TABLE channels (
    id              BIGSERIAL PRIMARY KEY,
    workspace_key   VARCHAR(100) NOT NULL DEFAULT 'default',
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    channel_type    VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC',
    created_by      BIGINT       NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (workspace_key, name)
);
```

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|--------|------|----------|--------|------|
| `id` | BIGSERIAL | ✅ | auto | PK |
| `workspace_key` | VARCHAR(100) | ✅ | `'default'` | 워크스페이스 식별키 |
| `name` | VARCHAR(100) | ✅ | - | 채널명 |
| `description` | TEXT | ❌ | NULL | 채널 설명 |
| `channel_type` | VARCHAR(20) | ✅ | `'PUBLIC'` | 채널 종류. → [채널 타입 Enum 참고](#channel-type) |
| `created_by` | BIGINT | ✅ | - | FK → `users.id`. 채널 생성자 |
| `created_at` | TIMESTAMPTZ | ✅ | NOW() | 생성 시각 |
| `updated_at` | TIMESTAMPTZ | ✅ | NOW() | 수정 시각 |

**제약조건**
- `UNIQUE(workspace_key, name)` : 같은 워크스페이스 내 채널명 중복 불가

**인덱스**
- `idx_channels_workspace_key ON channels(workspace_key)`

**관련 Java Entity**: `com.ech.backend.domain.channel.Channel`

---

### 4-3. `channel_members` — 채널 멤버

**목적**: 채널과 사용자의 M:N 관계 매핑. 채널 내 역할(OWNER/MANAGER/MEMBER)을 관리한다.

```sql
CREATE TABLE channel_members (
    id          BIGSERIAL PRIMARY KEY,
    channel_id  BIGINT       NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    member_role VARCHAR(20)  NOT NULL DEFAULT 'MEMBER',
    joined_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (channel_id, user_id)
);
```

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|--------|------|----------|--------|------|
| `id` | BIGSERIAL | ✅ | auto | PK |
| `channel_id` | BIGINT | ✅ | - | FK → `channels.id` (CASCADE) |
| `user_id` | BIGINT | ✅ | - | FK → `users.id` (CASCADE) |
| `member_role` | VARCHAR(20) | ✅ | `'MEMBER'` | 채널 내 역할. → [채널 멤버 역할 참고](#channel-member-role) |
| `joined_at` | TIMESTAMPTZ | ✅ | NOW() | 참여 시각 |

**제약조건**
- `UNIQUE(channel_id, user_id)` : 동일 채널에 동일 사용자 중복 참여 불가

**인덱스**
- `idx_channel_members_channel_id ON channel_members(channel_id)`
- `idx_channel_members_user_id ON channel_members(user_id)`

**관련 Java Entity**: `com.ech.backend.domain.channel.ChannelMember`

---

### 4-4. `messages` — 메시지

**목적**: 채널 내 채팅 메시지 및 스레드 답글. 소프트 삭제와 아카이빙을 지원한다.

```sql
CREATE TABLE messages (
    id                  BIGSERIAL PRIMARY KEY,
    channel_id          BIGINT       NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    sender_id           BIGINT       NOT NULL REFERENCES users(id),
    parent_message_id   BIGINT       REFERENCES messages(id) ON DELETE SET NULL,
    body                TEXT         NOT NULL,
    message_type        VARCHAR(20)  NOT NULL DEFAULT 'TEXT',
    is_edited           BOOLEAN      NOT NULL DEFAULT FALSE,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    archived_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|--------|------|----------|--------|------|
| `id` | BIGSERIAL | ✅ | auto | PK |
| `channel_id` | BIGINT | ✅ | - | FK → `channels.id` (CASCADE) |
| `sender_id` | BIGINT | ✅ | - | FK → `users.id`. 전송자 |
| `parent_message_id` | BIGINT | ❌ | NULL | FK → `messages.id`. 스레드 답글이면 부모 ID |
| `body` | TEXT | ✅ | - | 메시지 본문. 감사 로그에는 미수집 |
| `message_type` | VARCHAR(20) | ✅ | `'TEXT'` | 메시지 유형. → [메시지 타입 참고](#message-type) |
| `is_edited` | BOOLEAN | ✅ | `FALSE` | 편집 여부 |
| `is_deleted` | BOOLEAN | ✅ | `FALSE` | 소프트 삭제 여부 |
| `archived_at` | TIMESTAMPTZ | ❌ | NULL | 아카이빙 시각. NULL이면 활성 메시지 |
| `created_at` | TIMESTAMPTZ | ✅ | NOW() | 전송 시각 |
| `updated_at` | TIMESTAMPTZ | ✅ | NOW() | 수정 시각 |

**제약조건**
- `parent_message_id`가 NULL이면 일반 메시지, 값이 있으면 스레드 답글

**인덱스**
- `idx_messages_channel_id_created_at ON messages(channel_id, created_at DESC)` — 채널 메시지 목록 조회
- `idx_messages_sender_id ON messages(sender_id)`
- `idx_messages_parent_message_id ON messages(parent_message_id)` — 스레드 조회
- `idx_messages_archived_at ON messages(archived_at) WHERE archived_at IS NOT NULL` — 부분 인덱스
- `idx_messages_body_trgm ON messages USING GIN (body gin_trgm_ops) WHERE archived_at IS NULL AND is_deleted = FALSE` — 통합 검색

**관련 Java Entity**: `com.ech.backend.domain.message.Message`

---

### 4-5. `channel_read_states` — 채널 읽음 상태

**목적**: 사용자가 채널의 어느 메시지까지 읽었는지 기록한다. 읽지 않은 메시지 수 계산에 사용된다.

```sql
CREATE TABLE channel_read_states (
    id                   BIGSERIAL PRIMARY KEY,
    channel_id           BIGINT       NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    user_id              BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_read_message_id BIGINT       REFERENCES messages(id) ON DELETE SET NULL,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (channel_id, user_id)
);
```

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|--------|------|----------|--------|------|
| `id` | BIGSERIAL | ✅ | auto | PK |
| `channel_id` | BIGINT | ✅ | - | FK → `channels.id` |
| `user_id` | BIGINT | ✅ | - | FK → `users.id` |
| `last_read_message_id` | BIGINT | ❌ | NULL | FK → `messages.id`. 마지막으로 읽은 메시지 |
| `updated_at` | TIMESTAMPTZ | ✅ | NOW() | 읽음 상태 갱신 시각 |

**제약조건**
- `UNIQUE(channel_id, user_id)` : 한 사용자가 같은 채널에 읽음 상태를 중복 등록 불가

**인덱스**
- `idx_channel_read_states_channel_user ON channel_read_states(channel_id, user_id)`

**관련 Java Entity**: `com.ech.backend.domain.channel.ChannelReadState`

---

### 4-6. `channel_files` — 채널 첨부파일

**목적**: 채널에 업로드된 파일의 메타데이터를 저장한다. 실제 파일은 로컬 스토리지(`FILE_STORAGE_DIR`)에 저장된다.

```sql
CREATE TABLE channel_files (
    id                BIGSERIAL PRIMARY KEY,
    channel_id        BIGINT        NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    uploaded_by       BIGINT        NOT NULL REFERENCES users(id),
    original_filename VARCHAR(500)  NOT NULL,
    content_type      VARCHAR(255)  NOT NULL,
    size_bytes        BIGINT        NOT NULL,
    storage_key       VARCHAR(1024) NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
```

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|--------|------|----------|--------|------|
| `id` | BIGSERIAL | ✅ | auto | PK |
| `channel_id` | BIGINT | ✅ | - | FK → `channels.id` |
| `uploaded_by` | BIGINT | ✅ | - | FK → `users.id`. 업로더 |
| `original_filename` | VARCHAR(500) | ✅ | - | 원본 파일명 (XSS 방지 처리됨) |
| `content_type` | VARCHAR(255) | ✅ | - | MIME 타입 (예: `image/png`) |
| `size_bytes` | BIGINT | ✅ | - | 파일 크기 (바이트) |
| `storage_key` | VARCHAR(1024) | ✅ | - | 스토리지 상대 경로. 예: `channels/1/2026/03/{UUID}_파일명.pdf` |
| `created_at` | TIMESTAMPTZ | ✅ | NOW() | 업로드 시각 |

**파일 저장 경로 구조**
```
{FILE_STORAGE_DIR}/channels/{channelId}/{YYYY}/{MM}/{UUID}_{sanitized_filename}
```
- 베이스 경로는 `app_settings.file.storage.base-dir`에서 런타임에 읽어온다.
- `storage_key`는 베이스 경로를 제외한 상대 경로를 저장한다.

**인덱스**
- `idx_channel_files_channel_id_created_at ON channel_files(channel_id, created_at DESC)`
- `idx_channel_files_filename_trgm ON channel_files USING GIN (original_filename gin_trgm_ops)` — 통합 검색

**관련 Java Entity**: `com.ech.backend.domain.file.ChannelFile`

---

### 4-7. `kanban_boards` — 칸반 보드

**목적**: 프로젝트/업무 관리를 위한 칸반 보드. 워크스페이스별로 구분된다.

```sql
CREATE TABLE kanban_boards (
    id            BIGSERIAL PRIMARY KEY,
    workspace_key VARCHAR(100) NOT NULL DEFAULT 'default',
    name          VARCHAR(200) NOT NULL,
    description   TEXT,
    created_by    BIGINT       NOT NULL REFERENCES users(id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (workspace_key, name)
);
```

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|--------|------|----------|--------|------|
| `id` | BIGSERIAL | ✅ | auto | PK |
| `workspace_key` | VARCHAR(100) | ✅ | `'default'` | 워크스페이스 식별키 |
| `name` | VARCHAR(200) | ✅ | - | 보드명 |
| `description` | TEXT | ❌ | NULL | 보드 설명 |
| `created_by` | BIGINT | ✅ | - | FK → `users.id`. 생성자 |
| `created_at` | TIMESTAMPTZ | ✅ | NOW() | 생성 시각 |
| `updated_at` | TIMESTAMPTZ | ✅ | NOW() | 수정 시각 |

**제약조건**
- `UNIQUE(workspace_key, name)` : 워크스페이스 내 보드명 중복 불가

**관련 Java Entity**: `com.ech.backend.domain.kanban.KanbanBoard`

---

### 4-8. `kanban_columns` — 칸반 컬럼

**목적**: 칸반 보드의 단계(예: 할 일, 진행 중, 완료). 순서를 `sort_order`로 관리한다.

```sql
CREATE TABLE kanban_columns (
    id         BIGSERIAL PRIMARY KEY,
    board_id   BIGINT       NOT NULL REFERENCES kanban_boards(id) ON DELETE CASCADE,
    name       VARCHAR(200) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|--------|------|----------|--------|------|
| `id` | BIGSERIAL | ✅ | auto | PK |
| `board_id` | BIGINT | ✅ | - | FK → `kanban_boards.id` (CASCADE) |
| `name` | VARCHAR(200) | ✅ | - | 컬럼명 |
| `sort_order` | INT | ✅ | `0` | 화면 표시 순서 (오름차순) |
| `created_at` | TIMESTAMPTZ | ✅ | NOW() | 생성 시각 |

**인덱스**
- `idx_kanban_columns_board_id ON kanban_columns(board_id)`

**관련 Java Entity**: `com.ech.backend.domain.kanban.KanbanColumn`

---

### 4-9. `kanban_cards` — 칸반 카드

**목적**: 칸반 컬럼 내 개별 업무 카드. 상태와 순서를 관리한다.

```sql
CREATE TABLE kanban_cards (
    id          BIGSERIAL PRIMARY KEY,
    column_id   BIGINT       NOT NULL REFERENCES kanban_columns(id) ON DELETE CASCADE,
    title       VARCHAR(500) NOT NULL,
    description TEXT,
    sort_order  INT          NOT NULL DEFAULT 0,
    status      VARCHAR(50)  NOT NULL DEFAULT 'OPEN',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|--------|------|----------|--------|------|
| `id` | BIGSERIAL | ✅ | auto | PK |
| `column_id` | BIGINT | ✅ | - | FK → `kanban_columns.id` (CASCADE) |
| `title` | VARCHAR(500) | ✅ | - | 카드 제목 |
| `description` | TEXT | ❌ | NULL | 카드 설명 |
| `sort_order` | INT | ✅ | `0` | 컬럼 내 순서 |
| `status` | VARCHAR(50) | ✅ | `'OPEN'` | 카드 상태. → [카드 상태 Enum 참고](#kanban-card-status) |
| `created_at` | TIMESTAMPTZ | ✅ | NOW() | 생성 시각 |
| `updated_at` | TIMESTAMPTZ | ✅ | NOW() | 수정 시각 |

**인덱스**
- `idx_kanban_cards_column_id_sort ON kanban_cards(column_id, sort_order)`
- `idx_kanban_cards_title_trgm ON kanban_cards USING GIN (title gin_trgm_ops)` — 통합 검색

**관련 Java Entity**: `com.ech.backend.domain.kanban.KanbanCard`

---

### 4-10. `kanban_card_assignees` — 칸반 카드 담당자

**목적**: 칸반 카드와 담당자 사용자의 M:N 매핑.

```sql
CREATE TABLE kanban_card_assignees (
    id      BIGSERIAL PRIMARY KEY,
    card_id BIGINT NOT NULL REFERENCES kanban_cards(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE (card_id, user_id)
);
```

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|--------|------|----------|--------|------|
| `id` | BIGSERIAL | ✅ | auto | PK |
| `card_id` | BIGINT | ✅ | - | FK → `kanban_cards.id` (CASCADE) |
| `user_id` | BIGINT | ✅ | - | FK → `users.id` (CASCADE) |

**제약조건**
- `UNIQUE(card_id, user_id)` : 동일 카드에 동일 담당자 중복 불가

**인덱스**
- `idx_kanban_card_assignees_user_id ON kanban_card_assignees(user_id)`

**관련 Java Entity**: `com.ech.backend.domain.kanban.KanbanCardAssignee`

---

### 4-11. `kanban_card_events` — 칸반 카드 이벤트 이력

**목적**: 칸반 카드의 상태 변경, 컬럼 이동, 담당자 변경 등의 이력을 기록한다.

```sql
CREATE TABLE kanban_card_events (
    id            BIGSERIAL PRIMARY KEY,
    card_id       BIGINT       NOT NULL REFERENCES kanban_cards(id) ON DELETE CASCADE,
    actor_user_id BIGINT       NOT NULL REFERENCES users(id),
    event_type    VARCHAR(40)  NOT NULL,
    from_ref      VARCHAR(500),
    to_ref        VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|--------|------|----------|--------|------|
| `id` | BIGSERIAL | ✅ | auto | PK |
| `card_id` | BIGINT | ✅ | - | FK → `kanban_cards.id` (CASCADE) |
| `actor_user_id` | BIGINT | ✅ | - | FK → `users.id`. 이벤트 발생 사용자 |
| `event_type` | VARCHAR(40) | ✅ | - | 이벤트 유형. → [카드 이벤트 타입 참고](#kanban-card-event-type) |
| `from_ref` | VARCHAR(500) | ❌ | NULL | 변경 전 값 (컬럼명, 상태값 등) |
| `to_ref` | VARCHAR(500) | ❌ | NULL | 변경 후 값 |
| `created_at` | TIMESTAMPTZ | ✅ | NOW() | 이벤트 발생 시각 |

**인덱스**
- `idx_kanban_card_events_card_created ON kanban_card_events(card_id, created_at DESC)`

**관련 Java Entity**: `com.ech.backend.domain.kanban.KanbanCardEvent`

---

### 4-12. `work_items` — 업무 항목

**목적**: 채팅 메시지에서 파생된 업무 항목. 메시지와 양방향 연결된다.

```sql
CREATE TABLE work_items (
    id                BIGSERIAL PRIMARY KEY,
    title             VARCHAR(500) NOT NULL,
    description       TEXT,
    status            VARCHAR(50)  NOT NULL DEFAULT 'OPEN',
    source_message_id BIGINT       UNIQUE REFERENCES messages(id) ON DELETE SET NULL,
    source_channel_id BIGINT       NOT NULL REFERENCES channels(id),
    created_by        BIGINT       NOT NULL REFERENCES users(id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|--------|------|----------|--------|------|
| `id` | BIGSERIAL | ✅ | auto | PK |
| `title` | VARCHAR(500) | ✅ | - | 업무 제목 |
| `description` | TEXT | ❌ | NULL | 업무 상세 내용 |
| `status` | VARCHAR(50) | ✅ | `'OPEN'` | 업무 상태. → [업무 상태 Enum 참고](#work-item-status) |
| `source_message_id` | BIGINT | ❌ | NULL | FK → `messages.id`. 원본 메시지 (UNIQUE) |
| `source_channel_id` | BIGINT | ✅ | - | FK → `channels.id`. 출처 채널 |
| `created_by` | BIGINT | ✅ | - | FK → `users.id`. 생성자 |
| `created_at` | TIMESTAMPTZ | ✅ | NOW() | 생성 시각 |
| `updated_at` | TIMESTAMPTZ | ✅ | NOW() | 수정 시각 |

**제약조건**
- `UNIQUE(source_message_id)` : 메시지 하나당 업무 항목은 1개만 허용

**인덱스**
- `idx_work_items_source_channel_id ON work_items(source_channel_id)`
- `idx_work_items_created_by ON work_items(created_by)`
- `idx_work_items_title_trgm ON work_items USING GIN (title gin_trgm_ops)` — 통합 검색

**관련 Java Entity**: `com.ech.backend.domain.work.WorkItem`

---

### 4-13. `error_logs` — 에러 로그

**목적**: 서비스 운영 중 발생하는 예외/오류를 기록한다. 보존 정책에 따라 자동 삭제된다.  
**주의**: 대화 본문, 개인정보 등 민감 데이터는 저장하지 않는다.

```sql
CREATE TABLE error_logs (
    id            BIGSERIAL PRIMARY KEY,
    error_code    VARCHAR(50)   NOT NULL,
    error_class   VARCHAR(255)  NOT NULL,
    message       VARCHAR(2000),
    path          VARCHAR(500),
    http_method   VARCHAR(20),
    actor_user_id BIGINT,
    request_id    VARCHAR(100),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
```

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|--------|------|----------|--------|------|
| `id` | BIGSERIAL | ✅ | auto | PK |
| `error_code` | VARCHAR(50) | ✅ | - | 에러 코드 (예: `INVALID_ARGUMENT`, `DB_SAVE_FAILED`) |
| `error_class` | VARCHAR(255) | ✅ | - | Java 예외 클래스명 |
| `message` | VARCHAR(2000) | ❌ | NULL | 에러 메시지 |
| `path` | VARCHAR(500) | ❌ | NULL | 요청 경로 (예: `/api/channels/1/messages`) |
| `http_method` | VARCHAR(20) | ❌ | NULL | HTTP 메서드 (GET/POST 등) |
| `actor_user_id` | BIGINT | ❌ | NULL | 요청 사용자 ID (비로그인 시 NULL) |
| `request_id` | VARCHAR(100) | ❌ | NULL | 요청 추적 ID |
| `created_at` | TIMESTAMPTZ | ✅ | NOW() | 에러 발생 시각 |

**인덱스**
- `idx_error_logs_created_at ON error_logs(created_at DESC)`
- `idx_error_logs_error_code ON error_logs(error_code)`

**관련 Java Entity**: `com.ech.backend.domain.error.ErrorLog`

---

### 4-14. `audit_logs` — 감사 로그

**목적**: 채널/메시지/파일/업무/배포 등 주요 도메인 이벤트를 기록한다.  
**준수 원칙**: 대화 본문·파일 원문·개인 식별 정보는 수집하지 않는다 (윤리·보안 정책).

```sql
CREATE TABLE audit_logs (
    id             BIGSERIAL PRIMARY KEY,
    event_type     VARCHAR(60)  NOT NULL,
    actor_user_id  BIGINT,
    resource_type  VARCHAR(40)  NOT NULL,
    resource_id    BIGINT,
    workspace_key  VARCHAR(100) NOT NULL DEFAULT 'default',
    detail         VARCHAR(500),
    request_id     VARCHAR(100),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|--------|------|----------|--------|------|
| `id` | BIGSERIAL | ✅ | auto | PK |
| `event_type` | VARCHAR(60) | ✅ | - | 이벤트 유형. → [AuditEventType Enum 참고](#audit-event-type) |
| `actor_user_id` | BIGINT | ❌ | NULL | 행위자 사용자 ID (시스템 작업 시 NULL) |
| `resource_type` | VARCHAR(40) | ✅ | - | 대상 리소스 종류 (예: `CHANNEL`, `FILE`) |
| `resource_id` | BIGINT | ❌ | NULL | 대상 리소스 ID |
| `workspace_key` | VARCHAR(100) | ✅ | `'default'` | 워크스페이스 키 |
| `detail` | VARCHAR(500) | ❌ | NULL | 부가 정보 (예: `channelId=1 filename=보고서.pdf`) |
| `request_id` | VARCHAR(100) | ❌ | NULL | 요청 추적 ID |
| `created_at` | TIMESTAMPTZ | ✅ | NOW() | 이벤트 기록 시각 |

**인덱스**
- `idx_audit_logs_created_at ON audit_logs(created_at DESC)`
- `idx_audit_logs_event_type ON audit_logs(event_type)`
- `idx_audit_logs_actor_user_id ON audit_logs(actor_user_id)`
- `idx_audit_logs_resource ON audit_logs(resource_type, resource_id)`

**관련 Java Entity**: `com.ech.backend.domain.audit.AuditLog`

---

### 4-15. `app_settings` — 앱 전역 설정

**목적**: 서버 재기동 없이 변경 가능한 앱 전역 설정 저장소.  
관리자 UI(`⚙️ 설정` 탭) 또는 환경 변수로 초기값이 설정된다.

```sql
CREATE TABLE app_settings (
    id            BIGSERIAL PRIMARY KEY,
    setting_key   VARCHAR(100) NOT NULL UNIQUE,
    setting_value TEXT,
    description   TEXT,
    updated_by    BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|--------|------|----------|--------|------|
| `id` | BIGSERIAL | ✅ | auto | PK |
| `setting_key` | VARCHAR(100) | ✅ | - | 설정 키. UNIQUE |
| `setting_value` | TEXT | ❌ | NULL | 설정 값 |
| `description` | TEXT | ❌ | NULL | 설정 설명 |
| `updated_by` | BIGINT | ❌ | NULL | FK → `users.id`. 마지막 변경자 |
| `updated_at` | TIMESTAMPTZ | ✅ | NOW() | 마지막 변경 시각 |

**기본 설정 키 (서버 기동 시 자동 시드)**

| setting_key | 기본값 | 설명 |
|-------------|--------|------|
| `file.storage.base-dir` | `D:/testStorage` (환경변수 `FILE_STORAGE_DIR`) | 첨부파일 저장 기본 경로 |
| `file.max-size-mb` | `100` | 단일 파일 최대 크기 (MB) |
| `ai.gateway.allow-external-llm` | `app.ai`·환경과 동일 시드 | AI 게이트웨이 외부 LLM 허용 (`true`/`false`) |
| `ai.gateway.policy-version` | 동일 시드 | 상태 API 정책 버전 문자열 |
| `ai.gateway.chat-max-requests-per-minute` | 동일 시드 | 분당 chat 호출 상한 (`0`=비활성) |
| `ai.gateway.chat-max-requests-per-hour` | 동일 시드 | 시간당 chat 호출 상한 (`0`=비활성) |
| `ai.gateway.llm-max-input-chars` | `app.ai.llm-max-input-chars`·`AI_GATEWAY_LLM_MAX_INPUT_CHARS` 시드 | 마스킹 후 LLM 프롬프트 최대 코드포인트(256–8000) |
| `ai.llm.http-enabled` | 동일 시드 | OpenAI 호환 HTTP 호출 활성화 |
| `ai.llm.base-url` | 동일 시드 | LLM 베이스 URL; 비우면 yml/환경 폴백 |
| `ai.llm.api-key` | 동일 시드 | Bearer 토큰; 비우면 yml/환경 폴백 |
| `ai.llm.model` | 동일 시드 | 모델 이름 |
| `ai.llm.max-tokens` | 동일 시드 | `max_tokens` 정수 |

**인덱스**
- `idx_app_settings_key ON app_settings(setting_key)`

**관련 Java Entity**: `com.ech.backend.domain.settings.AppSetting`

---

### 4-16. `retention_policies` — 데이터 보존 정책

**목적**: 리소스 유형별 데이터 보존 기간과 아카이빙/삭제 정책을 관리한다.  
매일 02:00에 스케줄러가 실행되어 만료 데이터를 자동 처리한다.

```sql
CREATE TABLE retention_policies (
    id             BIGSERIAL PRIMARY KEY,
    resource_type  VARCHAR(40)  NOT NULL UNIQUE,
    retention_days INT          NOT NULL DEFAULT 365,
    is_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    description    TEXT,
    updated_by     BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|--------|------|----------|--------|------|
| `id` | BIGSERIAL | ✅ | auto | PK |
| `resource_type` | VARCHAR(40) | ✅ | - | 대상 리소스. → [RetentionResourceType 참고](#retention-resource-type) |
| `retention_days` | INT | ✅ | `365` | 보존 일수. 0 이하 = 영구 보관 |
| `is_enabled` | BOOLEAN | ✅ | `FALSE` | 정책 활성 여부 (비활성이면 스케줄러 미동작) |
| `description` | TEXT | ❌ | NULL | 정책 설명 |
| `updated_by` | BIGINT | ❌ | NULL | FK → `users.id`. 마지막 변경자 |
| `updated_at` | TIMESTAMPTZ | ✅ | NOW() | 마지막 변경 시각 |

**기본 시드 데이터 (비활성 상태로 시드)**

| resource_type | retention_days | is_enabled |
|---------------|----------------|------------|
| `MESSAGES` | 365 | false |
| `AUDIT_LOGS` | 180 | false |
| `ERROR_LOGS` | 90 | false |

**관련 Java Entity**: `com.ech.backend.domain.retention.RetentionPolicy`

---

### 4-17. `release_versions` — 릴리즈 버전

**목적**: 관리자가 업로드한 WAR/JAR 배포 파일을 관리한다. SHA-256 체크섬으로 무결성을 검증한다.

```sql
CREATE TABLE release_versions (
    id           BIGSERIAL PRIMARY KEY,
    version      VARCHAR(50)  NOT NULL UNIQUE,
    file_name    VARCHAR(255) NOT NULL,
    file_path    VARCHAR(500) NOT NULL,
    file_size    BIGINT       NOT NULL DEFAULT 0,
    checksum     VARCHAR(64),
    status       VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED',
    description  TEXT,
    uploaded_by  BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    uploaded_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    activated_at TIMESTAMPTZ
);
```

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|--------|------|----------|--------|------|
| `id` | BIGSERIAL | ✅ | auto | PK |
| `version` | VARCHAR(50) | ✅ | - | 버전명 (예: `v1.2.0`). UNIQUE |
| `file_name` | VARCHAR(255) | ✅ | - | 업로드 파일명 |
| `file_path` | VARCHAR(500) | ✅ | - | 서버 저장 절대 경로 |
| `file_size` | BIGINT | ✅ | `0` | 파일 크기 (바이트) |
| `checksum` | VARCHAR(64) | ❌ | NULL | SHA-256 해시값 (무결성 검증) |
| `status` | VARCHAR(20) | ✅ | `'UPLOADED'` | 릴리즈 상태. → [ReleaseStatus 참고](#release-status) |
| `description` | TEXT | ❌ | NULL | 릴리즈 노트 |
| `uploaded_by` | BIGINT | ❌ | NULL | FK → `users.id`. 업로더 |
| `uploaded_at` | TIMESTAMPTZ | ✅ | NOW() | 업로드 시각 |
| `activated_at` | TIMESTAMPTZ | ❌ | NULL | 활성화 시각 |

**인덱스**
- `idx_release_versions_status ON release_versions(status)`

**관련 Java Entity**: `com.ech.backend.domain.release.ReleaseVersion`

---

### 4-18. `deployment_history` — 배포 이력

**목적**: 릴리즈 활성화(배포)와 롤백 이력을 기록한다. 감사 로그와 연동된다.

```sql
CREATE TABLE deployment_history (
    id            BIGSERIAL PRIMARY KEY,
    release_id    BIGINT       NOT NULL REFERENCES release_versions(id),
    action        VARCHAR(30)  NOT NULL,
    from_version  VARCHAR(50),
    to_version    VARCHAR(50)  NOT NULL,
    actor_user_id BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    note          TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

| 컬럼명 | 타입 | NOT NULL | 기본값 | 설명 |
|--------|------|----------|--------|------|
| `id` | BIGSERIAL | ✅ | auto | PK |
| `release_id` | BIGINT | ✅ | - | FK → `release_versions.id` |
| `action` | VARCHAR(30) | ✅ | - | 배포 행위. → [DeploymentAction 참고](#deployment-action) |
| `from_version` | VARCHAR(50) | ❌ | NULL | 이전 버전 (최초 배포 시 NULL) |
| `to_version` | VARCHAR(50) | ✅ | - | 대상 버전 |
| `actor_user_id` | BIGINT | ❌ | NULL | FK → `users.id`. 배포 수행자 |
| `note` | TEXT | ❌ | NULL | 배포 메모 |
| `created_at` | TIMESTAMPTZ | ✅ | NOW() | 배포 수행 시각 |

**인덱스**
- `idx_deployment_history_created_at ON deployment_history(created_at DESC)`

**관련 Java Entity**: `com.ech.backend.domain.release.DeploymentHistory`

---

## 5. 인덱스 전체 목록

| 인덱스명 | 테이블 | 컬럼 | 종류 | 목적 |
|---------|--------|------|------|------|
| `idx_channels_workspace_key` | channels | workspace_key | BTREE | 워크스페이스 채널 목록 조회 |
| `idx_channel_members_channel_id` | channel_members | channel_id | BTREE | 채널 멤버 목록 조회 |
| `idx_channel_members_user_id` | channel_members | user_id | BTREE | 사용자가 속한 채널 조회 |
| `idx_messages_channel_id_created_at` | messages | (channel_id, created_at DESC) | BTREE | 채널 메시지 시간순 조회 |
| `idx_messages_sender_id` | messages | sender_id | BTREE | 발신자별 메시지 조회 |
| `idx_messages_parent_message_id` | messages | parent_message_id | BTREE | 스레드 답글 조회 |
| `idx_messages_archived_at` | messages | archived_at | BTREE (부분) | 아카이빙 대상 조회 |
| `idx_messages_body_trgm` | messages | body | GIN (부분) | 통합 검색 (활성 메시지만) |
| `idx_channel_read_states_channel_user` | channel_read_states | (channel_id, user_id) | BTREE | 읽음 상태 빠른 조회 |
| `idx_channel_files_channel_id_created_at` | channel_files | (channel_id, created_at DESC) | BTREE | 채널 파일 목록 조회 |
| `idx_channel_files_filename_trgm` | channel_files | original_filename | GIN | 파일명 통합 검색 |
| `idx_kanban_columns_board_id` | kanban_columns | board_id | BTREE | 보드별 컬럼 조회 |
| `idx_kanban_cards_column_id_sort` | kanban_cards | (column_id, sort_order) | BTREE | 컬럼 카드 순서 조회 |
| `idx_kanban_cards_title_trgm` | kanban_cards | title | GIN | 칸반 카드 통합 검색 |
| `idx_kanban_card_assignees_user_id` | kanban_card_assignees | user_id | BTREE | 담당자별 카드 조회 |
| `idx_kanban_card_events_card_created` | kanban_card_events | (card_id, created_at DESC) | BTREE | 카드 이벤트 이력 조회 |
| `idx_work_items_source_channel_id` | work_items | source_channel_id | BTREE | 채널별 업무 조회 |
| `idx_work_items_created_by` | work_items | created_by | BTREE | 사용자별 업무 조회 |
| `idx_work_items_title_trgm` | work_items | title | GIN | 업무 항목 통합 검색 |
| `idx_error_logs_created_at` | error_logs | created_at DESC | BTREE | 에러 시간순 조회 |
| `idx_error_logs_error_code` | error_logs | error_code | BTREE | 에러 코드별 필터 |
| `idx_audit_logs_created_at` | audit_logs | created_at DESC | BTREE | 감사 로그 시간순 조회 |
| `idx_audit_logs_event_type` | audit_logs | event_type | BTREE | 이벤트 유형별 필터 |
| `idx_audit_logs_actor_user_id` | audit_logs | actor_user_id | BTREE | 사용자별 감사 로그 |
| `idx_audit_logs_resource` | audit_logs | (resource_type, resource_id) | BTREE | 리소스별 감사 로그 |
| `idx_app_settings_key` | app_settings | setting_key | BTREE | 설정 키 빠른 조회 |
| `idx_release_versions_status` | release_versions | status | BTREE | 상태별 릴리즈 조회 |
| `idx_deployment_history_created_at` | deployment_history | created_at DESC | BTREE | 배포 이력 시간순 조회 |

---

## 6. Enum 값 정의

### <a name="roles"></a>사용자 역할 (users.role)

| 값 | 설명 | 주요 권한 |
|----|------|-----------|
| `MEMBER` | 일반 사용자 | 채널 조회/메시지 전송/파일 업다운로드 |
| `MANAGER` | 팀장/관리자 | + 채널 생성/관리, 칸반 보드 생성 |
| `ADMIN` | 시스템 관리자 | 모든 권한 + 시스템 설정/감사 로그/배포 관리 |

### <a name="user-status"></a>사용자 상태 (users.status)

| 값 | 설명 |
|----|------|
| `ACTIVE` | 정상 활성 계정 |
| `INACTIVE` | 비활성 계정 (퇴사 처리 등) |

### <a name="channel-type"></a>채널 유형 (channels.channel_type)

| 값 | 설명 |
|----|------|
| `PUBLIC` | 공개 채널 (워크스페이스 내 누구나 참여 가능) |
| `PRIVATE` | 비공개 채널 (초대된 멤버만 접근) |
| `DM` | 1:1 다이렉트 메시지 |

### <a name="channel-member-role"></a>채널 멤버 역할 (channel_members.member_role)

| 값 | 설명 |
|----|------|
| `OWNER` | 채널 소유자 |
| `MANAGER` | 채널 관리자 |
| `MEMBER` | 일반 멤버 |

### <a name="message-type"></a>메시지 유형 (messages.message_type)

| 값 | 설명 |
|----|------|
| `TEXT` | 일반 텍스트 메시지 |
| `SYSTEM` | 시스템 알림 메시지 (채널 입장 등) |

### <a name="kanban-card-status"></a>칸반 카드 상태 (kanban_cards.status)

| 값 | 설명 |
|----|------|
| `OPEN` | 미완료 |
| `IN_PROGRESS` | 진행 중 |
| `DONE` | 완료 |
| `CANCELLED` | 취소 |

### <a name="kanban-card-event-type"></a>칸반 카드 이벤트 유형 (kanban_card_events.event_type)

| 값 | 설명 |
|----|------|
| `STATUS_CHANGED` | 상태 변경 |
| `MOVED` | 컬럼 이동 |
| `ASSIGNEE_ADDED` | 담당자 추가 |
| `ASSIGNEE_REMOVED` | 담당자 제거 |
| `CREATED` | 카드 생성 |
| `UPDATED` | 카드 수정 |
| `DELETED` | 카드 삭제 |

### <a name="work-item-status"></a>업무 항목 상태 (work_items.status)

| 값 | 설명 |
|----|------|
| `OPEN` | 미처리 |
| `IN_PROGRESS` | 처리 중 |
| `DONE` | 완료 |

### <a name="audit-event-type"></a>감사 로그 이벤트 유형 (audit_logs.event_type)

| 분류 | 값 |
|------|----|
| 채널 | `CHANNEL_CREATED`, `CHANNEL_JOINED` |
| 메시지 | `MESSAGE_SENT`, `MESSAGE_REPLY_SENT` |
| 파일 | `FILE_UPLOADED`, `FILE_DOWNLOAD_INFO_ACCESSED` |
| 업무 | `WORK_ITEM_CREATED` |
| 칸반 | `KANBAN_BOARD_CREATED`, `KANBAN_COLUMN_CREATED/UPDATED/DELETED` |
| 칸반 카드 | `KANBAN_CARD_CREATED/UPDATED/MOVED/STATUS_CHANGED/DELETED` |
| 칸반 담당자 | `KANBAN_ASSIGNEE_ADDED`, `KANBAN_ASSIGNEE_REMOVED` |
| 조직 | `ORG_SYNC_EXECUTED`, `USER_STATUS_CHANGED` |
| 보존 | `RETENTION_POLICY_UPDATED`, `DATA_ARCHIVED` |
| 배포 | `RELEASE_UPLOADED/ACTIVATED/ROLLED_BACK/DELETED` |

### <a name="retention-resource-type"></a>보존 정책 리소스 유형 (retention_policies.resource_type)

| 값 | 처리 방식 |
|----|-----------|
| `MESSAGES` | 소프트 아카이빙 (`archived_at` 컬럼 설정) |
| `AUDIT_LOGS` | 물리 삭제 (hard delete) |
| `ERROR_LOGS` | 물리 삭제 (hard delete) |

### <a name="release-status"></a>릴리즈 상태 (release_versions.status)

| 값 | 설명 |
|----|------|
| `UPLOADED` | 업로드만 완료, 미활성 |
| `ACTIVE` | 현재 활성(배포 중) 버전 (한 시점에 1개만 허용) |
| `PREVIOUS` | 이전 버전 (롤백 대상) |
| `DEPRECATED` | 더 이상 사용하지 않는 버전 |

### <a name="deployment-action"></a>배포 행위 (deployment_history.action)

| 값 | 설명 |
|----|------|
| `ACTIVATED` | 신규 버전 활성화 |
| `ROLLED_BACK` | 이전 버전으로 롤백 |

---

## 7. 테이블 관계도 (ERD 텍스트)

```
users ──┬──< channel_members >── channels ──< messages (self-ref: parent)
        │                              │
        │                              ├──< channel_read_states
        │                              │
        │                              └──< channel_files
        │
        ├──< work_items >── (source) messages
        │         └── (source) channels
        │
        ├──< kanban_boards ──< kanban_columns ──< kanban_cards ──< kanban_card_assignees >── users
        │                                                  │
        │                                                  └──< kanban_card_events
        │
        ├──< error_logs          (actor_user_id, nullable)
        ├──< audit_logs          (actor_user_id, nullable)
        ├──< app_settings        (updated_by, nullable)
        ├──< retention_policies  (updated_by, nullable)
        ├──< release_versions    (uploaded_by, nullable)
        └──< deployment_history  (actor_user_id, nullable)
                  └── release_versions
```

### 주요 관계 정리

| 관계 | 종류 | 설명 |
|------|------|------|
| users ↔ channels | M:N (channel_members) | 사용자는 여러 채널 참여, 채널에는 여러 사용자 |
| channels → messages | 1:N | 채널 삭제 시 CASCADE |
| messages → messages | Self 1:N | parent_message_id로 스레드 구현 |
| channels → channel_files | 1:N | 채널 첨부파일 |
| channels → work_items | 1:N | 메시지 기반 업무 항목 |
| kanban_boards → kanban_columns | 1:N | 칸반 구조 |
| kanban_columns → kanban_cards | 1:N | 칸반 구조 |
| kanban_cards ↔ users | M:N (kanban_card_assignees) | 담당자 매핑 |
| kanban_cards → kanban_card_events | 1:N | 이벤트 이력 |
| release_versions → deployment_history | 1:N | 배포 이력 |

---

## 8. 데이터 보존 정책

관리자 UI(`⚙️ 설정` 탭 > `🚀 배포 관리` > 보존 정책)에서 관리 가능하다.

| 테이블 | 기본 보존 기간 | 처리 방식 | 스케줄 |
|--------|---------------|-----------|--------|
| `messages` | 365일 (비활성) | 소프트 아카이빙 (`archived_at` 설정) | 매일 02:00 |
| `audit_logs` | 180일 (비활성) | 물리 삭제 | 매일 02:00 |
| `error_logs` | 90일 (비활성) | 물리 삭제 | 매일 02:00 |

> `is_enabled = FALSE`이면 스케줄러가 해당 정책을 건너뛴다.  
> 수동 실행: `POST /api/admin/retention-policies/trigger`

---

## 9. 초기 데이터 (시드)

### 테스트 사용자 시드

파일: `docs/sql/seed_test_users.sql`

```bash
# 실행 (운영 DB에는 실행 금지)
psql -h localhost -U ech_user -d ech -f docs/sql/seed_test_users.sql
```

| 사번 | 이메일 | 이름 | 부서 | 역할 |
|------|--------|------|------|------|
| CSTalk-ADM-001 | admin@cstalk-test.local | 시스템 관리자 | 운영본부 | ADMIN |
| CSTalk-TST-001~003 | (생략) | 김테스트 외 | 테스트부서 | MEMBER/MANAGER |
| CSTalk-DEV-001~005 | (생략) | 이개발 외 | 개발1팀/2팀 | MEMBER/MANAGER |
| CSTalk-HR-001 | (생략) | 정인사 | 인사총무팀 | MEMBER |
| CSTalk-SAL-001 | (생략) | 강영업 | 영업1팀 | MEMBER |
| CSTalk-INA-001 | (생략) | 퇴사처리유저 | 인사총무팀 | MEMBER (INACTIVE) |

### 서버 기동 시 자동 시드 (DataInitializer)

서버가 기동될 때 `com.ech.backend.api.init.DataInitializer`가 자동으로 실행된다:

1. `password_hash`가 NULL인 사용자에게 기본 비밀번호 `Ech@1234!` 설정 (BCrypt 해시)
2. `retention_policies` 기본 정책 3건 (비활성 상태로) 삽입
3. `app_settings` 기본 설정 2건 (`file.storage.base-dir`, `file.max-size-mb`) 삽입

---

## 10. 운영 주의 사항

### 데이터 마이그레이션

JPA `ddl-auto: update`를 사용하므로 신규 컬럼은 자동 추가되지만, **컬럼 수정/삭제는 자동으로 이루어지지 않는다**.  
스키마 변경 시 `ALTER TABLE` 스크립트를 `docs/sql/`에 버전별로 관리한다.

### 감사 로그 민감 정보 수집 금지

`audit_logs` 및 `error_logs`에는 **메시지 본문, 파일 원문, 비밀번호** 등은 절대 저장하지 않는다.  
`detail` 컬럼에는 `channelId=1 filename=보고서.pdf` 형식의 메타데이터만 기록한다.

### 검색 인덱스 초기화

`pg_trgm` GIN 인덱스는 최초 설치 시 또는 DB 복원 후 수동으로 생성해야 한다:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- 이후 docs/sql/postgresql_schema_draft.sql의 GIN 인덱스 구문 실행
```

### 파일 스토리지 경로 변경

`app_settings`의 `file.storage.base-dir` 값을 변경하면 **즉시** 새 파일부터 해당 경로에 저장된다.  
기존 파일의 `storage_key`는 상대 경로이므로, 이전 경로에 파일이 그대로 남아있어야 다운로드가 가능하다.  
경로를 바꿀 경우 기존 파일도 새 경로로 복사/이동해야 한다.

### 커넥션 풀 권장 설정

```env
# Backend (HikariCP)
DB_POOL_MAX=10          # CPU 코어 수 × 2 ~ 4 권장
DB_POOL_MIN_IDLE=2

# Realtime (node-postgres)
DB_POOL_MAX=5
DB_POOL_MIN=1
```
