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

## 2-0) 「서버 내부 오류」·채널 목록 500 / 채팅방 메시지 로드 500
- 재현 예: `InvalidDataAccessResourceUsageException`, PostgreSQL `bigint = character varying` — **`channel_members.user_id` 또는 `messages.sender_id`가 아직 `users.id`(bigint)만 참조**하는 레거시 DB인 경우, JPA 매핑(`employee_no` FK)과 충돌한다. **권장**: `docs/sql/migrate_user_refs_id_to_employee_no.sql` 이관. **임시**: 앱이 `information_schema`로 bigint 여부를 보고 `GET /api/channels`·채널 메시지/스레드 조회·파일 메시지 멤버십 확인에 JDBC 보조 경로를 쓴다(`ChannelMemberUserIdColumnInspector`, `MessageService`). Inspector는 `public` 등 스키마를 넓게 조회해 `current_schema()`만으로 컬럼을 못 찾아 레거시 분기가 꺼지는 것을 완화한다.

## 2-0-1) 「서버 내부 오류」가 계속될 때 (일반)
- 백엔드 콘솔에 `Unhandled exception` 로그와 스택이 찍힌다(전역 예외 처리기).
- 기본 설정(`app.expose-error-detail=true`)에서는 API JSON `error.message` 끝에 `[예외요약]`이 붙는다. 운영은 `EXPOSE_ERROR_DETAIL=false` 권장.
- DB에 `error_logs` 테이블이 있으면 최근 행을 조회해 `error_code`, `error_class`, `message`, `path`를 확인한다.
- 브라우저 개발자 도구 콘솔: 새로고침 시 `/api/auth/me 실패` 로그에 응답 본문이 출력된다.

## 2) 로컬 실행 요약
1. Realtime 실행: `cd realtime && npm install && npm run dev`
2. Backend 실행: `cd backend && gradlew.bat bootRun` (Windows)
3. **Windows 배치(선택)**: 저장소 루트 **`start-ech-dev.bat`** — `curl`로 `:8080/api/health`·`:3001/health` 확인 후 미기동 서비스만 새 콘솔에서 실행. 단독: `tools/start-ech-backend.bat`, `tools/start-ech-realtime.bat` (`docs/ENVIRONMENT_SETUP.md` 5-2절).
4. 프론트 확인: **`http://localhost:8080/`** (백엔드가 `index.html` / `styles.css` / `app.js` 만 정적 제공). `file://` 로 `frontend/index.html` 을 열면 API·쿠키·CORS 이슈가 날 수 있음.
   - UI 테마(검정/하양)는 로그인 후 사용자 영역의 톱니바퀴 팝업에서 변경하며, 서버 `PUT /api/auth/me/theme`로 `users.theme_preference`에 저장됩니다(로그아웃/재로그인 후에도 사용자별 유지). `localStorage ech_theme`는 초기 페인트(FoUC 완화)용 캐시로 함께 사용합니다.
5. 환경 설정 상세: `docs/ENVIRONMENT_SETUP.md`

## 2-0-2-1) 초기 로그인 비밀번호(미설정 사용자)
- DB `app_settings` 키 **`auth.initial-password-plaintext`**: 비밀번호 해시가 없는 사용자에게 **서버 기동 시 한 번** 적용되는 평문 값. 관리자 **설정** 화면에서 변경 가능.
- 동일 **설정** 화면에서 `POST /api/admin/settings`로 **신규 키**를 추가할 수 있음(영문·숫자·`.`·`-`·`_`, 최대 100자, 중복 불가). 앱이 읽는 키는 `AppSettingsService`·`AppSettingKey`와 맞출 것.
- 이미 비밀번호가 있는 계정은 변경되지 않음. 값이 비어 있으면 `DataInitializer` 내장 기본(`Test1234!`)을 사용.

