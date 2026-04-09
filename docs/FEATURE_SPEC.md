# ECH 기능 명세서

이 문서는 구현된 기능의 동작 기준을 상세히 기록합니다.  
신규 개발/수정 시 해당 기능 항목을 반드시 갱신합니다.

> **제품 소개·비개발자용 요약**은 저장소 루트 [README.md](../README.md)를 참고하세요.  
> **기술 스택·API·로컬 실행**은 [DEVELOPER_README.md](./DEVELOPER_README.md)를 참고하세요.

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

## 화면설계 구역별 적용 훅 (ECH화면설계 1~9)
- 목적: 화면별 목업 차이를 실제 앱 구조에 안전하게 반영하기 위해 구역 단위 마크업 훅과 스타일 기준을 고정
- 사용자: 프론트엔드 개발자, 디자인 반영 담당자
- 관련 화면/경로: `frontend/index.html` (`#viewChat`·`data-ech-design-ref="ech-chat"`, `#modalWorkHub` **워크플로우 페이지 뷰**, `#viewReleases`, `#viewUserManagement`, `#viewOrgManagement`, `#viewSettings`) — 채팅 화면 시각 기준은 `design/ECH채팅/`과 동기화
- 관련 API: 해당 없음(표현 계층 중심)
- 관련 Socket 이벤트: 기존 채팅/업무 이벤트 그대로 사용(식별자 변경 없음)
- 입력/출력:
  - 입력: 화면 구역 클래스(`.ech-region--chat`, `.ech-region--admin`, `.ech-chat-header`, `.ech-composer-bar`, `.ech-workhub-shell`)
  - 출력: 구역별 스타일 스코프 적용, 기존 동작 로직(`app.js`) 유지
- 상태 전이/예외 케이스:
  - Tailwind 산출물(`ech-tailwind.css`) 미로드 시에도 `styles.css` 기준 레이아웃 유지
  - `index.html` 유틸 클래스/마크업 수정 후에는 `npm run build:css` 재실행으로 유틸 산출물 동기화
- 권한/보안: UI 클래스 스코프 변경만 포함, 권한 로직 영향 없음
- 로그/감사 포인트: 해당 없음
- 테스트 기준:
  - 라이트 테마에서 글로벌 바(`app-shell-topbar`)·모달 스크림·채팅 멤버 패널·컴포저(`.ech-composer-glass`) 포커스 링이 과도한 실선 없이(고스트 보더·글래스 계열) 일관되는지 확인
  - 기본(다크) 테마에서 `html:not([data-theme="light"])` 규칙으로 글로벌 바·헤더 검색 포커스·로그인 카드·모달·채팅 헤더/타임라인/날짜 구분선/컴포저·시작 화면(`.ech-home-*`)·사이드바 활성 표시·관리자 인사이트·설정·조직·사용자 분할·통합 검색·업무 허브 모달 셸이 라이트와 동일 계열(글래스·인디고 액센트)로 보이는지 확인
  - 채팅(`#viewChat`)이 `design/ECH채팅`에 맞게 헤더(채널명 인디고·팀원 N명·메뉴 아이콘)·날짜 구분선·말풍선·글래스 컴포저·하단 힌트로 보이는지 확인(툴바 아이콘은 장식, 서식 미구현)
  - 채팅 화면 진입 시 헤더/타임라인/컴포저가 정상 렌더링되는지 확인
  - 업무·칸반 모달 너비/스크롤이 깨지지 않는지 확인
  - 관리자 각 뷰(배포/사용자/조직/설정) 전환 시 패널 헤더 톤이 일관되는지 확인
  - 사용자 관리(`viewUserManagement`)의 상단 인사이트 카드(조회 대상/선택 조직 인원/저장 대기 변경)가 조직 선택/변경 대기 상태와 동기화되는지 확인
  - 설정(`viewSettings`)이 Hero 카드 + 좌측 입력 폼/우측 목록 캔버스 2열 구조로 표시되고, 반응형에서 1열로 정상 전환되는지 확인
  - 조직 관리(`viewOrgManagement`)가 상단 인사이트 카드 + 좌측 탭 레일(`org-tab-rail`)·우측 본문(`org-tab-main`) 2단 구조로 표시되고, 탭 전환 시 현재 탭명·표시 항목 수·저장 대기 건수가 갱신되는지 확인
  - 배포 관리(`viewReleases`)가 인사이트 카드(등록 수·현재 운영 버전·배포 이력 건수) + `release-layout`(좌측 업로드 카드·우측 목록/이력)로 표시되고, `loadReleases` 후 지표가 API 결과와 일치하는지 확인
  - 워크플로우 페이지 뷰(`modalWorkHub` id 사용)에서 업무 섹션이 **위**·칸반 섹션이 **아래**로 한 열(`work-hub-body` 세로 스택)로 배치되는지 확인(모든 뷰포트 동일)
  - 사용자 프로필 모달(`modalUserProfile`)이 `ECH화면설계 (9)`에 맞게 히어로·부제(부서·직급 ` · `)·카드형 필드·하단 DM/닫기 버튼으로 표시되는지 확인
- 비고: 설계 소스 매핑은 `docs/DESIGN_SYSTEM.md`의 "화면설계 (1)~(9) ↔ 앱 구역 매핑" 표를 기준으로 유지한다. 목업(`design/`)과의 **시각·밀도 갭**은 별도로 [DESIGN_GAP_CHECKLIST.md](./DESIGN_GAP_CHECKLIST.md)에서 구역별 체크·우선순위(P0~P2)·권장 작업 순서(동 문서 §7)로 추적한다.

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
  - `PUT /api/channels/{channelId}/dm-name` (다자간 DM 이름 변경, 1:1 DM 불가)
  - `POST /api/channels/{channelId}/delegate-manager` (채널 관리자 위임)
  - `POST /api/channels/{channelId}/leave` (채팅방 나가기 — 관리자는 위임 대상 필요)
  - `DELETE /api/channels/{channelId}` (채널 폐쇄 — 관리자만)
- 관련 Socket 이벤트: 추후 `channel:join`과 연계 예정
- 입력/출력:
  - 생성 입력: `workspaceKey`, `name`, `description`, `channelType`(`PUBLIC`|`PRIVATE`|`DM`), 선택 `createdByEmployeeNo`(구 클라이언트용·**실제 생성자는 Bearer JWT의 사원번호**), 선택 `dmPeerEmployeeNos`(DM일 때 상대 **사원번호** 목록 — 서버가 내부 고유 `name`(`__dm__…`)과 표시용 `description`을 구성하고, 동일 참가자 조합이면 기존 채널 반환 후 누락 멤버만 추가)
  - 참여 입력: `employeeNo`, `memberRole` (`JoinChannelRequest`)
  - 출력: 채널 기본 정보 + 멤버 목록(`members`: `employeeNo`, `name`, `department`, `jobLevel`, `jobPosition`, `jobTitle`, `memberRole`, `joinedAt` — `ChannelMemberResponse`)
