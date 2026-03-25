# ERRORS

에러 발생 내역을 기록합니다.

---

## 2026-03-25 — 실시간 메시지 수신 안됨 + 로그아웃 후 관리자 탭 잔류

- **에러 요약 1**: 메시지 전송 후 새로고침해야만 보임 — 실시간 수신(socket `message:new`) 무시됨
- **발생 위치 1**: `frontend/app.js` — `socket.on("message:new")`, `realtime/src/server.js` — broadcastPayload
- **원인 1**: PostgreSQL `bigint` 컬럼을 `pg` 라이브러리가 **문자열**로 반환. 서버가 `channelId: "1"` (string)을 전송하는데 프론트엔드가 `msg.channelId === activeChannelId` (number)로 비교 → `"1" === 1` → `false` → 렌더링 건너뜀
- **해결 1**: `server.js` broadcastPayload에 `Number()` 명시 변환 추가, `app.js` 비교에서 `Number(msg.channelId) === activeChannelId` 로 수정

- **에러 요약 2**: 관리자 로그아웃 후 일반 사용자 로그인 시 관리자 탭 잔류 (새로고침 후 해소)
- **발생 위치 2**: `frontend/app.js` — `logoutBtn` 이벤트 핸들러, `showMain()`
- **원인 2**: 로그아웃 시 DOM 상태(adminSection)를 초기화하지 않아 다음 로그인 계정의 권한과 무관하게 이전 상태 유지
- **해결 2**: 로그아웃 핸들러에서 `adminSection.classList.add("hidden")` 추가, `showMain()`에서도 항상 hidden 초기화 후 role 조건 적용

---

## 2026-03-25 — 리얼타임 서버 메시지 전송 실패 (DB NOT NULL 제약 위반)

- **에러 요약**: 채널에서 메시지 전송 시 "전송 실패: 메시지 저장 중 오류가 발생했습니다. 잠시 후 재시도해주세요." 반환
- **발생 위치**: `realtime/src/db.js` — `saveMessage()` 함수
- **원인**: `messages` 테이블의 `message_type`, `is_deleted`, `is_edited`, `updated_at`, `created_at` 컬럼이 모두 `NOT NULL`이지만, 리얼타임 서버의 INSERT문이 `channel_id`, `sender_id`, `body`만 지정하여 NOT NULL 제약 위반 발생
- **해결**: `db.js` INSERT 쿼리에 누락 컬럼 추가 — `message_type='TEXT'`, `is_deleted=false`, `is_edited=false`, `created_at=NOW()`, `updated_at=NOW()`
- **추가 개선**: 브로드캐스트 페이로드에 `senderName` 추가 — 메시지 수신 시 발신자 이름 표시

---

## 2026-03-25 — CI 테스트 실패: MEMBER 채널 생성 권한 변경 후 테스트 불일치

- **에러 요약**: CI `./gradlew test` 실행 시 `채널 API 통합 테스트 > MEMBER 권한으로 채널 생성 시 403 반환 FAILED`
- **발생 위치**: `backend/src/test/java/com/ech/backend/api/channel/ChannelApiTest.java:56`
- **원인**: `ChannelController.createChannel()` 의 `@RequireRole`을 MANAGER → MEMBER로 변경하여 모든 사용자가 채널을 생성할 수 있게 됐으나, 기존 테스트는 여전히 MEMBER의 채널 생성이 403(Forbidden)을 반환해야 한다고 가정함
- **해결**: `ChannelApiTest`의 `create_channel_as_member_forbidden()` 테스트를 `create_channel_as_member_success()`로 수정 — 기대 상태 코드 `isForbidden()` → `isOk()`로 변경

---

## 2026-03-24 — 프론트엔드 정적 파일 서빙 실패 (500 반환)

- **에러 요약**: `http://localhost:8080/index.html` 접속 시 HTML 대신 `{"code":"INTERNAL_SERVER_ERROR"}` JSON 반환
- **발생 위치**: `GlobalExceptionHandler.java`, `application.yml`
- **원인**:
  1. `frontend/` 폴더가 Spring Boot 정적 리소스 경로(`classpath:/static/`)에 없음
  2. `NoResourceFoundException` 발생 시 `GlobalExceptionHandler.handleUnhandled(Exception)`에 걸려 500 반환
- **해결**:
  - `application.yml`에 `spring.web.resources.static-locations: file:../frontend/` 추가
  - `GlobalExceptionHandler`에 `NoResourceFoundException` → 404 핸들러 추가

---

## 2026-03-24 — User 엔티티 JPA 기본 생성자 누락 (로그인 500)

