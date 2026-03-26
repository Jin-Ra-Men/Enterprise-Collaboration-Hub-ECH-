# ECH 기능 명세서

이 문서는 구현된 기능의 동작 기준을 상세히 기록합니다.  
신규 개발/수정 시 해당 기능 항목을 반드시 갱신합니다.

## 작성 원칙
- 기능 단위로 섹션을 분리합니다.
- "무엇을, 왜, 어떻게" 관점으로 작성합니다.
- 문서를 읽은 개발자가 코드 수정 가능할 수준으로 상세히 작성합니다.

## 기능 템플릿

### 기능명
- 목적:
- 사용자:
- 관련 화면/경로:
- 관련 API:
- 관련 Socket 이벤트:
- 입력/출력:
- 상태 전이/예외 케이스:
- 권한/보안:
- 로그/감사 포인트:
- 테스트 기준:
- 비고:

---

## 공통 응답/에러 처리
- 목적: API 응답 포맷을 일관화하고 예외를 표준 코드로 처리
- 사용자: Backend 개발자, API 연동 개발자
- 관련 화면/경로: Backend 전역
- 관련 API: 전 API 공통 (`ApiResponse`)
- 관련 Socket 이벤트: 해당 없음
- 입력/출력:
  - 성공: `success=true`, `data` 포함
  - 실패: `success=false`, `error.code`, `error.message` 포함
- 상태 전이/예외 케이스:
  - `IllegalArgumentException` -> `BAD_REQUEST`
  - `MethodArgumentNotValidException` -> `VALIDATION_ERROR`
  - 그 외 예외 -> `INTERNAL_SERVER_ERROR`
- 권한/보안: 예외 메시지에 내부 구현 세부정보 노출 금지
- 로그/감사 포인트: 서버 공통 예외 로그 기준에 따라 추후 확장
- 테스트 기준:
  - 정상 응답 포맷 검증
  - 검증 실패 시 오류 코드/메시지 검증
  - 내부 오류 시 표준 오류 코드 검증
- 비고: `HealthController`가 공통 포맷 적용 기준 예시

---

## PostgreSQL 스키마 초안 (사용자/채널/메시지)
- 목적: 채널 기반 협업 서비스의 핵심 데이터 구조를 초기 확정
- 사용자: Backend 개발자, DBA, API 연동 개발자
- 관련 화면/경로: 전체 메시징 기능 공통
- 관련 API: 채널/메시지 CRUD, 멤버십 관리 API
- 관련 Socket 이벤트: `channel:join`, `message:send`, `message:new`
- 입력/출력:
  - 사용자(`users`), 채널(`channels`), 채널 멤버(`channel_members`), 메시지(`messages`) 저장
  - 메시지 스레드용 `parent_message_id` 지원
  - 채널별 읽음 포인터(`channel_read_states`, `(channel_id, user_id)` 유니크)
  - 채널 첨부 메타데이터(`channel_files`, 실제 바이너리는 외부 스토리지)
  - 칸반(`kanban_boards`, `kanban_columns`, `kanban_cards`, `kanban_card_assignees`, `kanban_card_events`)
  - 업무(`work_items`, 메시지 기반 생성 시 `source_message_id` 유니크)
- 상태 전이/예외 케이스:
  - 채널 삭제 시 멤버/메시지 연쇄 삭제(`ON DELETE CASCADE`)
  - 스레드 부모 삭제 시 자식 메시지는 부모 참조 해제(`ON DELETE SET NULL`)
- 권한/보안:
  - 사용자 식별자는 내부 PK + 사번(`employee_no`)을 병행 관리
  - 역할/상태 컬럼으로 계정 제어 확장 가능
- 로그/감사 포인트:
  - `created_at`, `updated_at` 기준으로 감사 확장 가능
- 테스트 기준:
  - 유니크 제약(`email`, `employee_no`, `workspace_key + name`) 검증
  - FK 무결성/삭제 정책 검증
  - 채널별 최신 메시지 조회 인덱스 성능 점검
- 비고:
  - 스키마 파일: `docs/sql/postgresql_schema_draft.sql`

---

## 채널 도메인 API 1차 (생성/조회/참여)
- 목적: 채널 협업의 핵심 흐름(채널 개설, 조회, 멤버 참여)을 서버 API로 제공
- 사용자: Backend 개발자, Frontend 개발자
- 관련 화면/경로: 채널 목록/채널 상세/채널 참여 UI
- 관련 API:
  - `POST /api/channels` (채널 생성)
  - `GET /api/channels/{channelId}` (채널 상세 조회)
  - `POST /api/channels/{channelId}/members` (채널 참여)
- 관련 Socket 이벤트: 추후 `channel:join`과 연계 예정
- 입력/출력:
  - 생성 입력: `workspaceKey`, `name`, `description`, `channelType`(`PUBLIC`|`PRIVATE`|`DM`), `createdByUserId`, 선택 `dmPeerUserIds`(DM일 때 상대·그룹 참가자 userId — 서버가 내부 고유 `name`(`__dm__…`)과 표시용 `description`을 구성하고, 동일 참가자 조합이면 기존 채널 반환 후 누락 멤버만 추가)
  - 참여 입력: `userId`, `memberRole`
  - 출력: 채널 기본 정보 + 멤버 목록(`members`: `userId`, `name`, `department`, `jobRank`, `dutyTitle`, `memberRole`, `joinedAt`)
