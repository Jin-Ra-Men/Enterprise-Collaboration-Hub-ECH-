# CHANGELOG

프로젝트 변경 이력을 기록합니다.

## 2026-03-26

### Added
- `docs/sql/migrate_users_add_org_columns.sql`: `users`에 `company_name` / `division_name` / `team_name` 컬럼 추가(기존 DB용 idempotent `ALTER`)
- `docs/sql/backfill_users_org_hierarchy.sql`: 시드와 동일한 사번별 조직 값 백필(외부 계정·미등록 사번 보조 로직 포함)

### Changed
- `docs/DB_SCHEMA.md`: `users` 명세에 조직 3컬럼 반영
- `README.md`, `docs/HANDOVER.md`, `docs/ENVIRONMENT_SETUP.md`, `docs/FEATURE_SPEC.md`: 마이그레이션·백필 절차 안내

## 2026-03-25 (17차)

### Fixed
- `ChannelType` enum에 `DM` 추가 — `ChannelService` DM 생성 분기(`channelType == DM` + `dmPeerUserIds`)가 컴파일·실행되도록 정합 (`./gradlew test` 통과)

### Changed
- `frontend/app.js`: 미사용 `createFileAttachmentRow` / `appendUploadedFileMessage` 제거 — 첨부 메시지 행은 `createFileAttachmentRowFromMsg`·`loadMessages` 경로만 사용

## 2026-03-25 (18차)

### Changed
- 프론트: 채널/DM 생성 시 구성원 선택을 `+` 버튼 기반 팝업(`modalAddMemberPicker`)으로 통일
- 프론트: 멤버 추가 팝업에서 검색 결과가 현재 컨텍스트(채널 생성/DM 생성/기존 채널 추가)에 맞는 선택 목록에 반영되도록 수정

## 2026-03-25 (19차)

### Changed
- 프론트: `modalAddMemberPicker` UI를 스크린샷 형태(좌측 조직도 트리 + 우측 검색/결과)로 재구성
- 백엔드: 조직도 생성 시 `division_name`/`team_name`이 비어 있으면 `users.department` 문자열에서 본부/부서를 유추하도록 보완

## 2026-03-25 (20차)

### Changed
- 프론트: `modalAddMemberPicker` 팝업 크기 확대 및 좌측 조직도/우측 부서원 영역 레이아웃 고정
- 프론트: 검색 UI를 팝업 상단 1줄로 단일화(좌/우 박스 내부 검색 제거)

## 2026-03-25 (16차)

### Changed
- 파랑 테마를 `화이트 베이스 + 블루 포인트`로 조정(앱/사이드바/채팅/입력 배경을 밝은 톤으로 변경, 주요 버튼·액센트만 파란색 유지)

## 2026-03-25 (15차)

### Fixed
- Windows/PowerShell에서 한글 커밋 메시지가 깨지던 `chore` 커밋을 UTF-8 메시지 파일 + `git commit --amend -F` 로 수정 후 원격 반영(`--force-with-lease`)
- `.gitignore`에 `fix_msg_utf8.txt` 추가(에디터가 생성하는 임시 커밋 메시지 파일 추적 방지)

## 2026-03-25 (14차)

### Added
- 프론트: **테마 선택** — 검정(기본 다크)·하양(라이트)·파랑(다크 블루/시안), `localStorage`(`ech_theme`) + `data-theme` 연동, 로그인·사이드바에 칩 UI

### Changed
- `styles.css`: 테마별 CSS 변수 세트(`html[data-theme="light"]` / `blue`), 그라데이션·사이드바·포커스 링을 변수로 통일

### Docs
- `docs/FEATURE_SPEC.md`, `README.md`, `docs/HANDOVER.md`

## 2026-03-25 (13차)

