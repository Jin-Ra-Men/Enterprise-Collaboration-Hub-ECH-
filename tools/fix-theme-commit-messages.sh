#!/usr/bin/env bash
# e5b33f4 이후 커밋들의 한글 메시지를 UTF-8로 재작성합니다.
# Git Bash에서: bash ./tools/fix-theme-commit-messages.sh
set -euo pipefail
cd "$(dirname "$0")/.."
BASE="e5b33f4"
rm -f .git/reword-msg-counter
chmod +x tools/git-editor-reword-msg.sh
SCRIPT="$(cd "$(dirname "$0")" && pwd)/git-editor-reword-msg.sh"
export GIT_SEQUENCE_EDITOR='sed -i.bak -e "s/^pick/reword/"'
# 경로에 공백이 있어도 동작하도록 bash로 명시 실행
export GIT_EDITOR="bash \"$SCRIPT\""
git rebase -i "$BASE"
rm -f .git/reword-msg-counter .git/reword-msg-counter.bak 2>/dev/null || true
echo "OK. Run: git log -6 --oneline && git cat-file -p HEAD | head -12"