- 상태 전이/예외 케이스:
  - 중복 채널명(`workspaceKey + name`) 생성 시 예외
  - 없는 사용자/채널 조회 시 예외
  - 이미 참여한 사용자 재참여 시 예외
  - 생성 시 생성자는 자동으로 `MANAGER` 멤버십 부여
- 권한/보안:
  - 현재는 기본 도메인 동작 중심 (인증/인가 고도화는 다음 단계)
- 로그/감사 포인트:
  - 생성/참여 시각, 멤버 권한, 생성자 기준으로 감사 확장 가능
- 테스트 기준:
  - 채널 생성 성공/중복 실패
  - 채널 상세 조회 성공/없는 채널 실패
  - 채널 참여 성공/중복 참여 실패
- 비고:
  - 구현 파일:
    - `backend/src/main/java/com/ech/backend/api/channel/ChannelController.java`
    - `backend/src/main/java/com/ech/backend/api/channel/ChannelService.java`
    - `backend/src/main/java/com/ech/backend/domain/channel/*`

---

## 실시간 메시지 저장 연계 (Socket + PostgreSQL)
- 목적: `message:send` 요청을 DB에 먼저 저장한 뒤 저장 성공 데이터만 브로드캐스트
- 사용자: Frontend 개발자, Realtime/Backend 개발자
- 관련 화면/경로: 채널 메시지 입력/수신 UI
- 관련 API: 해당 없음 (소켓 중심)
- 관련 Socket 이벤트:
  - 입력: `message:send`
  - 출력: `message:new`, `message:error`
  - 보조: `channel:join`
- 입력/출력:
  - 입력 payload: `channelId`, `senderId`, `text`
  - 저장 성공 출력: `messageId`, `channelId`, `senderId`, `text`, `createdAt`
  - 저장 실패 출력: `code=DB_SAVE_FAILED` 또는 `code=NOT_CHANNEL_MEMBER` 오류 이벤트
- 상태 전이/예외 케이스:
  - payload 유효성 실패 -> `message:error` (`INVALID_PAYLOAD`)
  - 본문 길이 초과 -> `message:error` (`MESSAGE_TOO_LARGE`, 기본 상한 `MAX_MESSAGE_BODY_LENGTH=4000`, Backend 메시지 API `@Size(max=4000)`과 정합)
  - `channel_members`에 없는 사용자가 전송 -> `message:error` (`NOT_CHANNEL_MEMBER`, INSERT 전 검사)
  - DB 저장 실패 -> `message:error` (`DB_SAVE_FAILED`)
  - 저장 성공한 경우에만 `message:new` 브로드캐스트
- 성능·메모리:
  - Socket.io `maxHttpBufferSize`로 비정상적으로 큰 패킷 완충 완화
  - `pg` Pool: `max`/`idleTimeoutMillis`/`connectionTimeoutMillis` 환경변수로 조정 가능
- 권한/보안:
  - 리얼타임 서버는 INSERT 전 `channel_members(channel_id, user_id)` 존재 여부로 발신자가 해당 채널 멤버인지 검사함 (직접 SQL로 넣은 메시지는 멤버가 아닌 발신자도 표시될 수 있음 — `docs/sql/cleanup_dev_messages.sql` 참고)
- 로그/감사 포인트:
  - `messages` 테이블의 `created_at` 기반 메시지 생성 이력 추적 가능
- 테스트 기준:
  - 정상 저장 후 브로드캐스트 이벤트 확인
  - 잘못된 payload 시 오류 이벤트 확인
  - DB 장애 시 오류 이벤트/브로드캐스트 미발생 확인
- 비고:
  - 구현 파일:
    - `realtime/src/db.js`
    - `realtime/src/server.js`
    - `realtime/package.json` (`pg` 의존성 추가)

---

## 실행 환경 안정화 (Phase 1-1)
- 목적: 로컬 개발 환경에서 Backend/Realtime/Frontend를 일관된 기준으로 실행 가능하도록 표준화
- 사용자: 모든 개발자
- 관련 화면/경로: 개발 환경 공통
- 관련 API: 해당 없음
- 관련 Socket 이벤트: 해당 없음
- 입력/출력:
  - 입력: Java/Node/PostgreSQL 설치 및 환경변수 설정
  - 출력: `gradlew.bat` 실행 가능, `npm install` 완료, 표준 환경 문서 확보
- 상태 전이/예외 케이스:
  - Java 8 사용 시 Spring Boot 3.x 빌드 실패 가능
  - 시스템 Gradle 부재 시 wrapper 생성/사용 필요
- 권한/보안:
  - 개발 환경 설정은 사용자 로컬 계정 기준으로 적용
- 로그/감사 포인트:
  - 환경 이슈는 `.cursor/rules/ERRORS.md`에 기록
- 테스트 기준:
  - `java -version` 17+ 확인
  - `backend/gradlew.bat -p backend test --dry-run` 성공
  - `realtime/npm install` 성공
