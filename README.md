# 🌐 Enterprise Collaboration Hub (ECH)

> **Java & Node.js 기반의 고성능 사내 협업 플랫폼**
> 실시간 소통부터 관리자 관제까지, 사내 인프라를 최대로 활용한 통합 협업 솔루션입니다.

---

## 📌 프로젝트 개요 (Overview)
**ECH**는 기존 시스템의 안정성과 최신 협업 툴의 기민함을 결합한 프로젝트입니다. Java의 견고한 비즈니스 로직과 Node.js의 실시간 통신을 결합하고, 사내 그룹웨어와 완벽히 연동되는 관리 시스템을 제공합니다.

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
| **Backend (Core)** | **Java / Spring Boot** | 비즈니스 로직, 그룹웨어 SSO 연동, API 관리 |
| **Real-time** | **Node.js / Socket.io** | 실시간 메시지 중계 및 소켓 세션 관리 |
| **Frontend** | **Vanilla JS (ES6+)** | 가벼운 UI 렌더링 및 Chart.js 기반 데이터 시각화 |
| **Database** | **PostgreSQL** | 메시지, 유저, 채널 및 통계 데이터 저장 |
| **Storage** | **Internal File Server** | 사내 보안 규정에 맞춘 첨부 파일 저장소 |

---

## 🏗 시스템 아키텍처 (Architecture)

```mermaid
graph TD
    User((User Client)) <-->|Socket| Node[Node.js Server]
    Admin((Admin Panel)) <-->|REST API| Java[Java Spring Server]
    Java <-->|Auth| GW[사내 그룹웨어 SSO]
    Java <--> DB[(Main DB)]
    Node <--> DB
    Java <-->|File IO| Storage[Internal Storage]
