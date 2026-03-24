# ECH 인수인계서

이 문서는 프로젝트 인수인계를 위한 운영/개발 지식을 기록합니다.  
개발 완료 후에도 후속 개발자가 문서만으로 수정/운영할 수 있도록 유지합니다.

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

## 3) 핵심 문서 위치
- 요구사항: `docs/PROJECT_REQUIREMENTS.md`
- 로드맵: `docs/ROADMAP.md`
- 기능 명세: `docs/FEATURE_SPEC.md`
- 변경 이력: `.cursor/rules/CHANGELOG.md`
- 에러 이력: `.cursor/rules/ERRORS.md`
- 코어 룰: `.cursor/rules/core-rules.mdc`

## 4) 운영/개발 체크리스트
- 신규 기능 개발 시:
  - 요구사항/로드맵 반영 여부 확인
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