- 상태 전이/예외 케이스:
  - 중복 채널명(`workspaceKey + name`) 생성 시 예외
  - DM 1:1은 동일 2인 조합이면 기존 채널 재사용(내부명 편차/레거시 데이터 보정 포함)
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
- 목적: 퀵 레일은 **채널/DM 목록과 동일한 세로 구간**(`.sidebar-body`) 상단에 두고, 그 아래로 **워크플로우** 단축·채널/DM 목록이 이어지게 함(구 **워크스페이스 상단 줄**·조직도 단축 버튼은 제거; 조직도는 상단 **팀**·환영 화면에서 진입). **통합 검색**은 상단 글로벌 바 `appHeaderSearchInput`만 사용(사이드바 중복 검색 제거). 퀵에는 **미읽음을 최상단(배지)**으로 올리고, **최근 대화**도 항상 표시(상한 `QUICK_RAIL_MAX_ITEMS`). 사이드바는 **돌출 탭**으로 접어 **퀵 64px만** 남기고 나머지(목록·프로필)는 숨김.
- 사용자: 일반 채팅 사용자
- 관련 화면/경로: `frontend/index.html` `.sidebar` → `.sidebar-body`(`#quickContainer`·`#quickRailScroll` + `.sidebar-main`); `#btnSidebarEdgeToggle`; `frontend/app.js` `renderQuickUnreadList`·`QUICK_RAIL_MAX_ITEMS`·`compareQuickRailChannel`; `frontend/styles.css` `.sidebar-body`·`.sidebar-main`·`.quick-rail`·`.sidebar-column`(324px 펼침)
- 관련 API: `GET /api/channels` (`unreadCount`, **`lastMessageAt`**, `createdAt` 폴백 정렬)
- 관련 Socket 이벤트: `message:new`, `channel:system` 등 기존 디바운스 채널 목록 갱신과 동일(약 400ms) — 갱신 시 퀵 레일도 동일 데이터로 재렌더
- 입력/출력:
  - 퀵 레일: **고정**(`ech_quick_rail_pinned_{employeeNo}` JSON 배열 순서)에 포함된 채널은 항상 목록 앞쪽에 같은 순서로 배치. 나머지는 정렬 = (1) `unreadCount > 0` 우선 (2) 각 그룹 내 `lastMessageAt`(없으면 `createdAt`) 내림차순. 전체 슬롯 상한 최대 15개(`QUICK_RAIL_MAX_ITEMS`). 우클릭 메뉴 `퀵 레일에 고정` / `퀵 레일 고정 해제`. 배지는 미읽음일 때만; `.quick-rail-link.channel-item`·고정 시 `.quick-rail-link-pinned`; 전체 이름은 `title`·`data-tooltip-title`·`aria-label`
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

### 채팅방별 알림 끄기·신규 메시지 토스트
- 목적: 참여 중인 다른 채널/DM에 신규 메시지가 오면 우하단 토스트(`pushNewMessageToast`)로 안내하되, 사용자가 방마다 **일반 메시지 토스트만** 끌 수 있게 함(서버 저장 없음, 브라우저 `localStorage`).
- 저장 키: `ech_notify_muted_channels_{employeeNo}` — JSON 숫자 배열(채널 ID). `isChannelNotifyMuted` / `setChannelNotifyMuted`.
- 효과: 음소거된 채널에서는 **`pushNewMessageToast`만** 억제. **멘션 토스트**(`pushMentionToast`, `mention:notify` 및 현재 채널 `message:new` 폴백)는 음소거와 **무관하게 항상** 표시. **미읽음 배지**(`unreadCount` 기반 사이드바·퀵 레일)는 서버/읽음 상태 그대로이며 음소거와 **무관**.
- 현재 보고 있는 채널은 신규 일반 메시지 토스트 대상에서 제외(기존과 동일).
- UI: `#channelList` / `#dmList` / `#quickRailScroll`의 `.channel-item` **우클릭** → `#channelSidebarContextMenu`(`알림 끄기`↔`알림 켜기`, `채팅방 나가기`). 채널·DM 상단 햄버거(멤버 패널) `#btnHeaderNotifyToggle`에서 동일 토글. 음소거 시 목록 행에 **벨+슬래시** 아이콘(`notifyMutedBellSvg`) 표시.
- 햄버거 패널 순서(위→아래): 알림 끄기/켜기 → 첨부파일 → 업무/칸반 → 이름 변경 → **멤버 목록** 제목 → 멤버 리스트 → 구성원 추가 → 채팅방 나가기 → 채널 폐쇄.
- 비고: 업무 사이드바 변경 등 `pushActivityToast`는 별도 정책(채널 음소거와 무관할 수 있음).

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
    - `WORK_ITEM`: 응답의 `relatedChannelId`(소스 채널)가 있으면 해당 채널로 전환 후 `업무 · 칸반` 모달을 열고 해당 업무 행(`data-work-item-id`)으로 스크롤·강조
    - `KANBAN_CARD`: 응답의 `relatedChannelId`(보드의 `source_channel_id`)가 있으면 해당 채널로 전환 후 동일 모달을 열고 해당 카드(`data-kanban-card-id`) 강조; 채널 미연동 보드 카드는 `relatedChannelId`가 없을 수 있어 안내 후 동작 생략 가능
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

## 관리자 앱 기초설정 (app_settings)
- 목적: `app_settings` 테이블에 전역 키-값을 관리자가 조회·수정하고, **새 행을 직접 추가**할 수 있게 함
- 사용자: `ADMIN`
- 관련 화면: 관리자 > **설정** (`viewSettings`) — 기존 목록 + 「기초설정 추가」 카드(설정 키·값·설명·추가 버튼)
- 관련 API:
  - `GET /api/admin/settings` — 전체 목록
  - `GET /api/admin/settings/{key}` — 단건
  - `PUT /api/admin/settings/{key}` — 값/설명 수정 (`UpdateSettingRequest`)
  - `POST /api/admin/settings` — 신규 추가 (`CreateSettingRequest`: `key` 필수, `value`·`description`·`updatedBy` 선택)
- 키 규칙: 영문·숫자·`.`·`-`·`_`만, 길이 1~100, DB `setting_key` 유니크와 동일하게 중복 시 400
- 동작: 애플리케이션 코드가 `AppSettingsService.get()` 등으로 읽는 키만 런타임에 의미가 있음. 임의 키 추가는 향후 기능 플래그·연동 설정 등 확장용
- 예외: 중복 키, 잘못된 키 형식(Bean Validation), 존재하지 않는 `updatedBy` 사번

---