- 비고:
  - 환경 문서: `docs/ENVIRONMENT_SETUP.md`

---

## 관리자 버전 업그레이드 관리 (초안)
- 목적: 관리자 페이지에서 신규 버전 배포와 롤백을 안전하게 수행
- 사용자: `Admin`
- 관련 화면/경로: 관리자 > 배포/버전 관리
- 관련 API(예정):
  - `POST /api/admin/releases` (WAR 등 배포 파일 업로드)
  - `GET /api/admin/releases` (릴리즈 목록 조회)
  - `POST /api/admin/releases/{releaseId}/activate` (버전 활성화)
  - `POST /api/admin/releases/{releaseId}/rollback` (롤백 실행)
- 관련 Socket 이벤트: 해당 없음 (관리자 작업 로그는 추후 이벤트 알림 검토)
- 입력/출력:
  - 입력: 파일, 버전명, 릴리즈 노트, 작업자 정보
  - 출력: 릴리즈 ID, 상태(UPLOADED/ACTIVE/ROLLED_BACK), 처리 시각
- 상태 전이/예외 케이스:
  - 비정상 파일 형식 업로드 차단
  - 활성 버전 전환 실패 시 자동 롤백 또는 수동 롤백 안내
  - 동일 버전 중복 등록 방지
- 권한/보안:
  - `Admin` 전용 권한
  - 업로드 파일 무결성 검증(해시/서명) 및 크기 제한
- 로그/감사 포인트:
  - 업로드/활성화/롤백 행위에 대한 감사 로그 필수
- 테스트 기준:
  - 정상 업로드/활성화/롤백 시나리오
  - 권한 없는 사용자 접근 차단
  - 실패 후 복구/이력 정확성 검증
- 비고:
  - 실제 배포 방식(무중단/순차 재기동)은 인프라 정책과 연계해 확정

---

## 스레드 답글 기능 (부모/자식 메시지)
- 목적: 채널 메시지에 대한 문맥형 답글(스레드) 생성/조회 지원
- 사용자: Member, Manager, Admin
- 관련 화면/경로: 채널 메시지 목록, 스레드 패널
- 관련 API:
  - `POST /api/channels/{channelId}/messages` (부모 메시지 생성)
  - `POST /api/channels/{channelId}/messages/{parentMessageId}/replies` (답글 생성)
  - `GET /api/channels/{channelId}/messages/{parentMessageId}/replies` (스레드 조회)
- 관련 Socket 이벤트:
  - 현재 API 중심 구현, 추후 소켓 이벤트와 동기화 확장 예정
- 입력/출력:
  - 입력: `senderId`, `text`
  - 출력: `messageId`, `channelId`, `senderId`, `parentMessageId`, `text`, `createdAt`
- 상태 전이/예외 케이스:
  - 없는 채널/사용자/부모 메시지 요청 시 예외
  - 부모 메시지와 요청 채널 불일치 시 예외
  - 스레드 조회는 `createdAt ASC` 정렬
- 권한/보안:
  - 현재는 도메인 검증 중심, 채널 멤버 기반 권한 검증은 RBAC 단계에서 연계
- 로그/감사 포인트:
  - 답글 생성 시 부모 메시지 연결 관계(`parent_message_id`) 추적 가능
- 테스트 기준:
  - 부모 메시지 생성 후 답글 생성/조회 성공
  - 채널 불일치/없는 부모 메시지 요청 실패
  - 인덱스(`idx_messages_parent_message_id`) 기반 스레드 조회 성능 점검
- 비고:
  - 구현 파일:
    - `backend/src/main/java/com/ech/backend/api/message/MessageController.java`
    - `backend/src/main/java/com/ech/backend/api/message/MessageService.java`
    - `backend/src/main/java/com/ech/backend/domain/message/*`

---

## 채널별 읽음 포인터 (마지막 읽은 메시지)
- 목적: 채널 단위로 사용자가 마지막으로 읽은 메시지를 저장해 미읽음/스크롤 복귀 등 UX에 활용
- 사용자: 채널 멤버
- 관련 화면/경로: 채널 메시지 목록, 사이드바 미읽음 배지(추후)
- 관련 API:
  - `GET /api/channels/{channelId}/read-state?userId=...` (조회, 미설정 시 `lastReadMessageId=null`)
  - `PUT /api/channels/{channelId}/read-state` (갱신, body: `userId`, `lastReadMessageId`)
- 관련 Socket 이벤트: 해당 없음 (추후 `read:update` 브로드캐스트 검토)
- 입력/출력:
  - 갱신 입력: `userId`, `lastReadMessageId`(해당 채널 소속 메시지 ID)
  - 출력: `channelId`, `userId`, `lastReadMessageId`, `updatedAt` (`updatedAt`은 미설정 조회 시 null)
- 상태 전이/예외 케이스:
  - 채널 멤버가 아니면 조회/갱신 불가
  - 다른 채널의 메시지 ID로 갱신 시 예외
  - 없는 채널/사용자/메시지 요청 시 예외
- 권한/보안:
  - 멤버십 검증만 수행, 인증 세션의 `userId`와 요청 `userId` 일치 검증은 RBAC 단계에서 보완
