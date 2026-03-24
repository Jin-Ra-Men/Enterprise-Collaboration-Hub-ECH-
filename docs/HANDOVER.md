# ECH 인수인계서

이 문서는 **프로젝트를 처음 맡는 개발자**와 **운영·관리 관점에서 시스템을 파악해야 하는 담당자**가, 저장소 문서만으로 ECH가 무엇인지·어떻게 돌아가는지 빠르게 이해하도록 정리합니다.  
(Git 커밋 절차·메시지 규칙 등 버전 관리 세부는 `.cursor/rules/core-rules.mdc` 등을 참고합니다.)

## 1) 시스템 개요
- 프로젝트: Enterprise Collaboration Hub (ECH)
- 모티브: Slack, Flow, Teams
- 주요 구성:
  - Backend: Spring Boot
  - Realtime: Node.js `http + socket.io` (Express 미사용)
  - Frontend: Vanilla JS
  - DB: PostgreSQL

## 2) 로컬 실행 요약
1. Realtime 실행: `cd realtime && npm install && npm run dev`
2. Backend 실행: `cd backend && gradlew.bat bootRun` (Windows)
3. Frontend 확인: `frontend/index.html` 브라우저 열기
4. 환경 설정 상세: `docs/ENVIRONMENT_SETUP.md`

## 2-1) 개발자 / 운영·관리자 — 무엇부터 보면 되나
- **개발자**
  - API·도메인 동작: `docs/FEATURE_SPEC.md`, 엔드포인트 목록은 아래 **6)**.
  - 앞으로의 기능 순서: `docs/ROADMAP.md`, 요구 배경: `docs/PROJECT_REQUIREMENTS.md`.
  - DB 구조: `docs/sql/postgresql_schema_draft.sql` 및 **3-1)**. 로컬에서 사람 데이터가 필요하면 `docs/sql/seed_test_users.sql` (`docs/ENVIRONMENT_SETUP.md` 5-1절).
  - Java는 `backend/`, 실시간은 `realtime/`(Express 없이 `http`+Socket.io), 데모 UI는 `frontend/`.
- **운영·관리자**
  - 구성 요소: Java API 서버, Node 실시간 서버, PostgreSQL, (첨부는 외부 스토리지 연동 전제).
  - 가용성 확인: Backend `GET /api/health`, Realtime `GET /health`(DB 연계 여부 포함).
  - 인증·RBAC·감사·배포(WAR 업로드·롤백 등)는 로드맵 **Phase 3** 및 아래 **7)**에 예정 범위가 정리되어 있습니다.
  - 다중 서버로 Realtime을 늘릴 경우 Presence는 현재 메모리 기반이므로 공유 저장소(예: Redis) 검토가 필요합니다(아래 **6) Realtime 메모**).

## 3) 핵심 문서 위치
- 요구사항: `docs/PROJECT_REQUIREMENTS.md`
- 로드맵: `docs/ROADMAP.md`
- 기능 명세: `docs/FEATURE_SPEC.md`
- DB 스키마 초안: `docs/sql/postgresql_schema_draft.sql`
- 테스트 사용자 시드: `docs/sql/seed_test_users.sql`
- RBAC 매트릭스: `docs/RBAC_MATRIX.md`
- 변경 이력: `.cursor/rules/CHANGELOG.md`
- 에러 이력: `.cursor/rules/ERRORS.md`
- 코어 룰: `.cursor/rules/core-rules.mdc`

## 3-1) 데이터베이스 스키마 인수인계 메모
- 현재 기준 DB: PostgreSQL
- 1차 확정 테이블:
  - `users`: 사용자 기본 정보/권한/상태
  - `channels`: 채널 메타/타입/생성자
  - `channel_members`: 채널 멤버십 및 멤버 권한
  - `channel_read_states`: 채널별 사용자 마지막 읽은 메시지(`last_read_message_id`)
  - `channel_files`: 채널 첨부 메타데이터(스토리지 키·원본명·크기 등)
  - `kanban_boards` / `kanban_columns` / `kanban_cards` / `kanban_card_assignees` / `kanban_card_events`
  - `work_items`: 메시지에서 파생된 업무(`source_message_id` 유니크, 메시지 삭제 시 NULL)
  - `messages`: 메시지 본문/스레드/수정·삭제 상태
- 핵심 제약:
  - 사용자 고유값: `employee_no`, `email`
  - 채널 고유값: `(workspace_key, name)`
  - 채널 멤버 중복 방지: `(channel_id, user_id)`
  - 채널별 읽음 포인터 유니크: `(channel_id, user_id)` (`channel_read_states`)
