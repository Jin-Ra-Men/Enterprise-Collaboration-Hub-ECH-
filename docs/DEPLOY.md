# CSTalk 배포 절차 및 롤백 가이드

## 목차
1. [사전 요구사항](#사전-요구사항)
2. [환경 구성](#환경-구성)
3. [최초 배포 절차](#최초-배포-절차)
4. [업그레이드 배포 절차](#업그레이드-배포-절차)
5. [롤백 절차](#롤백-절차)
6. [각 서비스별 실행 방법](#각-서비스별-실행-방법)
7. [배포 체크리스트](#배포-체크리스트)

---

## 사전 요구사항

| 항목 | 최소 버전 | 비고 |
|------|-----------|------|
| JDK | 17 | Spring Boot 3 요구사항 |
| Node.js | 18 LTS | Realtime 서버 |
| PostgreSQL | 14 이상 | pg_trgm 확장 필요 |
| 디스크 | 20GB 이상 | 파일 스토리지 + 로그 |

---

## 환경 구성

### 1. `.env` 파일 준비

`.env.example`을 복사하여 `.env`를 생성한 뒤 환경에 맞게 수정한다.

```bash
cp .env.example .env
```

주요 설정 항목:

```env
# DB
DB_HOST=localhost
DB_PORT=5432
DB_NAME=ech
DB_USER=ech_user
DB_PASSWORD=yourpassword

# JWT (최소 32자 이상의 무작위 문자열)
JWT_SECRET=your-very-long-random-secret-key

# 파일 스토리지 (첨부파일 저장 경로, 절대 경로 권장)
FILE_STORAGE_DIR=D:/testStorage

# 릴리즈 파일 디렉토리
APP_RELEASES_DIR=/opt/ech/releases
```

### 2. PostgreSQL DB 초기화

```sql
-- DB 및 사용자 생성
CREATE DATABASE ech;
CREATE USER ech_user WITH PASSWORD 'yourpassword';
GRANT ALL PRIVILEGES ON DATABASE ech TO ech_user;

-- pg_trgm 확장 활성화 (통합 검색 성능에 필요)
\c ech
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

스키마는 `docs/sql/postgresql_schema_draft.sql`을 참고한다.  
Spring Boot 기동 시 JPA `ddl-auto: update`로 테이블이 자동 생성된다.

---

## 최초 배포 절차

### Backend (Spring Boot)

```bash
# 1. 소스 빌드
cd backend
./gradlew bootJar

# 2. 실행 (JAR)
java -jar build/libs/cstalk-backend-*.jar \
  --spring.config.location=file:./.env

# 또는 환경 변수 직접 지정
DB_HOST=localhost DB_USER=ech_user ... java -jar build/libs/cstalk-backend-*.jar
```

기동 시 `DataInitializer`가 실행되어 테스트 계정 및 기본 설정값이 자동 생성된다.

### Realtime 서버 (Node.js)

```bash
cd realtime
npm install
node src/server.js
```

또는 PM2를 사용하는 경우:

```bash
npm install -g pm2
pm2 start src/server.js --name ech-realtime
pm2 save
```

### 프론트엔드

`frontend/` 디렉토리를 Nginx 또는 Apache의 웹루트에 배포한다.

```nginx
server {
    listen 80;
    root /var/www/ech/frontend;
    index index.html;

    # API 프록시
    location /api/ {
        proxy_pass http://localhost:8080;
    }

    # WebSocket 프록시
    location /socket.io/ {
        proxy_pass http://localhost:3001;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

---

## 업그레이드 배포 절차

관리자 UI (`⚙️ 배포 관리` 탭)를 통해 무중단 배포를 수행할 수 있다.

### 방법 1: 관리자 UI 사용 (권장)

1. 관리자 계정으로 로그인
2. `🚀 배포 관리` 탭 클릭
3. **릴리즈 업로드** 섹션에서 버전명, JAR/WAR 파일, 설명 입력 후 업로드
4. 업로드된 릴리즈가 목록에 `UPLOADED` 상태로 표시됨
5. `활성화` 버튼 클릭 → `ACTIVE` 상태로 전환
6. 서버를 재시작하여 새 버전 적용

### 방법 2: API 직접 호출

```bash
# 릴리즈 업로드
curl -X POST http://localhost:8080/api/admin/releases \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -F "version=v1.2.0" \
  -F "description=버그 수정 및 성능 개선" \
  -F "file=@build/libs/cstalk-backend-v1.2.0.jar"

# 릴리즈 활성화
curl -X POST http://localhost:8080/api/admin/releases/{releaseId}/activate \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"note":"v1.2.0 배포"}'
```

### 서버 재시작 (PM2)

```bash
pm2 restart cstalk-backend
```

또는 `current-version.txt` 파일을 외부 스크립트에서 감지하여 자동 재시작하도록 구성할 수 있다.

---

## 롤백 절차

### 방법 1: 관리자 UI

1. `🚀 배포 관리` 탭 > `롤백` 버튼 클릭
2. 이전 `PREVIOUS` 상태의 릴리즈가 `ACTIVE`로 전환됨
3. 서버 재시작

### 방법 2: API

```bash
curl -X POST http://localhost:8080/api/admin/releases/rollback \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"note":"긴급 롤백: v1.2.0 장애 발생"}'
```

### DB 롤백이 필요한 경우

마이그레이션 스크립트를 역순으로 적용하거나 백업을 복원한다.

```bash
# PostgreSQL 백업
pg_dump -U ech_user ech > ech_backup_$(date +%Y%m%d).sql

# 복원
psql -U ech_user ech < ech_backup_20260101.sql
```

---

## 각 서비스별 실행 방법

### 헬스체크

| 서비스 | URL | 기대 응답 |
|--------|-----|-----------|
| Backend | `GET /actuator/health` 또는 `GET /api/auth/me` | `200 OK` |
| Realtime | `GET http://localhost:3001/health` | `{"status":"ok","db":"ok"}` |

### 포트 기본값

| 서비스 | 기본 포트 | 환경변수 |
|--------|-----------|---------|
| Backend | 8080 | `SPRING_PORT` |
| Realtime | 3001 | `SOCKET_PORT` |
| PostgreSQL | 5432 | `DB_PORT` |

---

## 배포 체크리스트

배포 전:
- [ ] `.env` 파일 설정값 검토 (특히 `JWT_SECRET`, `DB_PASSWORD`)
- [ ] `FILE_STORAGE_DIR` 경로 존재 및 쓰기 권한 확인
- [ ] DB 백업 완료
- [ ] 신규 마이그레이션 SQL 적용 여부 확인

배포 후:
- [ ] Backend 헬스체크 정상 응답 확인
- [ ] Realtime 서버 헬스체크 정상 응답 확인
- [ ] 관리자 로그인 및 기본 기능 동작 확인
- [ ] 감사 로그에 배포 이벤트 기록 여부 확인
- [ ] 에러 로그 (`error_logs` 테이블) 이상 없음 확인