- 로그/감사 포인트:
  - `channel_read_states.updated_at` 기준 갱신 이력 추적 가능
- 테스트 기준:
  - 멤버 사용자 읽음 갱신 후 조회 값 일치
  - 비멤버/타 채널 메시지 ID 요청 실패
- 비고:
  - 스키마: `docs/sql/postgresql_schema_draft.sql` (`channel_read_states`)
  - 구현 파일:
    - `backend/src/main/java/com/ech/backend/api/channel/ChannelReadStateController.java`
    - `backend/src/main/java/com/ech/backend/api/channel/ChannelReadStateService.java`
    - `backend/src/main/java/com/ech/backend/domain/channel/ChannelReadState.java`
    - `backend/src/main/java/com/ech/backend/domain/channel/ChannelReadStateRepository.java`

---

## 칸반 보드 1차 (보드/컬럼/카드 CRUD·담당·이력)
- 목적: 워크스페이스 단위 칸반 보드에서 컬럼·카드를 관리하고, 담당자 지정·상태·컬럼 이동 이력을 남김
- 사용자: 협업 사용자(보드 수준 RBAC는 추후)
- 관련 화면/경로: 보드 뷰, 카드 상세, 담당자 배지
- 관련 API:
  - `POST /api/kanban/boards` — 보드 생성 (`workspaceKey`, `name`, `description`, `createdByUserId`)
  - `GET /api/kanban/boards?workspaceKey=default` — 보드 목록(최대 100건)
  - `GET /api/kanban/boards/{boardId}` — 보드 상세(컬럼·카드·담당자 id 목록)
  - `DELETE /api/kanban/boards/{boardId}`
  - `POST /api/kanban/boards/{boardId}/columns` — 컬럼 추가 (`name`, `sortOrder` 선택)
  - `PUT /api/kanban/boards/{boardId}/columns/{columnId}` — 이름·정렬 (`actorUserId`)
  - `DELETE /api/kanban/boards/{boardId}/columns/{columnId}` — 컬럼 및 소속 카드 연쇄 삭제
  - `POST /api/kanban/boards/{boardId}/columns/{columnId}/cards` — 카드 생성 (`actorUserId`, `title`, `description`, `sortOrder`, `status`)
  - `PUT /api/kanban/cards/{cardId}` — 제목·설명·정렬·`status`·`columnId`(이동) 부분 갱신 (`actorUserId`)
  - `DELETE /api/kanban/cards/{cardId}`
  - `POST /api/kanban/cards/{cardId}/assignees` — 담당 추가 (`actorUserId`, `assigneeUserId`)
  - `DELETE /api/kanban/cards/{cardId}/assignees/{assigneeUserId}?actorUserId=...`
  - `GET /api/kanban/cards/{cardId}/history?limit=` — 이벤트 이력(기본 50, 최대 100)
- 관련 Socket 이벤트: 해당 없음
- 입력/출력:
  - 카드: `status` 문자열(기본 `OPEN`), `assigneeUserIds` 배열
  - 이력: `eventType`(`CARD_CREATED`, `COLUMN_MOVED`, `STATUS_CHANGED`, `ASSIGNEE_ADDED`, `ASSIGNEE_REMOVED`), `fromRef`/`toRef`
- 상태 전이/예외 케이스:
  - 동일 `(workspaceKey, name)` 보드 중복 생성 불가
  - 카드 컬럼 이동은 **같은 보드** 내 컬럼만 허용
  - 담당 중복 추가 불가
- 권한/보안:
  - `actorUserId`는 존재 여부만 검증(인증 연동·보드 멤버 검사는 RBAC 단계)
- 성능:
  - 보드 목록 100건 상한, 카드 이력 조회 100건 상한
  - 보드 상세는 카드·담당자를 `join fetch`로 한 번에 로드해 N+1 완화
- 테스트 기준:
  - 보드 생성 후 컬럼·카드 CRUD, 컬럼 이동·상태 변경 시 이력 행 생성
  - 담당 추가/제거 이력 생성
- 비고:
  - 스키마: `docs/sql/postgresql_schema_draft.sql`
  - 구현: `backend/.../api/kanban/*`, `backend/.../domain/kanban/*`

---

## 메시지 기반 업무 항목(채팅 → 업무 연계)
- 목적: 특정 채팅 메시지에서 업무 항목을 만들고, 메시지와 업무를 안정적으로 연결
- 사용자: 채널 멤버
- 관련 화면/경로: 메시지 액션 메뉴, 업무 상세
- 관련 API:
  - `POST /api/messages/{messageId}/work-items` (생성, body: `createdByUserId`, 선택 `title`, `description`, `status`)
  - `GET /api/messages/{messageId}/work-items` (0~1건 리스트, 메시지당 최대 1건)
  - `GET /api/work-items/{workItemId}` (단건 조회)
- 관련 Socket 이벤트: 해당 없음
- 입력/출력:
  - `title`/`description` 생략 시 메시지 본문에서 기본값(제목: 첫 줄 최대 80자, 설명: 본문 최대 4000자)
  - 응답에 `sourceMessageId`, `sourceChannelId`, `createdByUserId` 포함
