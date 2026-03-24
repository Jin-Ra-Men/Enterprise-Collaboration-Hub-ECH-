#!/usr/bin/env bash
# UTF-8 메시지 파일로 현재 HEAD 커밋 메시지만 교체합니다 (tree·parent 동일).
# 사용: Git Bash에서 ./tools/rewrite-head-commit-message.sh docs/your-msg.txt
set -euo pipefail
cd "$(dirname "$0")/.."
MSG_FILE="${1:?메시지 파일 경로 필요}"
export GIT_AUTHOR_NAME="$(git log -1 --format=%an HEAD)"
export GIT_AUTHOR_EMAIL="$(git log -1 --format=%ae HEAD)"
export GIT_AUTHOR_DATE="$(git log -1 --format=%aD HEAD)"
export GIT_COMMITTER_NAME="$(git log -1 --format=%cn HEAD)"
export GIT_COMMITTER_EMAIL="$(git log -1 --format=%ce HEAD)"
export GIT_COMMITTER_DATE="$(git log -1 --format=%cD HEAD)"
TREE=$(git rev-parse "HEAD^{tree}")
PARENT=$(git rev-parse HEAD~1)
NEW=$(git commit-tree "$TREE" -p "$PARENT" < "$MSG_FILE")
git update-ref HEAD "$NEW"
echo "HEAD -> $NEW"
