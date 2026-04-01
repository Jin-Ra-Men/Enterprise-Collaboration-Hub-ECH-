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

## DM 채널 생성 제약 자동 보정 (환경 호환)
- 목적: 오래된 로컬 DB 스키마에서 DM 생성 실패를 자동 복구하여 사용자 DM 생성 흐름을 안정화
- 사용자: 채팅 사용자, 운영자, 백엔드 개발자
- 관련 화면/경로: 프론트 DM 생성(`modalCreateDm`, 프로필 `DM 보내기`) -> `POST /api/channels`
- 관련 API: `POST /api/channels` (`channelType=DM`)
- 관련 Socket 이벤트: 해당 없음
- 입력/출력:
  - 입력: `workspaceKey`, `name`, `description`, 선택 `createdByEmployeeNo`(하위 호환·**서버는 JWT 사원번호만 생성자로 사용**), `dmPeerEmployeeNos`
  - 출력: DM 채널 생성 또는 기존 DM 채널 재사용 응답
- 상태 전이/예외 케이스:
  - 구 스키마에서 `channels.channel_type` CHECK가 `PUBLIC/PRIVATE`만 허용하면 INSERT 실패 가능
  - 기동 시 `DataInitializer.ensureChannelTypeConstraintAllowsDm()`가 `channel_type` 관련 CHECK 제약을 탐지/교체하여 `PUBLIC/PRIVATE/DM`으로 재구성
- 권한/보안: 기존 채널 생성 권한 정책(`@RequireRole(MEMBER)`) 유지
- 로그/감사 포인트: `DataInitializer` 보정 결과를 애플리케이션 로그로 기록
- 테스트 기준:
  - 구 스키마 DB에서 서버 기동 후 DM 생성 API 성공 확인
  - 동일 참여자 DM 재생성 시 기존 채널 재사용 확인
- 비고: 수동 반영 시 `docs/sql/migrate_channels_allow_dm_type.sql` 사용 가능

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
  - `GET /api/channels?employeeNo=...` (내 채널/DM 목록 — **DM**의 `description`은 DB 저장값이 아니라 요청 `employeeNo`(본인)을 제외한 참가자 표시명을 서버가 매번 계산; **`unreadCount`** 는 본인 읽음 포인터 이후의 **루트 메시지** 건수로 계산해 사이드바 빨간 배지에 표시; **`lastMessageAt`** 는 채널별 루트 메시지 기준 최신 `created_at`(메시지 없으면 `null`); **`dmPeerEmployeeNos`** 는 DM만 조회자를 제외한 참가자 **사번** 배열(정렬) — 사이드바 DM 줄 프레즌스 점에 사용)
  - `POST /api/channels` (채널 생성)
  - `GET /api/channels/{channelId}` (채널 상세 조회)
  - `POST /api/channels/{channelId}/members` (구성원 추가 — **PUBLIC/PRIVATE는 채널 개설자만**, **DM은 멤버면 누구나** 가능)
  - `DELETE /api/channels/{channelId}/members?targetEmployeeNo=...` (멤버 내보내기 — **JWT 사원번호가 채널 `created_by`인 개설자만**; 대상이 개설자이면 400; 비개설자면 403; 해당 멤버가 아니면 400)
- 관련 Socket 이벤트: 추후 `channel:join`과 연계 예정
- 입력/출력:
  - 생성 입력: `workspaceKey`, `name`, `description`, `channelType`(`PUBLIC`|`PRIVATE`|`DM`), 선택 `createdByEmployeeNo`(구 클라이언트용·**실제 생성자는 Bearer JWT의 사원번호**), 선택 `dmPeerEmployeeNos`(DM일 때 상대 **사원번호** 목록 — 서버가 내부 고유 `name`(`__dm__…`)과 표시용 `description`을 구성하고, 동일 참가자 조합이면 기존 채널 반환 후 누락 멤버만 추가)
  - 참여 입력: `employeeNo`, `memberRole` (`JoinChannelRequest`)
  - 출력: 채널 기본 정보 + 멤버 목록(`members`: `employeeNo`, `name`, `department`, `jobLevel`, `jobPosition`, `jobTitle`, `memberRole`, `joinedAt` — `ChannelMemberResponse`)
- 상태 전이/예외 케이스:
  - 중복 채널명(`workspaceKey + name`) 생성 시 예외
  - 없는 사용자/채널 조회 시 예외
  - 이미 참여한 사용자 재참여 시 예외
  - 생성 시 생성자는 자동으로 `MANAGER` 멤버십 부여
  - 내보내기 시 대상 사용자 `channel_read_states` 행 삭제 후 `channel_members` 행 삭제; 감사 `CHANNEL_MEMBER_REMOVED`; 동일 트랜잭션에서 `messages`에 `message_type=SYSTEM` 본문(내보내진 사용자 표시명 포함) 저장, 커밋 후 Realtime **내부 HTTP**로 `channel:system` 브로드캐스트 → 모든 접속 클라이언트가 채팅에 동일 시스템 줄 표시·재조회 시에도 유지
  - 구성원 추가 권한:
    - 채널 타입이 `DM`이 아니면 `created_by`와 JWT 사번이 같은 개설자만 허용(아니면 403)
    - 채널 타입이 `DM`이면 기존 멤버 권한 범위에서 추가 허용
