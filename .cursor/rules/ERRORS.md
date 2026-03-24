# ERRORS

에러 발생 내역을 기록합니다.

## 2026-03-24

- 에러 요약: 백엔드 실행 환경 점검 중 Java/Gradle 미충족 확인
  - 발생 위치(파일/명령/기능): `java -version`, `gradle -v`
  - 원인: 로컬 Java가 1.8이며, 프로젝트 요구사항(Java 17+)과 불일치. 시스템 Gradle 미설치.
  - 해결 방법 또는 현재 상태: Java 17 설치/적용 완료, Backend `gradlew.bat` 생성 및 실행 검증 완료. 이슈 해소.