### Added
- 백엔드: 사용자·조직 트리용 `company_name` / `division_name` / `team_name` 및 `GET /api/user-directory/organization` 응답 `{ companies: [...] }` (회사→본부→팀→사용자)
- 프론트: 채팅 날짜 구분선, 첨부를 일반 메시지 행과 동일 레이아웃으로 표시, 구성원 추가·채널·DM용 **검색+조직도 통합 피커** (`.picker-unified`)
- 프론트: 모달·관리자·검색·조직도·첨부 블록까지 이어지는 **다크 refined** 테마 (`styles.css` CSS 변수)

### Changed
- `docs/sql/seed_test_users.sql`, `postgresql_schema_draft.sql`: 회사/본부/팀 샘플·컬럼 반영
- `UserDirectoryApiTest`: `$.data.companies` 기준 검증

### Docs
- `docs/FEATURE_SPEC.md`, `docs/HANDOVER.md`, `README.md` — 조직 API·UI 반영

## 2026-03-25 (12차)

### Added
- `users.job_rank`(직위), `users.duty_title`(직책) 컬럼 및 JPA·프로필/검색/채널 멤버 API(`jobRank`, `dutyTitle`) 반영
- 조직 동기화(TEST)·`seed_test_users.sql`에 직위·직책 샘플 값 반영

### Changed
- 프론트: 프로필 모달에 직위(항상 행, 값 없으면 `-`), 직책은 값이 있을 때만 표시
- 프론트: 채널 멤버 패널에 부서·직위 한 줄 요약, 직책은 있을 때만 추가 줄
- 프론트: 채팅 메시지·멤버 패널에서 프레즌스 점을 아바타 네모 우측 하단에 배치(`.msg-avatar-wrap`, `.member-avatar-wrap`)

### Changed (에이전트 룰)
- `core-rules.mdc` Git 섹션에 **작업 완료 시 커밋·푸시(자동)** 절차 통합(기존 별도 `auto-commit-push.mdc` 내용 흡수 후 해당 파일 제거)

## 2026-03-25 (11차)

### Added
- 프론트엔드: 프로필 모달 `DM 보내기` 버튼(기존 DM 만들기와 동일하게 `POST /api/channels` + 멤버 추가 후 해당 DM으로 전환)
- 프론트엔드: 구성원 검색 옆 `조직도` 버튼 + 트리형 조직도 팝업(체크박스 다중 선택 후 한 번에 추가)
- 프론트엔드: 채널 헤더에 `구성원 추가` 기능(채널 생성 이후 멤버 추가 모달)
- 프론트엔드: 채널 헤더에 `첨부파일 모아보기` 모달
- 프론트엔드: 파일 업로드 완료 시 채팅 본문에 첨부 카드(파일명/크기/다운로드) 메시지 표시

### Changed
- 프론트엔드: 프로필 모달에서 역할·계정 상태 항목 제거(동료 정보는 이름·사원번호·이메일·부서 중심)
- 백엔드: `ChannelMemberResponse`에 `department` 추가
- 프론트엔드: 멤버 리스트에서 `MANAGER/MEMBER` 대신 조직 정보(부서) 소형 텍스트 노출
- 프론트엔드: 로그아웃 버튼 클릭 시 즉시 로그아웃하지 않고 확인 대화상자 표시

## 2026-03-25 (10차)

### Fixed
- Spring Boot 기본 `/**` 정적 리소스 매핑이 `/api/**` 를 가로채 `NoResourceFoundException`(api/users/profile, api/user-directory/organization 등)이 나던 문제 수정
- `spring.web.resources.add-mappings: false` + `FrontendResourceConfig` 로 `index.html` / `styles.css` / `app.js` 만 명시 노출, `/` → `/index.html` 리다이렉트

## 2026-03-25 (9차)

### Added
- 백엔드: `GET /api/user-directory/organization` — 조직도용 부서별 사용자 목록(정적 리소스와 `/api/users/*` 충돌 회피)

### Changed
- 백엔드: `GET /api/users/organization` 제거(프론트는 `user-directory` 경로 사용)
- 백엔드: 채널 파일 다운로드 — `FileSystemResource`·실제 파일 크기·유효하지 않은 Content-Type 시 `application/octet-stream`, 미존재 시 `NotFoundException`(404), 스토리지 base 이탈 경로 차단
- 프론트엔드: 조직도 요청 URL을 `/api/user-directory/organization`으로 변경