- 권한/보안:
  - 현재는 기본 도메인 동작 중심 (인증/인가 고도화는 다음 단계)
- 로그/감사 포인트:
  - 생성/참여 시각, 멤버 권한, 생성자 기준으로 감사 확장 가능
- 테스트 기준:
  - 채널 생성 성공/중복 실패
  - 채널 상세 조회 성공/없는 채널 실패
  - 채널 참여 성공/중복 참여 실패(성공 시 멤버 내보내기와 동일하게 `SYSTEM` 메시지·Realtime `channel:system` 알림)
  - 개설자 멤버 내보내기 성공 / 비개설자 403 / 개설자 본인 내보내기 400 (`ChannelApiTest`)
- 비고:
  - 구현 파일:
    - `backend/src/main/java/com/ech/backend/api/channel/ChannelController.java`
    - `backend/src/main/java/com/ech/backend/api/channel/ChannelService.java`
    - `backend/src/main/java/com/ech/backend/domain/channel/*`

---

## 좌측 퀵 레일(미읽음)·사이드바 접기
- 목적: **워크스페이스(ECH) 헤더는 사이드바 최상단 전폭**으로 두고, 퀵 레일은 그 아래에서 **검색·채널/DM 목록과 동일한 세로 구간**(`.sidebar-body`)에만 배치해 ECH 제목 영역을 침범하지 않음. 퀵에는 **미읽음을 최상단(배지)**으로 올리고, **최근 대화**도 항상 표시(상한 `QUICK_RAIL_MAX_ITEMS`). 사이드바는 **돌출 탭**으로 접어 **퀵 64px만** 남기고 나머지(워크스페이스·검색·목록·프로필)는 숨김.
- 사용자: 일반 채팅 사용자
- 관련 화면/경로: `frontend/index.html` `.sidebar-workspace`(ECH) → `.sidebar-body`(`#quickContainer`·`#quickRailScroll` + `.sidebar-main`); `#btnSidebarEdgeToggle`; `frontend/app.js` `renderQuickUnreadList`·`QUICK_RAIL_MAX_ITEMS`·`compareQuickRailChannel`; `frontend/styles.css` `.sidebar-body`·`.sidebar-main`·`.quick-rail`·`.sidebar-column`(324px 펼침)
- 관련 API: `GET /api/channels` (`unreadCount`, **`lastMessageAt`**, `createdAt` 폴백 정렬)
- 관련 Socket 이벤트: `message:new`, `channel:system` 등 기존 디바운스 채널 목록 갱신과 동일(약 400ms) — 갱신 시 퀵 레일도 동일 데이터로 재렌더
- 입력/출력:
  - 퀵 레일: 정렬 = (1) `unreadCount > 0` 우선 (2) 각 그룹 내 `lastMessageAt`(없으면 `createdAt`) 내림차순; 상위 최대 15개(`QUICK_RAIL_MAX_ITEMS`); 배지는 미읽음일 때만; `.quick-rail-link.channel-item`; 전체 이름은 `title`·`data-tooltip-title`·`aria-label`
  - 채널 0개: `.quick-rail-empty` 문구
  - 접기: `localStorage` `ech_sidebar_collapsed` (`1`/`0`); `.sidebar-column` 너비 324px↔**64px**(퀵 레일만 표시·워크스페이스/검색·목록/프로필 숨김); 탭 화살표 `‹` / `›`
- 상태 전이/예외 케이스:
  - `lastMessageAt` 미수신·파싱 실패 시 `createdAt`만으로 정렬(둘 다 없으면 0)
  - 퀵 항목은 `selectChannel` 연동·`.channel-item`과 동일 active 표시
- 권한/보안: 기존 채널 목록 API·인증과 동일
- 로그/감사 포인트: 해당 없음
- 테스트 기준:
  - 타 채널 메시지 수신 후 퀵 레일 순서·배지가 목록과 일치
  - 미읽음 0이어도 최근 대화가 퀵에 나오고, 미읽음 발생 시 해당 항목이 상단·배지로 올라옴
  - ECH 헤더가 퀵 레일보다 위에만 보이고, 퀵이 검색 행과 같은 높이에서 시작함
  - 돌출 탭으로 접기/펼치기 및 새로고침 후 상태 유지
- 비고: 퀵 레일 DM도 `dmSidebarLeadingHtml`·`data-presence-user`·`sidebar-dm-presence`로 사이드바와 동일하게 프레즌스 점 갱신(`refreshPresenceDots`); 상대 사번 없을 때만 `●` 폴백

---

