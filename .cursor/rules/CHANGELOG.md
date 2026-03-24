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

### Changed
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

### Removed
- Docker 기반 실행 파일 제거 (`docker-compose.yml`)
- 중복 변경이력 파일 제거 (`docs/CHANGELOG_AI.md`, `CHANGELOG`)
- 루트 에러 파일 제거 (`ERRORS.md`)
- 구 룰 파일 제거 (`.cursor/rules/changelog-ai-log.mdc`)