- **에러 요약**: 로그인 API 호출 시 `{"code":"INTERNAL_SERVER_ERROR"}` 반환, `error_logs` 테이블에 `JpaSystemException: No default constructor for entity 'User'` 기록
- **발생 위치**: `backend/src/main/java/com/ech/backend/domain/user/User.java`
- **원인**: JPA는 엔티티 조회 시 리플렉션으로 기본 생성자를 호출하는데, `User` 클래스에 인수 없는 생성자(`protected User() {}`)가 없었음
- **해결**: `User.java`에 `protected User() {}` 기본 생성자 추가

---

## 2026-03-24 — seed_test_users.sql INSERT 실패 (NOT NULL 위반)

- **에러 요약**: `psql -f seed_test_users.sql` 실행 시 `"created_at" 칼럼 null 값이 not null 제약조건에 위반` 오류
- **발생 위치**: `docs/sql/seed_test_users.sql`
- **원인**: INSERT 컬럼 목록에 `created_at`이 누락됨. JPA `ddl-auto: update`로 생성된 컬럼은 DB 레벨 DEFAULT 없이 `NOT NULL`만 설정됨
- **해결**: INSERT 컬럼 목록 및 VALUES에 `created_at, NOW()` 추가

---

## 2026-03-24 — CI 통합 테스트 2차 실패 (ChannelApiTest PathNotFoundException)

- **에러 요약**: `채널 API 통합 테스트 > MANAGER 이상 권한으로 채널 생성 성공 FAILED` — `com.jayway.jsonpath.PathNotFoundException`
- **발생 위치**: `ChannelApiTest.java:36`
- **원인**: `ChannelResponse` 레코드 필드명이 `channelId`인데 테스트에서 `$.data.id`로 조회 → 경로 없음 예외 발생. 연쇄적으로 `채널 단건 조회 성공` 테스트도 `channelId`가 0으로 추출되어 실패
- **해결**: `ChannelApiTest`의 `$.data.id` → `$.data.channelId`, `path("id")` → `path("channelId")` 3곳 수정

---

## 2026-03-24 — CI 통합 테스트 1차 실패 8건

- **에러 요약**: GitHub Actions CI `./gradlew test` 실행 시 21개 중 8개 실패
- **발생 위치**: `AuthApiTest`, `ChannelApiTest`, `SearchApiTest`

| # | 테스트 | 기대 | 실제 | 원인 |
|---|---|---|---|---|
| 1 | 잘못된 비밀번호 로그인 401 | 401 | 400 | `AuthService`가 `IllegalArgumentException` 던짐 → `GlobalExceptionHandler` 400 처리 |
| 2 | 존재하지 않는 계정 로그인 401 | 401 | 400 | 동일 |
| 3 | 토큰 없이 /me 호출 401 | 401 | 403 | `SecurityConfig`에 `authenticationEntryPoint` 미설정, Spring Security 기본이 `Http403ForbiddenEntryPoint` |
| 4 | 잘못된 JWT로 /me 호출 401 | 401 | 403 | 동일 |
| 5 | 채널 생성 성공 | 200 | 400 | 테스트 JSON `"type"` 사용, 실제 필드명은 `channelType` → `@NotNull` 검증 실패 |
| 6 | 채널 단건 조회 성공 | 200 | 400/404 | 채널 생성 실패로 `channelId=0` → 조회 실패 |
| 7 | 존재하지 않는 채널 404 | 404 | 400 | `ChannelService`가 `IllegalArgumentException` 던짐 → 400 처리 |
| 8 | JWT 없이 검색 401 | 401 | 403 | 원인 3번과 동일 |

- **해결**:
  - `UnauthorizedException` 신규 추가 → `GlobalExceptionHandler` 401 핸들러, `AuthService`에서 사용
  - `NotFoundException` 신규 추가 → `GlobalExceptionHandler` 404 핸들러, `ChannelService`에서 사용
  - `SecurityConfig`에 `.exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(UNAUTHORIZED)))` 추가
  - `ChannelApiTest` JSON `"type"` → `"channelType"` 수정

---

## 2026-03-24

- **에러 요약**: 백엔드 실행 환경 점검 중 Java/Gradle 미충족 확인
- **발생 위치**: `java -version`, `gradle -v`
- **원인**: 로컬 Java가 1.8이며, 프로젝트 요구사항(Java 17+)과 불일치. 시스템 Gradle 미설치.
- **해결**: Java 17 설치/적용 완료, Backend `gradlew.bat` 생성 및 실행 검증 완료. 이슈 해소.
