# ECH 기본 프로젝트 틀 문서

## 1) 목적
이 문서는 `Enterprise Collaboration Hub (ECH)`의 초기 스캐폴드(기본 골격)를 설명합니다.  
Docker 없이 로컬 개발 환경에서 바로 실행/확장할 수 있도록 구성되어 있습니다.

## 2) 디렉터리 구조

```text
.
├─ backend/                      # Java Spring Boot API 서버
│  ├─ build.gradle               # Spring Boot 의존성/빌드 설정
│  ├─ settings.gradle
│  └─ src/main/
│     ├─ java/com/ech/backend/
│     │  ├─ EchBackendApplication.java
│     │  └─ api/HealthController.java
│     └─ resources/application.yml
├─ realtime/                     # Node.js http + Socket.io 실시간 서버 (Express 미사용)
│  ├─ package.json
│  └─ src/server.js
├─ frontend/                     # Vanilla JS 프론트엔드
│  ├─ index.html
│  ├─ app.js
│  └─ styles.css
├─ docs/
│  ├─ PROJECT_SCAFFOLD.md
│  └─ PROJECT_REQUIREMENTS.md
├─ .cursor/rules/CHANGELOG.md    # 변경 이력
├─ .cursor/rules/ERRORS.md       # 에러 이력
└─ .env.example                  # 로컬 환경 변수 예시
```

## 3) 구성 요소별 역할

### Backend (`backend`)
- 기술: Java 17, Spring Boot
- 역할:
  - REST API 제공
  - 인증/권한, 비즈니스 로직, DB 트랜잭션 처리
- 현재 기본 엔드포인트:
  - `GET /api/health` -> 서버 상태 확인

### Realtime (`realtime`)
- 기술: Node.js http, Socket.io (Express 미사용)
- 역할:
  - 채널 기반 실시간 메시지 중계
  - 접속/이벤트 처리
- 현재 기본 이벤트:
  - `channel:join`
  - `message:send`
  - `message:new` (브로드캐스트)

### Frontend (`frontend`)
- 기술: Vanilla JS (ES6+)
- 역할:
  - 최소 UI 스켈레톤 제공
  - 소켓 연결/메시지 송수신 테스트

## 4) 로컬 실행 가이드 (Docker 미사용)

### 사전 준비
- JDK 17+
- Node.js 18+
- PostgreSQL (로컬 설치)

### 환경 변수 설정
`.env.example` 값을 기준으로 OS 환경 변수 또는 IDE 실행 구성에 주입합니다.

```bash
DB_HOST=localhost
DB_PORT=5432
DB_NAME=ech
DB_USER=ech_user
DB_PASSWORD=ech_password
SPRING_PORT=8080
SOCKET_PORT=3001
```

### 실행 순서
1. Realtime 서버 실행
   - `cd realtime`
   - `npm install`
   - `npm run dev`
2. Backend 서버 실행
   - Windows: `cd backend && gradlew.bat bootRun`
   - macOS/Linux: `cd backend && ./gradlew bootRun`
3. Frontend 실행
   - `frontend/index.html`을 브라우저에서 직접 열어 확인

## 5) 다음 확장 권장 순서
1. 인증 체계(JWT/세션) 및 권한 모델 설계
2. 채널/메시지/사용자 엔티티 모델링
3. 파일 업로드 저장소 연동(NAS/S3)
4. 통합 검색 인덱스 설계
5. 감사 로그/보존 정책 구현
