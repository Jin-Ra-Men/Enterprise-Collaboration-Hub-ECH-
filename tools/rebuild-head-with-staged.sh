#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
MSG_FILE="${1:?}"
export GIT_AUTHOR_NAME="$(git log -1 --format=%an HEAD)"
export GIT_AUTHOR_EMAIL="$(git log -1 --format=%ae HEAD)"
export GIT_AUTHOR_DATE="$(git log -1 --format=%aD HEAD)"
export GIT_COMMITTER_NAME="$(git log -1 --format=%cn HEAD)"
export GIT_COMMITTER_EMAIL="$(git log -1 --format=%ce HEAD)"
export GIT_COMMITTER_DATE="$(git log -1 --format=%cD HEAD)"
T=$(git write-tree)
P=$(git rev-parse HEAD~1)
NEW=$(git commit-tree "$T" -p "$P" < "$MSG_FILE")
git update-ref HEAD "$NEW"
echo "HEAD -> $NEW"