- 메시지 ↔ 업무 링크 구조(2-5-2):
  - **정방향**: `work_items.source_message_id` → `messages.id` (유니크, 메시지당 업무 1건)
  - **역방향**: `messages` 테이블에 역참조 컬럼 없음. `GET /api/messages/{id}/work-items` 또는 `work_items` 조회로 연결 확인
  - 메시지 삭제 시 FK `ON DELETE SET NULL`로 업무는 유지되며 `source_message_id`만 비움, `source_channel_id`·`created_by`로 맥락 유지
- 상태 전이/예외 케이스:
  - 없는 메시지, 비멤버 생성 시도, 동일 메시지로 중복 생성 시 예외
- 권한/보안:
  - 채널 멤버십만 검증, 인증 세션과 `createdByUserId` 정합은 RBAC 단계에서 보완
- 테스트 기준:
  - 생성 후 조회·목록 일치, 중복 생성 실패, 메시지 삭제 후 업무 단건 조회 가능(`sourceMessageId` null)
- 비고:
  - 스키마: `docs/sql/postgresql_schema_draft.sql` (`work_items`)
  - 구현: `backend/.../api/work/*`, `backend/.../domain/work/*`

---

## 조직도/사내 계정 연동 인터페이스 (테스트 조직 우선)
- 목적: 지금은 그룹웨어 미연동 상태에서 테스트 조직으로 기능 검증하고, 이후 실제 그룹웨어 제공자만 교체해서 동일 API를 재사용
- 사용자: Admin
- 관련 화면/경로: 관리자 > 사용자 동기화(예정)
- 관련 API:
  - `GET /api/admin/org-sync/users?source=TEST|GROUPWARE` (동기화 대상 프리뷰)
  - `POST /api/admin/org-sync/users/sync?source=...` (배치 동기화 실행, 현재 `TEST` 소스 동작)
  - `PUT /api/admin/org-sync/users/{employeeNo}/status` (`ACTIVE`/`INACTIVE`)
- 연동 방식 결정(3-1-1):
  - 현재: **수동 배치 트리거 방식** (`POST .../sync`)
  - 확장: 추후 `GROUPWARE` 제공자를 추가해 웹훅(이벤트) 또는 스케줄러 배치로 동일 서비스에 연결
- 계정 활성/비활성 정책(3-1-2):
  - 동기화 시 `status`를 `ACTIVE`/`INACTIVE`로 반영
  - 관리자 API로 사번 단위 상태 변경 지원 (`employeeNo` 기준)
  - 비활성 계정은 로그인/권한 단계(RBAC)에서 접근 제한하도록 연계 예정
- 상태 전이/예외 케이스:
  - `GROUPWARE`는 아직 제공자 미구현이므로 명시적 예외 반환
  - 잘못된 상태값(`ACTIVE`/`INACTIVE` 외) 요청 시 검증 실패
- 권한/보안:
  - 현재 인증 미적용, 추후 관리자 권한(RBAC)으로 API 보호 필요
- 테스트 기준:
  - `TEST` 프리뷰/동기화 결과 개수 일치
  - 동기화 후 `users` 테이블 UPSERT 동작
  - 상태 변경 API 정상/미존재 사번 실패 검증
- 비고:
  - 제공자 인터페이스: `OrgUserProvider`
  - 현재 구현: `TestOrgUserProvider`
  - 추후 구현 대상: `GROUPWARE` 제공자

---

## RBAC 세분화 (API 어노테이션 + 인터셉터)
- 목적: 인증이 완성되기 전에도 API 단 최소 권한 경계를 강제해 관리자/운영 기능 오남용을 방지
- 사용자: Admin, Manager, Member
- 관련 API:
  - 공통: `X-User-Role` 헤더(`MEMBER|MANAGER|ADMIN`)
  - `ADMIN`: `/api/admin/org-sync/**`
  - `MANAGER+`: `GET /api/users/search`, 채널 생성/멤버 추가, 칸반 변경 API
- 설계:
  - `@RequireRole` 어노테이션으로 엔드포인트 최소 역할 선언
  - `RoleGuardInterceptor`가 요청 헤더를 해석하고 계층 비교(`ADMIN >= MANAGER >= MEMBER`)
  - 위반 시 `FORBIDDEN` 에러 응답 반환
- 상태 전이/예외 케이스:
  - 헤더 없음/오타 시 `FORBIDDEN`
  - 최소 권한 미만 역할 요청 시 `FORBIDDEN`
- 권한/보안:
  - 현재는 헤더 기반 임시 모델이며, 인증 연동 시 토큰/세션의 역할 클레임으로 대체 필요
  - body의 `userId`와 인증 주체 정합 검증은 후속 RBAC 고도화 과제
- 테스트 기준:
  - `ADMIN` 헤더로 관리자 API 접근 성공
  - `MANAGER`/`MEMBER` 헤더로 관리자 API 접근 실패
  - `MANAGER` 헤더로 사용자 검색/채널 생성/칸반 변경 성공
