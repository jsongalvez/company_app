#!/usr/bin/env bash
# Harness for scripts/gate-check.mjs — runs the shipped checker against fixtures,
# asserts flips, evidence, exit codes. Mirrors the wayfinder-loop harness pattern.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CHECKER="$ROOT/scripts/gate-check.mjs"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

fails=0
assert() { # assert <desc> <got> <want>
  if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1 (got: $2, want: $3)"; fails=$((fails+1)); fi
}

# --- fixture 1: all pass ------------------------------------------------------
cat > "$WORK/pass.md" <<'EOF'
# Gates: fixture

- [ ] G1: true exits 0
  CHECK: true
  EXPECT: EXIT 0
  EVIDENCE: pending

- [ ] G2: echo output matched
  CHECK: echo hello
  EXPECT: hello
  EVIDENCE: pending
EOF
node "$CHECKER" "$WORK/pass.md" > "$WORK/out1" 2>&1; c1=$?
assert "pass: exit 0" "$c1" "0"
assert "pass: G1 flipped" "$(grep -c '^\s*- \[x\] G1' "$WORK/pass.md")" "1"
assert "pass: G2 flipped" "$(grep -c '^\s*- \[x\] G2' "$WORK/pass.md")" "1"
assert "pass: G1 evidence written" "$(grep 'EVIDENCE:' "$WORK/pass.md" | sed -n 1p | grep -c pending)" "0"
assert "pass: report 2/2" "$(grep -o '2/2 gates met' "$WORK/out1")" "2/2 gates met"

# --- fixture 2: failing check ------------------------------------------------
cat > "$WORK/fail.md" <<'EOF'
- [ ] G1: failing command
  CHECK: exit 3
  EXPECT: EXIT 0
  EVIDENCE: pending
EOF
node "$CHECKER" "$WORK/fail.md" > "$WORK/out2" 2>&1; c2=$?
assert "fail: exit 1" "$c2" "1"
assert "fail: box not flipped" "$(grep -c '^\s*- \[x\]' "$WORK/fail.md")" "0"
assert "fail: 0/1 reported" "$(grep -o '0/1 gates met' "$WORK/out2")" "0/1 gates met"

# --- fixture 3: EXPECT substring + MATCHES ------------------------------------
cat > "$WORK/expect.md" <<'EOF'
- [ ] G1: substring miss
  CHECK: echo foo
  EXPECT: bar
  EVIDENCE: pending

- [ ] G2: regex match
  CHECK: echo line-42
  EXPECT: MATCHES ^line-\d+$
  EVIDENCE: pending
EOF
node "$CHECKER" "$WORK/expect.md" > "$WORK/out3" 2>&1; c3=$?
assert "expect: exit 1" "$c3" "1"
assert "expect: G1 unflipped" "$(grep -c '^\s*- \[ \] G1' "$WORK/expect.md")" "1"
assert "expect: G2 flipped" "$(grep -c '^\s*- \[x\] G2' "$WORK/expect.md")" "1"

# --- fixture 4: hand-checked box with pending evidence is re-verified ---------
cat > "$WORK/stale.md" <<'EOF'
- [x] G1: claimed done without evidence
  CHECK: echo verified-now
  EXPECT: verified-now
  EVIDENCE: pending
EOF
node "$CHECKER" "$WORK/stale.md" > /dev/null 2>&1; c4=$?
assert "stale: pass re-verifies, exit 0" "$c4" "0"
assert "stale: evidence written" "$(grep -c 'EVIDENCE: verified-now' "$WORK/stale.md")" "1"

cat > "$WORK/stale-fail.md" <<'EOF'
- [x] G1: claimed done, check fails now
  CHECK: exit 1
  EXPECT: EXIT 0
  EVIDENCE: pending
EOF
node "$CHECKER" "$WORK/stale-fail.md" > /dev/null 2>&1; c5=$?
assert "stale-fail: box unflipped" "$(grep -c '^\s*- \[x\]' "$WORK/stale-fail.md")" "0"
assert "stale-fail: exit 1" "$c5" "1"

# --- fixture 5: no gates ------------------------------------------------------
echo "# empty" > "$WORK/empty.md"
node "$CHECKER" "$WORK/empty.md" > /dev/null 2>&1; c6=$?
assert "empty: exit 1" "$c6" "1"

# --- fixture 6: idempotent re-run refreshes evidence --------------------------
node "$CHECKER" "$WORK/pass.md" > /dev/null 2>&1
node "$CHECKER" "$WORK/pass.md" > /dev/null 2>&1; c7=$?
assert "rerun: exit 0" "$c7" "0"
assert "rerun: boxes stay flipped" "$(grep -c '^\s*- \[x\]' "$WORK/pass.md")" "2"

# --- fixture 7: G-nc-style id parses and never corrupts a sibling --------------
cat > "$WORK/nc.md" <<'EOF'
- [ ] G1: first gate keeps its own check
  CHECK: echo g1
  EXPECT: g1
  EVIDENCE: pending

- [ ] G-nc: negative control
  CHECK: echo red
  EXPECT: red
  EVIDENCE: pending