## 채팅 @멘션·알림
- 목적: `@` 입력 후 사용자 검색 자동완성으로 멘션 삽입, 멘션된 **채널 멤버**에게 실시간 알림, 알림/미확인 목록 클릭 시 해당 채팅방·메시지로 이동
- 사용자: 일반 채팅 사용자
- 관련 화면/경로: `frontend/index.html` `messageInput`, `#mentionSuggest`, `#mentionToastStack`, `#mentionList`; `frontend/app.js` `formatMessageWithMentions`, `scheduleMentionSuggestUpdate`, `pushMentionToast`, `mention:notify` 수신, `renderMentionInboxList`
- 관련 API: **`GET /api/channels/{channelId}`** 응답의 `members`로 @자동완성(이름·사번 부분 일치, **현재 채널 멤버만**). 멘션 저장·알림 검증은 기존과 동일(`channel_members`).
- 관련 Socket 이벤트:
  - 출력(멘션 수신자에게만): `mention:notify` — payload: `channelId`, `channelName`, `channelType`, `senderName`, `messagePreview`, `messageId`
  - 저장 경로: 소켓 `message:send` 성공 후 Realtime이 본문에서 토큰 파싱·`channel_members` 검증 후 발송; REST `POST .../messages`는 `MentionNotificationService`가 동일 검증 후 `POST .../internal/notify-mentions`로 Node에 위임
- 입력/출력:
  - 저장 본문 토큰: `@{사원번호|표시명}` (표시명에 `|`, `}` 금지·프론트에서 제거). 메시지 렌더 시 `@표시명` 강조·클릭 시 프로필(`msg-user-trigger`)
  - 자동완성 후보는 **해당 채팅방 멤버만**(본인 제외); 전사 `GET /api/users/search`는 멘션 입력에 사용하지 않음
  - 본문당 토큰 최대 20개(Realtime·Java 공통)
  - 발신자 본인·비멤버 사번은 알림 제외
  - 미확인 멘션 목록: 현재 보고 있지 않은 채널의 멘션 토스트를 사용자별 localStorage(`ech_mention_inbox_{employeeNo}`)에 최대 100건 보관, 확인(클릭) 시 목록에서 제거
- 상태 전이/예외 케이스:
  - Realtime 미기동/내부 URL 비어 있으면 Java 쪽 `notifyMentions`는 HTTP 실패를 로그만 남기고 메시지 저장은 유지
  - `mention:notify`가 누락/지연되는 경우(소켓 emit 경로 불일치 등) 현재 채널에서 `message:new` 수신 시 본문 토큰을 클라이언트가 파싱해 내 멘션이면 폴백 토스트를 표시
  - 미확인 목록 항목 클릭 시 `selectChannel(..., { targetMessageId })`로 이동 후 해당 메시지 DOM(`msg-{id}`)으로 스크롤·강조, 메시지가 현재 타임라인 범위 밖이면 채널 이동까지만 보장
- 권한/보안:
  - 알림 대상은 **해당 채널 멤버**인 사번만(DB 검증)
- 로그/감사 포인트: 해당 없음(메시지 전송 감사는 기존 `MESSAGE_SENT`)
- 테스트 기준:
  - 멘션 삽입 후 전송 시 피멘션자 소켓에 `mention:notify` 수신
  - 토스트 클릭 시 `selectChannel`로 동일 채널 오픈
- 비고: 구현 `MentionParser`, `MentionNotificationService`, `RealtimeBroadcastClient.notifyMentions`, `realtime/src/db.js` 멘션 헬퍼, `POST /internal/notify-mentions`. 멘션 토스트 DOM은 **`#mainApp` 밖(body 직계)** 에 두어 `.app-layout { overflow:hidden }` 에 잘리지 않게 함. 프론트 Socket.io URL은 기본 `{페이지와 동일 hostname}:3001`로 자동(REST는 `window.location.origin`), `meta ech-realtime-url` / `localStorage ech_realtime_url`로 덮어쓰기 가능 — `localhost` 고정과 `127.0.0.1` 접속 불일치 시 멘션·프레즌스가 동시에 안 되는 전형 원인

---

## 통합 검색 확장(채널명/댓글)
- 목적: 기존 통합 검색에서 메시지/파일/업무/칸반 외에 `채널명`, `댓글(COMMENT_*)`도 동일 모달에서 검색 가능하게 확장
- 사용자: 일반 채팅 사용자
- 관련 화면/경로: `frontend/index.html` `#searchTypeSelect` (`COMMENTS`, `CHANNELS` 옵션), `frontend/app.js` `runSearch`, `TYPE_ICON`, `TYPE_LABEL`
- 관련 API:
  - `GET /api/search?q={keyword}&type={SearchType}&limit=...`
  - `GET /api/channels/{channelId}/messages/{messageId}?employeeNo=...` (스레드 모달 원글 단건 보강 로드)
- 관련 Socket 이벤트: 해당 없음
- 입력/출력:
  - 입력 타입 확장: `SearchType` = `ALL | MESSAGES | COMMENTS | CHANNELS | FILES | WORK_ITEMS | KANBAN_CARDS`
  - 출력 유형 확장: `COMMENT`, `CHANNEL`
  - `MESSAGES`: 루트 텍스트 메시지 중심 검색(`COMMENT_*`/`REPLY_*`/`FILE_*` 제외)
  - `COMMENT`: 본인이 속한 채널의 `COMMENT_*` 메시지 본문 검색 결과 반환 (`threadRootMessageId` 포함)
  - `CHANNEL`: 본인이 속한 채널의 `name`/`description` 검색 결과 반환
  - DM 채널 결과의 채널명 표시는 내부 키(`__dm__...`) 대신 표시용 설명(상대방 이름 요약)을 우선 사용
  - 검색 결과 클릭 동작:
    - `MESSAGE`: 해당 채널로 이동 후 메시지 DOM(`msg-{id}`) 포커스
    - `COMMENT`: 해당 채널로 이동 후 `threadRootMessageId` 기준으로 스레드 모달 오픈, 대상 댓글/답글 행 DOM(`thread-msg-{id}`)으로 스크롤 + 강조
    - `FILE`: 이미지(`image/*` 또는 확장자)면 이미지 라이트박스(크게보기+다운로드), 그 외 파일은 즉시 다운로드
    - `CHANNEL`: 해당 채널로 이동
