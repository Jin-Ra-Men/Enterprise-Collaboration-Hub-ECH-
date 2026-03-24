# ECH 기능 명세서

이 문서는 구현된 기능의 동작 기준을 상세히 기록합니다.  
신규 개발/수정 시 해당 기능 항목을 반드시 갱신합니다.

## 작성 원칙
- 기능 단위로 섹션을 분리합니다.
- "무엇을, 왜, 어떻게" 관점으로 작성합니다.
- 문서를 읽은 개발자가 코드 수정 가능할 수준으로 상세히 작성합니다.

## 기능 템플릿

### 기능명
- 목적:
- 사용자:
- 관련 화면/경로:
- 관련 API:
- 관련 Socket 이벤트:
- 입력/출력:
- 상태 전이/예외 케이스:
- 권한/보안:
- 로그/감사 포인트:
- 테스트 기준:
- 비고:

---

## 공통 응답/에러 처리
- 목적: API 응답 포맷을 일관화하고 예외를 표준 코드로 처리
- 사용자: Backend 개발자, API 연동 개발자
- 관련 화면/경로: Backend 전역
- 관련 API: 전 API 공통 (`ApiResponse`)
- 관련 Socket 이벤트: 해당 없음
- 입력/출력:
  - 성공: `success=true`, `data` 포함
  - 실패: `success=false`, `error.code`, `error.message` 포함
- 상태 전이/예외 케이스:
  - `IllegalArgumentException` -> `BAD_REQUEST`
  - `MethodArgumentNotValidException` -> `VALIDATION_ERROR`
  - 그 외 예외 -> `INTERNAL_SERVER_ERROR`
- 권한/보안: 예외 메시지에 내부 구현 세부정보 노출 금지
- 로그/감사 포인트: 서버 공통 예외 로그 기준에 따라 추후 확장
- 테스트 기준:
  - 정상 응답 포맷 검증
  - 검증 실패 시 오류 코드/메시지 검증
  - 내부 오류 시 표준 오류 코드 검증
- 비고: `HealthController`가 공통 포맷 적용 기준 예시
