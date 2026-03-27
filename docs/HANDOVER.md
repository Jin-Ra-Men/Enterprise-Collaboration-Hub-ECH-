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
3. 프론트 확인: **`http://localhost:8080/`** (백엔드가 `index.html` / `styles.css` / `app.js` 만 정적 제공). `file://` 로 `frontend/index.html` 을 열면 API·쿠키·CORS 이슈가 날 수 있음.
   - UI 테마(검정/하양/파랑)는 로그인 후 사용자 영역의 톱니바퀴 팝업에서 변경하며, 서버 `PUT /api/auth/me/theme`로 `users.theme_preference`에 저장됩니다(로그아웃/재로그인 후에도 사용자별 유지). `localStorage ech_theme`는 초기 페인트(FoUC 완화)용 캐시로 함께 사용합니다.
4. 환경 설정 상세: `docs/ENVIRONMENT_SETUP.md`

## 2-1) 개발자 / 운영·관리자 — 무엇부터 보면 되나
- **개발자**
  - API·도메인 동작: `docs/FEATURE_SPEC.md`, 엔드포인트 목록은 아래 **6)**.
  - 앞으로의 기능 순서: `docs/ROADMAP.md`, 요구 배경: `docs/PROJECT_REQUIREMENTS.md`.
  - DB 구조: `docs/sql/postgresql_schema_draft.sql` 및 **3-1)**. 로컬에서 사람 데이터가 필요하면 `docs/sql/seed_test_users.sql` (`docs/ENVIRONMENT_SETUP.md` 5-1절).
  - Java는 `backend/`, 실시간은 `realtime/`(Express 없이 `http`+Socket.io), 데모 UI 소스는 `frontend/`(로컬에서는 `bootRun` 시 8080에서 위 3개 파일만 서빙, **`/**` 전체를 정적으로 열지 않음** — `/api/**` 가 리소스 핸들러에 먹히는 404 방지).
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

## 3-0) 최신 운영 메모 (2026-03-27)
- DM 생성 실패 이슈 대응:
  - 증상: 프론트에서 `DM 생성 실패` 알림 노출
  - 원인: 일부 로컬 DB에서 `channels.channel_type` CHECK 제약이 `PUBLIC/PRIVATE`만 허용
  - 조치: `DataInitializer`가 부팅 시 `channel_type` 관련 CHECK 제약을 탐지/교체하여 `PUBLIC/PRIVATE/DM`으로 강제 정합
  - 수동 보정: `docs/sql/migrate_channels_allow_dm_type.sql`
- 점검 포인트:
  - 서버 재기동 후 DM 생성 API(`POST /api/channels`, `channelType=DM`) 정상 여부 확인
  - 기존 DB에 커스텀 제약명이 있어도 자동 보정 로그가 남는지 확인
- DM 메시지 전송 실패(소켓 경로) 대응:
  - 증상: DM 채널 진입은 되지만 메시지 전송이 실패하는 케이스 존재
  - 조치: 프론트 `sendMessage()`에 소켓 ACK 실패/지연(2.5초)·소켓 미연결 시 메시지 API(`POST /api/channels/{channelId}/messages`) 자동 폴백 추가
  - 효과: 실시간 저장 경로 장애/불일치가 있어도 사용자 메시지 전송 자체는 유지
- 사용자 참조 키 전환(employee_no):
  - 조치: 채널/메시지/파일/칸반/업무의 User 연관 `@JoinColumn`을 `users.employee_no` 기준으로 전환
  - 리얼타임: `senderId(users.id)` 입력을 내부에서 `employee_no`로 해석해 멤버십·메시지 저장 수행
  - 운영 SQL: `docs/sql/migrate_user_refs_id_to_employee_no.sql` 적용 후 검증 필요

## 3-1) 데이터베이스 스키마 인수인계 메모
- 현재 기준 DB: PostgreSQL
- 1차 확정 테이블:
- `users`: 사용자 기본 정보/권한/상태(조직도는 `company_name` / `division_name` / `team_name` 및 회사 필터용 `company_code`; 컬럼이 없으면 `migrate_users_add_org_columns.sql`·`migrate_users_company_key.sql` 후 `seed_test_users.sql` 또는 백필 스크립트)
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
  - `POST /api/channels/{channelId}/files/upload?userId=...` (multipart)
  - `POST /api/channels/{channelId}/files` (메타만)
  - `GET /api/channels/{channelId}/files/{fileId}/download?userId=...`
  - `GET /api/channels/{channelId}/files/{fileId}/download-info?userId=...`