- 상태 전이/예외 케이스:
  - 검색어 2자 미만은 기존과 동일하게 400
  - 댓글/채널 검색도 멤버십 필터를 통과한 채널만 결과 포함
  - 검색 submit 시 선택된 검색 타입을 유지하며, 타입을 강제로 `ALL`로 리셋하지 않음
- 권한/보안: JWT 인증 필수, 채널 권한 범위(멤버십) 외 데이터는 검색 결과에서 제외
- 로그/감사 포인트: 기존 통합 검색 정책과 동일
- 테스트 기준:
  - `type=COMMENTS` 요청 시 `$.data.type == COMMENTS`
  - `type=CHANNELS` 요청 시 `$.data.type == CHANNELS`
  - 댓글 검색 결과에 `threadRootMessageId` 포함 여부 확인
  - `GET /api/channels/{channelId}/messages/{messageId}` 단건 조회 성공/권한 검증
  - 프론트 검색 타입 셀렉트에서 댓글/채널명 필터 표시 확인
  - 검색 결과 클릭 시 타입별 이동/다운로드/이미지 팝업 동작 확인
  - 댓글 검색 결과 클릭 시 스레드 모달이 열리고 해당 댓글/답글 위치로 정확히 포커스되는지 확인
- 비고: 구현 `SearchService`, `MessageRepository.searchCommentsInJoinedChannels`, `ChannelRepository.searchByKeywordInJoinedChannels`

---

## 실시간 메시지 저장 연계 (Socket + PostgreSQL)
- 목적: `message:send` 요청을 DB에 먼저 저장한 뒤 저장 성공 데이터만 브로드캐스트
- 사용자: Frontend 개발자, Realtime/Backend 개발자
- 관련 화면/경로: 채널 메시지 입력/수신 UI
- 관련 API: 해당 없음 (소켓 중심)
- 관련 Socket 이벤트:
  - 입력: `message:send`
  - 출력: `message:new`, `message:error`, `mention:notify`(멘션 대상 개인 전달)
  - 보조: `channel:join`
- 입력/출력:
  - 입력 payload: `channelId`, `senderId`(발신자 **사원번호** 문자열), `text`
  - 저장 성공 출력: `messageId`, `channelId`, `senderId`(사번 문자열), `senderName`, `text`, `createdAt`
  - 저장 실패 출력: `code=DB_SAVE_FAILED` 또는 `code=NOT_CHANNEL_MEMBER` 오류 이벤트
- 상태 전이/예외 케이스:
  - payload 유효성 실패 -> `message:error` (`INVALID_PAYLOAD`)
  - 본문 길이 초과 -> `message:error` (`MESSAGE_TOO_LARGE`, 기본 상한 `MAX_MESSAGE_BODY_LENGTH=4000`, Backend 메시지 API `@Size(max=4000)`과 정합)
  - `channel_members`에 없는 사용자가 전송 -> `message:error` (`NOT_CHANNEL_MEMBER`, INSERT 전 검사)
  - DB 저장 실패 -> `message:error` (`DB_SAVE_FAILED`)
  - 저장 성공한 경우에만 `message:new` 브로드캐스트
  - 프론트는 소켓 ACK 실패/지연(2.5초) 또는 소켓 미연결 시 `POST /api/channels/{channelId}/messages`로 자동 폴백해 전송 유실을 줄임
  - 메시지 API 폴백 요청의 발신자 식별자(`senderId`)는 employee_no 문자열 기준으로 처리
- 성능·메모리:
  - Socket.io `maxHttpBufferSize`로 비정상적으로 큰 패킷 완충 완화
  - `pg` Pool: `max`/`idleTimeoutMillis`/`connectionTimeoutMillis` 환경변수로 조정 가능
- 권한/보안:
  - 리얼타임 서버는 `users.employee_no`로 발신자 존재를 검증한 뒤, `channel_members`(컬럼명 `user_id`, 값은 사번 FK) 멤버십과 `messages.sender_id`(사번) 저장을 수행한다.
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
  - `GET /api/channels/{channelId}/messages?employeeNo=...&limit=` (채널 **루트** 메시지 목록)
  - `GET /api/channels/{channelId}/messages/timeline?employeeNo=...&limit=` (메인 타임라인: 루트 + `REPLY_*` 답글, `isReply`·`replyTo*`·**`replyToSenderName`**(답장 대상 작성자 표시명)·`replyToPreview` 메타 포함)
  - `POST /api/channels/{channelId}/messages` (부모 메시지 생성)
  - `POST /api/channels/{channelId}/messages/{parentMessageId}/replies` (답글 생성)
  - `POST /api/channels/{channelId}/messages/{parentMessageId}/comments` (댓글 생성, `COMMENT_*`)
  - `GET /api/channels/{channelId}/messages/{parentMessageId}/replies` (스레드 조회)