## 관리자 첨부 저장소 진단 (storage probe)
- 목적: `file.storage.base-dir`(로컬·UNC)가 **백엔드 JVM** 기준으로 디렉터리 생성·임시 파일 쓰기·삭제 가능한지 확인. RDP 세션의 PowerShell 성공과 불일치(서비스 계정 ≠ 대화형 사용자)하는 UNC 문제를 줄이기 위함.
- 사용자: `ADMIN`
- 관련 API: `GET /api/admin/storage/probe` — 응답 `StorageProbeResponse`: `resolvedPath`, `writable`, `uncPath`, `detail`(`ok` 또는 예외 요약)
- 테스트 기준: 관리자 토큰으로 호출 시 `writable=true`·`detail=ok`(테스트 프로파일은 임시 디렉터리 사용)

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
- 관련 화면/경로: 채널 메시지 목록, 스레드 패널, 햄버거 메뉴 **스레드 모아보기**(`modalThreadHub`, 채널·DM 공통)
- 관련 API:
  - `GET /api/channels/{channelId}/messages?employeeNo=...&limit=` (채널 **루트** 메시지 목록)
  - `GET /api/channels/{channelId}/messages/threads?employeeNo=...&limit=` (스레드 활동이 있는 **원글**만, 최근 스레드 활동 시각 내림차순, 상한 100; 응답 항목은 타임라인 ROOT와 동일하게 `threadCommentCount`·`lastCommentAt`·`lastCommentSenderName` 포함)
  - `GET /api/channels/{channelId}/messages/timeline?employeeNo=...&limit=&beforeMessageId=` — 응답 `data`는 **`{ items, hasMoreOlder }`** (`MessageTimelinePageResponse`). 최신 페이지는 `beforeMessageId` 생략; **이전 구간**은 화면에 보이는 **가장 오래된 타임라인 행**의 `messageId`를 `beforeMessageId`로 넘긴다(서버는 `(createdAt,id)` 커서로 `limit+1`건 조회 후 `hasMoreOlder` 판별, 한 요청 최대 200행). 댓글(`COMMENT_*`)은 타임라인에 없으므로 커서로 사용 불가(400).
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
  - 프론트: 원글·첨부 행 하단 **「N개의 댓글」+ 마지막 댓글 시각** 클릭 시 스레드 모달(건수는 루트 직계 댓글·답글 + 댓글에 달린 답글까지 합산에 가깝게 집계); **답글** 선택 시 입력창 위 **답장 미리보기 바**(이름·본문 스니펫·취소). **스레드 모아보기**는 `refreshThreadHubData`·행 클릭 시 `openThreadModal`·`timelineRootMessageById` 캐시 주입.
  - 프론트 `loadMessages`: **timeline 요청이 HTTP 404**이면(구버전 백엔드 등) 위 루트 목록 API로 **자동 폴백**해 채팅 읽기는 가능(이 경우 타임라인 전용 답글 UI는 제한될 수 있음).
  - 프론트 채팅: 최초 `loadMessages`는 타임라인 **`TIMELINE_PAGE_LIMIT`(100)**·응답 `hasMoreOlder` 저장. 상단 스크롤 시 `beforeMessageId`로 **이전 페이지** `prepend`(`prependTimelineMessages`·스크롤 위치 보정). DOM은 `trimMessages()`: 하단 근처일 때만 앞에서 제거해 **`MAX_CHAT_DOM_NODES`(4000)** 근처까지, **강제 상한 `HARD_MAX`(10000)**. DB 전체 이력은 페이지 단위로만 메모리에 올림(10만 건 채널도 요청당 최대 200행). 대량 테스트용 `tools/sql/seed_mass_channel_messages.sql` 참고.
  - 경로 매칭: 단건 조회·`.../replies`·`.../comments`는 `{id:\\d+}`로 **숫자만** 매칭해 `GET .../messages/threads`·`/timeline`과 `/{messageId}` 오매칭을 방지한다.
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
  - `POST /api/channels/{channelId}/read-state/mark-latest-root` (body: `employeeNo`) — 채널에 루트 메시지가 있으면 **가장 최신 루트**( `created_at DESC`, `id DESC` )를 읽음 포인터로 설정. 대량 히스토리에서도 **첫 화면만 로드해도** 미읽음 배지를 없앨 수 있게 함
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
  - **미읽음 루트 건수**(`unreadCount`): 읽음 포인터가 없으면 채널 내 루트 전체 건수(`countRootMessagesInChannel`). 포인터가 있으면 그보다 타임라인상 **더 최신**인 루트만 센다(`countRootMessagesNewerThanCursor`, `(created_at > 커서) OR (동일 시각 AND id > 커서.id)`). null 커서를 한 JPQL에 넣으면 PostgreSQL에서 매개변수 타입 오류가 날 수 있어 **서비스에서 분기**한다.
  - 프론트 **「새 메시지」 구분선**(`showNewMsgsDivider`): lastRead 직후 DOM에 **다른 사람이 보낸** 루트 메시지가 하나라도 있을 때만 삽입(첨부 직후 `loadMessages`처럼 읽음 포인터 갱신보다 앞선 타이밍에서, 내 첨부만 새 구간이 될 때 위에 선이 붙던 문제 방지).
  - 프론트 **스크롤 기억**: 사용자·채널별로 메시지 목록 `scrollTop/scrollHeight` 비율을 `localStorage`에 저장(`ech_chat_scroll_v1_{employeeNo}`), 다른 채널로 전환 직전·스크롤 시(디바운스) 저장, `loadMessages` 후 복원(이미지 로드 보정용 이중 `requestAnimationFrame`).
  - 스키마: `docs/sql/postgresql_schema_draft.sql` (`channel_read_states`)
  - 구현 파일:
    - `backend/src/main/java/com/ech/backend/api/channel/ChannelReadStateController.java`
    - `backend/src/main/java/com/ech/backend/api/channel/dto/MarkChannelReadCaughtUpRequest.java`
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
  - `POST /api/kanban/boards/{boardId}/columns/{columnId}/cards` — 카드 생성 (`actorEmployeeNo`, `title`, `description`, `sortOrder`, `status`, 선택 `assigneeEmployeeNos` 사번 배열·최대 50명)
  - `PUT /api/kanban/cards/{cardId}` — 제목·설명·정렬·`status`·`columnId`(이동) 부분 갱신 (`actorEmployeeNo`)
  - `DELETE /api/kanban/cards/{cardId}`
  - `POST /api/kanban/cards/{cardId}/assignees` — 담당 추가 (body: `actorEmployeeNo`, `assigneeEmployeeNo`)
  - `DELETE /api/kanban/cards/{cardId}/assignees/{assigneeEmployeeNo}?actorEmployeeNo=...` — 서비스에서 `KanbanCard.assignees` 컬렉션과 DB를 일치시킨 뒤 응답을 만들어, 제거 직후 `assigneeEmployeeNos`에 해제 대상이 남는 영속성 캐시 불일치를 방지
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
- 시각·명칭: **워크플로우** — `design/ECH화면설계 (1)` Work Management 톤(단일 패널 `work-hub-panel--workflow`, 키커 Workflow, 섹션 Tasks/Board). 채널·DM 연결은 모달 `#workHubChannelContext`로 표시. 구 `(6)` 칸반 컬럼·카드 스타일 유지. 업무 목록 상태 칩·완료 유사 컬럼 `.kanban-column--done-like` 등 기존과 동일(동작·API 동일).
- 관련 API:
  - `GET /api/channels/{channelId}/work-items?employeeNo=&limit=` — 채널 업무 목록
  - `POST /api/channels/{channelId}/work-items` — 채널 업무 생성(`createdByEmployeeNo`, `title`, 선택 `description`, `status`, `sourceMessageId`)
  - `PUT /api/work-items/{workItemId}` — 채널 업무 수정(`actorEmployeeNo`, 부분 갱신)
  - `DELETE /api/work-items/{workItemId}?actorEmployeeNo=` — 채널 멤버, **기본 소프트 삭제**(`in_use=false`). `hard=true` 시 연결 칸반 카드 삭제 후 업무 행 완전 삭제
  - `POST /api/work-items/{workItemId}/restore?actorEmployeeNo=` — 소프트 삭제된 업무 복원(`in_use=true`)
  - `GET /api/work-items/sidebar/by-assigned-cards?employeeNo=&limit=` — 내가 **칸반 카드 담당**인 업무 목록(사이드바)
  - `GET /api/kanban/channels/{channelId}/board?employeeNo=` — 채널 기본 칸반 보드 조회/없으면 자동 생성