- 메시지/스레드:
  - `POST /api/channels/{channelId}/messages`
  - `POST /api/channels/{channelId}/messages/{parentMessageId}/replies`
  - `GET /api/channels/{channelId}/messages/{parentMessageId}/replies`
- 사용자 검색·프로필·조직도:
  - `GET /api/users/search?q=...&department=...`
  - `GET /api/user-directory/organization`
  - `GET /api/users/profile?userId=...`
  - `GET /api/users/{userId}/profile` (호환)
- 칸반:
  - `POST/GET/GET{id}/DELETE /api/kanban/boards`, 컬럼·카드 CRUD, 담당자 추가/삭제, `GET /api/kanban/cards/{cardId}/history`
- 메시지→업무:
  - `POST /api/messages/{messageId}/work-items`, `GET /api/messages/{messageId}/work-items`, `GET /api/work-items/{workItemId}`
- 조직 동기화(관리):
  - `GET /api/admin/org-sync/users?source=TEST|GROUPWARE`, `POST /api/admin/org-sync/users/sync?source=...`
  - `PUT /api/admin/org-sync/users/{employeeNo}/status`
- 오류 로그(관리):
  - `GET /api/admin/error-logs?from=&to=&errorCode=&path=&limit=`
- 감사 로그(관리):
  - `GET /api/admin/audit-logs?from=&to=&actorUserId=&eventType=&resourceType=&workspaceKey=&limit=`
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
- 사용자 검색은 `org_group_members(TEAM)` + `org_groups.display_name`(팀 표시명)을 사용해 부서 필터를 지원합니다.
- `GET /api/user-directory/organization-filters`는 `org_groups(COMPANY, is_active=true)` 기반으로 셀렉트 옵션(`label`, `companyGroupCode`)을 줍니다(첫 항목 전체이며 `companyGroupCode=null`).
- `GET /api/user-directory/organization?companyGroupCode=`로 선택 회사 트리만 내려보내며, 전체 옵션이면 쿼리 파라미터를 생략하여 전체 트리를 반환합니다.
- 검색 키워드(`q`)는 이름/이메일/사번에 대해 부분 일치 조회를 수행합니다.
- Presence는 Realtime 서버 메모리 기반으로 관리됩니다.
- 소켓별로 사용자를 추적하며, 해당 사용자의 **모든** 소켓이 끊기면 OFFLINE 브로드캐스트 후 메모리에서 제거합니다(유령 userId 누적 방지).
- 운영 환경에서 다중 인스턴스 구성 시 Presence 저장소(예: Redis) 공유가 필요합니다.

### 테스트 조직 연동(그룹웨어 전환 대비) 인수인계 메모
- 현재 조직 동기화 인터페이스는 `OrgUserProvider` 기반이며 `TEST` 제공자만 구현되어 있습니다.
- `POST /api/admin/org-sync/users/sync?source=TEST`는 테스트 조직 데이터를 `users`에 UPSERT합니다.
- `GROUPWARE` 소스는 의도적으로 미구현 예외를 반환합니다. 실제 연동 시 `GROUPWARE` 제공자 클래스만 추가하면 API는 유지됩니다.
- 계정 활성/비활성은 `PUT /api/admin/org-sync/users/{employeeNo}/status`로 사번 기준 반영합니다.

### 인증(JWT) 인수인계 메모
- **방식**: JWT Stateless 인증. 로그인 성공 시 액세스 토큰(기본 8시간) 발급.
- **엔드포인트**
  - `POST /api/auth/login` — loginId(사원번호 또는 이메일) + password → JWT + 사용자 정보
  - `GET /api/auth/me` — JWT 검증 후 현재 사용자 반환