## 2026-03-25 (8차)

### Added
- 백엔드: `GET /api/users/profile?userId=` — 프로필 조회(쿼리형, 프론트 기본 연동)

### Changed
- 프론트엔드: 프로필 요청을 경로형 `/{userId}/profile` 대신 쿼리형으로 전환(404 회피)

## 2026-03-25 (7차)

### Added
- 백엔드: `GET /api/users/organization` — ACTIVE 사용자를 부서별로 묶어 조직도 선택 UI에 사용
- 백엔드: `GET /api/users/{userId}/profile` — 동료 프로필 조회(멤버 권한)
- 프론트엔드: 채팅 화면 **첨부 파일** 목록(접기/펼치기) + JWT 기반 다운로드
- 프론트엔드: 메시지 발신자·멤버 패널에서 **프로필 모달**, **프레즌스 점** 표시(소켓 `presence:update` + `GET /presence` 스냅샷)
- 프론트엔드: 채널/DM 만들기 시 **조직도에서 선택**(부서별 접기)

### Changed
- 백엔드: `ChannelFileResponse`에 `uploaderName` 추가, 채널 파일 디스크 경로를 `channels/{workspaceKey}_ch{channelId}_{slug}/yyyy/mm/` 형태로 저장(기존 `channels/{channelId}/...` 경로의 `storageKey`는 그대로 다운로드 가능)
- 백엔드: `GET /api/users/search` — 키워드에 부서 부분 일치, 숫자만 입력 시 사용자 ID 일치 포함

## 2026-03-25 (6차)

### Changed
- 프론트엔드: 채팅 시각 — **24시간제 HH:mm** (`fmtTime`), 오전/오후 문구 제거. 시각을 패널 오른쪽 끝이 아니라 **본문 직후** 인라인 배치(`.msg-content-row` 블록 + 인라인 `span`)

## 2026-03-25 (5차)

### Changed
- 프론트엔드: 채팅 메시지 시각 표시 규칙 — 동일 발신자·동일 분(로컬) 연속 메시지는 **마지막 줄에만** 시각 표시, 분이 바뀌면 줄마다 시각 표시(Slack 유사). `msg-content-row` 레이아웃으로 본문·시간 정렬

## 2026-03-25 (4차)

### Changed
- `docs/HANDOVER.md` — 로컬·개발 환경 서버 재시작 절(구성요소, 기동/중지 순서, PowerShell 예시, 접속 확인) 최하단 추가

## 2026-03-25

### Added
- 백엔드: `GET /api/channels?userId=` — 사용자가 참여한 채널 목록 조회 API 추가
- 백엔드: `GET /api/channels/{id}/messages?userId=&limit=` — 채널 메시지 내역 조회 API 추가
- 백엔드: `ChannelSummaryResponse` DTO 신규 생성
- 백엔드: `ChannelMemberResponse`에 `name` 필드 추가
- 백엔드: `MessageResponse`에 `senderName` 필드 추가

### Changed
- 백엔드: `ChannelController.createChannel()` `@RequireRole` MANAGER → MEMBER (모든 사용자 채널 생성 허용)
- 백엔드: `ChannelController.joinChannel()` `@RequireRole` MANAGER → MEMBER
- 백엔드: `UserSearchController` `@RequireRole` MANAGER → MEMBER (모든 사용자 동료 검색 허용)
- 프론트엔드: `index.html` Slack 스타일 레이아웃으로 완전 재작성
- 프론트엔드: `app.js` 완전 재작성 (채널 사이드바, 메시지 히스토리, 실시간 소켓, 파일 업로드 통합, 채널/DM 생성 모달)
- 프론트엔드: `styles.css` Slack 스타일 완전 재작성

