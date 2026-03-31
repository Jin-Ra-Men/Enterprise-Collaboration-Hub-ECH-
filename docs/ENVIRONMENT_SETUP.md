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

### 4-1) 브라우저에 `xhr poll error`, `Failed to fetch`, `[ECH] Realtime connect_error` 가 나올 때
- **원인**: 프론트가 붙는 URL(기본 `http://<페이지호스트>:3001`)에 **Realtime(Node) 프로세스가 없음**이 가장 흔합니다.
- **조치**: 터미널에서 `realtime` 폴더로 이동 후 `npm run dev` 가 떠 있는지 확인합니다. 콘솔에 `ECH realtime server listening on http://0.0.0.0:3001` 유사 로그가 보여야 합니다.
- **점검**: 브라우저나 터미널에서 `GET http://localhost:3001/health` — `status: ok` JSON이면 HTTP 수신은 정상입니다.
- (선택) 바인딩 주소: 환경변수 `SOCKET_HOST`(기본 `0.0.0.0`), 포트: `SOCKET_PORT`(기본 `3001`).

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
# 백엔드→실시간 서버 내부 브로드캐스트 인증(선택). 미설정이면 realtime은 토큰 없이 허용, 운영에서는 양쪽에 동일 값 설정 권장.
# REALTIME_INTERNAL_BASE_URL=http://localhost:3001
# REALTIME_INTERNAL_TOKEN=
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
이미 만들어 둔 `users` 테이블에 `company_name` / `division_name` / `team_name` 또는 `company_code` 컬럼이 없다면 먼저 `docs/sql/migrate_users_add_org_columns.sql`·`docs/sql/migrate_users_company_key.sql`을 실행한 뒤 시드를 적용합니다.

그 다음 조직도 API를 동작시키려면 `org_groups`/`org_group_members`를 백필해야 합니다.
```bash
psql -h localhost -p 5432 -U ech_user -d ech -f docs/sql/create_org_groups.sql
psql -h localhost -p 5432 -U ech_user -d ech -f docs/sql/create_org_group_members.sql
psql -h localhost -p 5432 -U ech_user -d ech -f docs/sql/backfill_org_groups_from_users.sql
psql -h localhost -p 5432 -U ech_user -d ech -f docs/sql/backfill_org_group_members_from_users.sql
```

## 6) 점검 체크리스트
- Java 17 적용 확인 (`java -version`)
- Backend wrapper 실행 확인 (`gradlew.bat`)
- Realtime 의존성 설치 완료 (`npm install`)
- PostgreSQL 연결 정보 확인 (`.env.example` 기준)
- (선택) 사용자 검색·멤버 API 테스트용 시드 적용 (`seed_test_users.sql`)
- (테스트) `./gradlew test` 실행 시 H2 인메모리 환경에서 `org_groups/org_group_members`는 테스트 코드가 users 기반으로 직접 시드함(OrgSync POST upsert는 H2 `ON CONFLICT` 미지원)