- 입력/출력:
  - 업무 상태는 API 값은 `OPEN`/`IN_PROGRESS`/`DONE`이며, UI 셀렉트·목록은 한글 라벨(예: 미착수·진행 중·완료)로 표시
  - 칸반 보드는 채널당 1개를 기본으로 사용하며, 최초 조회 시 `할 일/진행 중/완료` 컬럼을 자동 생성
  - 칸반 카드는 **반드시 동일 채널의 업무 항목(`work_item_id`)에 연결**된다(미연결 카드 없음). `POST .../columns/.../cards` body에 `workItemId` 필수. 카드 담당: 채널 멤버 자동완성·`POST/DELETE .../assignees`(후보는 **본인 포함**, 이미 해당 카드에 배정된 사번만 제외; UI에서 이름·조직·직급을 구분 표시)
  - UI: 레이아웃은 **항상** **업무 항목 패널 위 → 칸반 보드 패널 아래** 한 열 세로 배치(`work-hub-body` flex column; 넓은 화면에서도 좌우 2열로 나누지 않음). 모달 본문 전체 스크롤. 신규 카드는 **저장된 업무**를 선택한 뒤 큐에 넣고, 저장 시 생성. 업무 **✕**는 소프트 삭제를 **저장 전까지** `workHubPendingWorkDeleteIds`에만 넣고, 목록에서는 **삭제 예정**(주황 배지)·**삭제 취소**로 표시(행을 바로 숨기지 않음). 비활성 업무의 **복원**·**완전 삭제**는 **업무 목록 행** 또는 **업무 상세**에서 선택 가능하며, 모두 `workHubPendingWorkRestoreIds` / `workHubPendingWorkPurgeIds`에만 쌓였다가 **업무·칸반 모달 저장(`flushWorkHubSave`)** 시에만 API 호출(즉시 DB 반영 아님). 목록에서 **복원 예정**(녹색 배지)·**완전 삭제 예정**(적색 배지)으로 표시, **취소** 버튼으로 대기 큐에서 제거. **완전 삭제**는 서버에서 연결 칸반 카드를 먼저 지우므로, 저장 시 해당 업무의 카드 ID를 `workHubPendingCard*` pending 맵에서 제거한 뒤 나머지 카드만 `PUT`(없으면 「카드를 찾을 수 없습니다」 방지). 공통 확인창 `#modalAppDialog`는 z-index를 상세 모달보다 높게 두어 **완전 삭제 확인**이 상세 모달에 가리지 않음
  - 모달 **저장** 성공 시 사이드바 **내 업무 항목** 목록을 다시 조회해 담당 칸반 변경이 즉시 반영된다
  - 사이드바 **내 업무 항목** 행 클릭은 채팅 채널 전환 없이 해당 채널의 업무·칸반 모달만 연다(스코프된 `channelId`로 API 호출). 행 **보조 텍스트**는 `channelName`인데, DM은 내부 채널명(`_dm__...`)이 아니라 **표시용 제목**(`channels.description`)을 쓴다 — API `GET /api/work-items/sidebar/by-assigned-cards` 응답의 `channelName`은 백엔드에서 DM일 때 `description` 우선(`WorkItemService.channelDisplayNameForSidebar`). 프론트는 로드된 채널 목록으로 한 번 더 보정(`displayChannelLabelForWorkSidebar`). 업무·칸반 모달 **업무 목록**에서 행을 클릭해 상세를 열면 해당 행에 `channel-work-item--selected`로 **선택 강조**(테두리·그림자·배경 틴트), 사이드바에서 항목을 눌러 들어올 때도 동일 키로 강조
  - 칸반 카드는 컬럼 내 세로 순서뿐 아니라 **컬럼 간 드래그앤드롭**으로 이동 가능. **`article.kanban-card-item`에 `draggable="true"`** 를 두되, `dragstart` 시 이벤트 타깃이 **`input`/`textarea`/`select`/`button`/담당 검색 영역 등**이면 `preventDefault`로 드래그를 막아 본문·제목은 잡고 끌고, 컨트롤은 그대로 쓸 수 있게 한다. Chrome에서 카드가 `draggable` 조상 아래에 있을 때 이동 후 `<select>` 표시가 어긋나는 문제는 **드롭 처리 말미에 `rebuildKanbanCardColumnSelectDom`으로 컬럼 `<select>` 노드를 통째로 교체**하고, 컬럼 변경은 **`#channelKanbanBoard`에 위임한 `change` 리스너 한 개**로 처리한다. 드롭 시 출발·도착 컬럼의 순서·컬럼 임시 상태를 반영하고, **저장** 시 컬럼이 바뀐 카드에 대해 `PUT .../kanban/cards/{id}`에 `columnId`와 함께 컬럼에 대응하는 `status`(`OPEN`/`IN_PROGRESS`/`DONE`, 기본 3컬럼은 정렬 순 0·1·2)를 함께 보낸다
  - 카드가 **연결된 업무(`work_item_id`)** 가 있을 때, 드래그앤드롭·카드 행 컬럼 셀렉트·카드 상세에서 컬럼을 바꾸면 프론트의 **`workHubPendingWorkStatus`**(업무 목록 저장용)도 동일 컬럼 기준 `statusForKanbanColumnId`로 맞춘다. 그렇지 않으면 업무 행은 예전 상태(예: 진행 중)를 **저장**에 실어 보내 카드는 완료 컬럼인데 업무 상태만 어긋날 수 있다
  - 드롭 직후에는 보드 **모든 컬럼**의 카드 순서·`columnId` pending을 DOM에서 다시 읽고(`syncKanbanBoardFromDomFull`), 카드 하단 컬럼 `<select>` 값을 카드가 실제로 들어 있는 컬럼(`data-column-id`)과 맞춘다 — `dragend`/`drop` 이벤트 순서 차이로 셀렉트만 어긋나는 현상 방지
  - `loadChannelKanbanBoard()`는 DnD·저장·셀렉트 변경 등으로 **동시에 여러 번** 호출될 수 있다. 채널별 **요청 세대(`kanbanBoardFetchGenByChannelId`)**를 두어, 늦게 완료된 **오래된 응답**은 `renderKanbanBoard`를 호출하지 않게 해 카드 컬럼과 행 `<select>`가 다시 어긋나는 간헐 현상을 막는다(동기 XHR로 바꿀 필요 없음)
  - **연속 DnD**에서는 두 번째 `drop`이 `requestAnimationFrame`으로 동기화를 미루는 동안 직전 `GET`이 끝나 세대가 아직 안 올라간 것처럼 보일 수 있어, 컬럼 `drop`과 컬럼 `<select>` 변경 시 **`loadChannelKanbanBoard` 호출 전(동기 구간)** 에 세대를 한 번 더 올려 진행 중인 조회를 즉시 무효화한다
  - **DnD 전용**: 컬럼 간 드래그앤드롭으로 카드 노드가 이미 옮겨진 뒤에는 **`loadChannelKanbanBoard`를 호출하지 않는다**(업무 목록만 `loadChannelWorkItems`). 풀 보드 GET+`innerHTML` 재렌더는 연속 DnD·pending 타이밍과 맞물려 행 컬럼 `<select>`만 어긋나는 증상을 유발할 수 있어, 드롭 후 상태는 DOM 동기화(`syncKanbanBoardFromDomFull` 등)와 저장 시 서버 반영에 맡긴다. **셀렉트로 컬럼만 바꿀 때**는 카드 DOM 이동이 렌더에 의존하므로 기존처럼 `loadChannelKanbanBoard`를 호출한다
  - DnD 직후 행 `<select>`는 노드 이동 뒤에도 브라우저가 이전 선택을 유지하는 경우가 있어 **`applyKanbanColumnSelectToColumnId`**로 옵션 인덱스를 직접 맞추고, **`setTimeout(0)`** 뒤 `sync*`를 한 번 더 돌려 dragend·레이아웃 이후 DOM과 맞춘다
  - 드래그앤드롭 타깃은 **`.kanban-card-list`만**이 아니라 **`section.kanban-column` 전체**(`dragover`/`drop` on column, 리스트는 컬럼 높이를 채워 카드 아래 빈 영역에서도 수신). 컬럼에 점선 강조(`.kanban-column-drag-over`)
  - 카드 `<article>`에 **`data-render-column-id`**(렌더 버킷 컬럼 id)를 두어 행 하단 컬럼 `<select>` 표시값이 실제 칸과 일치하도록 함. 드래그 데이터는 **`application/x-ech-kanban-card`**(및 빈 `text/plain`)로 두어 검색 `input` 등에 실수로 놓을 때 문자열이 끼어드는 것을 방지하고, 업무 허브 캡처에서 해당 입력류 위 `drop`을 막음
- 상태 전이/예외 케이스:
  - 비멤버 조회/생성/수정 시 예외
  - `sourceMessageId` 지정 시 다른 채널 메시지를 참조하면 생성 거부