- 비고:
  - 매트릭스 문서: `docs/RBAC_MATRIX.md`
  - 구현: `backend/.../common/rbac/*`, `@RequireRole` 적용된 컨트롤러들

---

## 운영 오류 로그 수집 (민감정보 비수집)
- 목적: 프로그램 사용 중 발생하는 예외를 누적해 운영자가 원인 추적 가능하도록 하되, 대화 본문/민감 데이터는 저장하지 않음
- 사용자: Admin
- 관련 API:
  - `GET /api/admin/error-logs?from=&to=&errorCode=&path=&limit=`
- 수집 지점:
  - `GlobalExceptionHandler`에서 `FORBIDDEN`, `BAD_REQUEST`, `VALIDATION_ERROR`, `INTERNAL_SERVER_ERROR` 처리 시 저장
- 저장 필드:
  - `errorCode`, `errorClass`, `message(길이 제한)`, `path`, `httpMethod`, `actorUserId(헤더 기반)`, `requestId`, `createdAt`
- 저장 금지:
  - 메시지/대화 본문, 파일 원문, 토큰/쿠키/비밀번호
- 상태 전이/예외 케이스:
  - 로깅 실패는 응답 흐름을 깨지 않도록 무시(`safeLog`)
- 권한/보안:
  - 조회 API는 `ADMIN` 권한 필요
- 테스트 기준:
  - 잘못된 요청 유발 시 `error_logs` 적재 확인
  - 기간/에러코드/경로 필터 조회 확인
- 비고:
  - 스키마: `docs/sql/postgresql_schema_draft.sql` (`error_logs`)
  - 구현: `backend/.../api/errorlog/*`, `backend/.../domain/error/*`

---

## 조직도(부서) 기반 사용자 검색
- 목적: 부서/이름/이메일/사번/사용자 ID 기준으로 사용자 검색 제공 및 부서별 조직도에서 멤버 선택
- 사용자: 로그인한 멤버(`MEMBER` 이상)
- 관련 화면/경로: 채널·DM 만들기 및 구성원 추가에서 `+` 팝업(`modalAddMemberPicker`)으로 **상단 회사 셀렉트**(그룹사 공용·코비젼·프리랜서·M365 등) + 좌측 조직도(AXTree 유사 계층) + 우측 검색/결과를 함께 사용, 관리자 사용자 관리(확장 시)
- 관련 API:
  - `GET /api/users/search?q=...&department=...`
  - `GET /api/user-directory/organization-filters` — ACTIVE 사용자의 **`company_key` + `company_name` 고유 조합**마다 셀렉트 옵션(`label`, `companyKey`, `companyName`). 첫 항목은 전체(`companyKey`/`companyName` null). `company_name` 이 비어 있으면 옵션 라벨은 `"{키별 기본문구} (회사명 없음)"` 이고 API에는 `companyName` 빈 문자열로 필터.
  - `GET /api/user-directory/organization?companyKey=&companyName=`(선택) — ACTIVE 사용자를 **회사 → 본부 → 팀(부서)** 3단계 트리로 그룹화. **회사 필터**: `companyKey`/`companyName` 모두 생략 또는 `ORGROOT`면 전체. `companyKey`만 주면 해당 키(및 레거시 null 키는 `GENERAL`과 동일) 전체. `companyName`까지 주면 `users.company_name` 트림 일치 행만(빈 문자열 파라미터는 회사명 null/공백 행만). 셀렉트에서 특정 회사를 고르면 좌측 트리에는 **그 조합에 속한 사용자만** 나와 해당 회사 노드만 보이게 된다. DB에 조직 컬럼이 없으면 `docs/sql/migrate_users_add_org_columns.sql`·`migrate_users_company_key.sql` 적용 후 시드/백필. (구) `/api/users/organization` 은 정적 `/**` 매핑과 겹칠 수 있어 분리.
  - `GET /api/users/profile?userId=` — 동료 프로필(프론트 기본, 이름·사원번호·이메일·부서·직위; 직책(`dutyTitle`)은 값이 있을 때만 모달에 표시. **DM 보내기**로 DM 채널 생성·입장). 응답에 `role`/`status`가 있어도 프로필 모달에는 표시하지 않음
  - `GET /api/users/{userId}/profile` — 동일(경로형, 하위 호환)
- 관련 Socket 이벤트: 해당 없음
- 입력/출력:
  - 검색 입력: `q`(이름/이메일/사번/부서 부분 일치, 숫자만 입력 시 사용자 ID 일치), `department`(정확히 일치하는 부서명으로 추가 필터)
  - 검색 출력: `userId`, `employeeNo`, `name`, `email`, `department`, `jobRank`, `dutyTitle`, `role`, `status`
  - 조직도 출력: `{ companies: [{ companyId, name, divisions: [{ divisionId, name, teams: [{ teamId, name, users: [...] }] }] }] }` (사용자 객체는 검색 API와 동일 필드 위주)
- 상태 전이/예외 케이스:
  - `q`/`department`가 비어 있으면 전체 사용자 또는 부서 필터 기준 조회
- 권한/보안:
  - 현재는 기본 검색 기능 제공, 상세 권한 제한은 RBAC 단계에서 강화