### Fixed
- 메시지 보내기 — 채널 선택 후 소켓을 통해 정상 전송
- 배포관리 탭 — 일반 사용자에게 노출되지 않도록 사이드바 ADMIN 전용 섹션으로 이동
- 첨부파일 — 현재 채팅방에서만 업로드 (채널 ID 수동 입력 제거)
- 연속 메시지(같은 발신자) UI — `.msg-continued`에 `padding-left`와 `msg-spacer`가 겹쳐 이중 들여쓰기 되던 문제 수정 (`styles.css`), `senderId` 숫자 정규화 및 채널 전환 시 `lastSenderId` 초기화 (`app.js`)
- 리얼타임 — `saveMessage` 전 `channel_members` 멤버십 검사, 비멤버 시 `NOT_CHANNEL_MEMBER` (`db.js`, `server.js`)
- 운영 참고 — 개발용 직접 INSERT 메시지 정리 예시 `docs/sql/cleanup_dev_messages.sql` 추가, `FEATURE_SPEC.md` 실시간 절 보완

## 2026-03-24 (2차)

### Fixed
- `UnauthorizedException` 추가 및 `AuthService` 로그인 실패 시 401 반환 처리
- `NotFoundException` 추가 및 `ChannelService.getChannel()` 채널 미존재 시 404 반환 처리
- `GlobalExceptionHandler`에 401/404 핸들러 추가
- `SecurityConfig`에 `authenticationEntryPoint(HttpStatusEntryPoint(UNAUTHORIZED))` 추가 — JWT 미제공/만료 시 403 대신 401 반환
- `ChannelApiTest` JSON 필드명 오타 수정 (`"type"` → `"channelType"`)

## 2026-03-24

### Added
- 초기 프로젝트 스캐폴드 생성 (`backend`, `realtime`, `frontend`)
- 헬스체크 API 추가 (`/api/health`)
- 실시간 메시지 기본 이벤트(`channel:join`, `message:send`, `message:new`) 구현
- 스캐폴드 상세 문서 추가 (`docs/PROJECT_SCAFFOLD.md`)
- 기본 Cursor 룰 반영: 한글 응답, 변경점 기록, 에러 기록
- 에러 기록 파일 추가 (`ERRORS.md`)
- 기능/조건 기준 문서 추가 (`docs/PROJECT_REQUIREMENTS.md`)
- 기본 룰 파일명 변경 및 통합 룰 적용 (`.cursor/rules/core-rules.mdc`)
- 개발 로드맵 문서 추가 (`docs/ROADMAP.md`)
- 기능명세서 템플릿 문서 추가 (`docs/FEATURE_SPEC.md`)
- 인수인계 템플릿 문서 추가 (`docs/HANDOVER.md`)
- PostgreSQL 스키마 초안 SQL 추가 (`docs/sql/postgresql_schema_draft.sql`)
- 채널별 읽음 포인터 API (`GET/PUT /api/channels/{channelId}/read-state`) 및 `channel_read_states` 도메인/스키마 초안
- 채널 파일 메타데이터 API(`channel_files`, 목록/등록/download-info) 및 스키마 초안
- Realtime Presence 소켓 단위 추적·전원 연결 종료 시 OFFLINE 정리, 메시지 본문 길이 상한·Socket 버퍼 상한, `pg` Pool 타임아웃 옵션
- Frontend 데모 메시지 DOM 상한(200건), Backend Hikari 풀 환경변수 연동
- 칸반 보드 도메인(`kanban_*` 테이블, `/api/kanban/*` CRUD·담당·이력 API)
- 메시지 기반 업무 항목(`work_items`, `POST/GET /api/messages/{id}/work-items`, `GET /api/work-items/{id}`)
- 로컬 테스트용 사용자·부서 시드 SQL(`docs/sql/seed_test_users.sql`, 관리자·다양한 부서/역할/INACTIVE·부서NULL 포함) 및 `ENVIRONMENT_SETUP` 안내
- 조직 동기화 인터페이스 API(`OrgUserProvider`, `TEST` 제공자, `GET/POST /api/admin/org-sync/users*`, 사용자 상태 변경 API) 추가
- RBAC 어노테이션/인터셉터(`@RequireRole`, `RoleGuardInterceptor`) 및 `docs/RBAC_MATRIX.md` 추가
- 운영 오류 로그(`error_logs` 테이블, 전역 예외 저장, 관리자 조회 API `/api/admin/error-logs`) 추가

