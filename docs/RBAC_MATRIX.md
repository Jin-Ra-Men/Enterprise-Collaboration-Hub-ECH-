# ECH RBAC 역할-권한 매트릭스 (Phase 3-2)

이 문서는 현재 API 기준 최소 RBAC 정책을 정의합니다.

## 역할 정의
- `MEMBER`: 일반 사용자
- `MANAGER`: 팀/프로젝트 운영 권한
- `ADMIN`: 시스템 관리자 권한 (`MANAGER` 포함)

## 권한 계층
- `ADMIN` >= `MANAGER` >= `MEMBER`

## 헤더 기반 권한 체크 (현재 구현)
- 서버는 `X-User-Role` 헤더를 읽어 권한을 판정합니다.
- 허용 값: `MEMBER`, `MANAGER`, `ADMIN`
- 값이 없거나 잘못되면 `FORBIDDEN` 반환

> 참고: 현재는 인증 미구현 상태이므로 헤더는 신뢰 경계 밖 입력입니다.
> Phase 3-2 이후 인증 연동 시 토큰/세션의 역할 클레임으로 대체해야 합니다.

## API별 최소 권한

### `ADMIN`
- `/api/admin/org-sync/**` 전체
  - 조직 사용자 프리뷰/동기화
  - 사용자 상태(`ACTIVE`/`INACTIVE`) 변경

### `MANAGER` 이상
- `GET /api/users/search`
- `POST /api/channels`
- `POST /api/channels/{channelId}/members`
- 칸반 변경 API
  - 보드 생성/삭제
  - 컬럼 생성/수정/삭제
  - 카드 생성/수정/삭제
  - 담당자 추가/제거

### `MEMBER` 이상 (현재 별도 제한 없음)
- 메시지 작성/조회, 읽음 상태, 파일 메타데이터, 워크아이템 생성/조회 등
  - 도메인 단에서 채널 멤버십 등 추가 검증 수행

## 향후 고도화 포인트
- 인증 연동 후 `X-User-Role` 제거
- `X-User-Id` 또는 토큰 subject와 요청 body userId 정합 검사
- 프로젝트/채널 단위 세분 권한(읽기/쓰기/관리) 분리
- 감사 로그(`누가`, `어떤 권한으로`, `무엇을`) 연계