- **테스트 계정 기본 비밀번호**: `Test1234!` (서버 기동 시 `DataInitializer`가 자동 설정)
- **확장 포인트**: `AuthProvider` 인터페이스를 구현한 `GroupwareAuthProvider`를 추가하면 그룹웨어 SSO/OAuth2 전환 가능. `AuthService`는 등록된 Provider를 순서대로 시도.
- **보안 주의**:
  - `JWT_SECRET` 환경변수는 운영에서 반드시 32자 이상 무작위 값으로 교체 (`openssl rand -base64 32`)
  - `DataInitializer`는 그룹웨어 연동 전환 후 비활성화 또는 삭제 권장
  - 현재 리프레시 토큰 미구현. 필요 시 Phase 4에서 추가.
- **구현 파일**:
  - `backend/src/main/java/com/ech/backend/common/security/` (SecurityConfig, JwtUtil, JwtAuthFilter, UserPrincipal)
  - `backend/src/main/java/com/ech/backend/api/auth/` (AuthProvider, TestAuthProvider, AuthService, AuthController, dto/)
  - `backend/src/main/java/com/ech/backend/api/init/DataInitializer.java`

### RBAC 인수인계 메모
- 현재 RBAC은 `X-User-Role` 헤더 기반 임시 모델입니다 (`MEMBER|MANAGER|ADMIN`).
- 권한 체크는 `@RequireRole` + `RoleGuardInterceptor`로 수행됩니다.
- 자세한 권한 표는 `docs/RBAC_MATRIX.md`를 기준으로 운영합니다.
- 인증 도입 후에는 헤더가 아닌 토큰/세션 클레임으로 역할 판정 로직을 치환해야 합니다.

### 오류 로그 인수인계 메모
- 전역 예외는 `error_logs` 테이블에 적재됩니다.
- 저장 목적은 운영 추적이며, 메시지 본문/파일 원문/토큰 등 민감 데이터는 저장하지 않습니다.
- 조회 API는 `ADMIN` 전용이며 기간/코드/경로 기준 필터가 가능합니다.

### 통합 검색 인수인계 메모
- **API**: `GET /api/search?q={keyword}&type={SearchType}&limit={1~50}` (JWT 인증 필요)
- **검색 범위**:
  - MESSAGES: 본인이 속한 채널의 메시지 본문 (아카이브/삭제 제외)
  - FILES: 본인이 속한 채널의 파일명
  - WORK_ITEMS: 업무 제목/설명 (워크스페이스 전체)
  - KANBAN_CARDS: 칸반 카드 제목/설명 (워크스페이스 전체)
- **성능**: `docs/sql/postgresql_schema_draft.sql`의 `CREATE EXTENSION pg_trgm` + GIN 인덱스 적용 권장
- **한계**: 현재 ILIKE 기반, 대규모 데이터셋에서는 전용 검색엔진(Elasticsearch 등) 도입 고려
- **구현 파일**: `backend/.../api/search/` (SearchService, SearchController, dto/)

### 관리자 배포 관리 인수인계 메모
- **목적**: 백엔드 WAR/JAR 파일을 웹 UI에서 업로드하고, 버전 전환(활성화) 및 롤백을 관리한다.
- **파일 저장 위치**: `APP_RELEASES_DIR` 환경 변수(기본 `./releases`). 실제 배포 시 절대 경로 지정 권장.
- **`releases/current-version.txt`**: 현재 활성 버전 문자열이 기록됨. 외부 배포 스크립트가 참조해 서비스 재시작 가능.
- **활성화 흐름**: 기존 ACTIVE → PREVIOUS 전환 → 신규 ACTIVE 지정 → 이력 기록 → 감사 로그 기록
- **롤백 흐름**: 가장 최근의 PREVIOUS 버전을 ACTIVE로 승격 → 이전 ACTIVE는 PREVIOUS로 이동
- **삭제 제약**: ACTIVE/PREVIOUS 상태는 삭제 불가. UPLOADED/DEPRECATED만 삭제 가능.
- **멀티파트 크기**: 기본 500MB (`MAX_UPLOAD_SIZE` 환경 변수로 조정).
- **API 목록** (모두 ADMIN 권한 필요):
  - `POST /api/admin/releases` — 업로드 (multipart/form-data)
  - `GET /api/admin/releases` — 전체 목록
  - `GET /api/admin/releases/{id}` — 단건 조회
  - `POST /api/admin/releases/{id}/activate` — 활성화
  - `POST /api/admin/releases/rollback` — 롤백
  - `GET /api/admin/releases/history` — 배포 이력
  - `DELETE /api/admin/releases/{id}` — 삭제
