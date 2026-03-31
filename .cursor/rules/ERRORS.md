# ERRORS

에러 발생 내역을 기록합니다.

---

## 2026-03-31 — `./gradlew test` 실패 (JVM 8 실행)

- **에러 요약**: `backend/./gradlew.bat test` 실행 시 Spring Boot Gradle plugin 요구 버전 미충족으로 빌드 실패
- **발생 위치(파일/명령/기능)**: 명령 `backend/./gradlew.bat test`
- **원인**: 이 셸에서 JVM이 Java 8로 실행되어 프로젝트 요구사항(Java 17+)을 만족하지 못함
- **해결/현재 상태**: `JAVA_HOME`을 JDK 17으로 지정 후 재실행하여 `./gradlew test`는 통과(BUILD SUCCESSFUL)

---

## 2026-03-27 — DM 생성 실패 (channels channel_type 체크 제약)

- **에러 요약**: DM 생성 API(`POST /api/channels`, `channelType=DM`) 호출 시 내부 오류 발생
- **발생 위치(파일/명령/기능)**: 채널 생성 기능, DB `channels` 테이블 제약 `channels_channel_type_check`
- **원인**: 기존 로컬 DB 제약이 `PUBLIC/PRIVATE`만 허용하고 `DM`을 허용하지 않아 INSERT 실패
- **해결/현재 상태**: `DataInitializer` 기동 보정 로직으로 제약을 `PUBLIC/PRIVATE/DM`으로 재생성, 수동 적용용 SQL `docs/sql/migrate_channels_allow_dm_type.sql` 추가

---

## 2026-03-27 — `./gradlew test` 실패 (JVM 8 실행)

- **에러 요약**: `./gradlew test` 실행 시 Spring Boot Gradle plugin 요구 버전 미충족으로 빌드 실패
- **발생 위치(파일/명령/기능)**: 명령 `backend/./gradlew.bat test`
- **원인**: 현재 셸 JVM이 Java 8로 실행되어, 프로젝트 요구사항(Java 17+)을 만족하지 못함
- **해결/현재 상태**: `JAVA_HOME`을 JDK 17으로 지정 후 재실행하여 검증 진행 (2026-03-27 재확인: 동일 원인으로 테스트 미실행 상태)

---

## 2026-03-27 — `./gradlew test` 실패 (employee_no 계약 전환 중 테스트 컴파일)

- **에러 요약**: `JwtUtilTest`에서 `UserPrincipal` 시그니처 변경(`userId` 제거) 미반영으로 `compileTestJava` 실패
- **발생 위치(파일/명령/기능)**: `backend/src/test/java/com/ech/backend/util/JwtUtilTest.java`, 명령 `./gradlew test`
- **원인**: 인증 principal을 `employeeNo` 중심으로 바꾸는 과정에서 테스트 생성자/검증 코드가 기존 `userId` 기준을 사용
- **해결/현재 상태**: `JwtUtilTest`를 `employeeNo` 기준으로 수정 후 재실행 진행

---

## 2026-03-27 — `./gradlew test` 실패 (API 계약 전환 후 통합테스트 불일치)

- **에러 요약**: `AuthApiTest`/`ChannelApiTest`가 구 필드(`userId`, `createdByUserId`)를 검증해 4건 실패
- **발생 위치(파일/명령/기능)**: `backend/src/test/java/com/ech/backend/api/auth/AuthApiTest.java`, `backend/src/test/java/com/ech/backend/api/channel/ChannelApiTest.java`, 명령 `./gradlew test`
- **원인**: API 요청·응답 계약을 `employeeNo` 중심으로 변경했지만 테스트 JSON/검증 경로가 구 계약에 머물러 있음
- **해결/현재 상태**: 테스트 요청/검증을 `employeeNo` 계약으로 수정 후 재실행 진행

---

## 2026-03-26 — 첨부파일 다운로드 시 감사로그 INSERT 실패 (read-only 트랜잭션)

- **에러 요약**: 첨부파일 다운로드 시 서버 내부 오류가 발생하고, DB 로그에 `SQLState: 25006` / `read-only transaction` INSERT 실패가 기록됨
- **발생 위치(파일/명령/기능)**: `backend/src/main/java/com/ech/backend/api/file/ChannelFileService.java` (`downloadFile`, `getDownloadInfo` 경로에서 `auditLogService.safeRecord` 호출)
- **원인**: `safeRecord()`가 같은 클래스 내부 `record()`를 직접 호출(자기호출)하여 `@Transactional(REQUIRES_NEW)`가 적용되지 않았고, 상위 read-only 트랜잭션 문맥에서 `audit_logs` INSERT가 실행됨
- **해결/현재 상태**: `AuditLogService.safeRecord()` 자체에 `@Transactional(propagation = Propagation.REQUIRES_NEW)`를 부여해 독립 쓰기 트랜잭션으로 실행되도록 수정

---

## 2026-03-25 — 백엔드 컴파일 실패 (`ChannelType.DM` 누락)