### Changed
- `.gitignore`에 로컬 Gradle 배포 산출물(`tools/gradle-8.10-bin.zip`, `tools/gradle-8.10/`) 제외 규칙 추가
- `core-rules.mdc` 커밋 규칙 중복 섹션을 통합해 단일 "Git 커밋 규칙"으로 가독성 개선
- `core-rules.mdc`에 "Git 커밋 메시지 규칙" 보강(UTF-8 파일 커밋/수정, 인코딩 설정 유지, 메시지 요약 원칙)
- `core-rules.mdc` 커밋 규칙 보강: 커밋 직후 한글 깨짐 검증, PowerShell `-m`/`commit -F` 인코딩 위험, UTF-8 파일+Git Bash·`tools/rewrite-head-commit-message.sh` 안내, `i18n.commitEncoding` 권장
- `tools/rewrite-head-commit-message.sh` 추가(Git Bash에서 HEAD 메시지를 UTF-8 파일로 안전히 재작성)
- `tools/rebuild-head-with-staged.sh` 추가(스테이징된 트리로 HEAD 커밋만 UTF-8 메시지와 함께 재구성)
- `docs/HANDOVER.md`에서 Git 커밋 전용 절 제거, 개발자·운영 관점 빠른 이해(2-1)로 정리
- `README.md`를 Docker 미사용 기준으로 정리
- 빠른 시작 가이드를 OS별(Spring 실행)로 명확화
- 실시간 서버에서 `Express`/`cors` 의존성 제거, `http + socket.io` 구조로 단순화
- DB 기준을 PostgreSQL 단일 기준으로 정리 (`README.md`, `docs/PROJECT_REQUIREMENTS.md`)
- 변경기록 관리 파일을 `.cursor/rules/CHANGELOG`로 통합
- 커서 룰의 변경 기록 대상 경로를 `.cursor/rules/CHANGELOG`로 변경
- Realtime 서버 정책을 `Express 미사용`으로 명시
- 변경/에러 이력 경로를 `.cursor/rules/CHANGELOG.md`, `.cursor/rules/ERRORS.md`로 명확히 통일
- 문서 참조 경로 정리 (`README.md`, `docs/PROJECT_REQUIREMENTS.md`, `docs/PROJECT_SCAFFOLD.md`, `.cursor/rules/changelog-ai-log.mdc`)
- 프로젝트 모티브를 Slack/Flow/Teams로 명시하고 해당 기준으로 작업 원칙 반영
- `core-rules.mdc`에 커밋 규칙 추가(기능 단위 커밋, 한글 메시지, 내용 기반 메시지, 브랜치 전략 사용자 확인)
- `core-rules.mdc`에 커밋 메시지 형식 규칙 추가(type + 한글 제목, 타입별 기준, 예시)
- 로드맵 기반 작업/완료 시 취소선 처리 규칙 반영 (`.cursor/rules/core-rules.mdc`, `docs/PROJECT_REQUIREMENTS.md`, `README.md`)
- Backend 공통 응답 포맷(`ApiResponse`) 및 전역 예외 처리(`GlobalExceptionHandler`) 추가, `HealthController` 응답 포맷 통일
- 로드맵 Phase 1 항목 `기본 에러 처리/공통 응답 포맷 적용` 완료 처리(취소선 반영)
- 개발 완료 후 기능명세서/인수인계서를 지속 상세 업데이트하는 규칙 반영 (`.cursor/rules/core-rules.mdc`, `docs/PROJECT_REQUIREMENTS.md`, `docs/ROADMAP.md`, `README.md`)
- 원격 `main` pull 과정에서 `README.md` 충돌 해결 후 로컬 기준 문서 구조를 유지하며 병합 반영
- 기능명세서/인수인계서에 DB 스키마(사용자/채널/메시지) 상세 반영 (`docs/FEATURE_SPEC.md`, `docs/HANDOVER.md`)
- README 문서 링크에 DB 스키마 초안 경로 추가 (`docs/sql/postgresql_schema_draft.sql`)
- 로드맵 Phase 1 항목 `PostgreSQL 스키마 초안 작성` 완료 처리(취소선 반영)
- 채널 도메인 API 1차(`생성/조회/참여`) 구현 및 관련 도메인 엔티티/리포지토리/DTO/서비스/컨트롤러 추가
- 로드맵 Phase 1 항목 `채널 도메인 API 1차 구현` 완료 처리(취소선 반영)
- 기능명세서/인수인계서에 채널 API 상세 반영 (`docs/FEATURE_SPEC.md`, `docs/HANDOVER.md`)
- 로드맵 완료 표기 방식을 취소선에서 체크박스(`[x]`)로 변경 (`docs/ROADMAP.md`, `.cursor/rules/core-rules.mdc`, `docs/PROJECT_REQUIREMENTS.md`)
- 로드맵을 단계/세부 작업 단위로 상세 세분화 (Phase별 1차/2차 하위 작업 정의)
- 로드맵 완료 표기 방식을 체크박스 `[x]`에서 `[v]`로 변경
- 로드맵 하위 작업(`1-1-1`, `1-1-2` 등)을 상위 항목 하단으로 들여쓰기 정렬
- 문서 가독성 개선(README 문서 섹션 정리) 및 관리자 버전 업그레이드 관리 요구사항 추가
- 요구사항/로드맵/기능명세/인수인계서에 관리자 릴리즈 업로드(WAR), 버전 전환, 롤백, 감사로그 항목 반영
- Realtime 메시지 저장 연계 구현(`pg` 기반 DB 저장 -> 저장 성공 시 `message:new` 브로드캐스트) 및 프론트 입력 payload(`channelId/senderId`) 정렬
- 로드맵 Phase 1의 `1-5` 하위 항목 완료 처리(`[v]`)
- 기능명세서/인수인계서에 Realtime 메시지 저장 연계 상세 반영
- Java 17 설치 및 `JAVA_HOME`/`Path` 적용, Backend Gradle Wrapper 생성/검증으로 Phase 1의 `1-1`/하위 항목 완료 처리
- 로컬 환경 설정 상세 문서 추가 (`docs/ENVIRONMENT_SETUP.md`) 및 README/HANDOVER/FEATURE_SPEC 연동
- 메시지/스레드 API 추가 (`POST /messages`, `POST /replies`, `GET /replies`) 및 메시지 도메인(`Message`) 구현
- 로드맵 Phase 2의 `2-1`, `2-1-1`, `2-1-2` 완료 처리(`[v]`)
- 기능명세서/인수인계서에 스레드 답글 기능 상세 반영
- 조직도(부서) 기반 사용자 검색 API(`GET /api/users/search`) 구현
- Realtime 사용자 Presence 기능(`presence:set`, `presence:update`, `GET /presence`) 구현
- 로드맵 항목 `2-2-2`, `3-1-3` 완료 처리(`[v]`) 및 관련 문서 반영
- 새 기능 추가 시 `README.md`를 적절한 섹션에 함께 업데이트하도록 문서 규칙 강화 (`core-rules.mdc`, `PROJECT_REQUIREMENTS.md`, `HANDOVER.md`)
- README에 누락된 최신 기능 반영: 조직도 기반 사용자 검색, Presence 기능, 메시지/스레드/사용자검색 API 및 Realtime 이벤트 요약 추가
- 로드맵 Phase 2 항목 `2-2`, `2-2-1` 완료 처리(`[v]`), 기능명세/인수인계/README 동기화
- 로드맵 Phase 2 항목 `2-3`, `2-3-1`, `2-3-2` 완료 처리(`[v]`), 문서/환경 예시 동기화
- 로드맵 Phase 2 항목 `2-4`, `2-4-1`, `2-4-2` 완료 처리(`[v]`), 기능명세/인수인계/README/스키마 초안 동기화
- 로드맵 Phase 2 항목 `2-5`, `2-5-1`, `2-5-2` 완료 처리(`[v]`), 기능명세/인수인계/README/스키마 초안 동기화
- 로드맵 Phase 3 항목 `3-1`, `3-1-1`, `3-1-2` 완료 처리(`[v]`), 테스트 조직 우선 연동/추후 그룹웨어 전환 기준 문서화
- 로드맵 Phase 3 항목 `3-2`, `3-2-1`, `3-2-2` 완료 처리(`[v]`), 역할-권한 매트릭스 및 API 단 권한 체크 반영
- 로드맵 Phase 3 항목 `3-3-0`(오류 로그 기반) 완료 처리(`[v]`), 3-3-1/3-3-2는 후속
- 감사 이벤트 로그 도메인(`audit_logs` 테이블, `AuditEventType` Enum, `AuditLog` 엔티티/리포지토리) 추가
- `AuditLogService`(이벤트 기록·REQUIRES_NEW 트랜잭션·`safeRecord` 래퍼·검색) 구현
- 관리자 감사 로그 조회 API(`GET /api/admin/audit-logs`, 기간/행위자/이벤트유형/리소스/워크스페이스 필터) 추가
- `ChannelService`, `MessageService`, `ChannelFileService`, `WorkItemService`, `KanbanService`에 감사 로그 연동(`safeRecord` 호출)
- 로드맵 Phase 3 항목 `3-3-1`, `3-3-2` 완료 처리(`[v]`)
- 보존 정책/아카이빙 기능 구현 (Phase 3-4 완료)
  - `retention_policies` 테이블 및 `RetentionResourceType` Enum 추가 (MESSAGES/AUDIT_LOGS/ERROR_LOGS)
  - `messages.archived_at` 컬럼 추가 (소프트 아카이브)
  - `RetentionPolicyService`: 정책 CRUD + 아카이빙 실행 로직
  - `ArchivingScheduler`: 매일 02:00 활성 정책 자동 실행 (`@Scheduled`)
  - `RetentionPolicyController`: 관리자 API (`GET/PUT /api/admin/retention-policies`, `POST /trigger`, `POST /trigger/{resourceType}`)
  - `DataInitializer`: 서버 기동 시 기본 보존 정책 자동 시드
  - `AuditEventType` `RETENTION_POLICY_UPDATED`, `DATA_ARCHIVED` 추가
  - `SchedulingConfig` (`@EnableScheduling`) 추가
  - 로드맵 Phase 3-4 완료 처리(`[v]`)