## 2-0-2) 데스크톱(Electron) 배포·자동 업데이트
- 빌드: `cd desktop && npm install && npm run build:win` → `desktop/dist/`에 NSIS 설치 파일, **`latest.yml`**, (생성 시) **`*.exe.blockmap`**
- GitHub 릴리즈: **설치 파일만** 올리면 `electron-updater`가 메타를 못 읽어 자동 업데이트가 동작하지 않음. 동일 태그에 `latest.yml`과 blockmap까지 올릴 것
- **GitHub 릴리즈에 보이는 “Source code (zip/tar.gz)”**: GitHub가 **태그 시점의 저장소 스냅샷**을 **자동으로 붙이는 항목**이며, `publish-electron-github-release.ps1`로 올리는 **설치 exe·`latest.yml`·blockmap**과는 별개입니다. 공개 저장소에서는 누구나 소스를 받을 수 있으므로 **민감하면 Private 저장소** 또는 **내부망 `desktop-updates/`만** 쓰는 배포를 권장합니다. **표준 GitHub 기능만으로는 비활성화·삭제가 어렵습니다.**
- 업로드 스크립트: `powershell -File ./tools/publish-electron-github-release.ps1` (환경변수 `GITHUB_TOKEN`). 인자 생략 시 태그는 `package.json`의 `version`에 맞춘 `v{version}`
- 첫 업데이터 포함 빌드는 사용자가 **한 번** 새 설치 파일로 수동 설치해야 할 수 있음
- **내부망(PC가 GitHub 접속 불가)**: `electron-updater` 기본값은 GitHub Releases라 자동 업데이트가 동작하지 않는다. 대응: (1) 설치 프로그램 옆 `ech-server.json`에 `serverUrl`(또는 `updateBaseUrl`)을 내부 백엔드로 두면, 업데이트 메타는 `http://{백엔드}/desktop-updates/latest.yml` 에서 받는다. (2) WEB 서버에 `C:\ECH\releases\desktop\`(또는 `DESKTOP_UPDATE_DIR`)에 `latest.yml`과 `ECH-Setup-{version}.exe`를 배포한다. 백엔드는 `DesktopUpdateResourceConfig`로 해당 디렉터리를 `/desktop-updates/**` 로 노출한다.
- **단일 인스턴스·아이콘**: `main.js`에서 `app.requestSingleInstanceLock()`으로 중복 실행을 막고, 재실행 시 기존 창 포커스. Windows는 EXE/작업 표시줄에 **`assets/icon.ico`** 임베드가 안정적이며, `prebuild:win`(`scripts/generate-icon-ico.mjs`)이 `icon.png`에서 ICO를 생성한다. 런타임은 Windows에서 `.ico`를 우선 로드하고 `app.setAppUserModelId('com.ech.desktop')`를 설정한다.
- **Windows 시작 시 실행**: 설치본에서 트레이 **우클릭** → **Windows 시작 시 실행**(`app.setLoginItemSettings`). 개발 모드에서는 비활성. `preload`에 `getOpenAtLogin` / `setOpenAtLogin`(웹 UI 연동 선택).
- **설치 경로(NSIS)**: `package.json` `build.nsis.perMachine: true` — 기본 **`%PROGRAMFILES%\ECH\`**. `ech-server.json`은 exe 옆 또는 **`%ProgramData%\ECH\ech-server.json`** (`readEchServerJson` 순서).

## 2-1) 개발자 / 운영·관리자 — 무엇부터 보면 되나
- **개발자**
  - API·도메인 동작: `docs/FEATURE_SPEC.md`, 엔드포인트 목록은 아래 **6)**.
  - 앞으로의 기능 순서: `docs/ROADMAP.md`, 요구 배경: `docs/PROJECT_REQUIREMENTS.md`.
  - DB 구조: `docs/sql/postgresql_schema_draft.sql` 및 **3-1)**. 로컬에서 사람 데이터가 필요하면 `docs/sql/seed_test_users.sql` (`docs/ENVIRONMENT_SETUP.md` 5-1절).
  - Java는 `backend/`, 실시간은 `realtime/`(Express 없이 `http`+Socket.io), 데모 UI 소스는 `frontend/`(로컬에서는 `bootRun` 시 8080에서 위 3개 파일만 서빙, **`/**` 전체를 정적으로 열지 않음** — `/api/**` 가 리소스 핸들러에 먹히는 404 방지).
- **운영·관리자**
  - 구성 요소: Java API 서버, Node 실시간 서버, PostgreSQL, (첨부는 외부 스토리지 연동 전제).
  - **첨부 저장 경로**: DB `app_settings` 의 `file.storage.base-dir` 이 `FILE_STORAGE_DIR` 보다 우선한다. 업로드 전부 실패 시 해당 값·폴더 권한·기동 로그 `[ECH] file storage` 를 확인. 절차: `docs/DEPLOYMENT_WINDOWS.md`(트러블슈팅·SQL 예시). UNC 사용 시 **백엔드 프로세스** 기준 쓰기 여부는 관리자 `GET /api/admin/storage/probe` 로 확인(대화형 PowerShell 성공과 불일치할 수 있음).
  - 가용성 확인: Backend `GET /api/health`, Realtime `GET /health`(DB 연계 여부 포함).
  - 인증·RBAC·감사·배포(WAR 업로드·롤백 등)는 로드맵 **Phase 3** 및 아래 **7)**에 예정 범위가 정리되어 있습니다.
  - 다중 서버로 Realtime을 늘릴 경우 Presence는 현재 메모리 기반이므로 공유 저장소(예: Redis) 검토가 필요합니다(아래 **6) Realtime 메모**).

## 3-0-3) 프론트 UX 메모 (2026-04-06)
- **DM 채팅 헤더**: `#chatChannelPrefix`는 DM일 때 멤버 로드 후 상대(그룹 DM은 최대 3명 + `+N`) **프레즌스 점**을 사이드바 DM과 동일 규칙으로 표시(`frontend/app.js` `updateChatHeaderDmPresence`, `dmSidebarLeadingHtml`).
- **이미지 다운로드**: 약 **512KB 이상**·GIF/SVG 제외 시 원본 vs JPEG 압축 선택 모달; 압축은 브라우저에서 `GET .../download` blob → 캔버스(최대 변 4096px) 저장(서버 전용 압축 API 없음).
- **데스크톱 GitHub 릴리즈 에셋 업로드**: 로컬에서 `cd desktop && npm run build:win` 후 `desktop/dist`에 `latest.yml`·`ECH-Setup-{version}.exe`·`.blockmap` 생성. `GITHUB_TOKEN`(repo releases 권한) 설정 뒤 `powershell -File ./tools/publish-electron-github-release.ps1 v1.1.0` — 태그·릴리즈 생성 및 에셋 업로드(`README.md` 자동 업데이트 절차 참고).
- **이미지 크게 보기·모아보기 성능**: 서버 `preview_*`가 있으면 라이트박스는 먼저 `/preview`로 표시 후 **원본 보기** 선택 가능(`openChannelImageLightbox`). 파일 허브 이미지 탭 썸네일은 보이는 셀만 로드(IntersectionObserver).
- **채팅 첨부(다중·DnD)**: 메인 채팅 `#viewChat`에 파일 **드래그 앤 드롭**, 첨부 `<input type="file" multiple>`(메인·스레드), 클립보드 이미지 **여러 장** 붙여넣기 → `pendingFilesQueue` / `threadPendingFilesQueue`로 미리보기 후 순차 업로드. 다른 모달이 열려 있으면 채팅 드롭은 처리하지 않음(`isModalOverlayBlockingChatDrop`). **연속 FILE 첨부 메시지**(같은 분·같은 발신자, 스레드 댓글 없음)는 타임라인에서 `tryConsumeFileAttachmentGroup` → `createFileAttachmentGroupRowFromMsgs`로 **한 묶음** + **일괄저장**(`batchDownloadChannelImageFiles`). **이미지**는 `buildImageGridHtml`·`wireImageGridThumbs`로 **2열 그리드** 썸네일, 클릭 시 `openChannelImageLightbox`; **비이미지**는 `buildAttachmentCardHtml`·`wireAttachmentCardActions`로 **카드**(저장·저장 후 열기만). **저장 후 열기**(`saveChannelFileAndOpenInNewTab`): 브라우저는 blob 새 탭; **Electron**은 `ech-open-temp-file-default-app` → 임시 파일 + `shell.openPath`(OS 기본 앱). 이미지+문서 혼합 묶음은 그리드 위·카드 아래로 같은 말풍선에 배치.
- **채팅 입력·말풍선**: `#messageInput`·`#threadMessageInput`은 **`textarea`** — Enter 전송·Shift+Enter 개행, 타임라인은 `\n`→`<br>`; 본인 메시지는 `msg-mine` 오른쪽·아바타 생략·**시각은 말풍선 콘텐츠 왼쪽**(텍스트 `row-reverse`, 첨부 푸터 정렬); 채널 전환 시 `composerDraftByChannelId`로 미전송 입력·답글 대상·대기 첨부 복원, 로그아웃 시 `clearSession`에서 맵 비움.
- **「새 메시지」구분선**: 입력·전송 시 `clearChatReadAnchorUi`; 본인 전송 후 `loadMessages`는 `skipNewMsgsDivider`/`uploadFile` `skipNewMsgsDividerAfterReload`로 재삽입 방지.
- **업무·칸반**: 비활성 업무 복원/완전 삭제는 **저장 시** `flushWorkHubSave`에서만 API — 목록·상세 공통 `queueWorkItemRestore`/`queueWorkItemPurge`. 업무 **✕(소프트 삭제)** 는 `workHubPendingWorkDeleteIds`에만 넣고 목록에 **삭제 예정** 배지로 남김(`cancelWorkItemDeletePending`). **완전 삭제** 시 서버가 칸반 카드를 먼저 지우므로, `queueWorkItemPurge`·`flushWorkHubSave`의 purge 직후 `clearPendingKanbanStateForWorkItem`/`collectKanbanCardIdsForWorkItem`로 해당 카드 ID를 pending 맵에서 제거(저장 시 잔여 `PUT /kanban/cards/{id}` 실패 방지). 확인창 `#modalAppDialog`는 `z-index: 1350`으로 중첩 모달 위.

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
  - 리얼타임: `message:send`의 `senderId`는 **클라이언트가 보내는 사원번호 문자열**을 그대로 검증·저장(구 숫자 `users.id` 경로 제거)
  - 운영 SQL: `docs/sql/migrate_user_refs_id_to_employee_no.sql` 적용 후 검증 필요(스크립트는 대상 컬럼의 FK를 먼저 떼고 컬럼 치환)
  - API 2차: 채널/메시지/파일/읽음상태 핵심 요청 파라미터를 `employeeNo` 기준으로 전환
  - API 3차: 칸반/릴리즈/설정/보존정책의 actor·creator·updater·uploader 식별자도 employeeNo 기준으로 확장
  - 프론트·소켓(2026-03-30): 프로필 조회 `?employeeNo=`, 프레즌스 키 `employeeNo`, 관리자 릴리즈/설정 요청 필드 정합

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
  - Realtime(Node `:3001`): `message:new`는 `io.to(channelId)` 브로드캐스트 — 클라이언트는 **참여 중인 채널마다** `channel:join`을 보내야 수신한다(프론트 `joinAllChannelSocketRooms`·`loadMyChannels` 후·소켓 재연결 시). 멘션은 소켓 직접 `mention:notify`라 join 범위와 무관
  - `GET /api/channels?employeeNo=...` — 내 채널/DM 목록. DM은 요약 `description`을 **조회자(employeeNo)를 제외한 멤버** 이름(없으면 사번)으로 계산; **`unreadCount`** 는 읽음 포인터 메시지보다 타임라인상 **더 최신**인 **루트**만 센다(`created_at`·`id` 기준, `MessageRepository.countRootMessagesNewerThanCursor`); **`lastMessageAt`** 는 채널별 루트 메시지 최신 시각(JSON ISO); **`dmPeerEmployeeNos`** 는 DM만 조회자 제외 멤버 **사번** 배열(사이드바 DM 프레즌스 점). 프론트는 `loadMessages`·실시간 수신 시 `POST .../read-state/mark-latest-root`로 **최신 루트까지 읽음**(대량 히스토리에서도 배지 즉시 해제), 채널 전환·스크롤 시 `localStorage` `ech_chat_scroll_v1_{employeeNo}`에 **스크롤 비율** 저장·복원; **퀵 레일**은 ECH 헤더 아래·검색~목록과 같은 세로 구간(`#quickContainer`·`#quickRailScroll`); **미읽음 우선·최근 대화** 최대 15개·미읽음만 배지(`compareQuickRailChannel`). **퀵 고정**: 우클릭 `퀵 레일에 고정` → `ech_quick_rail_pinned_{employeeNo}`에 ID 순서 저장·재렌더 시 앞쪽 고정(새 메시지로 순서 안 밀림), 나가기 시 ID 제거. 좌측 패널 펼침 **324px**(64+260), 접힘 **64px**(퀵만); **접기**는 `#btnSidebarEdgeToggle`·`ech_sidebar_collapsed`
  - `POST /api/channels` — 생성자는 JWT에서 식별: **`uid` 클레임(= `users.id`) 우선**, 없으면 사원번호, 레거시 토큰은 숫자-only subject를 DB id로 폴백(숫자 사번과 충돌 시 재로그인 권장). body는 `workspaceKey`, `name`, `channelType` 등, 선택 `createdByEmployeeNo`(하위 호환), DM 시 `dmPeerEmployeeNos` (`CreateChannelRequest`)
  - 동일 1:1 DM 조합은 기존 채널을 우선 재사용(레거시 내부명 편차가 있어도 멤버 조합 기준으로 재사용)
  - `GET /api/channels/{channelId}`
  - `POST /api/channels/{channelId}/members` — body: `employeeNo`, `memberRole` (`JoinChannelRequest`)
    - 권한: `PUBLIC/PRIVATE`는 채널 개설자(`created_by`)만 추가 가능, `DM`은 개설자가 아니어도 멤버 추가 가능
  - `DELETE /api/channels/{channelId}/members?targetEmployeeNo=...` — **개설자(`created_by` = JWT 사원번호)만** 다른 멤버 제거; 본인(개설자) 제거는 400
  - `GET /api/channels/{channelId}/read-state?employeeNo=...`
  - `PUT /api/channels/{channelId}/read-state` — body: `employeeNo`, `lastReadMessageId`
  - `POST /api/channels/{channelId}/read-state/mark-latest-root` — body: `employeeNo`(채널 최신 루트까지 읽음)
  - `GET /api/channels/{channelId}/files?employeeNo=...`
  - `POST /api/channels/{channelId}/files/upload?employeeNo=...` (multipart)
  - `POST /api/channels/{channelId}/files` (메타만)
  - `GET /api/channels/{channelId}/files/{fileId}/download?employeeNo=...`
  - `GET /api/channels/{channelId}/files/{fileId}/download-info?employeeNo=...`
- 메시지/스레드:
  - `GET /api/channels/{channelId}/messages?employeeNo=...` — 목록(프론트 기본)
  - `POST /api/channels/{channelId}/messages` — body: `senderId`(발신자 **사원번호** 문자열), `text`(멘션 토큰 `@{사번|표시명}` 가능 → `MentionNotificationService`가 채널 멤버 검증 후 Realtime `internal/notify-mentions` 호출)
  - `POST /api/channels/{channelId}/messages/{parentMessageId}/replies`
  - `GET /api/channels/{channelId}/messages/{parentMessageId}/replies`
- 사용자 검색·프로필·조직도:
  - `GET /api/users/search?q=...&department=...`
  - `GET /api/user-directory/organization`
  - `GET /api/users/profile?employeeNo=...` (프론트 기본)
  - `GET /api/users/profile?userId=...` (숫자 ID, 호환)
  - `GET /api/users/{userId}/profile` (호환)
- 칸반:
  - `POST/GET/GET{id}/DELETE /api/kanban/boards`, 컬럼·카드 CRUD, 담당자 추가/삭제, `GET /api/kanban/cards/{cardId}/history`
  - `GET /api/kanban/channels/{channelId}/board?employeeNo=...` — 채널 기본 보드 조회(없으면 자동 생성)
- 메시지→업무:
  - `POST /api/messages/{messageId}/work-items`, `GET /api/messages/{messageId}/work-items`, `GET /api/work-items/{workItemId}`
  - `GET /api/channels/{channelId}/work-items?employeeNo=...&limit=...`, `POST /api/channels/{channelId}/work-items`, `PUT /api/work-items/{workItemId}`
- 조직 동기화(관리):
  - `GET /api/admin/org-sync/users?source=TEST|GROUPWARE`, `POST /api/admin/org-sync/users/sync?source=...`
  - `PUT /api/admin/org-sync/users/{employeeNo}/status`
- 오류 로그(관리):
  - `GET /api/admin/error-logs?from=&to=&errorCode=&path=&limit=`
- 감사 로그(관리):
  - `GET /api/admin/audit-logs?from=&to=&actorUserId=&eventType=&resourceType=&workspaceKey=&limit=` (`actorUserId`는 DB `users.id` 숫자; 행위자 필터는 추후 `employeeNo` 확장 여지)
- Realtime 소켓:
  - `channel:join`
  - `message:send` (DB 저장 연계)
  - `message:new` (저장 성공 시 송신)
  - `channel:system` (백엔드 내부 HTTP로 브로드캐스트 — 멤버 참여·내보내기 등 `SYSTEM` 메시지, payload: `channelId`, `text`, `createdAt`, `messageId`)
  - `message:error` (유효성/저장 실패)
  - `mention:notify` (멘션된 채널 멤버에게만, payload: `channelId`, `channelName`, `channelType`, `senderName`, `messagePreview`, `messageId` — 클라이언트는 `presence:set`으로 등록된 소켓에만 수신; 프론트 토스트는 `#mainApp` 밖 DOM 권장)
  - `presence:set`
  - `presence:update`
  - `presence:snapshot` (해당 소켓에만, `presence:set` 처리 직후 전체 `{ data: [...] }` — 멀티 창/탭 시 상대 상태 누락 완화)
  - `presence:error`

### 채널·멤버·식별자 계약 메모
- **사용자 식별(클라이언트→서버)**: 채널/읽음/파일/메시지 목록 등 대부분의 쿼리·본문은 **`employeeNo`(사원번호 문자열)** 를 사용합니다. JWT subject도 사번 기준이며, 프론트 `app.js`는 이에 맞춰 동작합니다.
- 채널 **생성** 시 `createdByEmployeeNo`는 자동으로 해당 채널 멤버(`MANAGER`)로 등록됩니다.
- **DM** 생성 시 `dmPeerEmployeeNos`에 상대 사번 목록을 넣습니다(프론트 `startDmWithUser`).
- **멤버 추가**는 `POST .../members` body의 `employeeNo` + `memberRole`입니다.
- **멤버 추가 권한**: 일반 채널(PUBLIC/PRIVATE)은 개설자만, DM은 멤버면 추가 가능.
- **채널 운영 API**:
  - `PUT /api/channels/{channelId}/dm-name` — 다자간 DM(3인 이상) 이름 변경
  - `POST /api/channels/{channelId}/delegate-manager` — 개설자가 관리자 위임
  - `POST /api/channels/{channelId}/leave` — 나가기(개설자는 위임 대상 필요)
  - `DELETE /api/channels/{channelId}` — 개설자 채널 폐쇄
- 채널명은 워크스페이스 기준으로 유니크합니다. (`workspaceKey + name`)
- 멤버 중복 참여는 서버에서 차단합니다.
- DB 컬럼명 `channel_members.user_id` 등은 역사적 이름이며, **값은 `users.employee_no`** 와 FK로 연결됩니다(스키마 초안·마이그레이션 참고).

### Realtime 메시지 저장 연계 메모
- Realtime 서버는 PostgreSQL에 직접 연결해 메시지를 저장합니다.
- `message:send` payload는 숫자형 `channelId`, **발신자 사원번호 문자열** `senderId`, 문자열 `text`가 필요합니다.
- 본문 길이는 `MAX_MESSAGE_BODY_LENGTH`(기본 4000)를 초과하면 저장하지 않고 `MESSAGE_TOO_LARGE` 오류를 반환합니다.
- DB 저장 성공 시에만 `message:new`가 채널 룸으로 전송됩니다.
- `/health` 엔드포인트에서 DB 연결 상태(`db: ok/error`)를 함께 확인할 수 있습니다.
- `pg` Pool은 `DB_POOL_MAX`, `DB_POOL_IDLE_MS`, `DB_POOL_CONNECT_TIMEOUT_MS`로 조정할 수 있습니다.
- 백엔드는 커밋 후 `POST {app.realtime.internal-base-url}/internal/broadcast-channel-system`(선택 `X-Internal-Token` = `REALTIME_INTERNAL_TOKEN`)으로 채널 룸에 시스템 알림을 쏠 수 있다. 토큰 미설정 시 Realtime은 로컬 개발 편의상 인증 생략.
- 멘션: `POST .../internal/notify-mentions` — body `{ items: [{ targetEmployeeNo, channelId, channelName, channelType, senderName, messagePreview, messageId }] }`(동일 토큰 규칙). `message:send` 저장 직후에도 Realtime이 본문 파싱으로 동일 이벤트를 쏜다(소켓 전송 경로).
- 프론트 미확인 멘션 목록: 현재 채널이 아닌 곳에서 받은 `mention:notify`를 사용자별 localStorage 키(`ech_mention_inbox_{employeeNo}`)에 최대 100건 보관한다. 사이드바 `멘션` 섹션에서 미확인 목록만 표시하며, 항목 클릭 시 해당 채널로 이동하고 `targetMessageId`로 메시지 위치를 포커스한 뒤 목록에서 제거한다.
- 채팅방별 알림 끄기(프론트 전용): `ech_notify_muted_channels_{employeeNo}`에 음소거할 채널 ID 목록을 저장한다. 해당 채널/DM에서는 **다른 채에서 온 일반 신규 메시지 토스트**(`pushNewMessageToast`)만 억제한다. **멘션 토스트**는 항상 뜨며, **미읽음 배지**(`unreadCount`)도 음소거와 무관하다. 채널/DM/퀵 레일 우클릭 메뉴 또는 상단 햄버거(멤버 패널)에서 토글한다.
- 프론트 실시간 URL: 기본은 `{페이지 프로토콜}//{hostname}:3001`(API는 페이지 `origin`). `localhost` vs `127.0.0.1` 불일치로 소켓만 실패하는 경우가 있어 자동 맞춤한다. 운영은 `<meta name="ech-realtime-url">` 또는 `localStorage ech_realtime_url`로 지정 가능. HTTPS 페이지에서 HTTP 소켓은 차단될 수 있어 프록시/TLS 필요.

### 사용자 검색/Presence 인수인계 메모
- 사용자 검색은 `org_group_members(TEAM)` + `org_groups.display_name`(팀 표시명)을 사용해 부서 필터를 지원합니다.
- `GET /api/user-directory/organization-filters`는 `org_groups(COMPANY, is_active=true)` 기반으로 셀렉트 옵션(`label`, `companyGroupCode`)을 줍니다(첫 항목 전체이며 `companyGroupCode=null`).
- `GET /api/user-directory/organization?companyGroupCode=`로 선택 회사 트리만 내려보내며, 전체 옵션이면 쿼리 파라미터를 생략하여 전체 트리를 반환합니다.
- 검색 키워드(`q`)는 이름/이메일/사번에 대해 부분 일치 조회를 수행합니다.
- Presence는 Realtime 서버 메모리 기반으로 관리됩니다.
- `presence:set` / `presence:update` / `presence:snapshot`(소켓 전용 전체 목록) / `GET /presence` 스냅샷은 **`employeeNo`(문자열)** 를 키로 사용합니다(레거시 숫자 `userId`는 소켓에서 더 이상 유효하지 않음).
- 소켓별로 사용자를 추적하며, 해당 사용자의 **모든** 소켓이 끊기면 OFFLINE 브로드캐스트 후 메모리에서 제거합니다(유령 키 누적 방지).
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
  - MESSAGES: 본인이 속한 채널의 루트 텍스트 메시지 본문 (아카이브/삭제 제외, `COMMENT_*`/`REPLY_*`/`FILE_*` 제외)
  - COMMENTS: 본인이 속한 채널의 댓글(`COMMENT_*`) 본문 (아카이브/삭제 제외)
  - CHANNELS: 본인이 속한 채널의 채널명/설명
  - FILES: 본인이 속한 채널의 파일명
  - WORK_ITEMS: 업무 제목/설명 (워크스페이스 전체)
  - KANBAN_CARDS: 칸반 카드 제목/설명 (워크스페이스 전체)
- **성능**: `docs/sql/postgresql_schema_draft.sql`의 `CREATE EXTENSION pg_trgm` + GIN 인덱스 적용 권장
- **한계**: 현재 ILIKE 기반, 대규모 데이터셋에서는 전용 검색엔진(Elasticsearch 등) 도입 고려
- **구현 파일**: `backend/.../api/search/` (SearchService, SearchController, dto/)
- **프론트 결과 클릭 동작** (`frontend/app.js` `handleSearchResultClick`):
  - `MESSAGE`: `selectChannel(..., { targetMessageId })`로 채널 이동 + 메시지 포커스
  - `COMMENT`: 검색 응답의 `threadRootMessageId`가 있으면 채널 이동 후 `openThreadModal(threadRootMessageId, { targetCommentMessageId })`로 스레드 모달을 열고 대상 댓글/답글 위치(`thread-msg-{id}`)로 스크롤·강조. `threadRootMessageId`가 없으면 기존 채널 이동+메시지 포커스 폴백
  - `FILE`: `download-info` 조회 후 이미지면 원본 스트림 `blob:` URL로 `modalImagePreview` 오픈(다운로드 버튼은 `maybeDownloadChannelImageWithChoice`), 일반 파일은 `downloadChannelFile`로 즉시 다운로드
  - `CHANNEL`: 채널 카드 클릭과 동일하게 채널 진입
  - `WORK_ITEM`: `relatedChannelId`로 채널 전환 후 `업무 · 칸반` 모달 오픈, `data-work-item-id` 행 스크롤·강조
  - `KANBAN_CARD`: `relatedChannelId`가 있으면 동일하게 모달 오픈 후 `data-kanban-card-id` 카드 강조; 없으면(워크스페이스 전용 보드 등) 안내 토스트만
  - 검색 submit 시 `#searchTypeSelect` 값 유지(강제 `ALL` 리셋 제거)로 타입별 결과 오염 방지
  - DM 채널 결과 컨텍스트명은 내부 채널 키 대신 표시용 설명(상대방 이름 요약)을 우선 노출
  - 스레드 모달 원글이 타임라인 캐시에 없을 때는 `GET /api/channels/{channelId}/messages/{messageId}?employeeNo=...` 단건 API로 원글을 로드

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
- 채널 업무 허브는 `source_channel_id` 기준으로 목록을 조회하며, 채널 멤버만 생성/수정/조회 가능.

### 칸반 보드 인수인계 메모
- 보드는 `(workspace_key, name)` 유니크입니다.
- 카드 컬럼 이동은 동일 보드의 컬럼으로만 허용됩니다.
- `kanban_card_events`에 생성·이동·상태·담당 변경이 기록됩니다(카드 삭제 시 이력도 CASCADE 삭제).
- 보드 목록·카드 이력 API는 각각 100건 상한입니다.
- 채널 연동 보드는 `kanban_boards.source_channel_id`로 연결되며, 첫 조회 시 `할 일/진행 중/완료` 기본 컬럼을 자동 생성합니다.
- 카드 생성(`POST .../columns/.../cards`)·이동(`PUT /api/kanban/cards/{id}`)은 컨트롤러에서 `MEMBER` 이상이면 호출 가능하고, 서비스에서 채널 연동 보드는 **해당 채널 멤버**(`actorEmployeeNo`), 채널 미연동 보드는 **앱 역할 MANAGER 이상**으로 제한한다.
- 보드 상세 조회 시 카드 로드는 `findAllForBoardWithAssignees`에서 `assignees`를 **LEFT JOIN FETCH**한다. `JOIN FETCH`만 쓰면 담당자 0건 카드가 INNER JOIN처럼 빠져 UI에 안 보인다.
- 채널 허브 칸반 카드 담당: `POST/DELETE /api/kanban/cards/{id}/assignees`는 `MEMBER`+`assertCanMutateCard`(채널 보드는 채널 멤버, 워크스페이스 보드는 `MANAGER` 이상). 서비스는 채널 연동 보드에서 `assertAssigneeIsChannelMemberIfApplicable`로 담당 사번이 채널 멤버인지 검증. **채널 허브 UI**에서는 `GET /api/channels/{id}` 멤버 목록으로 담당 자동완성(↑↓·Enter, 열린 상태에서 미선택 Enter는 폼 제출 방지).
- 담당 제거(`removeAssignee`): `KanbanCard`의 `@OneToMany assignees`와 DB를 맞추기 위해, 자식 행만 `delete` 하면 영속 컨텍스트의 카드 컬렉션이 오래된 채로 남을 수 있음. `KanbanService`에서는 컬렉션에서 해당 `KanbanCardAssignee`를 제거(orphanRemoval)하거나, 컬렉션에 없을 때만 저장소 `delete`로 폴백한 뒤 `toCardResponse(card)`로 응답을 만든다.
- 업무 삭제: `DELETE /api/work-items/{workItemId}?actorEmployeeNo=...`(채널 멤버). 칸반 카드 삭제: `DELETE /api/kanban/cards/{cardId}?actorEmployeeNo=...` — 컨트롤러는 `MEMBER`, 서비스 `deleteCard`에서 `assertCanMutateCard`로 채널/워크스페이스 보드 구분.
- 업무 허브 모달: 하단 **저장**으로 신규 업무·신규 카드·업무 상태·카드 컬럼·담당 추가/해제(임시 맵)를 일괄 반영. 칸반 **컬럼만** 바꿀 때(DnD·카드 행 셀렉트·카드 상세) 연결 업무의 pending 상태(`workHubPendingWorkStatus`)를 `statusForKanbanColumnId`로 맞춰, 저장 시 업무 `PUT`과 카드 `PUT`이 같은 단계(OPEN/IN_PROGRESS/DONE)로 나가게 한다. DnD는 **`section.kanban-column` 전체**에 `dragover`/`drop`을 걸고 `.kanban-card-list`를 flex로 세로 채워 카드 아래 빈 영역에서도 드롭되게 한다. 칸반 카드는 **`article` 전체 `draggable`** 이지만 `dragstart`에서 폼 컨트롤·버튼·담당 검색 영역이면 취소; 드롭 후 **`rebuildKanbanCardColumnSelectDom`** 으로 컬럼 `<select>`를 새 노드로 갈아 끼워 Chrome 표시 불일치를 줄인다. 컬럼 `<select>` `change`는 **`ensureKanbanBoardColumnSelectChangeDelegated`** 한 번만 바인딩. **칸반 카드는 `work_item_id`로 업무에 종속**되며 신규 카드 생성 body에 `workItemId`가 필수다. 업무 삭제 API는 기본 **소프트 삭제**(`work_items.in_use`), `hard=true` 시 연결 카드 삭제 후 업무 행 삭제. 보드에서 ✕/검색 추가는 UI만 즉시 갱신하고 담당 API는 저장 시 `POST/DELETE .../assignees` 순서. **레이아웃**: `modal-work-hub`는 `max-height: min(90vh,100dvh-24px)`·헤더/푸터 고정, `work-hub-panel-body` 안에서만 세로 스크롤(설치형 창 축소 대응).
- 프론트 `loadChannelKanbanBoard()`는 DnD/저장/셀렉트 등으로 중첩 호출될 수 있다. **`kanbanBoardFetchGenByChannelId`**로 채널별 요청 세대를 올리고, 완료 시점에 세대·현재 허브 채널이 맞을 때만 `renderKanbanBoard`를 호출해, 늦게 도착한 **이전 GET 응답**이 UI를 덮어쓰지 않게 한다. **연속 DnD**는 `drop` 직후 rAF 전에 세대를 한 번 더 올려(컬럼 `<select>` 변경도 동일), 직전 조회 응답이 다음 드롭 DOM을 덮는 레이스를 막는다. **컬럼 DnD `drop` 핸들러**에서는 카드가 이미 DOM에서 옮겨졌으므로 보드 GET/풀 렌더를 하지 않고 `sync*` + `loadChannelWorkItems`만 호출한다(셀렉트 변경·모달 오픈 등은 기존처럼 보드 조회). DnD 후 행 컬럼 `<select>`는 **`applyKanbanColumnSelectToColumnId`(`selectedIndex`)** 와 **`setTimeout(0)` 뒤 재 `sync*`** 로 호스트 컬럼과 맞춘다.

### 채널 파일 메타데이터 인수인계 메모
- 바이너리는 외부 스토리지에 두고 `channel_files`에 메타만 저장합니다. **`preview_storage_key` / `preview_size_bytes`** 는 업로드 시 multipart `preview`가 있을 때 채워집니다(스키마: `docs/sql/migrate_channel_files_preview.sql`).
- 목록 API는 최신순 최대 100건으로 제한됩니다.
- `download-info`는 멤버 검증 후 `hasPreview`, `previewSizeBytes` 등을 포함합니다. 다운로드는 `GET .../download?variant=original|preview`, 인라인·썸네일은 `GET .../preview`.

### 프론트엔드 데모 UI 메모
- **스크롤·좌측 열**: `sidebar-column`은 `align-self:stretch`+내부 `flex:1 1 0%` 체인으로 메인 채팅 열과 **동일 높이**를 채움(`height:100%`만으로는 깨질 수 있음). `sidebar-main`(검색~관리자)만 세로 스크롤, 하단 **프로필** 고정. 퀵 레일은 `quick-rail-scroll`만 스크롤. **햄버거 채널 메뉴**는 `member-panel-scroll` 단일 스크롤.
- 동료 **프로필 모달**(`modalUserProfile`): `GET /api/users/profile?employeeNo=`로 로드. 역할·계정 상태는 표시하지 않음. **직급**(`jobLevel`)은 항상 행(없으면 `-`), **직위**(`jobPosition`)·**직책**(`jobTitle`)은 값이 있을 때만 해당 행 표시. **DM 보내기**(`btnProfileDm`)는 `startDmWithUser`로 `POST /api/channels`에 `channelType: DM`, `dmPeerEmployeeNos: [상대 사번]`, 호환용 `createdByEmployeeNo` 등을 요청 본문에 포함해 호출하며 **실제 생성자는 JWT 사원번호**로 결정된다. 서버가 내부 이름·멤버십을 처리한 뒤 `selectChannel`로 전환(자기 자신이면 버튼 비활성). DB `channels.channel_type`은 `DM` 문자열로 저장됩니다.
- **프레즌스**: `presence:set`/`presence:update`/스냅샷 키는 **사번 문자열**. 채팅·멤버 패널에서 `[data-presence-user]`에 사번을 두고 `refreshPresenceDots`가 갱신합니다. 로컬 UX: 좌측 하단 본인 상태 버튼(`#sidebarUserStatus`)으로 **온라인·자리비움** 전환(`AWAY`는 노란 점).
- **이미지 첨부**: 썸네일·인라인은 `getAuthedPreviewBlobUrl` → `GET .../preview`; 라이트박스는 `getAuthedFullImageBlobUrl` → `variant=original`. **다운로드**는 `maybeDownloadChannelImageWithChoice`가 서버 미리보기 유무에 따라 원본/미리보기 또는 레거시 JPEG 재인코딩을 분기합니다.
- **첨부·이미지 모아보기**: `GET /api/channels/{channelId}/files` 한 번 호출 후 `refreshChannelFileHubData`가 갱신. **전체 파일** 탭은 `filterNonImageFilesForHub`로 이미지 제외; **이미지** 탭만 `filterImageFilesForHub`·그리드. `btnOpenImageHub`는 이미지 탭으로 모달 오픈.
- **스레드 모아보기**: `GET /api/channels/{channelId}/messages/threads?employeeNo=&limit=`(기본 50, 서버 상한 100). 댓글·답글 활동이 있는 원글만 최근 활동 순. `btnOpenThreadHub` → `modalThreadHub`, 행 클릭 시 `cacheRootMessageForThreadModal` 후 `openThreadModal`. 백엔드는 `MessageController`에서 `/{messageId:\\d+}` 등으로 리터럴 `threads`·`timeline`과 경로 충돌을 막는다.
- **채팅 타임라인 페이지네이션**: `GET .../messages/timeline` 응답 `{ items, hasMoreOlder }`. `loadOlderTimelinePage`·`beforeMessageId`·`prependTimelineMessages`. DOM `trimMessages`는 하단 근처일 때만 앞줄 제거(`MAX_CHAT_DOM_NODES` 4000·`HARD_MAX` 10000). 로딩 줄 `#msgHistoryLoading`. 레포 `MessageRepository.findTimelineOlderThan`, `MessageTimelinePageResponse`.
- 채팅 시각 표시: **동일 발신자·동일 분(로컬 캘린더 분)** 묶음에서는 **그 분의 마지막 메시지 줄에만** 시각을 붙이고, **분이 바뀌면** 각 메시지 줄에 시각을 붙인다(`minuteKey` / `renderMessages` / `appendMessageRealtime`). 시각은 **24시간제 `HH:mm`**이며 본문 바로 뒤에 약간 띄워 인라인으로 붙인다(`fmtTime`, `.msg-content-row`).

### Backend 커넥션 풀 메모
- `application.yml`의 Hikari `maximum-pool-size`는 `DB_POOL_MAX`, `connection-timeout`은 `DB_POOL_CONNECT_TIMEOUT_MS`와 연동됩니다.

### 스레드 답글 기능 인수인계 메모
- 메시지 도메인은 `messages.parent_message_id`로 부모/자식 관계를 관리합니다.
- 스레드 조회는 `parent_message_id` 기준 오름차순(`createdAt ASC`)으로 반환됩니다.
- 성능 고려로 `idx_messages_parent_message_id` 인덱스를 사용합니다.
- 스레드 허브 목록은 `MessageRepository.findThreadRootIdsByChannelOrderByLastActivity`(네이티브 SQL, EXISTS + 서브쿼리 `MAX(created_at)` 정렬) 후 엔티티 로드·`attachThreadCommentSummaries`로 건수·마지막 활동 메타를 채웁니다.

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

---

## 업무 허브(프론트) 최신 반영 메모 (2026-04-01)
- 자동완성 UI:
  - 칸반 담당자 자동완성 목록(`.kanban-assignee-suggest li`)에 좌우 패딩을 추가해 텍스트 밀착 문제를 해소
- 카드 순서 변경 UX:
  - 기존 `↑/↓` 버튼 제거
  - 카드 자체를 세로 드래그앤드롭으로 재배치하고, 임시 변경은 저장 시 `sortOrder`로 반영
- 저장 흐름:
  - 저장 버튼 클릭 시 확인창을 보여준 뒤 진행
  - 저장 성공 후에도 `modalWorkHub`는 열린 채 유지(사용자가 **닫기**로 닫음)
  - 칸반 담당자 반영 순서를 `담당 해제 -> 담당 추가`로 바꿔 삭제 후 되살아나는 현상을 완화
  - 저장 직전 카드별 담당 추가/해제 맵을 정규화해 상충 상태를 제거하고, 담당 해제 후 저장 시 재바인딩되는 케이스를 보정
- 좌측 사이드바:
  - `내 담당 칸반` 섹션(`myKanbanList`)을 추가해 담당 카드 목록을 채널/DM 목록과 같은 영역에서 제공
  - 데이터 소스: `GET /api/work-items/sidebar/by-assigned-cards?employeeNo=...&limit=...`(내가 담당인 칸반 카드가 하나라도 있는 업무 항목)
  - 클릭 동작: 채널 진입 -> 업무·칸반 모달 오픈 -> 대상 카드 스크롤/강조

---

## 업무 허브 담당 DELETE 누락 보정 (2026-04-02)
- `normalizePendingCardAssigneeOps`는 저장 직전 `loadChannelKanbanBoard()` 이후 `activeWorkHubColumns`에서 카드의 현재 `assigneeEmployeeNos`를 읽어 해제 diff를 만든다. 이전에는 카드 탐색 실패 시 기준 목록이 빈 배열로 간주되어 `removeFinal`이 비고 `DELETE /api/kanban/cards/{id}/assignees/{emp}`가 호출되지 않을 수 있었다.
- 보정: `findWorkHubKanbanCardById`( `c.id` / `c.cardId` ), `kanbanPendingAssigneeMapGet`(맵 키 숫자·문자 혼용), 카드가 여전히 트리에서 안 잡힐 때만 스냅샷 `workHubPendingCardAssigneeRemove` 값으로 해제 집합 폴백 — `frontend/app.js`.

---

## 업무 허브·사이드바 UI 가독성 (2026-04-02)
- **담당 자동완성 후보**: `filterChannelMembersForAssigneeKeyword`에서 본인 사번을 제외하지 않음. 보드 인라인·신규 카드·카드 상세 제안 목록에서는 **이미 해당 카드에 배정된 사번**만 제외(`assigned` / `pendingEmp` / 상세 칩 집합).
- **제안 행 표시**: `.kanban-assignee-suggest-name`(이름 강조)와 `.kanban-assignee-suggest-meta`(조직·직급, 작은 글자·보조색). 버튼 클래스는 `.kanban-assignee-pick` / `.kanban-assignee-pick-new` / `.kanban-detail-assignee-pick` 공통 스타일.
- **키보드**: `bindKanbanAssigneeSuggestKeyboard`를 `modalWorkHub`와 `modalKanbanCardDetail` 모두에 붙여, 담당 검색 입력에서 ↑↓·Enter·Escape 동작을 동일하게 유지.
- **사이드바 빈 문구**: `내 업무 항목`·`멘션` 섹션의 빈 상태는 `sidebar-item sidebar-item-empty`(작은 글자·`--text-muted` 계열).
- **다크 기본 테마**: `:root`의 `--text-secondary` / `--text-muted`를 약간 밝게 조정해 보조 텍스트 대비를 올림. 범용 보조 문구는 `.muted`.
- **내 업무 항목 동기화**: `flushWorkHubSave()` 성공 시 `scheduleRefreshMyChannels()`로 사이드바 담당 업무 목록을 갱신한다.
- **채널 전환 없이 모달만**: 사이드바 행 클릭 시 `selectChannel`을 호출하지 않고 `workHubScopedChannelId`만 설정한 뒤 `loadWorkHubChannelMembersForAssignee` / `loadChannelWorkItems` / `loadChannelKanbanBoard`가 `getWorkHubChannelId()`로 해당 채널 API를 호출한다. 채팅 패널의 `activeChannelId`는 그대로. `closeModal("modalWorkHub")` 시 `clearWorkHubScopedChannel()`. 헤더 `📋`로 열 때는 `workHubScopedChannelId = null`로 현재 채널 기준.
- **칸반 DnD·status**: 카드를 다른 컬럼으로 끌어 놓을 수 있으며(`boardEl.querySelector(".kanban-card-dragging")`), 드롭 시 출발·도착 컬럼에 대해 `syncKanbanBoardPartial`로 임시 `columnId`/`sortOrder`를 갱신하고, 미저장 신규 카드는 `syncKanbanDraftsOrderFromDom`. 저장 시 `statusForKanbanColumnId(targetColumnId)`로 `PUT`/`POST`에 `status`를 포함한다.

---

## 채널 헤더/운영 UX 최신 반영 메모 (2026-04-01)
- 상단 메뉴:
  - 채널/DM 헤더 우측 액션 버튼을 햄버거 버튼(`btnHeaderMenu`)으로 통합
  - 햄버거 패널(`memberPanel`)에서 액션 버튼 + 멤버 목록을 함께 노출
- 관리자 표기/권한:
  - 기존 `개설자` 배지 표기를 `관리자`로 변경
  - 관리자는 패널에서 채널명 변경 가능(`PUT /api/channels/{channelId}/name`)
- 관리자 위임:
  - 사번 입력 방식 제거
  - 멤버 목록에서 우클릭(`memberContextMenu`)으로 위임 실행
- 공통 메시지창:
  - `uiAlert/uiConfirm/uiPrompt`를 추가해 브라우저 기본 경고창 의존도를 낮춤
- DM 나가기/중복 완화:
  - DM 나가기 시 마지막 멤버가 되어도 채널 삭제를 수행하지 않아 FK 제약 오류를 회피
  - 1:1 DM 재사용 탐색에 canonical 이름 매칭을 추가해 중복 생성 가능성을 줄임

---

## 업무 허브 상세 편집/운영 안정화 메모 (2026-04-01)
- 상세 모달:
  - 업무/칸반 카드를 클릭하면 상세 모달을 열어 제목·설명·상태를 편집 가능(칸반은 담당자 추가/해제 포함)
  - 편집은 즉시 서버 반영이 아니라 기존 임시 상태에 기록 후 하단 저장에서 일괄 반영
- 드래그 UX:
  - 칸반 드래그 시 삽입 예상 위치를 라인 형태로 강조(`kanban-drop-before`)
- 채널 운영:
  - 관리자 위임은 `channels.created_by`만 전환(멤버 row 재생성 제거)
  - 채널 폐쇄는 멤버/읽음 상태 정리로 사용자 목록에서 제거하는 방식으로 제약 오류를 회피

---

## AD 자동 로그인 / 관리자 사용자 관리 (Phase 5, 2026-04-03)

### AD 자동 로그인 흐름
1. Electron main.js: `ipcMain.handle('get-windows-username', () => os.userInfo().username)` — IPC 핸들러 등록
2. preload.js: `electronAPI.getWindowsUsername = () => ipcRenderer.invoke('get-windows-username')` 노출
3. app.js `tryAdAutoLogin()`: Electron 환경 감지 후 Windows 사용자명을 `employeeNo`로 삼아 `POST /api/auth/ad-login` 호출
4. 백엔드 `AuthService.adLogin()`: `UserRepository.findByEmployeeNo()` → ACTIVE 상태 검증 → JWT 발급
5. 실패 시 일반 로그인 화면으로 폴백

### 관리자 사용자 관리 주요 파일
| 역할 | 경로 |
|------|------|
| Controller | `backend/.../api/admin/user/AdminUserController.java` |
| Service | `backend/.../api/admin/user/AdminUserService.java` |
| 조회 DTO | `api/admin/user/dto/AdminUserListItemResponse.java` |
| 저장 DTO | `api/admin/user/dto/AdminUserSaveRequest.java` |
| 드롭다운 DTO | `api/admin/user/dto/OrgGroupOptionResponse.java` |
| 프론트 뷰 | `frontend/index.html` `#viewUserManagement` |
| 프론트 JS | `frontend/app.js` (사용자 관리 함수 블록) |

### 운영 주의사항
- **INACTIVE 계정**: 일반 로그인(`/api/auth/login`)과 AD 로그인(`/api/auth/ad-login`) 모두 차단됨
- **사용자 하드 삭제**: `org_group_members`의 조직 배정 레코드도 함께 삭제됨; 채널 멤버십 등은 별도 처리 필요
- **ADMIN 자기 자신 삭제 방지 미구현**: 현재 자신의 계정을 삭제해도 API가 처리됨 → 운영 시 주의
- **AD 신뢰 모델**: Windows 계정명과 DB `employee_no`를 1:1 매핑; AD 조인되지 않은 환경에서는 사원번호 직접 입력으로 우회 가능 → 반드시 사내 AD 환경에서만 배포

### 배치 저장 메커니즘 (프론트엔드)
- 편집/삭제는 `adminUserPendingChanges` Map에 `{ action: 'create'|'update'|'delete', data }` 형태로 누적
- 배너에 미반영 변경 건수 표시
- "저장" 클릭 시: delete → update → create 순서로 순차 API 호출
- "취소" 클릭 시: Map 초기화 후 서버 데이터로 재렌더링