- 관련 Socket 이벤트:
  - 현재 API 중심 구현, 추후 소켓 이벤트와 동기화 확장 예정
- 입력/출력:
  - 입력: `senderId`(발신자 사원번호 문자열), `text`
  - 출력: `messageId`, `channelId`, `senderId`(사번), `senderName`, `parentMessageId`, `text`, `createdAt`
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
  - 타임라인 **ROOT** 항목은 `threadCommentCount`, `lastCommentAt`, `lastCommentSenderName`(댓글 1개 이상일 때)로 메인 목록에 **댓글 요약**을 그릴 수 있다.
  - 프론트: 원글·첨부 행 하단 **「N개의 댓글」+ 마지막 댓글 시각** 클릭 시 스레드 모달(건수는 루트 직계 댓글·답글 + 댓글에 달린 답글까지 합산에 가깝게 집계); **답글** 선택 시 입력창 위 **답장 미리보기 바**(이름·본문 스니펫·취소).
  - 프론트 `loadMessages`: **timeline 요청이 HTTP 404**이면(구버전 백엔드 등) 위 루트 목록 API로 **자동 폴백**해 채팅 읽기는 가능(이 경우 타임라인 전용 답글 UI는 제한될 수 있음).
  - 구현 파일:
    - `backend/src/main/java/com/ech/backend/api/message/MessageController.java`
    - `backend/src/main/java/com/ech/backend/api/message/MessageService.java`
    - `backend/src/main/java/com/ech/backend/domain/message/*`

---

## 채널별 읽음 포인터 (마지막 읽은 메시지)
- 목적: 채널 단위로 사용자가 마지막으로 읽은 메시지를 저장해 미읽음/스크롤 복귀 등 UX에 활용
- 사용자: 채널 멤버
- 관련 화면/경로: 채널 메시지 목록, 채널/DM 사이드바 미읽음 배지(`GET /api/channels`의 `unreadCount`)
- 관련 API:
  - `GET /api/channels/{channelId}/read-state?employeeNo=...` (조회, 미설정 시 `lastReadMessageId=null`)
  - `PUT /api/channels/{channelId}/read-state` (갱신, body: `employeeNo`, `lastReadMessageId`)
- 관련 Socket 이벤트: 해당 없음 (추후 `read:update` 브로드캐스트 검토)
- 입력/출력:
  - 갱신 입력: `employeeNo`, `lastReadMessageId`(해당 채널 소속 메시지 ID)
  - 출력: `channelId`, `employeeNo`, `lastReadMessageId`, `updatedAt` (`updatedAt`은 미설정 조회 시 null)
- 상태 전이/예외 케이스:
  - 채널 멤버가 아니면 조회/갱신 불가
  - 다른 채널의 메시지 ID로 갱신 시 예외
  - 없는 채널/사용자/메시지 요청 시 예외
- 권한/보안:
  - 멤버십 검증만 수행, JWT subject(`employeeNo`)와 요청 `employeeNo` 정합은 RBAC·후속 고도화에서 보완
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
  - `POST /api/kanban/boards` — 보드 생성 (`workspaceKey`, `name`, `description`, `createdByEmployeeNo`)
  - `GET /api/kanban/boards?workspaceKey=default` — 보드 목록(최대 100건)
  - `GET /api/kanban/boards/{boardId}` — 보드 상세(컬럼·카드·담당자 사번 목록)
  - `DELETE /api/kanban/boards/{boardId}`
  - `POST /api/kanban/boards/{boardId}/columns` — 컬럼 추가 (`name`, `sortOrder` 선택)
  - `PUT /api/kanban/boards/{boardId}/columns/{columnId}` — 이름·정렬 (`actorEmployeeNo`)
  - `DELETE /api/kanban/boards/{boardId}/columns/{columnId}` — 컬럼 및 소속 카드 연쇄 삭제
  - `POST /api/kanban/boards/{boardId}/columns/{columnId}/cards` — 카드 생성 (`actorEmployeeNo`, `title`, `description`, `sortOrder`, `status`)
  - `PUT /api/kanban/cards/{cardId}` — 제목·설명·정렬·`status`·`columnId`(이동) 부분 갱신 (`actorEmployeeNo`)
  - `DELETE /api/kanban/cards/{cardId}`
  - `POST /api/kanban/cards/{cardId}/assignees` — 담당 추가 (body: `actorEmployeeNo`, `assigneeEmployeeNo`)
  - `DELETE /api/kanban/cards/{cardId}/assignees/{assigneeEmployeeNo}?actorEmployeeNo=...`
  - `GET /api/kanban/cards/{cardId}/history?limit=` — 이벤트 이력(기본 50, 최대 100)
- 관련 Socket 이벤트: 해당 없음
- 입력/출력:
  - 카드: `status` 문자열(기본 `OPEN`), 응답에 `assigneeEmployeeNos` 배열
  - 이력: `eventType`(`CARD_CREATED`, `COLUMN_MOVED`, `STATUS_CHANGED`, `ASSIGNEE_ADDED`, `ASSIGNEE_REMOVED`), `fromRef`/`toRef`, `actorEmployeeNo`