- 관리자 배포 관리 기능 구현 (Phase 3-5 완료)
  - `release_versions`, `deployment_history` 테이블 추가
  - `ReleaseVersion`, `DeploymentHistory` 엔티티/리포지토리 (`domain/release/`)
  - `ReleaseStatus`, `DeploymentAction` Enum 추가
  - `ReleaseService`: WAR/JAR 업로드(SHA-256 체크섬), 활성화, 롤백, 삭제, 이력 조회
  - `ReleaseController`: 7개 관리자 API (`/api/admin/releases`)
  - `AuditEventType` RELEASE_UPLOADED/ACTIVATED/ROLLED_BACK/DELETED 추가
  - `application.yml` 멀티파트 파일 크기 설정(기본 500MB), `app.releases-dir` 추가
  - 프론트엔드: 탭 네비게이션 + 배포 관리 화면(목록/업로드/활성화/이력)
  - 로드맵 Phase 3-5 완료 처리(`[v]`)
- docs/DB_SCHEMA.md: 전체 DB 구조 상세 명세서 신규 작성
  - DB 기본 정보, 확장 모듈, 전체 18개 테이블 상세 명세
  - 인덱스 전체 목록 (28개), Enum 값 정의, ERD 텍스트, 보존 정책, 시드 데이터, 운영 주의사항