- 인덱스:
  - 채널별 메시지 최신 조회: `idx_messages_channel_id_created_at`

## 4) 운영/개발 체크리스트
- 신규 기능 개발 시:
  - 요구사항/로드맵 반영 여부 확인
  - `README.md`의 주요 기능/문서 링크에 변경사항 반영
  - 구현 후 `docs/FEATURE_SPEC.md` 업데이트
  - 필요 시 `docs/HANDOVER.md` 운영 항목 업데이트
  - `.cursor/rules/CHANGELOG.md` 기록
- 장애/에러 발생 시:
  - `.cursor/rules/ERRORS.md` 기록
  - 재현 절차 및 해결 상태 명시

## 5) 인수인계 시 필수 전달 항목
- 현재 진행 중인 로드맵 항목
- 미해결 이슈/리스크
- 배포/운영 시 주의사항
- 다음 우선순위 작업

## 6) 현재 구현 완료 API (Phase 1 기준)
- 공통:
  - `GET /api/health`
- 채널 도메인:
  - `POST /api/channels`
  - `GET /api/channels/{channelId}`
  - `POST /api/channels/{channelId}/members`
  - `GET /api/channels/{channelId}/read-state?userId=...`
  - `PUT /api/channels/{channelId}/read-state`
  - `GET /api/channels/{channelId}/files?userId=...`
  - `POST /api/channels/{channelId}/files`
  - `GET /api/channels/{channelId}/files/{fileId}/download-info?userId=...`
- 메시지/스레드:
  - `POST /api/channels/{channelId}/messages`
  - `POST /api/channels/{channelId}/messages/{parentMessageId}/replies`
  - `GET /api/channels/{channelId}/messages/{parentMessageId}/replies`
- 사용자 검색:
  - `GET /api/users/search?q=...&department=...`
- 칸반:
  - `POST/GET/GET{id}/DELETE /api/kanban/boards`, 컬럼·카드 CRUD, 담당자 추가/삭제, `GET /api/kanban/cards/{cardId}/history`
- 메시지→업무:
  - `POST /api/messages/{messageId}/work-items`, `GET /api/messages/{messageId}/work-items`, `GET /api/work-items/{workItemId}`
- 조직 동기화(관리):
  - `GET /api/admin/org-sync/users?source=TEST|GROUPWARE`, `POST /api/admin/org-sync/users/sync?source=...`
  - `PUT /api/admin/org-sync/users/{employeeNo}/status`
- 오류 로그(관리):
  - `GET /api/admin/error-logs?from=&to=&errorCode=&path=&limit=`
- Realtime 소켓:
  - `channel:join`
  - `message:send` (DB 저장 연계)
  - `message:new` (저장 성공 시 송신)
  - `message:error` (유효성/저장 실패)
  - `presence:set`
  - `presence:update`
  - `presence:error`

### 채널 API 인수인계 메모
- 생성 시 생성자(`createdByUserId`)는 자동으로 채널 멤버(`MANAGER`)로 등록됩니다.
- 채널명은 워크스페이스 기준으로 유니크합니다. (`workspaceKey + name`)
- 멤버 중복 참여는 서버에서 차단합니다.
- 현재 인증/인가는 미적용 상태이며, RBAC 고도화 단계에서 보완 예정입니다.

### Realtime 메시지 저장 연계 메모
- Realtime 서버는 PostgreSQL에 직접 연결해 메시지를 저장합니다.
- `message:send` payload는 숫자형 `channelId`, `senderId`, 문자열 `text`가 필요합니다.
- 본문 길이는 `MAX_MESSAGE_BODY_LENGTH`(기본 4000)를 초과하면 저장하지 않고 `MESSAGE_TOO_LARGE` 오류를 반환합니다.
- DB 저장 성공 시에만 `message:new`가 채널 룸으로 전송됩니다.
- `/health` 엔드포인트에서 DB 연결 상태(`db: ok/error`)를 함께 확인할 수 있습니다.
- `pg` Pool은 `DB_POOL_MAX`, `DB_POOL_IDLE_MS`, `DB_POOL_CONNECT_TIMEOUT_MS`로 조정할 수 있습니다.

