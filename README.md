# 🌐 Enterprise Collaboration Hub (ECH)

> **Java & Node.js 기반의 고성능 사내 협업 플랫폼**
> 실시간 소통부터 관리자 관제까지, 사내 인프라를 최대로 활용한 통합 협업 솔루션입니다.

---

## 📌 프로젝트 개요 (Overview)
**ECH**는 기존 시스템의 안정성과 최신 협업 툴의 기민함을 결합한 프로젝트입니다. Java의 견고한 비즈니스 로직 처리와 Node.js의 빠른 실시간성을 활용하여 가볍고 끊김 없는 사용자 경험을 제공하는 것을 목표로 합니다.
또한 Slack, Flow, Teams를 모티브로 하여 채널 중심 협업, 실시간 소통, 업무 연계 경험을 제공하는 것을 지향합니다.

## ✨ 주요 기능 (Key Features)

### 💬 실시간 커뮤니케이션
- **채널 기반 메시징:** 프로젝트/부서별 공개 및 비공개 채널 운영
- **스레드(Thread) 대화:** 대화 맥락을 유지하는 답글 및 반응 기능
- **실시간 상태:** 팀원의 접속 상태 및 읽음 확인 실시간 동기화

### 🤝 협업 및 데이터 관리
- **파일 공유:** 드래그 앤 드롭 방식의 문서 업로드 및 히스토리 관리
- **통합 검색:** 대화 내역 및 공유 파일에 대한 고속 검색 지원

### 🛠 관리자 시스템 (Admin Dashboard)
- **그룹웨어 SSO 연동:** 별도 가입 없이 기존 사내 계정으로 즉시 로그인
- **통계 시각화:** 접속자 수, 메시지 전송량 등 주요 지표를 그래프로 시각화 (Chart.js 활용)
- **사용자 및 권한 관리:** 부서별 권한 설정, 퇴사자 계정 비활성화 및 보안 로그 감사
- **시스템 관제:** 소켓 서버 연결 상태 확인 및 전사 공지사항 즉시 배포

---

## 🛠 기술 스택 (Technical Stack)

| 구분 | 기술 (Tech) | 역할 (Role) |
| :--- | :--- | :--- |
| **Backend (Core)** | **Java / Spring Boot** | 비즈니스 로직, 인증, DB 트랜잭션 및 API 관리 |
| **Real-time** | **Node.js / Socket.io** | 실시간 메시지 중계, 알림 및 소켓 연결 관리 |
| **Frontend** | **Vanilla JS (ES6+)** | 외부 라이브러리를 최소화한 가볍고 빠른 반응형 UI |
| **Database** | **PostgreSQL** | 사용자 정보, 채널 구성, 메시지 및 업무 데이터 저장 |
| **Storage** | **File Server (NAS/S3)** | 사내 규정에 맞춘 대용량 첨부 파일 및 미디어 저장 |

---

## 🏗 시스템 아키텍처 (Architecture)

```mermaid
graph LR
    User((User Interface)) --- JS[Vanilla JavaScript]
    JS <-->|REST API| Java[Java Spring Server]
    JS <-->|WebSocket| Node[Node.js Socket Server]
    Java --- DB[(Main Database)]
    Node --- Redis[(Session/Status)]
    Java --- Storage[File Storage]
```

---

## 📁 기본 프로젝트 틀 (Scaffold)
기본 구조와 파일별 역할은 별도 문서에서 관리합니다.

- 상세 문서: `docs/PROJECT_SCAFFOLD.md`
- 기능/조건 명세: `docs/PROJECT_REQUIREMENTS.md`
- 개발 로드맵: `docs/ROADMAP.md`
- 기능 명세서: `docs/FEATURE_SPEC.md`
- 인수인계서: `docs/HANDOVER.md`
- 변경 이력: `.cursor/rules/CHANGELOG.md`
- 에러 이력: `.cursor/rules/ERRORS.md`

## 🚀 빠른 시작 (Docker 미사용)

### 1) 로컬 DB 준비
- PostgreSQL을 로컬에 설치합니다.
- `.env.example`을 참고해 환경 변수를 설정합니다.

예시:
```bash
DB_HOST=localhost
DB_PORT=5432
DB_NAME=ech
DB_USER=ech_user
DB_PASSWORD=ech_password
SPRING_PORT=8080
SOCKET_PORT=3001
```

### 2) Realtime 서버 실행 (Node.js)
```bash
cd realtime
npm install
npm run dev
```

### 3) Backend 서버 실행 (Spring Boot)
Windows:
```bash
cd backend
gradlew.bat bootRun
```

macOS/Linux:
```bash
cd backend
./gradlew bootRun
```

### 4) Frontend 확인
- `frontend/index.html`을 브라우저에서 열어 UI를 확인합니다.
- 기본 소켓 서버 주소는 `http://localhost:3001`입니다.