- **에러 요약**: `./gradlew test` 시 `:compileJava` 실패 — `cannot find symbol: variable DM` (`ChannelService.java`)
- **발생 위치**: `backend/src/main/java/com/ech/backend/api/channel/ChannelService.java` — `ChannelType.DM` 참조
- **원인**: DM 전용 채널 생성 로직은 추가되었으나 `ChannelType` enum에 `DM` 상수가 없음
- **해결**: `ChannelType.java`에 `DM` 추가 후 재빌드·테스트 통과

---

## 2026-03-25 — 연속 메시지 들여쓰기 + 비멤버 발신 메시지 표시

- **에러 요약 1**: 같은 사람이 연속으로 보낸 두 번째 줄·메시지가 오른쪽으로 과하게 들여쓰기됨
- **발생 위치 1**: `frontend/styles.css` — `.msg-row.msg-continued { padding-left: 46px; }` 와 `.msg-spacer { width: 36px; }` 및 `gap: 10px` 가 동시에 적용되어 가로 오프셋이 이중으로 쌓임
- **해결 1**: `.msg-continued` 의 `padding-left` 제거, `.msg-text` 에 `margin:0; text-align:left` 명시, `appendMessage` 에서 `senderId` 를 `Number()` 로 통일·`loadMessages`/`logout` 시 `lastSenderId` 초기화

- **에러 요약 2**: 채널 멤버가 아닌 사용자(예: 시스템 관리자) 이름으로 옛 메시지가 보임
- **발생 위치 2**: DB에 `messages` 행은 있으나 `channel_members` 에 해당 `sender_id` 가 없는 경우 — 개발 중 `psql` 등으로 **직접 INSERT** 한 데이터가 원인일 수 있음 (앱 경로는 멤버십을 거침)
- **해결 2**: 리얼타임 `saveMessage` 에 INSERT 전 `channel_members` 존재 검사 추가, 비멤버는 저장 거부 (`NOT_CHANNEL_MEMBER`). 기존 잘못된 행은 `docs/sql/cleanup_dev_messages.sql` 참고하여 선택 삭제

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

---

## 2026-03-26 — OrgSyncService groupCache 스코프 컴파일 오류

- **에러 요약**: `OrgSyncService.java`에서 `groupCache` 변수를 잘못 참조하여 `compileJava` 실패
- **발생 위치(파일/명령/기능)**: `backend/src/main/java/com/ech/backend/api/orgsync/OrgSyncService.java` — `upsertOrgAndMembership(...)` 호출부
- **원인**: `syncUsers(...)` 내부 지역변수 `groupCache`가 헬퍼 메서드 스코프에 없어서 컴파일 실패
- **해결/현재 상태**: `upsertOrgAndMembership(...)` 시그니처에 `groupCache`를 전달하도록 수정 후 `./gradlew test` 통과

---

## 2026-03-26 — H2에서 org-sync upsert SQL(ON CONFLICT) 문법 오류

- **에러 요약**: 테스트에서 `POST /api/admin/org-sync/users/sync?source=TEST` 호출 시 `users` UPSERT native SQL 구문 오류로 500 발생
- **발생 위치(파일/명령/기능)**: `UserDirectoryApiTest` — `org-sync` 동기화 호출(@BeforeEach), `UserRepository.upsertByEmployeeNo(...)`
- **원인**: H2 인메모리 DB는 PostgreSQL의 `INSERT ... ON CONFLICT` 문법을 지원하지 않아 `SQLState: 42000` 문법 오류
- **해결/현재 상태**: 테스트를 `org-sync` 호출 대신 Java 로 `org_groups/org_group_members`를 직접 시드하도록 수정하여 테스트 통과

---

## 2026-03-26 — OrgGroupCodes API 변경 후 테스트 컴파일 실패

- **에러 요약**: `./gradlew test` 실행 시 `UserDirectoryApiTest`에서 `fingerprintCompany/prettyCompany` 등 메서드 미존재 컴파일 오류 발생
- **발생 위치(파일/명령/기능)**: `backend/src/test/java/com/ech/backend/api/user/UserDirectoryApiTest.java`, 명령 `./gradlew test --no-daemon`
- **원인**: `OrgGroupCodes`를 난수형 코드 생성에서 가독 코드 생성 API(`companyCode/divisionCode/teamCode`)로 변경했지만 테스트 코드가 구 메서드를 호출
- **해결/현재 상태**: 테스트 코드를 신규 API 호출로 교체 완료

---

## 2026-03-26 — OrgGroupCodes alias switch 상수식 컴파일 오류

- **에러 요약**: `./gradlew compileJava` 실행 시 `constant string expression required` 다수 발생
- **발생 위치(파일/명령/기능)**: `backend/src/main/java/com/ech/backend/domain/org/OrgGroupCodes.java`, 명령 `./gradlew compileJava`
- **원인**: `switch` case 라벨에 `"문자열".toUpperCase()`를 사용하여 컴파일 타임 상수 조건을 위반
- **해결/현재 상태**: `switch`를 `if` 체인 비교로 교체하여 컴파일 가능하도록 수정