- 로그/감사 포인트:
  - 사용자 검색 행위는 추후 관리자 감사로그 확장 가능
- 테스트 기준:
  - 부서 필터 단독 조회
  - 키워드 단독 조회
  - 부서+키워드 복합 조회
- 비고:
  - 그룹웨어 미연동 시 로컬 테스트 데이터: `docs/sql/seed_test_users.sql`
  - 구현 파일:
    - `backend/src/main/java/com/ech/backend/api/user/UserSearchController.java`
    - `backend/src/main/java/com/ech/backend/api/user/UserSearchService.java`
    - `backend/src/main/java/com/ech/backend/domain/user/UserRepository.java`

---

## 채널 파일 메타데이터 (업로드 기록·다운로드 안내)
- 목적: 실제 파일은 NAS/S3 등 외부 스토리지에 두고, 채널 단위로 메타데이터만 DB에 기록·조회
- 사용자: 채널 멤버
- 관련 화면/경로: 채널 채팅 상단 **첨부 파일** 목록(프론트), 멀티파트 업로드, 다운로드
- 관련 API:
  - `POST /api/channels/{channelId}/files` (메타데이터만 등록, 하위 호환)
  - `POST /api/channels/{channelId}/files/upload?userId=...` (multipart `file` — 디스크 저장 + DB 메타)
  - `GET /api/channels/{channelId}/files?userId=...` (최신순 최대 100건, 응답에 `uploaderName` 포함)
  - `GET /api/channels/{channelId}/files/{fileId}/download?userId=...` (바이너리 스트림, `Authorization: Bearer` 필요)
  - `GET /api/channels/{channelId}/files/{fileId}/download-info?userId=...` (멤버 검증 후 스토리지 키·안내 문구)
- 관련 Socket 이벤트: 해당 없음
- 입력/출력:
  - 등록: `uploadedByUserId`, `originalFilename`, `contentType`, `sizeBytes`(1~512MiB), `storageKey`
  - 목록: 파일 id, `uploaderName`, 원본명, 타입, 크기, `storageKey`, 업로드 시각
  - 디스크 저장 경로(신규 업로드): `channels/{workspaceKey}_ch{channelId}_{채널명슬러그}/yyyy/mm/{uuid}_{원본파일명}` (기존 `channels/{channelId}/...` 키는 DB에 남아 있으면 다운로드 시 그 경로로 조회)
- 상태 전이/예외 케이스:
  - 비멤버 접근 불가
  - 파일명 경로 조각(`..` 등) 제거·검증
- 권한/보안:
  - 멤버십만 검증; 요청 `userId`와 인증 세션 정합은 RBAC 단계에서 보완
- 성능:
  - 목록은 페이지 크기 100으로 상한 고정(대량 조회로 인한 응답·직렬화 부담 완화)
- 테스트 기준:
  - 멤버 등록/목록/다운로드 안내 성공, 비멤버 실패
- 비고:
  - 스키마: `docs/sql/postgresql_schema_draft.sql` (`channel_files`)
  - 구현: `backend/.../api/file/*`, `backend/.../domain/file/*`

---

## 사용자 Presence 확인 기능
- 목적: 온라인/자리비움/오프라인 상태를 실시간 확인
- 사용자: 모든 사용자(조회), Admin/Manager(관리 화면 활용)
- 관련 화면/경로: 채팅 메시지·멤버 패널에서 **아바타 네모칸 우측 하단**에 프레즌스 점, 프론트는 연결 시 `GET /presence`로 스냅샷 후 `presence:update`로 갱신
- 관련 API:
  - `GET /presence` (현재 Presence 목록 조회)
- 관련 Socket 이벤트:
  - 입력: `presence:set`
  - 출력: `presence:update`, `presence:error`
- 입력/출력:
  - 입력: `userId`, `status` (`ONLINE`/`AWAY`/`OFFLINE`)
  - 출력: `userId`, `status`, `updatedAt`
- 상태 전이/예외 케이스:
  - 유효하지 않은 status/userId는 `presence:error` 반환
  - 동일 `userId` 다중 탭(소켓)은 집합으로 추적하며, **해당 사용자의 모든 소켓이 끊기면** `presence:update`(OFFLINE) 브로드캐스트 후 서버 메모리 맵에서 제거(미사용 userId 누적 방지)
- 권한/보안:
  - 현재는 기본 상태 이벤트 처리, 인증 연동 시 sender 검증 강화 예정
- 로그/감사 포인트:
  - presence 변경 시점(`updatedAt`) 기준 추적 가능
- 테스트 기준:
  - 상태 변경 이벤트 수신
  - 잘못된 payload 오류 이벤트
  - `/presence` 목록 조회 일관성
- 비고:
  - 구현 파일:
    - `realtime/src/server.js`

---

## 감사 이벤트 로그 (audit_logs) — Phase 3-3-1/3-3-2

### 개요
채널·메시지·파일·업무·칸반 도메인에서 발생하는 주요 이벤트를 `audit_logs` 테이블에 기록한다.  
**대화 본문·파일 원문 등 민감 데이터는 저장하지 않으며**, 리소스 ID·이벤트 유형·행위자·부가 메타만 수집한다.