- **구현 파일**:
  - `backend/.../domain/release/` (ReleaseVersion, DeploymentHistory, 리포지토리, Enum)
  - `backend/.../api/release/` (ReleaseService, ReleaseController, dto/)

### 보존 정책 및 아카이빙 인수인계 메모
- **목적**: 오래된 데이터를 자원 유형별로 정의된 보존 기간에 따라 자동 처리해 DB 비대화를 방지한다.
- **자원 유형**: `MESSAGES`(소프트 아카이브), `AUDIT_LOGS`(물리 삭제), `ERROR_LOGS`(물리 삭제)
- **기본 정책**: 서버 기동 시 `DataInitializer`가 자동 시드 (모두 비활성 상태로 생성)
  - MESSAGES: 365일 / AUDIT_LOGS: 180일 / ERROR_LOGS: 90일
- **스케줄러**: 매일 02:00 `ArchivingScheduler`가 활성 정책을 순서대로 실행
- **수동 트리거**: `POST /api/admin/retention-policies/trigger` (전체) 또는 `/trigger/{resourceType}` (단일)
- **API 목록**:
  - `GET /api/admin/retention-policies` — 전체 정책 조회
  - `PUT /api/admin/retention-policies/{resourceType}` — 정책 수정 (일수·활성여부·설명)
  - `POST /api/admin/retention-policies/trigger` — 전체 수동 실행
  - `POST /api/admin/retention-policies/trigger/{resourceType}` — 단일 수동 실행
- **메시지 아카이빙**: `messages.archived_at` 컬럼 설정 (소프트). 스레드 답글 조회 시 아카이브된 메시지 자동 제외.
- **구현 파일**:
  - `backend/.../domain/retention/` (RetentionPolicy, RetentionPolicyRepository, RetentionResourceType)
  - `backend/.../api/retention/` (RetentionPolicyService, ArchivingScheduler, RetentionPolicyController, dto/)
  - `backend/.../common/config/SchedulingConfig.java`

### 감사 이벤트 로그 인수인계 메모
- 채널·메시지·파일·업무·칸반 도메인의 주요 이벤트가 `audit_logs` 테이블에 기록됩니다.
- 이벤트 유형: `CHANNEL_CREATED`, `CHANNEL_JOINED`, `MESSAGE_SENT`, `MESSAGE_REPLY_SENT`, `FILE_UPLOADED`, `FILE_DOWNLOAD_INFO_ACCESSED`, `WORK_ITEM_CREATED`, `KANBAN_BOARD_CREATED`, `KANBAN_CARD_CREATED` 등
- **대화 본문·파일 원문은 절대 기록하지 않습니다.** `detail` 필드는 채널명·리소스 ID 등 메타만 저장하며 최대 500자입니다.
- `AuditLogService.safeRecord()` 호출 방식이므로 감사 로그 저장 실패가 비즈니스 응답에 영향을 주지 않습니다.
- 조회 API: `GET /api/admin/audit-logs` (`ADMIN` 전용, 기간/행위자/이벤트유형/리소스유형/워크스페이스/한도 필터)
- 향후 `OrgSyncService`, 배포 관리 등에도 적용 범위 확장 가능합니다.

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
- 동료 **프로필 모달**(`modalUserProfile`): 역할·계정 상태는 표시하지 않음. **직급**(`jobLevel`)은 항상 행(없으면 `-`), **직위**(`jobPosition`)·**직책**(`jobTitle`)은 값이 있을 때만 `profileJobPositionDt`/`profileModalJobPosition`, `profileJobTitleDt`/`profileModalJobTitle` 행 표시. **DM 보내기**(`btnProfileDm`)는 `startDmWithUser`로 `POST /api/channels`에 `channelType: DM`과 `dmPeerUserIds: [상대 userId]`를 한 번에 보내 서버가 내부 이름·멤버십을 처리한 뒤 `selectChannel`로 전환(자기 자신이면 버튼 비활성). DB `channels.channel_type`은 `DM` 문자열로 저장됩니다.
- **프레즌스**: 채팅 메시지(`.msg-avatar-wrap`)·멤버 패널(`.member-avatar-wrap`)에서 점을 아바타 사각형 **우측 하단**에 겹쳐 표시(`refreshPresenceDots`가 `[data-presence-user]` 갱신).
- `frontend/app.js`는 수신 메시지 DOM을 최대 200개로 유지해 브라우저 메모리·렌더 비용이 무한 증가하지 않도록 합니다.
- 채팅 시각 표시: **동일 발신자·동일 분(로컬 캘린더 분)** 묶음에서는 **그 분의 마지막 메시지 줄에만** 시각을 붙이고, **분이 바뀌면** 각 메시지 줄에 시각을 붙인다(`minuteKey` / `renderMessages` / `appendMessageRealtime`). 시각은 **24시간제 `HH:mm`**이며 본문 바로 뒤에 약간 띄워 인라인으로 붙인다(`fmtTime`, `.msg-content-row`).

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

