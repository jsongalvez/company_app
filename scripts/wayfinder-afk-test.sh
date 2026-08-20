#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
git init -q "$TMP/repo"
git -C "$TMP/repo" config user.email test@example.invalid
git -C "$TMP/repo" config user.name test
touch "$TMP/repo/.keep"
git -C "$TMP/repo" add .keep
git -C "$TMP/repo" commit -qm root
git -C "$TMP/repo" branch -M master
git -C "$TMP/repo" switch -q -c ralph/wayfinder-312
cp "$ROOT/scripts/wayfinder-ci.sh" "$TMP/wayfinder-ci.sh"
sed -i "s#REPO=.*#REPO=\"$TMP/repo\"#" "$TMP/wayfinder-ci.sh"
bash "$TMP/wayfinder-ci.sh" validate-branch 312
if bash "$TMP/wayfinder-ci.sh" validate-branch 313 2>/dev/null; then exit 1; fi
git -C "$TMP/repo" switch -q master
if bash "$TMP/wayfinder-ci.sh" validate-branch 312 2>/dev/null; then exit 1; fi
bash "$TMP/wayfinder-ci.sh" prepare 313
[ "$(git -C "$TMP/repo" branch --show-current)" = ralph/wayfinder-313 ]
git -C "$TMP/repo" switch -q ralph/wayfinder-312

cat > "$TMP/gh" <<'EOF'
#!/usr/bin/env bash
if [ "$1 $2" = 'pr view' ]; then
  printf '%s\n' '{"url":"https://example.invalid/pr/1","baseRefName":"master","headRefName":"ralph/wayfinder-312"}'
else
  printf 'quality pass\nopenapi pass\n'
fi
EOF
chmod +x "$TMP/gh"
git -C "$TMP/repo" switch -q ralph/wayfinder-312
PATH="$TMP:$PATH" bash "$TMP/wayfinder-ci.sh" validate-pr 312 https://example.invalid/pr/1
PATH="$TMP:$PATH" bash "$TMP/wayfinder-ci.sh" validate-ci https://example.invalid/pr/1
printf 'pending\n' > "$TMP/gh"
if PATH="$TMP:$PATH" bash "$TMP/wayfinder-ci.sh" validate-ci https://example.invalid/pr/1; then exit 1; fi

# No required checks configured: gate falls back to all reported checks.
cat > "$TMP/gh" <<'EOF'
#!/usr/bin/env bash
if [ "$1 $2" = 'pr view' ]; then
  printf '%s\n' '{"url":"https://example.invalid/pr/1","baseRefName":"master","headRefName":"ralph/wayfinder-312"}'
elif [ "$*" = 'pr checks --required' ]; then
  printf 'no required checks reported on the %s branch\n' "'ralph/wayfinder-312'"
  exit 1
else
  printf 'quality pass\njmh pass\n'
fi
EOF
PATH="$TMP:$PATH" bash "$TMP/wayfinder-ci.sh" validate-ci https://example.invalid/pr/1

cat > "$TMP/gh" <<'EOF'
#!/usr/bin/env bash
if [ "$1 $2" = 'pr view' ]; then
  printf '%s\n' '{"url":"https://example.invalid/pr/1","baseRefName":"master","headRefName":"ralph/wayfinder-312"}'
elif [ "$*" = 'pr checks --required' ]; then
  printf 'no required checks reported on the %s branch\n' "'ralph/wayfinder-312'"
  exit 1
else
  printf 'quality pass\nk6-baseline fail\n'
  exit 1
fi
EOF
if PATH="$TMP:$PATH" bash "$TMP/wayfinder-ci.sh" validate-ci https://example.invalid/pr/1; then exit 1; fi

printf 'wayfinder AFK checks: PASS\n'