### 사용자 검색/Presence 인수인계 메모
- 사용자 검색은 `users.department`를 조직도 속성으로 사용해 부서 필터를 지원합니다.
- 검색 키워드(`q`)는 이름/이메일/사번에 대해 부분 일치 조회를 수행합니다.
- Presence는 Realtime 서버 메모리 기반으로 관리됩니다.
- 소켓별로 사용자를 추적하며, 해당 사용자의 **모든** 소켓이 끊기면 OFFLINE 브로드캐스트 후 메모리에서 제거합니다(유령 userId 누적 방지).
- 운영 환경에서 다중 인스턴스 구성 시 Presence 저장소(예: Redis) 공유가 필요합니다.

### 테스트 조직 연동(그룹웨어 전환 대비) 인수인계 메모
- 현재 조직 동기화 인터페이스는 `OrgUserProvider` 기반이며 `TEST` 제공자만 구현되어 있습니다.
- `POST /api/admin/org-sync/users/sync?source=TEST`는 테스트 조직 데이터를 `users`에 UPSERT합니다.
- `GROUPWARE` 소스는 의도적으로 미구현 예외를 반환합니다. 실제 연동 시 `GROUPWARE` 제공자 클래스만 추가하면 API는 유지됩니다.
- 계정 활성/비활성은 `PUT /api/admin/org-sync/users/{employeeNo}/status`로 사번 기준 반영합니다.

### RBAC 인수인계 메모
- 현재 RBAC은 `X-User-Role` 헤더 기반 임시 모델입니다 (`MEMBER|MANAGER|ADMIN`).
- 권한 체크는 `@RequireRole` + `RoleGuardInterceptor`로 수행됩니다.
- 자세한 권한 표는 `docs/RBAC_MATRIX.md`를 기준으로 운영합니다.
- 인증 도입 후에는 헤더가 아닌 토큰/세션 클레임으로 역할 판정 로직을 치환해야 합니다.

### 오류 로그 인수인계 메모
- 전역 예외는 `error_logs` 테이블에 적재됩니다.
- 저장 목적은 운영 추적이며, 메시지 본문/파일 원문/토큰 등 민감 데이터는 저장하지 않습니다.
- 조회 API는 `ADMIN` 전용이며 기간/코드/경로 기준 필터가 가능합니다.

### 메시지→업무 인수인계 메모
- 메시지 하나당 연결된 업무는 최대 1건(`work_items.source_message_id` 유니크).
- 메시지가 삭제되어도 업무 행은 남고 `source_message_id`만 비워질 수 있음(`ON DELETE SET NULL`).

### 칸반 보드 인수인계 메모
- 보드는 `(workspace_key, name)` 유니크입니다.
- 카드 컬럼 이동은 동일 보드의 컬럼으로만 허용됩니다.
- `kanban_card_events`에 생성·이동·상태·담당 변경이 기록됩니다(카드 삭제 시 이력도 CASCADE 삭제).
- 보드 목록·카드 이력 API는 각각 100건 상한입니다.

### 채널 파일 메타데이터 인수인계 메모
- 바이너리는 외부 스토리지에 두고 `channel_files`에 메타만 저장합니다.
- 목록 API는 최신순 최대 100건으로 제한됩니다.
- `download-info`는 멤버 검증 후 `storageKey`와 안내 문구를 돌려주며, 실제 사전 서명 URL은 스토리지 연동 단계에서 확장합니다.

### 프론트엔드 데모 UI 메모
- `frontend/app.js`는 수신 메시지 DOM을 최대 200개로 유지해 브라우저 메모리·렌더 비용이 무한 증가하지 않도록 합니다.

### Backend 커넥션 풀 메모
- `application.yml`의 Hikari `maximum-pool-size`는 `DB_POOL_MAX`, `connection-timeout`은 `DB_POOL_CONNECT_TIMEOUT_MS`와 연동됩니다.

### 스레드 답글 기능 인수인계 메모
- 메시지 도메인은 `messages.parent_message_id`로 부모/자식 관계를 관리합니다.
- 스레드 조회는 `parent_message_id` 기준 오름차순(`createdAt ASC`)으로 반환됩니다.
- 성능 고려로 `idx_messages_parent_message_id` 인덱스를 사용합니다.

## 7) 관리자 업그레이드 관리 기능 인수인계 메모 (예정)
- 목표: 관리자 페이지에서 WAR 파일 업로드 기반 버전 관리/롤백 수행
- 예정 작업:
  - 릴리즈 업로드 API 및 저장소 정책 확정
  - 버전 전환/롤백 워크플로우 설계
  - 배포 이력/감사 로그 연동
- 운영 시 주의사항:
  - 무결성 검증 실패 파일은 즉시 폐기
  - 활성화 실패 시 복구 절차(자동 또는 수동) 문서화 필요
