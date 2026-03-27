#!/usr/bin/env bash
# Used as GIT_EDITOR during rebase reword — writes UTF-8 commit message by call order.
set -eu
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COUNT_FILE="$REPO_ROOT/.git/reword-msg-counter"
MSG_FILE="${1:?}"

if [[ ! -f "$COUNT_FILE" ]]; then
  echo 0 > "$COUNT_FILE"
fi
n=$(cat "$COUNT_FILE")
n=$((n + 1))
echo "$n" > "$COUNT_FILE"

case "$n" in
  1)
    printf '%s\n' "fix: 조직도 팝업 선택 사용자 영역 위치 조정" > "$MSG_FILE"
    ;;
  2)
    cat > "$MSG_FILE" <<'EOF'
fix: 시스템 메시지 이후 메시지 발화 분리

시스템 메시지가 끼어들면 다음 사용자 메시지를 새 발화로 처리해 아바타/이름을 다시 표시.
EOF
    ;;
  3)
    cat > "$MSG_FILE" <<'EOF'
fix: 첨부파일 다운로드 감사로그 트랜잭션 오류 수정

read-only 요청 흐름에서 감사로그 INSERT가 실패하던 문제를 safeRecord REQUIRES_NEW로 분리해 해결.
ERRORS/CHANGELOG에 원인과 조치 내역을 기록.
EOF
    ;;
  4)
    cat > "$MSG_FILE" <<'EOF'
feat: 사용자별 테마 팝업 설정 적용

로그아웃 버튼 옆 톱니바퀴에서 테마를 선택하도록 UI를 변경하고,
사용자별 테마를 DB(users.theme_preference)에 저장해 재로그인 후에도 유지되도록 구현.
EOF
    ;;
  5)
    printf '%s\n' "fix: 조직도 팝업 상단 검색 영역 입력·셀렉트 스타일 보강" > "$MSG_FILE"
    ;;
  *)
    echo "Unexpected reword index: $n" >&2
    exit 1
    ;;
esac
