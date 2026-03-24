# 🌐 Enterprise Collaboration Hub (ECH)

> **Java & Node.js 기반의 고성능 사내 협업 플랫폼**
> 단순한 채팅을 넘어, 팀의 생산성을 극대화하는 올인원 커뮤니케이션 툴입니다.

---

## 📌 프로젝트 개요 (Overview)
**ECH**는 기존 시스템의 안정성과 최신 협업 툴의 기민함을 결합한 프로젝트입니다. Java의 견고한 비즈니스 로직 처리와 Node.js의 빠른 실시간성을 활용하여 가볍고 끊김 없는 사용자 경험을 제공하는 것을 목표로 합니다.

## ✨ 주요 기능 (Key Features)

### 💬 커뮤니케이션 (Communication)
- **실시간 채널 메시징:** 워크스페이스 내 프로젝트별, 부서별 공개/비공개 채널 생성
- **스레드(Thread) 대화:** 특정 메시지에 대한 답글 기능으로 대화 맥락 유지
- **온라인 상태 표시:** 팀원의 실시간 접속 및 활동 상태 확인

### 🤝 협업 도구 (Collaboration)
- **파일 저장소:** 드래그 앤 드롭을 통한 문서 공유 및 버전 관리
- **공유 칸반 보드:** 채팅 중 바로 업무를 할당하고 진행 상황을 공유하는 인터랙티브 보드
- **통합 검색:** 과거 대화 내역 및 공유된 파일을 한 번에 찾아주는 강력한 인덱싱 검색

### 🔒 기업용 보안 및 관리 (Security & Admin)
- **조직도 연동:** 사내 인사 DB와 연동된 사용자 관리 및 부서별 자동 권한 설정
- **기록 보존:** 기업 보안 가이드라인에 맞춘 채팅 로그 아카이빙 및 감사 기능

---

## 🛠 기술 스택 (Technical Stack)

| 구분 | 기술 (Tech) | 역할 (Role) |
| :--- | :--- | :--- |
| **Backend (Core)** | **Java / Spring Boot** | 비즈니스 로직, 인증, DB 트랜잭션 및 API 관리 |
| **Real-time** | **Node.js / Socket.io** | 실시간 메시지 중계, 알림 및 소켓 연결 관리 |
| **Frontend** | **Vanilla JS (ES6+)** | 외부 라이브러리를 최소화한 가볍고 빠른 반응형 UI |
| **Database** | **PostgreSQL / MySQL** | 사용자 정보, 채널 구성, 메시지 및 업무 데이터 저장 |
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