- 상태 전이/예외 케이스:
  - 동일 `(workspaceKey, name)` 보드 중복 생성 불가
  - 카드 컬럼 이동은 **같은 보드** 내 컬럼만 허용
  - 담당 중복 추가 불가
- 권한/보안:
  - `actorEmployeeNo` 등은 존재 여부(사번 유효성) 위주 검증(인증 연동·보드 멤버 검사는 RBAC 단계)
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
  - `POST /api/messages/{messageId}/work-items` (생성, body: `createdByEmployeeNo`, 선택 `title`, `description`, `status`)
  - `GET /api/messages/{messageId}/work-items` (0~1건 리스트, 메시지당 최대 1건)
  - `GET /api/work-items/{workItemId}` (단건 조회)
- 관련 Socket 이벤트: 해당 없음
- 입력/출력:
  - `title`/`description` 생략 시 메시지 본문에서 기본값(제목: 첫 줄 최대 80자, 설명: 본문 최대 4000자)
  - 응답에 `sourceMessageId`, `sourceChannelId`, `createdByEmployeeNo` 포함
- 메시지 ↔ 업무 링크 구조(2-5-2):
  - **정방향**: `work_items.source_message_id` → `messages.id` (유니크, 메시지당 업무 1건)
  - **역방향**: `messages` 테이블에 역참조 컬럼 없음. `GET /api/messages/{id}/work-items` 또는 `work_items` 조회로 연결 확인
  - 메시지 삭제 시 FK `ON DELETE SET NULL`로 업무는 유지되며 `source_message_id`만 비움, `source_channel_id`·`created_by`로 맥락 유지
- 상태 전이/예외 케이스:
  - 없는 메시지, 비멤버 생성 시도, 동일 메시지로 중복 생성 시 예외
- 권한/보안:
  - 채널 멤버십만 검증, 인증 세션(`employeeNo`)과 `createdByEmployeeNo` 정합은 RBAC 단계에서 보완
- 테스트 기준:
  - 생성 후 조회·목록 일치, 중복 생성 실패, 메시지 삭제 후 업무 단건 조회 가능(`sourceMessageId` null)
- 비고:
  - 스키마: `docs/sql/postgresql_schema_draft.sql` (`work_items`)
  - 구현: `backend/.../api/work/*`, `backend/.../domain/work/*`

---

## 채널 연동 업무/칸반 허브 (사용자 활용 UI)
- 목적: 채널 대화 맥락에서 바로 업무를 생성/관리하고, 같은 채널의 칸반 진행 상태를 한 화면에서 확인
- 사용자: 채널 멤버
- 관련 화면/경로: 채널 헤더 `📋` 버튼 → `업무 · 칸반` 모달
- 관련 API:
  - `GET /api/channels/{channelId}/work-items?employeeNo=&limit=` — 채널 업무 목록
  - `POST /api/channels/{channelId}/work-items` — 채널 업무 생성(`createdByEmployeeNo`, `title`, 선택 `description`, `status`, `sourceMessageId`)
  - `PUT /api/work-items/{workItemId}` — 채널 업무 수정(`actorEmployeeNo`, 부분 갱신)
  - `GET /api/kanban/channels/{channelId}/board?employeeNo=` — 채널 기본 칸반 보드 조회/없으면 자동 생성
- 입력/출력:
  - 업무 상태는 `OPEN`/`IN_PROGRESS`/`DONE`
  - 칸반 보드는 채널당 1개를 기본으로 사용하며, 최초 조회 시 `할 일/진행 중/완료` 컬럼을 자동 생성
- 상태 전이/예외 케이스:
  - 비멤버 조회/생성/수정 시 예외
  - `sourceMessageId` 지정 시 다른 채널 메시지를 참조하면 생성 거부
- 권한/보안:
  - 채널 멤버십 기준으로 접근 제어
  - 칸반 카드 생성/이동 API는 `MEMBER` 이상 인증이 필요하며, **채널 연동 보드**(`source_channel_id`가 있는 보드)는 해당 채널 멤버만 카드 변경 가능. **워크스페이스 전용 보드**(채널 미연동)는 앱 역할 `MANAGER` 이상만 카드 생성/이동 가능
- 테스트 기준:
  - 채널 업무 생성/조회/상태 변경이 동일 채널에서 반영되는지 검증
  - 채널 칸반 첫 진입 시 기본 보드/컬럼 자동 생성 검증
  - 카드 생성 후 컬럼 이동 시 보드 재조회 결과 일치 검증
- 비고:
  - 구현: `frontend/app.js`(모달 상호작용), `backend/.../api/work/*`, `backend/.../api/kanban/*`

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
  - 공통: `X-User-Role` 헤더(`MEMBER|MANAGER|ADMIN`) 또는 JWT `role` 클레임
  - `ADMIN`: `/api/admin/org-sync/**`
  - `MANAGER+`: `GET /api/users/search`, 채널 생성/멤버 추가, 칸반 보드·컬럼 구조 변경 및 워크스페이스 전용(채널 미연동) 보드의 카드 변경
  - `MEMBER+`: 채널 연동 칸반 카드 생성/이동(`POST .../columns/.../cards`, `PUT /api/kanban/cards/{cardId}`) — 해당 채널 멤버만 허용(서비스에서 `source_channel_id` 기준 검증)
