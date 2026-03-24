# CHANGELOG

프로젝트 변경 이력을 기록합니다.

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

### Removed
- Docker 기반 실행 파일 제거 (`docker-compose.yml`)
- 중복 변경이력 파일 제거 (`docs/CHANGELOG_AI.md`, `CHANGELOG`)
- 루트 에러 파일 제거 (`ERRORS.md`)
- 구 룰 파일 제거 (`.cursor/rules/changelog-ai-log.mdc`)