- 권한/보안:
  - 채널 멤버십 기준으로 접근 제어
  - 칸반 카드 생성/이동/담당 추가·해제 API는 `MEMBER` 이상 인증이 필요하며, **채널 연동 보드**(`source_channel_id`가 있는 보드)는 해당 채널 멤버만 변경 가능. **워크스페이스 전용 보드**(채널 미연동)는 앱 역할 `MANAGER` 이상만 변경 가능
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
  - `MEMBER+`: 채널 연동 칸반 카드 생성/이동/담당 추가·해제(`POST .../columns/.../cards`, `PUT /api/kanban/cards/{cardId}`, `POST/DELETE .../assignees`) — 해당 채널 멤버만 허용(서비스에서 `source_channel_id` 기준 검증)
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
  - `MEMBER` JWT로 채널 연동 칸반 카드 생성/이동·담당 변경 성공(채널 멤버인 경우)
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
- 관련 화면/경로: 햄버거 메뉴 **첨부파일** / **이미지 모아보기** → `modalFileHub`(탭: 전체 파일·이미지). DM 포함 동일 `channelId` 기준. **전체 파일** 탭은 이미지(`contentType`/확장자 기준)를 제외한 첨부만 목록·다운로드; 이미지는 **이미지** 탭(썸네일 그리드·라이트박스)에만 표시. 이미지만 있을 때 전체 탭은 안내 문구(「이미지」 탭 안내)만 표시.
- **이미지 업로드**: multipart `file`=**원본(풀 해상도)**, 선택 `preview`=클라이언트 `maybeCompressImageForUpload` 결과(JPEG, 원본과 다를 때만) — 서버는 `preview_storage_key`·`preview_size_bytes`에 저장. 미리보기 없는 구버전 첨부는 기존과 동일(원본만).
- **이미지 표시**: 인라인·파일 허브 썸네일은 `GET .../files/{fileId}/preview`(미리보기 있으면 그 파일, 없으면 원본). 라이트박스(크게 보기): 서버 미리보기가 있고 `preview_size_bytes` < `size_bytes`이며 GIF/SVG가 아니면 먼저 **동일 `/preview`** 로 표시(대용량 원본 디코딩·전송 부담 감소)하고, 모달 **원본 보기**로 `GET .../download?variant=original` 전환. 미리보기가 없으면 라이트박스도 처음부터 `variant=original`. 구현: `openChannelImageLightbox`·`modalImagePreview`의 **원본 보기** 버튼.
- **대용량 이미지 다운로드(프론트)**: 이미지이고 서버에 `hasPreview`·`preview_size_bytes`가 있으면 `modalImageDownloadChoice`에서 **원본** vs **미리보기(압축본)** — 각 버튼 옆에 `size_bytes` / `preview_size_bytes` 표시. 서버 미리보기가 없고 크기가 **약 512KB 이상**이면 **원본** vs **브라우저 JPEG 재인코딩**(GIF·SVG는 원본만). 메타가 없으면 `download-info`로 보강.
- 관련 API:
  - `POST /api/channels/{channelId}/files` (메타데이터만 등록, 하위 호환)
  - `POST /api/channels/{channelId}/files/upload?employeeNo=...` (multipart `file` 필수, 선택 `preview` — 각각 디스크 저장 + DB `storage_key` / `preview_storage_key`)
  - `GET /api/channels/{channelId}/files?employeeNo=...` (최신순 최대 100건, 응답에 `uploaderName`, `uploadedByEmployeeNo`, `hasPreview`, `previewSizeBytes` 포함)
  - `GET /api/channels/{channelId}/files/{fileId}/download?employeeNo=...&variant=original|preview` (바이너리, `preview`는 미리보기 키 없으면 404)
  - `GET /api/channels/{channelId}/files/{fileId}/preview?employeeNo=...` (인라인·썸네일용: 미리보기 있으면 그 파일, 없으면 원본)
  - `GET /api/channels/{channelId}/files/{fileId}/download-info?employeeNo=...` (멤버 검증, `hasPreview`, `previewSizeBytes` 등)
- 관련 Socket 이벤트: 해당 없음
- 입력/출력:
  - 등록(메타만 API): `uploadedByEmployeeNo`, `originalFilename`, `contentType`, `sizeBytes`(1~512MiB), `storageKey`
  - 업로드 후 `MessageService`가 `message_type=FILE` 메시지를 남기며, JSON 본문에 `kind`, `fileId`, `originalFilename`, `sizeBytes`, **`contentType`**, 선택 **`previewSizeBytes`**(서버 미리보기가 있을 때)를 포함한다.
  - 목록: 파일 id, `uploaderName`, 원본명, 타입, 크기, `storageKey`, 업로드 시각
  - 디스크 저장 경로(신규 업로드): `channels/{workspaceKey}_ch{channelId}_{채널명슬러그}/yyyy/mm/{uuid}_{원본파일명}` (기존 `channels/{channelId}/...` 키는 DB에 남아 있으면 다운로드 시 그 경로로 조회)
- 상태 전이/예외 케이스:
  - 비멤버 접근 불가
  - 파일명 경로 조각(`..` 등) 제거·검증
- 권한/보안:
  - 멤버십만 검증; 요청 `employeeNo`와 JWT subject 정합은 RBAC 단계에서 보완