EOF
node "$CHECKER" "$WORK/nc.md" > "$WORK/out7" 2>&1; c7b=$?
assert "G-nc: exit 0" "$c7b" "0"
assert "G-nc: G1 flipped with own check" "$(grep -c '^\s*- \[x\] G1' "$WORK/nc.md")" "1"
assert "G-nc: G-nc flipped" "$(grep -c '^\s*- \[x\] G-nc' "$WORK/nc.md")" "1"
assert "G-nc: both reported" "$(grep -o '2/2 gates met' "$WORK/out7")" "2/2 gates met"

# --- fixture 8: malformed box line fails loudly --------------------------------
cat > "$WORK/mal.md" <<'EOF'
- [ ] G1: parses fine
  CHECK: true
  EXPECT: EXIT 0
  EVIDENCE: pending

- [x]G2:no space after bracket
  CHECK: echo never-run
  EXPECT: never-run
  EVIDENCE: pending
EOF
node "$CHECKER" "$WORK/mal.md" > "$WORK/out8" 2>&1; c8=$?
assert "malformed: exit 1" "$c8" "1"
assert "malformed: reported" "$(grep -c 'malformed box line' "$WORK/out8")" "1"
assert "malformed: file not half-flipped" "$(grep -c '^\s*- \[x\] G1' "$WORK/mal.md")" "0"

# --- fixture 9: CRLF file still parses ----------------------------------------
printf -- '- [ ] G1: crlf gate\r\n  CHECK: echo ok\r\n  EXPECT: ok\r\n  EVIDENCE: pending\r\n' > "$WORK/crlf.md"
node "$CHECKER" "$WORK/crlf.md" > /dev/null 2>&1; c9=$?
assert "crlf: exit 0" "$c9" "0"
assert "crlf: box flipped" "$(grep -c '^\s*- \[x\] G1' "$WORK/crlf.md")" "1"

# --- fixture 10: blank EXPECT is unmet -----------------------------------------
cat > "$WORK/blank.md" <<'EOF'
- [ ] G1: blank expect
  CHECK: true
  EXPECT: 
  EVIDENCE: pending
EOF
node "$CHECKER" "$WORK/blank.md" > "$WORK/out10" 2>&1; c10=$?
assert "blank-exp: exit 1" "$c10" "1"
assert "blank-exp: reported" "$(grep -c 'blank EXPECT' "$WORK/out10")" "1"

# --- fixture 11: failed re-run clears evidence --------------------------------
cat > "$WORK/clear.md" <<'EOF'
- [ ] G1: flaky
  CHECK: echo once
  EXPECT: once
  EVIDENCE: pending
EOF
node "$CHECKER" "$WORK/clear.md" > /dev/null 2>&1
sed -i 's/echo once/exit 1/' "$WORK/clear.md"
node "$CHECKER" "$WORK/clear.md" > /dev/null 2>&1; c11=$?
assert "clear: exit 1" "$c11" "1"
assert "clear: box unflipped" "$(grep -c '^\s*- \[x\]' "$WORK/clear.md")" "0"
assert "clear: evidence pending not stale" "$(grep -c 'EVIDENCE: pending' "$WORK/clear.md")" "1"

# --- fixture 12: CHECK runs from the repo root regardless of CWD ---------------
cat > "$WORK/cwd.md" <<'EOF'
- [ ] G1: pwd is repo root
  CHECK: pwd
  EXPECT: company_app
  EVIDENCE: pending
EOF
(cd /tmp && node "$CHECKER" "$WORK/cwd.md" > "$WORK/out12" 2>&1); c12=$?
assert "cwd: passes from /tmp" "$c12" "0"

# --- fixture 13: dry-run writes nothing ----------------------------------------
cat > "$WORK/dry.md" <<'EOF'
- [ ] G1: dry
  CHECK: true
  EXPECT: EXIT 0
  EVIDENCE: pending
EOF
node "$CHECKER" --dry "$WORK/dry.md" > /dev/null 2>&1; c13=$?
assert "dry: exit 0" "$c13" "0"
assert "dry: box not flipped" "$(grep -c '^\s*- \[x\]' "$WORK/dry.md")" "0"
assert "dry: evidence still pending" "$(grep -c 'EVIDENCE: pending' "$WORK/dry.md")" "1"

# --- fixture 14: gate without an EVIDENCE line gains one -----------------------
cat > "$WORK/noev.md" <<'EOF'
- [ ] G1: no evidence line yet
  CHECK: echo inserted
  EXPECT: inserted
EOF
node "$CHECKER" "$WORK/noev.md" > /dev/null 2>&1; c14=$?
assert "noev: exit 0" "$c14" "0"
assert "noev: box flipped" "$(grep -c '^\s*- \[x\] G1' "$WORK/noev.md")" "1"
assert "noev: evidence line inserted" "$(grep -c 'EVIDENCE: inserted' "$WORK/noev.md")" "1"

if [ "$fails" -gt 0 ]; then echo "$fails assertion(s) failed"; exit 1; fi
echo "all green — checker harness ($WORK)"