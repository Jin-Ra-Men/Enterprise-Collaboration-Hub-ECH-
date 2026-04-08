# ECH 기능 및 조건 명세서

## 1) 문서 목적
이 문서는 `Enterprise Collaboration Hub (ECH)` 개발의 기준 문서입니다.  
앞으로 기능 구현, 우선순위 결정, 테스트 범위는 본 문서를 기준으로 진행합니다.
ECH는 Slack, Flow, Teams를 모티브로 한 사내 협업 플랫폼이며, 구현 시 해당 서비스들의 강점을 참고합니다.
개발 순서와 진행 상태는 `docs/ROADMAP.md`를 기준으로 관리합니다.
구현된 기능 상세는 `docs/FEATURE_SPEC.md`, 인수인계 정보는 `docs/HANDOVER.md`를 기준으로 관리합니다.

## 2) 프로젝트 범위

### In Scope
- 사내 협업용 실시간 커뮤니케이션 플랫폼
- 채널/스레드 기반 메시징
- 파일 공유, 칸반 보드, 통합 검색
- 조직도 기반 사용자/권한 관리
- 감사 로그/기록 보존

### Out of Scope (초기 단계)
- 외부 고객용 공개 서비스
- 멀티 테넌트(SaaS) 과금/결제 기능
- 모바일 네이티브 앱

## 3) 사용자 역할
- `Admin`: 조직/권한/보안 정책 관리
- `Manager`: 부서/프로젝트 채널 운영, 업무 배정
- `Member`: 메시지, 파일 공유, 업무 협업
- `Auditor`(읽기 전용): 감사 로그 조회

## 4) 핵심 기능 요구사항

## 4.1 커뮤니케이션
- 채널 생성/수정/삭제 (공개/비공개)
- 채널 입장/퇴장
- 실시간 메시지 송수신
- 메시지 스레드 답글
- 메시지 수정/삭제 이력 관리(정책 기반)
- 읽음 상태 및 온라인 상태 표시

## 4.2 협업 도구
- 파일 업로드/다운로드/버전 관리
- 칸반 보드(할 일/진행 중/완료) 및 담당자 지정
- 채팅 내 업무 항목 생성(메시지 -> 업무 전환)
- 통합 검색(메시지/파일/업무)

## 4.3 보안 및 관리
- 사내 계정(조직도/인사DB) 연동
- 역할 기반 접근 제어(RBAC)
- 채팅/파일/업무 감사 로그 수집
- 보존 정책(예: N개월 보관 후 아카이빙)
- 관리자 감사 조회 화면/API 제공

## 4.4 운영/배포 관리 (Admin)
- 관리자 페이지에서 신규 버전 배포 파일 업로드 지원 (WAR 우선, 추후 확장 가능)
- 업로드된 버전 목록/메타데이터(버전명, 업로더, 업로드 시각, 상태) 조회
- 버전 전환(활성화) 및 배포 이력 관리
- 이전 안정 버전으로 롤백 기능 제공
- 업그레이드/롤백 작업에 대한 감사 로그 기록

## 5) 비기능 요구사항

### 성능
- 일반 채널 메시지 지연: 평균 1초 이내
- 동시 접속 사용자 수 증가 시 수평 확장 가능해야 함

### 안정성
- 서버 장애 시 메시지 유실 최소화 전략 필요(재시도/큐/저장 순서 보장)
- 주요 API/소켓 이벤트에 대한 예외 처리 표준화

### 보안
- 인증 토큰/세션 검증 필수
- 민감 데이터 저장/전송 시 보안 정책 준수
- 감사 추적(누가, 언제, 무엇을) 보장

### 운영성
- 헬스체크 엔드포인트 제공
- 환경 변수 기반 설정
- 로그 레벨/형식 일관성 유지

## 6) 기술/구조 조건
- Backend: Java 17+, Spring Boot
- Realtime: Node.js 18+, Socket.io
- Realtime 구현 원칙: Express 미사용 (`http + socket.io` 기반)
- Frontend: Vanilla JS (ES6+)
- DB: PostgreSQL (로컬/사내 환경)
- Docker는 사용하지 않음

## 7) API 및 이벤트 기준(초안)

### REST API (초안)
- `GET /api/health`
- `POST /api/channels`
- `GET /api/channels/{channelId}`
- `POST /api/channels/{channelId}/messages`
- `GET /api/search?q=...`
- `GET /api/users/search?q=...&department=...` (조직도/부서 기반 사용자 검색)
- `POST /api/admin/releases` (버전 파일 업로드)
- `GET /api/admin/releases` (버전 목록 조회)
- `POST /api/admin/releases/{releaseId}/activate` (버전 전환)
- `POST /api/admin/releases/{releaseId}/rollback` (롤백)

### Socket 이벤트 (초안)
- `channel:join`
- `channel:leave`
- `message:send`
- `message:new`
- `presence:update`
- `presence:set`

## 8) 개발 원칙
- 작은 단위로 기능을 나눠 구현하고 즉시 테스트
- 변경 시 반드시 `.cursor/rules/CHANGELOG.md` 기록
- 에러 발생 및 대응 내용은 `.cursor/rules/ERRORS.md` 기록
- IA/UX/기능 용어는 Slack, Flow, Teams 모티브에 맞게 일관성 있게 설계
- 로드맵 완료 항목은 `docs/ROADMAP.md`에서 체크박스(`[v]`)로 표시
- 개발 완료 후 기능명세서(`docs/FEATURE_SPEC.md`)와 인수인계서(`docs/HANDOVER.md`)를 반드시 상세 업데이트
- 새 기능 추가 시 `README.md`(제품 개요·주요 기능) 및 필요 시 `docs/DEVELOPER_README.md`(기술·API·실행)의 해당 섹션도 함께 업데이트
- 본 문서와 다른 방향의 구현이 필요하면 먼저 문서 업데이트 후 진행

## 9) 완료 기준(Definition of Done)
- 기능 요구사항 충족
- 기본 예외 처리 및 검증 포함
- 실행/테스트 방법 문서화
- `.cursor/rules/CHANGELOG.md` 반영 완료
- 에러 발생 시 `.cursor/rules/ERRORS.md` 반영 완료
- 기능별 명세/인수인계 문서 최신화 완료 (`docs/FEATURE_SPEC.md`, `docs/HANDOVER.md`)