- Phase 4 안정화 및 품질 완료
  - 4-1-1: Spring Boot 통합 테스트 (H2 인메모리, AuthApiTest/ChannelApiTest/SearchApiTest/JwtUtilTest)
  - 4-1-2: GitHub Actions CI 파이프라인 (.github/workflows/ci.yml)
  - 4-2-1: k6 부하 테스트 스크립트 (tools/k6/load-test.js, message-stress-test.js)
  - 4-2-2: HikariCP 튜닝 (min-idle, max-lifetime, keepalive-time), DB Pool 에러 핸들링 강화
  - 4-3-1: realtime pg Pool 에러 핸들링, gracefulShutdown, 재연결 재시도 로직
  - 4-3-2: socket.io 재연결 설정 강화, message:send ACK 콜백 적용, 메시지 rate limit
  - 4-4-1: docs/DEPLOY.md (배포/롤백/체크리스트)
  - 4-4-2: docs/MONITORING.md (알람 임계치/헬스체크/부하 기준/장애 대응)
  - 로드맵 Phase 4 전체 완료 처리([v])
- 파일 스토리지 경로 설정 및 실제 파일 업로드/다운로드 구현
  - app_settings 테이블, AppSetting 엔티티/리포지토리, AppSettingKey 상수 추가
  - AppSettingsService: DB 우선 + yml 폴백 스토리지 경로 조회/변경
  - AppSettingsController: GET/PUT /api/admin/settings (ADMIN 전용)
  - ChannelFileService: 실제 파일 저장 (channels/{id}/{YYYY}/{MM}/{UUID}_{name}), 다운로드
  - ChannelFileController: POST /upload(multipart), GET /{id}/download 추가
  - DataInitializer: file.storage.base-dir=D:/testStorage 자동 시드
  - application.yml: app.file-storage-dir 추가 (기본 D:/testStorage)
  - 프론트엔드: 채널 파일 업로드/목록/다운로드 UI + 관리자 설정 탭