- 성능:
  - 목록은 페이지 크기 100으로 상한 고정(대량 조회로 인한 응답·직렬화 부담 완화)
  - 파일 허브 **이미지** 탭: 스크롤 패널에 **보이는 셀만** 썸네일 요청(IntersectionObserver, root=`#fileHubPaneImages`). 처음 **전체 파일** 탭만 연 경우 **이미지** 탭으로 바꿀 때 placeholder 셀을 재observe해 로드 누락 방지(`setFileHubTab`).
  - 인라인·라이트박스용 `blob:` URL은 `app.js`의 `imageAttachmentBlobUrls`에 캐시해 **같은 채널 재입장·타임라인 갱신** 시 불필요한 재다운로드를 줄임(세션당 **약 120개** LRU, `clearSession` 시 전부 `revoke`)
  - 스레드 **루트별 댓글 수**(`threadCommentCount`)는 `MessageService.aggregateThreadCommentsForRoots`에서 **COMMENT_*** 메시지만 루트 id로 합산(타임라인 **답글 REPLY_*** 제외·동일 id 중복 제거). `GET .../messages/{rootId}/replies`도 원글 부모일 때 COMMENT_*만 반환
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
  - **통합 피커**: 채널 생성·DM 생성·구성원 추가 모두 **동일한 `+` 버튼 기반 팝업**(`modalAddMemberPicker`)을 사용합니다. 팝업에서 좌측 조직도(회사>본부>팀) + 우측 검색/결과로 사용자 선택 후 상위 모달의 선택 태그에 반영됩니다. 구성원 추가 `+`는 텍스트 대신 **SVG 십자**(`btn-picker-plus-icon`)로 그려 버튼 박스 안에 시각 중심을 맞춤.
  - **사이드바 조직도 모달**(`modalOrgChart`)·**구성원 추가 피커**·**관리자 사용자 관리** 표 모두 동일 정렬: 직책에 `팀장` 포함 시 최상단 → `jobLevel`(또는 관리자 화면의 직급 표시명) 문자열 기준 부장→차장→과장→대리→사원→인턴→기타 → 동일 조건은 이름 가나다(`sortOrgDirectoryMembers`).
  - 멤버 패널: `department`·`jobLevel`을 한 줄 요약, `jobPosition`·`jobTitle`은 값이 있을 때만 추가 표시
  - 멤버 패널: 개설자 사번(`createdByEmployeeNo`)과 일치하는 멤버에 `개설자` 배지 표시
  - 파일 업로드 성공 시: **비이미지**는 메시지 행 안에 **카드 리스트**(아이콘·파일명·크기 + **저장** / **저장 후 열기**만; **뷰어로 열기 없음**). **저장 후 열기**: **브라우저**에서는 먼저 **다운로드**(`<a download>`) 후 짧은 지연 뒤 blob **새 탭** 열기. **ECH 데스크톱(Electron)** 에서는 **저장 대화상자**로 경로를 고른 뒤 디스크에 쓰고 `shell.openPath`로 **OS 기본 앱** 실행(임시 폴더에 먼저 열지 않음). IPC `ech-save-file-and-open-default-app` / `electronAPI.saveFileAndOpenWithDefaultApp`. **이미지**는 같은 묶음 안에서 **2열 그리드** 썸네일 — **이미지 1장만**일 때는 그리드 한 칸이 아니라 **전체 폭**을 쓰도록 CSS(`:only-child` → `grid-column: 1 / -1`). 썸네일 클릭 시 `openChannelImageLightbox`. **연속 FILE 메시지**(같은 분·같은 발신자·스레드 댓글 없음)는 `tryConsumeFileAttachmentGroup` → `createFileAttachmentGroupRowFromMsgs`로 **한 말풍선**에 묶고, **일괄저장**은 **JSZip**으로 **ZIP 한 파일** 다운로드(`batchDownloadChannelImageFiles`; CDN `jszip`). **FILE 메시지 직후** 다음 타임라인 메시지는 **항상 아바타 행**을 새로 띄움(`shouldShowAvatarForMessage`에서 이전이 FILE이면 true, 실시간 `appendMessageRealtime`에서 직전 행이 `msg-has-attachment`면 아바타 표시)
  - **연속 이미지(같은 분·같은 발신자)**: 서버는 파일별 `FILE` 메시지로 저장하되, 타임라인에서는 **2장 이상**이면 **2열 그리드**로 묶어 표시(스레드 댓글이 달린 메시지는 묶지 않음). 하단 **일괄저장**은 **ZIP**(`attachments-{channelId}-{timestamp}.zip`)
  - **DM 채팅 헤더**: `#chatChannelPrefix`에 사이드바 DM 목록과 동일하게 `dmSidebarLeadingHtml` 기반 **프레즌스 점**(그룹 DM은 최대 3명 + `+N`). 멤버 API 로드 후 `updateChatHeaderDmPresence`로 갱신
  - **대용량 첨부·이미지(프론트)**: 미리보기는 큰 이미지를 다운스케일한 blob URL로 표시해 UI 멈춤을 줄임; 약 **2MB 초과** 이미지는 업로드 전 최대 변 길이 4096px·JPEG 재압축(애니 GIF는 원본 유지); 업로드는 `XMLHttpRequest`로 **진행률** 표시, 성공 후 타임라인·파일 허브 갱신은 병렬 처리(`buildImagePreviewObjectUrl`, `maybeCompressImageForUpload`, `uploadFileWithProgress`)
  - **줄바꿈**: 메인·스레드 입력은 **`textarea`** — **Enter** 전송, **Shift+Enter** 개행; 서버에 저장된 본문의 줄바꿈은 타임라인에서 `<br>` 로 표시(`formatMessageWithMentions` 후 `\n` 치환)
  - **본인 말풍선**: 발신자가 로그인 사용자와 같으면 **오른쪽 정렬**·**아바타·이름 행 숨김**(`msg-mine`·`msg-body--mine`), 텍스트·파일·이미지 첨부 행 공통. **시각**은 본문·첨부 푸터에서 **말풍선(콘텐츠)의 왼쪽**에 오도록 배치(텍스트는 `inline-flex`+`row-reverse`, 첨부 푸터는 `flex-start`/그룹은 `row-reverse`). **텍스트 줄**은 배경 말풍선(보라 톤/라이트는 인디고 틴트)·테두리·그림자로 구역을 구분하고, 본인 말풍선 **안쪽 본문은 좌측 정렬**로 읽기 쉽게 맞춤
  - **채널별 작성 중**: 다른 채널로 전환 시 현재 채널의 **입력 텍스트·답글 대상·대기 첨부 파일**을 `composerDraftByChannelId`에 저장하고, 돌아오면 복원; 로그아웃 시 맵 초기화
  - **「새 메시지」구분선**: 읽음 앵커 이후 미읽음이 있을 때 표시; 사용자가 입력창에 **내용을 입력**하거나 **메시지·파일 전송에 성공**하면 제거. 본인 전송 직후 타임라인 `loadMessages`는 `skipNewMsgsDivider`로 구분선을 다시 넣지 않음(파일 업로드 경로 `skipNewMsgsDividerAfterReload`)
  - 채팅 패널(`#viewChat`) 포커스 상태에서 클립보드에 이미지가 있으면 **붙여넣기(Ctrl+V)** 로 로컬 파일 선택과 동일하게 첨부 미리보기에 올린 뒤 전송 버튼(또는 Enter)으로 업로드·`FILE` 메시지 생성(열린 모달·`modal-overlay` 포커스일 때는 기본 붙여넣기 유지). 클립보드에 **이미지가 여러 개**면 모두 큐에 넣어 순차 업로드
  - **드래그 앤 드롭**: 채팅 본문 영역(`#viewChat`)에 파일을 끌어다 놓으면 동일한 첨부 큐로 반영(다른 모달이 열려 있으면 드롭 무시). **첨부 버튼·`<input type="file" multiple>`** 으로도 여러 파일 선택 가능; 전송 시 파일별로 `FILE` 메시지가 순서대로 생성
  - **스레드 댓글**: 스레드 입력의 파일 input도 `multiple`이며, 선택한 파일을 큐에 두고 순차 업로드(메인 채팅과 동일한 업로드·갱신 패턴)
  - **날짜 구분선**: 초기 목록(`renderMessages`)과 동일하게 로컬 날짜가 바뀔 때 pill 표시. **실시간**(`appendMessageRealtime`)은 마지막 DOM 형제가 시스템 메시지여도 뒤에서 마지막 채팅 행·이전 날짜 키를 찾아 구분선·같은 분 묶음을 맞춤; `channel:system`은 서버 `createdAt`이 있을 때 구분선 정합
  - UI: CSS 변수 기반 **다크·보라 액센트** 톤(모달·관리자·검색·조직도 블록 포함)
  - **테마 선택**: 로그인 사용자 영역의 톱니바퀴 버튼으로 팝업을 열어 `검정`(기본 다크·보라) / `하양`(라이트·인디고) / `오션 다크`(심야 블루 계열) / `크림 라이트`(웜 뉴트럴 계열) 선택. 즉시 적용되며 기본 테마(`검정/하양`)는 `PUT /api/auth/me/theme`로 사용자별 DB(`users.theme_preference`)에 저장
- 상태 전이/예외 케이스:
  - 중복 멤버 추가 시 서버 검증 메시지를 시스템 메시지로 노출
  - 사이드바에는 별도 **로그아웃** 버튼이 없음(세션 종료는 브라우저/앱 탭·창 종료 또는 `localStorage`/쿠키 삭제 등 운영 정책에 따름)
- 구현 파일:
  - `frontend/index.html`
  - `frontend/app.js`
  - `frontend/styles.css`
  - `backend/src/main/java/com/ech/backend/api/channel/dto/ChannelMemberResponse.java`
  - `backend/src/main/java/com/ech/backend/api/channel/ChannelService.java`
  - `backend/src/main/java/com/ech/backend/domain/channel/ChannelType.java` (`DM` 포함)

---

## 관리자 사이드바 레일 (2026-04-09)
- `#adminSection`은 `design/ECH화면설계 (7)` Admin Console 톤에 맞춰 상단 구분선·라이트 시 은은한 인디고 틴트(`sidebar-section--admin`), 항목은 Material 아이콘 + 라벨(`sidebar-item--admin-nav`).
- `showView` 호출 시 `syncAdminSidebarActive(viewId)`로 `viewOrgManagement`·`viewUserManagement`·`viewReleases`·`viewSettings` 중 하나와 사이드바 네 항목의 `.active`를 맞추고, 채팅/환영 등 비관리 뷰에서는 관리 메뉴 활성을 해제한다.

## 글로벌 바·시작 화면·워크플로우 진입 (2026-04-09 개정, 2026-04-09 워크플로우 명칭 통합)
- **대시보드** 상단 탭은 제거. **ECH** 로고는 `#btnAppShellHome` — 클릭 시 `clearActiveChannelAndReload()`로 슬림 시작 화면(`#viewWelcome`, `.ech-home-*`)으로 복귀.
- 시작 화면은 대형 히어로·3열 카드 대신 **한 카드** 안내(채널 목록 포커스, 상단 검색, 조직도). 로그인 후 제목은 `안녕하세요, {이름}님` 또는 `시작하기`.
- 상단 **워크플로우**(`btnTopNavProjects`)·좌측 **워크플로우**(`btnSidebarWorkflow`)는 `openWorkHubFromTopNav(panelFocus)` — 채널 있으면 바로 모달, 없으면 `getDefaultChannelForWorkHub` → `selectChannel`. 기본 사이드 진입은 업무 섹션으로 스크롤(`pendingWorkHubPanelFocus`·`#workHubPanelWork`).
- 통합 검색은 **상단** `appHeaderSearchInput`만 사용(사이드바 검색 필드 제거).
- 채팅 헤더 `btnOpenWorkHub`는 **현재 채널 필수**(패널 포커스 없음).

---

## 채널 업무 허브 UX 보강 (2026-04-01)
- 자동완성 목록 가독성:
  - 칸반 담당자 자동완성 목록 항목에 좌우 패딩을 추가해 텍스트가 경계선에 붙지 않도록 조정
- 카드 순서 변경:
  - 카드별 `↑/↓` 버튼 방식은 제거
  - 칸반 컬럼 내 카드 세로 드래그앤드롭으로 임시 순서를 변경하고, 저장 시 `sortOrder`로 반영
