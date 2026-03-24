# ECH 로컬 개발 환경 설정 가이드

## 1) 필수 버전
- Java: 17+
- Node.js: 18+
- PostgreSQL: 14+

## 2) Java 설정 (Windows)
1. JDK 17 설치 (예: Eclipse Temurin 17)
2. 사용자 환경변수 설정
   - `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot`
   - `Path`에 `%JAVA_HOME%\bin` 추가
3. 확인
   - `java -version` -> 17.x 출력 확인

## 3) Backend 실행 준비
1. 프로젝트 루트 기준 `backend/gradlew.bat` 사용
2. 확인 명령
   - `backend/gradlew.bat -p backend test --dry-run`

## 4) Realtime 실행 준비
1. `cd realtime`
2. `npm install`
3. `npm run dev`

## 5) 환경 변수 표준 (`.env.example`)
아래 값은 프로젝트 기본 표준입니다.

```bash
DB_HOST=localhost
DB_PORT=5432
DB_NAME=ech
DB_USER=ech_user
DB_PASSWORD=ech_password
SPRING_PORT=8080
SOCKET_PORT=3001
```

### 선택(성능·상한 튜닝)
- Realtime: `MAX_MESSAGE_BODY_LENGTH`, `DB_POOL_MAX`, `DB_POOL_IDLE_MS`, `DB_POOL_CONNECT_TIMEOUT_MS` (`.env.example` 주석 참고)
- Backend: `DB_POOL_MAX`, `DB_POOL_CONNECT_TIMEOUT_MS` → Hikari에 매핑

## 5-1) 테스트 사용자·부서 시드 (그룹웨어 미연동 시)
조직도 연동 전에는 `docs/sql/seed_test_users.sql`로 **테스트 부서·사용자·관리자 1명**을 넣을 수 있습니다.

- 포함 내용: `운영본부`(ADMIN), `테스트부서`·`개발1팀`·`개발2팀`·`인사총무팀`·`영업1팀`·`기획전략팀`·`보안감사팀`, 부서 `NULL` 1명, `INACTIVE` 1명 등.
- 관리자: 사번 `ECH-ADM-001`, 이메일 `admin.ech@ech-test.local`, 역할 `ADMIN`.
- 사번(`employee_no`) 기준 **UPSERT**이므로 같은 파일을 여러 번 실행해도 최신 값으로 맞춰집니다.

실행 예 (Windows에서 `psql` 경로는 설치 위치에 맞게 조정):

```bash
psql -h localhost -p 5432 -U ech_user -d ech -f docs/sql/seed_test_users.sql
```

스키마 초안 적용 후(`postgresql_schema_draft.sql` 등) 실행하는 것을 권장합니다.

## 6) 점검 체크리스트
- Java 17 적용 확인 (`java -version`)
- Backend wrapper 실행 확인 (`gradlew.bat`)
- Realtime 의존성 설치 완료 (`npm install`)
- PostgreSQL 연결 정보 확인 (`.env.example` 기준)
- (선택) 사용자 검색·멤버 API 테스트용 시드 적용 (`seed_test_users.sql`)