- 통합 검색 기능 구현 (Phase 3-6 완료)
  - PostgreSQL `pg_trgm` 확장 + GIN 인덱스 SQL 추가 (성능 튜닝 기준)
  - `SearchType` Enum(ALL/MESSAGES/FILES/WORK_ITEMS/KANBAN_CARDS), `SearchResultItem`, `SearchResponse` DTO
  - `MessageRepository`, `ChannelFileRepository` 채널 멤버십 기반 검색 쿼리 추가
  - `WorkItemRepository`, `KanbanCardRepository` 키워드 검색 쿼리 추가
  - `SearchService`, `SearchController` (GET /api/search, JWT 인증 필요)
  - 프론트엔드: 헤더 검색바 + 결과 모달(타입 필터, 타입별 배지 색상)
  - 로드맵 Phase 3-6 완료 처리(`[v]`)
- JWT 기반 로그인 인증 구현 (Phase 3-0 신규 추가 및 완료)
  - `SecurityConfig` (Spring Security 정책: Stateless JWT, CORS 허용)
  - `JwtUtil` (토큰 발급/검증), `JwtAuthFilter` (OncePerRequestFilter), `UserPrincipal` (인증 주체)
  - `AuthProvider` 인터페이스 + `TestAuthProvider` (로컬 BCrypt 검증, 그룹웨어 전환 대비)
  - `AuthService`, `AuthController` (`POST /api/auth/login`, `GET /api/auth/me`)
  - `DataInitializer`: 기동 시 비밀번호 미설정 사용자에게 `Test1234!` 자동 적용
  - `User` 엔티티에 `password_hash` 컬럼 추가, `users` DB 스키마 반영
  - `RoleGuardInterceptor` SecurityContext 우선 + X-User-Role 헤더 폴백
  - 프론트엔드: 로그인 화면, JWT sessionStorage 저장, 자동 Authorization 헤더 첨부, 로그아웃

### Removed
- Docker 기반 실행 파일 제거 (`docker-compose.yml`)
- 중복 변경이력 파일 제거 (`docs/CHANGELOG_AI.md`, `CHANGELOG`)
- 루트 에러 파일 제거 (`ERRORS.md`)
- 구 룰 파일 제거 (`.cursor/rules/changelog-ai-log.mdc`)