### 이벤트 유형 (`AuditEventType`)
| 이벤트 | 기록 서비스 |
|---|---|
| `CHANNEL_CREATED` | ChannelService |
| `CHANNEL_JOINED` | ChannelService |
| `MESSAGE_SENT` | MessageService |
| `MESSAGE_REPLY_SENT` | MessageService |
| `FILE_UPLOADED` | ChannelFileService |
| `FILE_DOWNLOAD_INFO_ACCESSED` | ChannelFileService |
| `WORK_ITEM_CREATED` | WorkItemService |
| `KANBAN_BOARD_CREATED` | KanbanService |
| `KANBAN_CARD_CREATED` | KanbanService |
| `KANBAN_COLUMN_CREATED/UPDATED/DELETED` | (Enum 정의됨, 추후 연동 가능) |
| `KANBAN_CARD_MOVED/STATUS_CHANGED/DELETED` | (Enum 정의됨) |
| `KANBAN_ASSIGNEE_ADDED/REMOVED` | (Enum 정의됨) |
| `ORG_SYNC_EXECUTED`, `USER_STATUS_CHANGED` | (Enum 정의됨) |

### DB 스키마
```sql
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
```

### API
| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| `GET` | `/api/admin/audit-logs` | ADMIN | 감사 로그 조회 (필터: from/to/actorUserId/eventType/resourceType/workspaceKey/limit) |

### 설계 원칙
- `AuditLogService.safeRecord()`: 예외를 삼키는 래퍼 → 감사 로깅 실패가 주 비즈니스 흐름에 영향을 주지 않음
- `REQUIRES_NEW` 전파 수준 → 호출 트랜잭션과 독립적으로 커밋
- `detail` 최대 500자, 줄바꿈 제거, 개인정보/본문 저장 금지
- 구현 파일:
  - `backend/src/main/java/com/ech/backend/domain/audit/AuditEventType.java`
  - `backend/src/main/java/com/ech/backend/domain/audit/AuditLog.java`
  - `backend/src/main/java/com/ech/backend/domain/audit/AuditLogRepository.java`
  - `backend/src/main/java/com/ech/backend/api/auditlog/AuditLogService.java`
  - `backend/src/main/java/com/ech/backend/api/auditlog/AuditLogController.java`
  - `backend/src/main/java/com/ech/backend/api/auditlog/dto/AuditLogResponse.java`

---

## 채널 멤버/첨부 UX 고도화 (프론트)
- 목적: 채널 운영 중 구성원 추가, 조직도 다중 선택, 첨부 접근성을 개선, 채팅 날짜 구분선, 다크 톤 UI
- 사용자: 로그인한 멤버
- 관련 화면/경로: 채널 만들기 모달, 채널 헤더(구성원 추가/첨부파일 모아보기), 멤버 패널, 채팅 본문
- 관련 API:
  - `GET /api/user-directory/organization` (조직도 트리 데이터)
  - `POST /api/channels/{channelId}/members` (채널 생성 후 추가 멤버 등록)
  - `GET /api/channels/{channelId}/files?userId=...`
  - `GET /api/channels/{channelId}/files/{fileId}/download?userId=...`
- 입력/출력:
  - **통합 피커**: 채널 생성·DM 생성·구성원 추가 모두 **동일한 `+` 버튼 기반 팝업**(`modalAddMemberPicker`)을 사용합니다. 팝업에서 좌측 조직도(회사>본부>팀) + 우측 검색/결과로 사용자 선택 후 상위 모달의 선택 태그에 반영됩니다.
  - 멤버 패널: `department`·`jobRank`를 한 줄 요약, `dutyTitle`은 값이 있을 때만 추가 줄(직책 없으면 UI에 안 보임)
  - 파일 업로드 성공 시: 일반 텍스트 메시지와 동일한 **메시지 행**(아바타·발신자·시간) 안에 첨부 인라인(파일명·크기·다운로드) 표시
  - **날짜 구분선**: 스레드 첫 메시지 또는 로컬 날짜가 바뀔 때 채팅 영역에 날짜 pill 표시
  - UI: CSS 변수 기반 **다크·보라 액센트** 톤(모달·관리자·검색·조직도 블록 포함)
  - **테마 선택**: `검정`(기본 다크·보라) / `하양`(라이트·인디고) / `파랑`(네이비·시안 액센트). `document.documentElement`의 `data-theme` 및 `localStorage` 키 `ech_theme`에 저장. 로그인 화면·사이드바 하단에 동일 칩 UI(`.theme-chip`)
- 상태 전이/예외 케이스:
  - 중복 멤버 추가 시 서버 검증 메시지를 시스템 메시지로 노출
  - 로그아웃 클릭 시 즉시 종료하지 않고 사용자 확인 후 처리
- 구현 파일:
  - `frontend/index.html`
  - `frontend/app.js`
  - `frontend/styles.css`
  - `backend/src/main/java/com/ech/backend/api/channel/dto/ChannelMemberResponse.java`
  - `backend/src/main/java/com/ech/backend/api/channel/ChannelService.java`
  - `backend/src/main/java/com/ech/backend/domain/channel/ChannelType.java` (`DM` 포함)