- 설계:
  - `@RequireRole` 어노테이션으로 엔드포인트 최소 역할 선언
  - `RoleGuardInterceptor`가 요청 헤더를 해석하고 계층 비교(`ADMIN >= MANAGER >= MEMBER`)
  - 위반 시 `FORBIDDEN` 에러 응답 반환
- 상태 전이/예외 케이스:
  - 헤더 없음/오타 시 `FORBIDDEN`
  - 최소 권한 미만 역할 요청 시 `FORBIDDEN`
- 권한/보안:
  - 현재는 헤더 기반 임시 모델이며, 인증 연동 시 토큰/세션의 역할 클레임으로 대체 필요
  - body의 `employeeNo` 등과 JWT subject 정합 검증은 후속 RBAC 고도화 과제
- 테스트 기준:
  - `ADMIN` 헤더로 관리자 API 접근 성공
  - `MANAGER`/`MEMBER` 헤더로 관리자 API 접근 실패
  - `MANAGER` 헤더로 사용자 검색/채널 생성/칸반 보드·컬럼 변경 성공
  - `MEMBER` JWT로 채널 연동 칸반 카드 생성/이동 성공(채널 멤버인 경우)
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
  - `GET /api/user-directory/organization-filters` — `org_groups`에서 **`group_type=COMPANY` + `is_active=true`** 인 회사 그룹들을 기준으로 셀렉트 옵션(`label`, `companyGroupCode`). 첫 항목은 전체이며 `companyGroupCode=null`로 반환.
  - `GET /api/user-directory/organization?companyGroupCode=`(선택) — ACTIVE 사용자를 **COMPANY → DIVISION → TEAM** 3단계 트리로 그룹화한다. `companyGroupCode`를 생략하거나 전체 옵션을 선택하면 전체를 반환하고, 특정 `companyGroupCode`가 오면 해당 회사 트리만 반환한다.  
    - 조직 노드/계층은 `org_groups.member_of_group_code` 기반으로 구성한다.
    - `org_groups.group_code` / `companyGroupCode` 값은 **ASCII 전용** pretty 코드(예: `COMP_*`, `DIV_*`, `TEAM_*`)이며, 표시명(`display_name`)은 한글을 유지한다.
    - 사용자 목록은 `org_group_members`에서 `member_group_type='TEAM'`에 해당하는 유저를 사용한다.
    - 각 사용자의 `department` 값은 `TEAM` 그룹의 `display_name`으로 채운다. 직급·직위·직책은 `org_group_members`의 `JOB_LEVEL` / `JOB_POSITION` / `JOB_TITLE` 매핑만 사용한다(`users` 컬럼 fallback 없음).
  - `GET /api/users/profile?employeeNo=` — 동료 프로필(프론트 기본, 사번 기준)
  - `GET /api/users/profile?userId=` — 동일(숫자 사용자 ID, 호환)
  - 프로필 내용: 이름·사원번호·이메일·부서·직급; 직위(`jobPosition`)·직책(`jobTitle`)은 값이 있을 때만 모달에 표시. **DM 보내기**로 DM 채널 생성·입장. 응답에 `role`/`status`가 있어도 프로필 모달에는 표시하지 않음
  - `GET /api/users/{userId}/profile` — 동일(경로형, 하위 호환)
- 관련 Socket 이벤트: 해당 없음
- 입력/출력:
  - 검색 입력: `q`(이름/이메일/사번/부서 부분 일치, 숫자만 입력 시 사용자 ID 일치), `department`(정확히 일치하는 부서명으로 추가 필터)
  - 검색 출력: `userId`, `employeeNo`, `name`, `email`, `department`, `jobLevel`, `jobPosition`, `jobTitle`, `role`, `status`
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
  - `POST /api/channels/{channelId}/files/upload?employeeNo=...` (multipart `file` — 디스크 저장 + DB 메타)
  - `GET /api/channels/{channelId}/files?employeeNo=...` (최신순 최대 100건, 응답에 `uploaderName`, `uploadedByEmployeeNo` 포함)
  - `GET /api/channels/{channelId}/files/{fileId}/download?employeeNo=...` (바이너리 스트림, `Authorization: Bearer` 필요)
  - `GET /api/channels/{channelId}/files/{fileId}/download-info?employeeNo=...` (멤버 검증 후 스토리지 키·안내 문구)
- 관련 Socket 이벤트: 해당 없음
- 입력/출력:
  - 등록(메타만 API): `uploadedByEmployeeNo`, `originalFilename`, `contentType`, `sizeBytes`(1~512MiB), `storageKey`
  - 업로드 후 `MessageService`가 `message_type=FILE` 메시지를 남기며, JSON 본문에 `kind`, `fileId`, `originalFilename`, `sizeBytes`, **`contentType`**(이미지 판별·프론트 인라인 표시용)을 포함한다.
  - 목록: 파일 id, `uploaderName`, 원본명, 타입, 크기, `storageKey`, 업로드 시각
  - 디스크 저장 경로(신규 업로드): `channels/{workspaceKey}_ch{channelId}_{채널명슬러그}/yyyy/mm/{uuid}_{원본파일명}` (기존 `channels/{channelId}/...` 키는 DB에 남아 있으면 다운로드 시 그 경로로 조회)
