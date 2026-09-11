#!/bin/bash
# Regression test for the setup-hooks.sh ktlint download path (ref #904).
#
# Without a fail flag, curl exits 0 on HTTP errors and the installer cached
# the error body as a trusted executable. The installer must now fail loudly
# (non-zero, nothing cached) on a 404, download atomically on success, and
# stay idempotent once cached. No network: a stub curl simulates the 404.
# Runs via tools/quality/validate.sh run_shell_validation.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SETUP="$ROOT_DIR/tools/quality/setup-hooks.sh"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

FAKE_BIN="$WORK_DIR/fake-bin"
SANDBOX="$WORK_DIR/sandbox"
SANDBOX_HOME="$WORK_DIR/home"
CURL_ARG_LOG="$WORK_DIR/curl-args.log"
MARKER="$WORK_DIR/curl-invoked"
mkdir -p "$FAKE_BIN" "$SANDBOX_HOME"

KTLINT_PATH="$SANDBOX_HOME/.cache/company-app/ktlint/1.8.0"
KTLINT_DIR="$(dirname "$KTLINT_PATH")"

die() { printf 'test-setup-hooks-download: %s\n' "$*" >&2; exit 1; }

git -C "$SANDBOX" init -q 2>/dev/null || { mkdir -p "$SANDBOX"; git -C "$SANDBOX" init -q; }
git -C "$SANDBOX" config user.email test@example.com
git -C "$SANDBOX" config user.name Test

printf '#!/bin/sh\nexit 0\n' > "$FAKE_BIN/java"
chmod +x "$FAKE_BIN/java"

# Faithful 404 simulation: without a fail flag real curl exits 0 and writes
# the error body; with -f/--fail it exits 22. The stub records its args so
# the test can pin the fail flag behaviorally and structurally.
cat > "$FAKE_BIN/curl" <<'STUB_EOF'
#!/bin/bash
printf '%s\n' "$*" >> "$CURL_ARG_LOG"
has_fail=0
out=""
want_out=0
for a in "$@"; do
  if [ "$want_out" -eq 1 ]; then
    out="$a"
    want_out=0
    continue
  fi
  case "$a" in
    -f|--fail)
      has_fail=1
      ;;
    -o)
      want_out=1
      ;;
    -*)
      case "$a" in
        *f*) has_fail=1 ;;
      esac
      case "$a" in
        *o) want_out=1 ;;
      esac
      ;;
  esac
done
mode="${CURL_STUB_MODE:-fail404}"
if [ "$mode" = "success" ]; then
  printf '#!/bin/sh\nexit 0\n' > "$out"
  exit 0
fi
printf '<html>404: Not Found</html>\n' > "$out"
if [ "$has_fail" -eq 1 ]; then
  exit 22
else
  exit 0
fi
STUB_EOF
chmod +x "$FAKE_BIN/curl"

run_setup() {
  (cd "$SANDBOX" && env PATH="$FAKE_BIN:$PATH" HOME="$SANDBOX_HOME" CURL_ARG_LOG="$CURL_ARG_LOG" CURL_STUB_MODE="${1:-fail404}" MARKER="$MARKER" bash "$SETUP" >"$WORK_DIR/out.log" 2>&1)
}

# --- Fixture 1: 404 fails loudly, caches nothing ---------------------------
: > "$CURL_ARG_LOG"
if run_setup fail404; then
  die "404 download unexpectedly succeeded; log: $(cat "$WORK_DIR/out.log")"
fi
[ ! -e "$KTLINT_PATH" ] || die "404 left a file at KTLINT_PATH"
leftover="$(ls "$KTLINT_DIR"/.ktlint.* 2>/dev/null || true)"
[ -z "$leftover" ] || die "404 left temp files: $leftover"
grep -Eq -- '(^| )-[^ ]*f' "$CURL_ARG_LOG" || die "curl invoked without a fail flag: $(cat "$CURL_ARG_LOG")"
grep -q "ERROR: failed to download" "$WORK_DIR/out.log" || die "missing failure message, got: $(cat "$WORK_DIR/out.log")"

# --- Fixture 2: success downloads an executable -----------------------------
: > "$CURL_ARG_LOG"
run_setup success || die "success download failed: $(cat "$WORK_DIR/out.log")"
[ -x "$KTLINT_PATH" ] || die "success left no executable at KTLINT_PATH"
grep -q "cached at" "$WORK_DIR/out.log" || die "missing cached message, got: $(cat "$WORK_DIR/out.log")"

# --- Fixture 3: second run is idempotent, never re-fetches -------------------
cat > "$FAKE_BIN/curl" <<'STUB_EOF'
#!/bin/sh
touch "$MARKER"
exit 99
STUB_EOF
chmod +x "$FAKE_BIN/curl"
rm -f "$MARKER"
(cd "$SANDBOX" && env PATH="$FAKE_BIN:$PATH" HOME="$SANDBOX_HOME" bash "$SETUP" >"$WORK_DIR/out2.log" 2>&1) \
  || die "idempotent rerun failed: $(cat "$WORK_DIR/out2.log")"
grep -q "already cached" "$WORK_DIR/out2.log" || die "missing already-cached line, got: $(cat "$WORK_DIR/out2.log")"
[ ! -e "$MARKER" ] || die "idempotent rerun re-invoked curl"

printf 'PASS: setup-hooks download fails closed on HTTP errors, atomic on success\n'
