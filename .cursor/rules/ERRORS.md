# ERRORS

에러 발생 내역을 기록합니다.

## 2026-03-24 — CI 통합 테스트 8건 실패

- 에러 요약: GitHub Actions CI에서 통합 테스트 8건 실패
  - **원인 1** 로그인 실패 시 401 대신 400: `AuthService`가 `IllegalArgumentException` 던짐 → 400 처리됨
  - **원인 2** JWT 없는 요청에 401 대신 403: `SecurityConfig`에 `authenticationEntryPoint` 미설정, Spring Security 기본값이 403
  - **원인 3** `ChannelApiTest` JSON 오타: `"type"` 대신 `"channelType"` 이어야 함 → `@NotNull` 검증 실패 → 400
  - **원인 4** 채널 없을 때 400 대신 404: `IllegalArgumentException` → 400으로 처리됨
  - 해결: `UnauthorizedException`/`NotFoundException` 추가, `GlobalExceptionHandler` 핸들러 추가, `SecurityConfig` `authenticationEntryPoint` 설정, 테스트 JSON 수정

## 2026-03-24

- 에러 요약: 백엔드 실행 환경 점검 중 Java/Gradle 미충족 확인
  - 발생 위치(파일/명령/기능): `java -version`, `gradle -v`
  - 원인: 로컬 Java가 1.8이며, 프로젝트 요구사항(Java 17+)과 불일치. 시스템 Gradle 미설치.
  - 해결 방법 또는 현재 상태: Java 17 설치/적용 완료, Backend `gradlew.bat` 생성 및 실행 검증 완료. 이슈 해소.
