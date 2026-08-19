#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOOK="$ROOT_DIR/.githooks/commit-msg"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

git -C "$TMP_DIR" init -q
git -C "$TMP_DIR" config user.name Test
git -C "$TMP_DIR" config user.email test@example.com

run_hook() {
    printf '%s\n' "$1" > "$TMP_DIR/message"
    (cd "$TMP_DIR" && "$HOOK" "$TMP_DIR/message")
}

run_hook 'fix: close issue (ref #123)'
run_hook 'fix: connect refs #123 and ref #456'
run_hook 'fix: closed issue ref #999'

if run_hook 'fix: missing issue reference'; then
    echo "missing reference was accepted" >&2
    exit 1
fi

printf '%s\n' 'Merge branch feature' > "$TMP_DIR/message"
printf '%s\n' '0000000000000000000000000000000000000000' > "$TMP_DIR/.git/MERGE_HEAD"
(cd "$TMP_DIR" && "$HOOK" "$TMP_DIR/message")
rm "$TMP_DIR/.git/MERGE_HEAD"

echo 'PASS: commit-msg hook fixtures'