## 8) 로컬·개발 환경 서버 재시작 (ECH 구성요소)

### 구성요소·포트
| 구성요소 | 포트 | 역할 |
|----------|------|------|
| PostgreSQL | 5432 | DB (백엔드·리얼타임 공통) |
| 백엔드 (Spring Boot, 내장 Tomcat) | 8080 | REST API, 정적 프론트(`../frontend` 의 `index.html`·`styles.css`·`app.js` 만 — `FrontendResourceConfig`) |
| 리얼타임 (Node.js + Socket.io) | 3001 | 실시간 메시지 저장·브로드캐스트 (`realtime/src/db.js` → PostgreSQL) |

### 권장 기동 순서
1. **PostgreSQL** — DB가 먼저 떠 있어야 함.
2. **백엔드** — API·로그인·채널 등.
3. **리얼타임** — 소켓 연결·`message:send` 처리.

### 권장 중지 순서 (역순)
1. **리얼타임** — 소켓·DB 풀 정리.
2. **백엔드** — Tomcat 종료.
3. **PostgreSQL** — 일상 개발에서는 **재시작하지 않아도 됨**. DB 스키마/복구가 필요할 때만 Windows 서비스에서 재시작.

### 수동 재시작 (PowerShell 예시)

**백엔드만** (실행 중 터미널에서 `Ctrl+C` 후):

```powershell
Set-Location "D:\Enterprise Collaboration Hub\Enterprise-Collaboration-Hub-ECH-\backend"
.\gradlew.bat bootRun --no-daemon
```

**리얼타임만**:

```powershell
Set-Location "D:\Enterprise Collaboration Hub\Enterprise-Collaboration-Hub-ECH-\realtime"
node src/server.js
```

**PostgreSQL** (서비스 이름은 설치본에 따라 다름, 관리자 권한 필요할 수 있음):

```powershell
Restart-Service postgresql-x64-16
# 또는 services.msc 에서 PostgreSQL 서비스 재시작
```

**포트 점유 PID 확인 후 강제 종료** (백엔드·리얼타임이 응답 없을 때):

```powershell
netstat -ano | findstr ":8080"
netstat -ano | findstr ":3001"
Stop-Process -Id <PID> -Force
```

### 접속 확인
- 웹 UI: `http://localhost:8080/` 또는 `http://localhost:8080/index.html`
- 리얼타임 헬스: `GET http://localhost:3001/health` (JSON, DB 풀 상태 포함)

### 비고
- 프론트는 별도 `npm` 서버 없이 백엔드가 정적 파일을 일부 서빙함 (`spring.web.resources.add-mappings: false`, `FrontendResourceConfig` — `/api/**` 가 정적 `/**` 에 먹히지 않도록).
- 리얼타임 코드(`db.js`, `server.js`) 변경 후에는 **Node 프로세스 재시작**이 필요함.
- 백엔드만 재시작해도 채팅은 동작하지만, **소켓·실시간 반영**을 쓰려면 리얼타임도 함께 기동되어 있어야 함.