- 저장 동작:
  - 하단 저장 버튼 클릭 시 확인창(`저장하시겠습니까?`)을 표시
  - 저장 성공 후에도 **워크플로우** 모달은 닫지 않고 유지(연속 편집·확인 용이)
  - 칸반 담당자 저장 순서를 `삭제 -> 추가`로 처리해 삭제 후 재바인딩되는 케이스를 완화
  - 저장 직전 카드별 담당 변경 연산을 정규화해(add/remove 충돌 제거), 담당 해제 후 저장 시 기존 담당이 다시 붙는 현상을 보정
  - 정규화 단계에서 로컬 보드 트리에서 카드를 찾지 못하면 서버 기준 assignee diff가 비어 담당 `DELETE`가 생략될 수 있어, `id`/`cardId` 복합 탐색·pending 맵 키 정규화·카드 미탐색 시 스냅샷 해제 목록 폴백으로 보정
- 좌측 사이드바:
  - **워크플로우** 고정 진입(`sidebar-section--hub-shortcuts`)과 별도로 **담당 업무 목록** 섹션(`myKanbanList`)을 둔다.
  - `GET /api/work-items/sidebar/by-assigned-cards?employeeNo=...&limit=...`로 **내가 칸반 카드 담당으로 지정된 업무 항목** 목록을 조회해 렌더(채널 연동 보드·`kanban_card_assignees` 기준)
  - 목록 항목 클릭 시 채팅 채널 전환 없이 `workHubScopedChannelId`로 **워크플로우** 모달을 열고 해당 행·카드를 스크롤·강조

---

## 채널 헤더/운영 UX 보강 (2026-04-01)
- 헤더 액션 구조:
  - 우측 상단 개별 액션 버튼을 햄버거 메뉴(`btnHeaderMenu`) 기반으로 통합
  - 햄버거 패널에서 채널 액션과 멤버 목록을 함께 제공
- 관리자 명칭/기능:
  - 멤버 목록 배지 텍스트를 `관리자`로 통일
  - 관리자 전용 채널명 변경 API 추가: `PUT /api/channels/{channelId}/name` body `{ name }`
- 관리자 위임:
  - 프론트에서 사번 직접 입력을 제거하고 멤버 우클릭 컨텍스트 메뉴 기반으로 위임
  - 백엔드 위임 API는 기존 `POST /api/channels/{channelId}/delegate-manager`를 사용
- 공통 알림창:
  - `uiAlert/uiConfirm/uiPrompt` 공통 함수를 통해 중앙 모달형 메시지창 제공
  - 주요 저장/삭제/운영 액션의 브라우저 기본 `confirm/alert`를 대체
- DM 안정화:
  - DM 나가기 시 마지막 멤버여도 채널 삭제를 수행하지 않고 이력 보존
  - 1:1 DM 재사용 탐색에 canonical 이름 매칭을 추가해 중복 생성 가능성을 완화

---

## 업무 허브 상세 편집/드래그 가시성 보강 (2026-04-01)
- 상세 편집:
  - 업무 항목 클릭 시 `modalWorkItemDetail`에서 제목/설명/상태 편집
  - 칸반 카드 클릭 시 `modalKanbanCardDetail`에서 제목/설명/컬럼/담당자 편집
  - 저장 즉시 API 호출 대신 기존 임시 맵(`workHubPending*`)에 반영하고 하단 저장 버튼에서 일괄 반영
- 드래그 가시성:
  - 칸반 카드 드래그 중 삽입 예상 지점 카드에 `kanban-drop-before` 스타일을 적용해 이동 위치 인지성 개선
- 채널 운영 안정화:
  - 관리자 위임 시 `createdBy` 전환만 수행하고 멤버 레코드 재생성을 제거해 FK 제약 오류 회피
  - 채널 폐쇄는 멤버/읽음 상태 정리로 목록 비노출 처리(제약 오류 회피 우선)

---

## 데스크톱 (Electron) Windows 자동 업데이트
- 목적: 설치형(NSIS) 클라이언트가 GitHub Releases(또는 내부망 generic 피드)에서 새 버전을 감지·내려받고, **다운로드 완료 시 메인 창에 모달**(`modalAppUpdate`)로 안내 — **확인** 시 `quitAndInstall`로 즉시 설치·재시작. **나중에**는 모달만 닫고, 이후 트레이에서 종료 시 `autoInstallOnAppQuit`로 적용 가능
- 사용자: Windows에 ECH 데스크톱을 설치한 사용자
- 관련 화면/경로: `desktop/main.js` — 메인 창 제목에 `app.getVersion()` 표시(`did-finish-load` 후 `setTitle`), 트레이 툴팁에도 `ECH v{version}`. 패키지 실행(`app.isPackaged`) 시에만 `electron-updater` 초기화
- 관련 API: GitHub Releases API(런타임) — `GET https://github.com/{owner}/{repo}/releases/latest` 계열로 메타 조회; 실제 파일은 릴리즈 에셋
- 관련 Socket 이벤트: 해당 없음
- 입력/출력:
  - `desktop/package.json`의 `build.publish`에 `provider: github`, `owner`, `repo` 설정(메타 생성·업데이터 URL 결정에 사용)
  - 빌드 산출물: `desktop/dist/latest.yml`, 설치 파일(`latest.yml`의 `path` 필드와 동일한 파일명), 권장 `*.exe.blockmap`
  - 배포: 동일 Git 태그 릴리즈에 위 파일들을 **모두** 에셋으로 올림(`tools/publish-electron-github-release.ps1`가 `latest.yml`의 `path`(URL용 파일명)로 에셋 이름을 맞추고, 로컬에 공백 포함 `ECH Setup {version}.exe`만 있을 때는 그 파일을 읽어 동일 에셋 이름으로 업로드)
- 상태 전이/예외 케이스:
  - **exe만** 릴리즈에 있고 `latest.yml`이 없으면 업데이터가 새 버전을 찾지 못함(기존 동작)
  - 개발 모드(`npm start`)에서는 업데이터 비활성화
  - 코드 서명이 없으면 Windows SmartScreen 경고는 남을 수 있음(업데이터 자체와는 별개)
- 권한/보안: Private 저장소면 사용자 환경에 GitHub 인증이 없어도 **공개 릴리즈 에셋**은 URL로 내려받기 가능; 비공개 배포면 별도 토큰/서버 검토 필요
- 로그/감사 포인트: 업데이터 오류는 메인 프로세스 콘솔 `[ECH] autoUpdater error:` 로그
- 테스트 기준:
  - 이전 버전 설치 후 상위 버전 릴리즈에 `latest.yml`+설치파일(+blockmap) 업로드 → 앱 기동 또는 주기 점검(6시간) 후 다운로드 완료 시 모달 노출·확인 시 재기동 적용(또는 종료 시 적용)
- 비고: `npm run build:win`은 `--publish never`로 업로드는 하지 않되, `publish` 설정이 있으면 `latest.yml` 등이 `dist`에 생성됨
- **내부망(인터넷/GitHub 불가)**: 설치 디렉터리의 `ech-server.json`에 `serverUrl`(예: `http://ech.co.kr:8080`) 또는 `updateBaseUrl`(예: `http://host:8080/desktop-updates/`)이 있으면 `electron-updater`가 `generic` 피드로 전환되어 **백엔드** `GET /desktop-updates/latest.yml` 및 동일 베이스 URL의 설치 파일을 사용한다. 서버에는 `{APP_RELEASES_DIR}/desktop`(또는 `DESKTOP_UPDATE_DIR`)에 `latest.yml`·`ECH-Setup-{version}.exe`를 배치하고, yml의 `path`와 실제 파일명을 일치시킨다. 상세: `docs/DEPLOYMENT_WINDOWS.md`, `DesktopUpdateResourceConfig`.
- **동료·운영자용 요약**: 동작 한눈에(비유·흐름·역할 표)는 `docs/DEPLOYMENT_WINDOWS.md`의 **「데스크톱 앱 자동 업데이트(내부망)」** 절 **「동작 개념」** 을 참고한다.
- **이미지 다운로드 선택 모달**(`modalImageDownloadChoice`): **원본** 옆 용량은 `channel_files.size_bytes`. 서버에 `preview_storage_key`가 있으면 **압축본(미리보기)** 옆에 `preview_size_bytes`를 표시하고 `GET .../download?variant=preview`로 내려받는다. 미리보기가 없는 구 첨부는 **압축본**이 브라우저 JPEG 재인코딩이며 용량은 실행 전까지 대략 안내만 한다.