- 상태 전이/예외 케이스:
  - 비멤버 접근 불가
  - 파일명 경로 조각(`..` 등) 제거·검증
- 권한/보안:
  - 멤버십만 검증; 요청 `employeeNo`와 JWT subject 정합은 RBAC 단계에서 보완
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
- 관련 화면/경로: 채팅 메시지·멤버 패널에서 **아바타 네모칸 우측 하단**에 프레즌스 점, 프론트는 `presence:set` 후 서버가 해당 소켓에만 `presence:snapshot`(전체 목록)을 내려 주고, 보조로 `GET /presence`·`presence:update`로 맞춤; 창 포커스·탭 복귀 시 재동기화(이미 **자리비움**이면 복귀 후에도 `AWAY` 유지). **좌측 하단** 본인 상태 줄 클릭 시 팝업에서 **온라인 / 자리비움(노란 점)** 선택 → `presence:set`
- 관련 API:
  - `GET /presence` (현재 Presence 목록 조회)
- 관련 Socket 이벤트:
  - 입력: `presence:set`
  - 출력: `presence:update`, `presence:snapshot`(요청 소켓 전용, `{ data: [...] }` 형태), `presence:error`
- 입력/출력:
  - 입력: `employeeNo`(사원번호 문자열), `status` (`ONLINE`/`AWAY`/`OFFLINE`) — 구 클라이언트 호환용으로 동일 값을 `userId` 키로 보내는 폴백이 있으나, **반드시 사번 문자열**을 넣어야 한다(DB 숫자 PK와 혼동 금지)
  - 출력: `employeeNo`, `status`, `updatedAt` (`GET /presence` 스냅샷 동일)
- 상태 전이/예외 케이스:
  - 유효하지 않은 status/빈 `employeeNo`는 `presence:error` 반환
  - 동일 `employeeNo` 다중 탭(소켓)은 집합으로 추적하며, **해당 사용자의 모든 소켓이 끊기면** `presence:update`(OFFLINE) 브로드캐스트 후 서버 메모리 맵에서 제거(미사용 키 누적 방지)
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
| `CHANNEL_MEMBER_REMOVED` | ChannelService |
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
  - 멤버 패널: 채널 개설자에게만 타 멤버 **내보내기** 버튼 노출 (`DELETE .../members?targetEmployeeNo=`)
  - `GET /api/channels/{channelId}/files?employeeNo=...`
  - `GET /api/channels/{channelId}/files/{fileId}/download?employeeNo=...`
- 입력/출력:
  - **통합 피커**: 채널 생성·DM 생성·구성원 추가 모두 **동일한 `+` 버튼 기반 팝업**(`modalAddMemberPicker`)을 사용합니다. 팝업에서 좌측 조직도(회사>본부>팀) + 우측 검색/결과로 사용자 선택 후 상위 모달의 선택 태그에 반영됩니다.
  - 멤버 패널: `department`·`jobLevel`을 한 줄 요약, `jobPosition`·`jobTitle`은 값이 있을 때만 추가 표시
  - 멤버 패널: 개설자 사번(`createdByEmployeeNo`)과 일치하는 멤버에 `개설자` 배지 표시
  - 파일 업로드 성공 시: 일반 텍스트 메시지와 동일한 **메시지 행**(아바타·발신자·시간) 안에 첨부 인라인 표시 — **이미지**(`contentType` 또는 확장자 기준)는 썸네일 + 클릭 시 확대 모달(`modalImagePreview`) + 모달 내 다운로드, 그 외는 파일명·크기·다운로드 버튼 행
  - 채팅 패널(`#viewChat`) 포커스 상태에서 클립보드에 이미지가 있으면 **붙여넣기(Ctrl+V)** 로 로컬 파일 선택과 동일하게 첨부 미리보기에 올린 뒤 전송 버튼(또는 Enter)으로 업로드·`FILE` 메시지 생성(열린 모달·`modal-overlay` 포커스일 때는 기본 붙여넣기 유지)
  - **날짜 구분선**: 초기 목록(`renderMessages`)과 동일하게 로컬 날짜가 바뀔 때 pill 표시. **실시간**(`appendMessageRealtime`)은 마지막 DOM 형제가 시스템 메시지여도 뒤에서 마지막 채팅 행·이전 날짜 키를 찾아 구분선·같은 분 묶음을 맞춤; `channel:system`은 서버 `createdAt`이 있을 때 구분선 정합
  - UI: CSS 변수 기반 **다크·보라 액센트** 톤(모달·관리자·검색·조직도 블록 포함)
  - **테마 선택**: 로그인 사용자 영역의 톱니바퀴 버튼으로 팝업을 열어 `검정`(기본 다크·보라) / `하양`(라이트·인디고) / `파랑`(네이비·시안 액센트) 선택. 즉시 적용되며 `PUT /api/auth/me/theme`로 사용자별 DB(`users.theme_preference`)에 저장되어 로그아웃/재로그인 후에도 유지
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