---

## 데스크톱 (Electron) 부팅·로그인 시 자동 실행
- 목적: Windows 로그인(또는 부팅 후 첫 로그인) 시 ECH 데스크톱을 자동 실행
- 사용자: NSIS 설치본(`app.isPackaged`) 사용자. 개발 모드(`electron .`)에서는 등록 대상이 아니며 트레이 메뉴 항목이 비활성
- 관련 경로: `desktop/main.js` — `app.setLoginItemSettings` / `getLoginItemSettings`, `path: process.execPath`. 트레이 **우클릭** 메뉴에 체크박스 — Windows 문구 **Windows 시작 시 실행**, macOS **로그인 시 자동 실행**, 그 외 **시작 시 자동 실행**. 메뉴를 열 때마다 `buildTrayMenu()`로 현재 OS 등록 상태를 반영
- IPC(선택 UI 연동): `ech-get-open-at-login`, `ech-set-open-at-login` → `preload.js`의 `electronAPI.getOpenAtLogin` / `setOpenAtLogin`
- 예외: 조직 GPO 등으로 **사용자 시작 프로그램**이 막히면 OS에 따라 등록이 실패하거나 무시될 수 있음. Windows **설정 → 앱 → 시작 프로그램**에서 동일 항목 확인 가능

---

## AD 자동 로그인 (Phase 5-1, 2026-04-03)

- 목적: AD 도메인 가입 사내 PC에서 Electron 앱 실행 시 별도 로그인 없이 자동 인증
- 사용자: 사내 AD 도메인에 조인된 Windows PC를 사용하는 모든 직원
- 관련 화면/경로:
  - `desktop/main.js` — IPC 핸들러 `get-windows-username` (Node `os.userInfo().username` 취득)
  - `desktop/preload.js` — `electronAPI.getWindowsUsername()` 렌더러에 노출
  - `frontend/app.js` — `tryAdAutoLogin()`: 앱 초기화 시 Electron 환경 감지 → AD 로그인 시도
- 관련 API:
  - `POST /api/auth/ad-login` — 사원번호 수신, DB 존재 + ACTIVE 상태 검증 후 JWT 발급
  - 요청 body: `{ "employeeNo": "사원번호" }`
  - 응답: `LoginResponse` (JWT 토큰 + 사용자 기본 정보)
- 인증 흐름:
  1. Electron main.js: `os.userInfo().username`으로 Windows 로그인 계정명 취득
  2. 렌더러: `electronAPI.getWindowsUsername()` 호출
  3. `tryAdAutoLogin()`: 취득된 username을 `employeeNo`로 삼아 `/api/auth/ad-login` 호출
  4. 백엔드: DB에서 해당 사원번호 조회 → ACTIVE 여부 확인 → JWT 발급
  5. 토큰을 `sessionStorage`에 저장 후 메인 화면 진입
  6. AD 로그인 실패(미등록·비활성) 시 일반 로그인 화면으로 폴백
- 보안 고려사항:
  - AD 조인 PC의 Windows 세션을 신뢰 기반으로 삼으므로, 비AD 환경에서는 사원번호를 임의 입력할 수 있음 → 운영 환경에서는 반드시 AD 조인 PC에서만 배포
  - 일반 로그인(`POST /api/auth/login`)도 INACTIVE 계정은 차단
- 예외 케이스:
  - 미등록 사원번호: `"이 PC의 Windows 계정(XXX)은 시스템에 등록되지 않았습니다."` 오류 → 일반 로그인 화면
  - INACTIVE 계정: `"비활성화된 계정입니다."` 오류 → 일반 로그인 화면
  - Electron 외 환경(웹 브라우저): AD 자동 로그인 시도 없이 일반 로그인 화면

---

## 관리자 사용자 관리 (Phase 5-2, 2026-04-03)

- 목적: 관리자가 직접 사용자 계정을 CRUD하고, 조직 정보(부서/직급/직위/직책)를 관리
- 사용자: ADMIN 역할을 가진 관리자
- 관련 화면/경로:
  - 사이드바 관리자 메뉴 → "사용자 관리" 항목
  - `frontend/index.html` `#viewUserManagement` 뷰, `#modalAdminUserEdit` 편집 모달
  - `frontend/app.js` — 사용자 관리 함수 블록 (`loadAdminUsers`, `renderAdminUserTable`, `openAdminUserEditModal`, `saveAdminUsers` 등)
  - `frontend/styles.css` — 사용자 관리 테이블·모달 스타일
- 관련 API (모두 ADMIN 전용):
  - `GET /api/admin/users` — 전체 사용자 목록 + 조직 정보 조회
  - `GET /api/admin/users/org-options` — 부서(TEAM)/직급(JOB_LEVEL)/직위(JOB_POSITION)/직책(JOB_TITLE) 드롭다운 옵션
  - `POST /api/admin/users` — 사용자 신규 등록
  - `PUT /api/admin/users/{employeeNo}` — 사용자 정보/상태/조직 수정
  - `DELETE /api/admin/users/{employeeNo}` — DB 하드 삭제
- 주요 기능:
  - **테이블 뷰**: 사원번호/이름/이메일/역할/상태/부서/직급/직위/직책 표시
  - **배치 저장**: 신규 추가·편집·삭제 모두 "저장" 버튼 클릭 시 일괄 반영 (중간 변경은 메모리에 보류)
  - **상태 관리**: ACTIVE(사용)/INACTIVE(미사용) 전환 — INACTIVE 계정은 로그인 불가
  - **하드 삭제**: DB에서 완전 삭제 (복구 불가; `org_group_members` 조직 배정도 함께 삭제)
  - **조직 배정**: 부서(TEAM)/직급(JOB_LEVEL)/직위(JOB_POSITION)/직책(JOB_TITLE)별 드롭다운 선택
- 데이터 모델:
  - `users` 테이블: `employee_no`, `email`, `name`, `role`(MEMBER/ADMIN), `status`(ACTIVE/INACTIVE)
  - `org_group_members` 테이블: `(employee_no, member_group_type)` UNIQUE — 타입당 하나의 그룹 배정
  - 부서/직급/직위/직책 정보는 `org_groups` → `org_group_members` JOIN으로 조회
- 상태 전이/예외:
  - 사원번호 중복 시 `POST` 409 오류
  - 존재하지 않는 사원번호로 `PUT`/`DELETE` 시 404 오류
  - ADMIN 본인 계정 삭제 방지는 현재 구현되어 있지 않음 (운영 주의)
- 보류 알림 배너: 저장 전 변경 건수를 배너로 표시, 취소 시 전체 초기화

---

## UI 구역 (ECH 화면설계 연동)
- 목적: `design/ECH화면설계 (1)~(9)` Stitch 목업과 실제 앱 구역을 추적 가능하게 맞춤
- 관련 화면: 채팅 `#viewChat`, 워크플로우 페이지 `#modalWorkHub`, 관리자 `#viewReleases`·`#viewUserManagement`·`#viewOrgManagement`·`#viewSettings`
- 마크업 훅: `.ech-region--chat`, `.ech-chat-header`, `.ech-messages-wrap`, `.ech-composer-bar`, `.ech-workhub-shell`, `.ech-region--admin`, `data-ech-design-ref` (예: `admin-releases`, `screen7-users`, `screen5-org`, `screen8-settings`)
- 스타일: `frontend/styles.css` — 시맨틱 스타일 우선(Tailwind 미로드 시에도 동작). `ech-tailwind.css` 갱신: `cd frontend && npm run build:css`
- 테스트 기준: 라이트 테마에서 채팅·칸반 모달·관리자 탭 전환 시 레이아웃 깨짐 없음
- 비고: 상세 매핑 표는 `docs/DESIGN_SYSTEM.md` 섹션 6
