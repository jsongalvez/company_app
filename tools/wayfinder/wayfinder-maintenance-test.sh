#!/usr/bin/env bash
# wayfinder-maintenance-test.sh — contract tests for wayfinder-maintenance.sh + the chief --spawn-maintenance lane (map #697 #739).
# Real git fixture repos for base-SHA resolution, fake-herdr/fake-gh fixtures
# for the chief lane: no real Herdr, tracker, Gradle, database, or network.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MAINT="$ROOT/tools/wayfinder/wayfinder-maintenance.sh"
CHIEF="$ROOT/tools/wayfinder/wayfinder-chief.sh"
WORKER="$ROOT/tools/wayfinder/wayfinder-worker.sh"
WORK="$(mktemp -d /tmp/opencode/wayfinder-maintenance-test.XXXXXX)"
trap 'rm -rf "$WORK"' EXIT
fail=0

ok() { echo "  ok: $1"; }
bad() { echo "  FAIL: $1"; fail=1; }

bash -n "$MAINT" || { echo "FAIL: maintenance fails bash -n"; exit 1; }
bash -n "$CHIEF" || { echo "FAIL: chief fails bash -n"; exit 1; }

mkfixture() { # <name> — init a git repo with one commit, print path.
    local dir="$WORK/$1"
    mkdir -p "$dir"
    git init -q "$dir"
    git -C "$dir" config user.email test@test.test
    git -C "$dir" config user.name test
    echo "seed $1" > "$dir/seed.txt"
    git -C "$dir" add seed.txt
    git -C "$dir" commit -qm "seed $1"
    printf '%s' "$dir"
}

FIX="$(mkfixture repo)"
HEAD_SHA="$(git -C "$FIX" rev-parse HEAD)"
export WAYFINDER_REPO="$FIX"
export WAYFINDER_ROLE=chief
mkdir -p "$WORK/ws"

maint() { bash "$MAINT" "$@" >"$WORK/out" 2>"$WORK/err" || { bad "maintenance $* failed: $(cat "$WORK/err")"; return 1; }; }
maint_expect_fail() { bash "$MAINT" "$@" >"$WORK/out" 2>"$WORK/err" && { bad "maintenance $* unexpectedly succeeded"; return 1; }; return 0; }

echo "1. resolve-base prints the full audited SHA and refuses unknown revisions"
[ "$(bash "$MAINT" resolve-base)" = "$HEAD_SHA" ] && ok "resolve-base defaults to HEAD" || bad "resolve-base default wrong"
[ "$(bash "$MAINT" resolve-base --base HEAD)" = "$HEAD_SHA" ] && ok "explicit HEAD resolves" || bad "explicit HEAD failed"
maint_expect_fail resolve-base --base deadbeef-dead && ok "unknown base refused" || true
grep -q "unknown base revision" "$WORK/err" && ok "refusal names the base" || bad "wrong refusal: $(cat "$WORK/err")"

echo "2. prompt carries the writable-but-isolated maintenance contract (map #697 #739)"
for token in "ROLE: maintenance" "TASK:" "repair issue #900" "MAP: #697" "WORKSPACE:" "BASE:" "COMMIT:" "ROOT CAUSE:" "SUMMARY:" "FILES:" "VERIFICATION:" "RISKS" "confidence" "HELP REQUESTS" "assign-first" "blocked" "exactly one repair task" "never modify canonical state directly" "awaiting chief review" "INTEGRATION GATE" "handoff" "spawn"; do
    bash "$MAINT" prompt "$HEAD_SHA" --repair-issue 900 --map 697 --workspace "$WORK/ws" | grep -- "$token" >/dev/null \
        && ok "prompt carries $token" || bad "prompt missing $token"
done
bash "$MAINT" prompt "$HEAD_SHA" --repair-issue 900 --map 697 --workspace "$WORK/ws" | grep "derive the workspace HEAD" >/dev/null \
    && ok "prompt without --base asks the worker to derive HEAD" || bad "prompt base fallback missing"
bash "$MAINT" prompt "$HEAD_SHA" --repair-issue 900 --map 697 --workspace "$WORK/ws" --base "$HEAD_SHA" --failing quality | grep "$HEAD_SHA" >/dev/null \
    && ok "prompt carries dispatched base" || bad "prompt missing --base value"
bash "$MAINT" prompt "$HEAD_SHA" --repair-issue 900 --map 697 --workspace "$WORK/ws" --failing "quality,compose" | grep "quality,compose" >/dev/null \
    && ok "prompt carries failing checks" || bad "prompt missing failing checks"
bash "$MAINT" prompt "$HEAD_SHA" --repair-issue 900 --map 697 --workspace "$WORK/ws" | grep -- "consume normal frontier" >/dev/null \
    && ok "prompt forbids frontier pickup" || bad "prompt missing frontier boundary"

echo "3. prompt refusals fail closed"
maint_expect_fail prompt && ok "prompt without SHA refused" || true
maint_expect_fail prompt short --repair-issue 900 --map 697 --workspace "$WORK/ws" && ok "short SHA refused" || true
maint_expect_fail prompt DEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEF --repair-issue 900 --map 697 --workspace "$WORK/ws" && ok "uppercase SHA refused" || true
maint_expect_fail prompt "$HEAD_SHA" --map 697 --workspace "$WORK/ws" && ok "prompt without --repair-issue refused" || true
maint_expect_fail prompt "$HEAD_SHA" --repair-issue 900 --workspace "$WORK/ws" && ok "prompt without --map refused" || true
maint_expect_fail prompt "$HEAD_SHA" --repair-issue 900 --map 697 && ok "prompt without --workspace refused" || true
maint_expect_fail prompt "$HEAD_SHA" --repair-issue nine --map 697 --workspace "$WORK/ws" && ok "non-numeric repair refused" || true
maint_expect_fail prompt "$HEAD_SHA" --repair-issue 900 --map 697 --workspace "$WORK/ws" --failing "$(printf 'a\tb')" && ok "tab-in-failing refused" || true

echo "4. maintenance holds no mutable state and touches no canonical state"
[ -z "$(git -C "$FIX" status --porcelain)" ] && ok "canonical fixture untouched by prompt/resolve-base" || bad "fixture dirtied"
WAYFINDER_ROLE=maintenance bash "$MAINT" prompt "$HEAD_SHA" --repair-issue 900 --map 697 --workspace "$WORK/ws" >/dev/null 2>&1 \
    && ok "leaf prompt observable" || bad "leaf prompt blocked"
WAYFINDER_ROLE=ticket bash "$MAINT" resolve-base >/dev/null 2>&1 \
    && ok "leaf resolve-base observable" || bad "leaf resolve-base blocked"
if grep -v '^#' "$MAINT" | grep -Eq 'HERDR_BIN|agent (start|prompt)|worktree (add|remove)'; then bad "maintenance invokes herdr/worktrees"; else ok "maintenance never invokes herdr/worktrees (prompt prose only)"; fi
if grep -v '^#' "$MAINT" | grep -n 'git ' | grep -v 'rev-parse' >/dev/null; then bad "maintenance runs git beyond read-only resolve-base"; else ok "git use stays read-only resolve-base"; fi

echo "5. worker seam already guards the maintenance role (no second channel)"
export WAYFINDER_WORKER_REGISTRY="$WORK/leaf.tsv" WAYFINDER_MAX_WORKERS=5
: > "$WAYFINDER_WORKER_REGISTRY"
mkdir -p "$WORK/leaf-ws"
cat >"$WORK/bin-herdr" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$WORK/bin-herdr"
if WAYFINDER_ROLE=maintenance HERDR_BIN="$WORK/bin-herdr" bash "$WORKER" spawn --role ticket --ticket 103 --workspace "$WORK/leaf-ws" --name wf-leaf >"$WORK/out" 2>"$WORK/err"; then
    bad "leaf maintenance-role spawn accepted"
elif grep -q "chief" "$WORK/err"; then ok "leaf spawn refused with chief pointer"; else bad "leaf refusal unclear: $(cat "$WORK/err")"; fi

# ---- chief --spawn-maintenance lane (fake herdr + fake gh) ----
mkdir -p "$WORK/cws" "$WORK/bin" "$WORK/maint-ws" "$WORK/maint-ws2"
export HERDR_CALLS="$WORK/herdr-calls" GH_CALLS="$WORK/gh-calls"
: > "$HERDR_CALLS"; : > "$GH_CALLS"

cat >"$WORK/bin/herdr" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"$HERDR_CALLS"
case "${1:-} ${2:-}" in
    "pane split") printf '{"result":{"pane":{"pane_id":"pane-7"}}}'; exit 0 ;;
    "agent start") printf '{"result":{"agent":{"name":"%s","status":"running"}}}' "$3"; exit 0 ;;
    "agent prompt") exit 0 ;;
    "agent list") printf '{"result":{"agents":[]}}'; exit 0 ;;
    "agent send-keys") exit 0 ;;
esac
printf 'unexpected herdr: %s\n' "$*" >&2; exit 1
EOF
chmod +x "$WORK/bin/herdr"

cat >"$WORK/bin/gh" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"$GH_CALLS"
if [ "${1:-}" = repo ]; then printf 'fixture/repo'; exit 0; fi
printf '{}\n'; exit 0
EOF
chmod +x "$WORK/bin/gh"

export HERDR_BIN="$WORK/bin/herdr" WAYFINDER_GH_BIN="$WORK/bin/gh"
export WAYFINDER_GH_REPO=fixture/repo WAYFINDER_WORKER_KIND=opencode WORK
export WAYFINDER_WORKER_REGISTRY="$WORK/workers.tsv"
: > "$WAYFINDER_WORKER_REGISTRY"
export PATH="$WORK/bin:$PATH"

chief() { bash "$CHIEF" "$@" >"$WORK/cout" 2>"$WORK/cerr" || { bad "chief $* failed: $(cat "$WORK/cerr")"; return 1; }; }
chief_expect_fail() { bash "$CHIEF" "$@" >"$WORK/cout" 2>"$WORK/cerr" && { bad "chief $* unexpectedly succeeded"; return 1; }; return 0; }

echo "6. chief spawns one chief-mediated maintenance worker per red SHA (map #697 #739)"
: > "$WAYFINDER_WORKER_REGISTRY"; : > "$HERDR_CALLS"
chief --map 697 --max-workers 2 --spawn-maintenance "$HEAD_SHA" --repair-issue 900 --maintenance-workspace "$WORK/maint-ws" --failing quality
grep -q $'^wf-697-maintenance\tmaintenance\t900\t'"$WORK/maint-ws"$'\t' "$WAYFINDER_WORKER_REGISTRY" \
    && ok "maintenance registered with repair issue as its ticket" || bad "maintenance row missing: $(cat "$WAYFINDER_WORKER_REGISTRY")"
grep -q "for red $HEAD_SHA" "$WORK/cout" \
    && ok "chief log links the worker to its failing SHA" || bad "SHA linkage missing: $(cat "$WORK/cout")"
grep -q "repair #900" "$WORK/cout" \
    && ok "chief log links the worker to its repair task" || bad "repair linkage missing: $(cat "$WORK/cout")"
grep -q "integration gated" "$WORK/cout" \
    && ok "chief log marks canonical integration gated" || bad "gate note missing: $(cat "$WORK/cout")"
grep -q "agent prompt" "$HERDR_CALLS" && ok "maintenance received its prompt asynchronously" || bad "no prompt dispatched"
grep -q -- "ROLE: maintenance" "$HERDR_CALLS" && ok "dispatched prompt carries the maintenance role" || bad "prompt missing role"
grep -q -- "TASK:" "$HERDR_CALLS" && ok "dispatched prompt carries the repair task" || bad "prompt missing task"
grep -q -- "ROOT CAUSE:" "$HERDR_CALLS" && ok "dispatched prompt demands root cause" || bad "prompt missing root-cause section"
grep -q -- "never modify canonical state directly" "$HERDR_CALLS" && ok "dispatched prompt carries isolation" || bad "prompt missing isolation"
grep -q -- "$HEAD_SHA" "$HERDR_CALLS" && ok "dispatched prompt carries the failing SHA" || bad "prompt missing SHA"
if grep -- "agent prompt" "$HERDR_CALLS" | grep -- '--wait' >/dev/null; then bad "maintenance dispatch leaked --wait"; else ok "maintenance dispatch stays async"; fi

echo "7. maintenance lane refusals fail closed with no side effects"
rows_before="$(grep -c maintenance "$WAYFINDER_WORKER_REGISTRY")"
chief_expect_fail --map 697 --spawn-maintenance short --repair-issue 901 --maintenance-workspace "$WORK/maint-ws" && ok "short SHA refused" || true
chief_expect_fail --map 697 --spawn-maintenance "$HEAD_SHA" --maintenance-workspace "$WORK/maint-ws" && ok "missing --repair-issue refused" || true
chief_expect_fail --map 697 --spawn-maintenance "$HEAD_SHA" --repair-issue 901 --maintenance-workspace "$WORK/missing-ws" && ok "missing workspace refused" || true
chief_expect_fail --map 697 --spawn-maintenance "$HEAD_SHA" --repair-issue 901 && ok "missing --maintenance-workspace refused" || true
chief_expect_fail --map 697 --once --maintenance-workspace "$WORK/maint-ws" && ok "stray --maintenance-workspace refused" || true
chief_expect_fail --map 697 --once --repair-issue 901 && ok "stray --repair-issue refused" || true
chief_expect_fail --map 697 --once --maintenance-name custom && ok "stray --maintenance-name refused" || true
chief_expect_fail --map 697 --once --failing quality && ok "stray --failing refused" || true
chief_expect_fail --map 697 --spawn-helper wf-x --scope s --helper-workspace "$WORK/maint-ws" --spawn-maintenance "$HEAD_SHA" --repair-issue 901 --maintenance-workspace "$WORK/maint-ws" \
    && ok "combined helper+maintenance lanes refused" || true
chief_expect_fail --map 697 --spawn-scout backend-domain --scout-workspace "$WORK/maint-ws" --spawn-maintenance "$HEAD_SHA" --repair-issue 901 --maintenance-workspace "$WORK/maint-ws" \
    && ok "combined scout+maintenance lanes refused" || true
chief_expect_fail --map 697 --spawn-maintenance "$HEAD_SHA" --repair-issue 901 --maintenance-workspace "$WORK/maint-ws" --helper-mode writable \
    && ok "--helper-mode on the maintenance lane refused" || true
chief_expect_fail --map 697 --spawn-maintenance "$HEAD_SHA" --repair-issue 901 --maintenance-workspace "$WORK/maint-ws" --failing "$(printf 'a\nb')" \
    && ok "newline-in-failing refused" || true
chief_expect_fail --map 697 --max-workers 2 --spawn-maintenance "$HEAD_SHA" --repair-issue 901 --maintenance-workspace "$WORK/maint-ws" --base deadbeef-dead \
    && ok "bogus --base refused pre-spawn (scout-lane parity)" || true
[ "$(grep -c maintenance "$WAYFINDER_WORKER_REGISTRY")" = "$rows_before" ] && ok "refusals spawned nothing further" || bad "refusal side effects: $(cat "$WAYFINDER_WORKER_REGISTRY")"

echo "8. narrow maintenance capacity bounds red-CI attention without an idle worker"
: > "$WAYFINDER_WORKER_REGISTRY"; : > "$HERDR_CALLS"
chief --map 697 --max-workers 3 --spawn-maintenance "$HEAD_SHA" --repair-issue 900 --maintenance-workspace "$WORK/maint-ws" >/dev/null 2>&1
chief_expect_fail --map 697 --max-workers 3 --spawn-maintenance "$HEAD_SHA" --repair-issue 901 --maintenance-workspace "$WORK/maint-ws2" \
    && ok "second maintenance refused at WAYFINDER_MAX_MAINTENANCE_WORKERS=1" || true
grep -q "maintenance capacity" "$WORK/cerr" && ok "refusal names maintenance capacity" || bad "wrong refusal: $(cat "$WORK/cerr")"
[ "$(grep -c 'maintenance' "$WAYFINDER_WORKER_REGISTRY")" -eq 1 ] && ok "refused dispatch left no row" || bad "capacity side effects: $(cat "$WAYFINDER_WORKER_REGISTRY")"
WAYFINDER_MAX_MAINTENANCE_WORKERS=2 bash "$CHIEF" --map 697 --max-workers 3 --spawn-maintenance "$HEAD_SHA" --repair-issue 901 --maintenance-workspace "$WORK/maint-ws2" >"$WORK/cout" 2>"$WORK/cerr" \
    && ok "raised narrow bound admits a second repair" || bad "raised bound refused: $(cat "$WORK/cerr")"
if WAYFINDER_MAX_MAINTENANCE_WORKERS=0 bash "$CHIEF" --map 697 --spawn-maintenance "$HEAD_SHA" --repair-issue 902 --maintenance-workspace "$WORK/maint-ws2" >"$WORK/cout" 2>"$WORK/cerr"; then
    bad "WAYFINDER_MAX_MAINTENANCE_WORKERS=0 accepted"
else ok "zero maintenance bound rejected"; fi

echo "9. recovery never duplicates a live repair worker for the same task/SHA"
: > "$WAYFINDER_WORKER_REGISTRY"
chief --map 697 --max-workers 3 --spawn-maintenance "$HEAD_SHA" --repair-issue 900 --maintenance-workspace "$WORK/maint-ws" >/dev/null 2>&1
chief_expect_fail --map 697 --max-workers 3 --spawn-maintenance "$HEAD_SHA" --repair-issue 900 --maintenance-workspace "$WORK/maint-ws2" --maintenance-name wf-custom \
    && ok "redispatch of the same repair refused (registry persists across restarts)" || true
grep -q "already holds maintenance worker" "$WORK/cerr" && ok "refusal names the live owner" || bad "wrong refusal: $(cat "$WORK/cerr")"
[ "$(grep -c 'maintenance' "$WAYFINDER_WORKER_REGISTRY")" -eq 1 ] && ok "no duplicate row for the same SHA/task" || bad "duplicate rows: $(cat "$WAYFINDER_WORKER_REGISTRY")"
WAYFINDER_MAX_MAINTENANCE_WORKERS=2 bash "$CHIEF" --map 697 --max-workers 3 --spawn-maintenance "$HEAD_SHA" --repair-issue 901 --maintenance-workspace "$WORK/maint-ws2" >/dev/null 2>&1 \
    && ok "a different repair task still dispatches" || bad "distinct repair refused"

echo "10. dry-run plans without spawning"
: > "$WAYFINDER_WORKER_REGISTRY"; : > "$HERDR_CALLS"
chief --map 697 --spawn-maintenance "$HEAD_SHA" --repair-issue 900 --maintenance-workspace "$WORK/maint-ws" --dry-run
grep -q 'would spawn maintenance for red' "$WORK/cout" && ok "dry-run logs the plan" || bad "no plan logged"
[ -s "$HERDR_CALLS" ] && bad "dry-run called herdr" || ok "dry-run called no herdr"
[ -s "$WAYFINDER_WORKER_REGISTRY" ] && bad "dry-run wrote worker registry" || ok "dry-run left worker registry untouched"

echo "11. unresolvable SHA refuses before spending capacity"
: > "$WAYFINDER_WORKER_REGISTRY"; : > "$HERDR_CALLS"
chief_expect_fail --map 697 --max-workers 2 --spawn-maintenance 0000000000000000000000000000000000000000 --repair-issue 900 --maintenance-workspace "$WORK/maint-ws" \
    && ok "unknown base refused pre-spawn" || true
grep -q "unknown base revision" "$WORK/cerr" && ok "refusal names the base" || bad "wrong refusal: $(cat "$WORK/cerr")"
[ -s "$WAYFINDER_WORKER_REGISTRY" ] && bad "refused spawn left a worker row" || ok "no worker spawned for bad base"
[ -s "$HERDR_CALLS" ] && bad "refused spawn called herdr" || ok "refused spawn called no herdr"

echo "12. the normal ticket pass never auto-spawns maintenance (explicit lane only)"
: > "$WAYFINDER_WORKER_REGISTRY"; : > "$HERDR_CALLS"
mkdir -p "$WORK/tws"
printf '[{"number":301},{"number":900}]' >"$WORK/sub-697.json"
cat >"$WORK/issue-301.json" <<'JSON'
{"number":301,"state":"open","body":"Part of #697. Ordinary ticket.","issue_dependencies_summary":{"blocked_by":0,"blocking":0},"assignees":[],"labels":[{"name":"wayfinder:task"}]}
JSON
cat >"$WORK/issue-900.json" <<'JSON'
{"number":900,"state":"open","body":"Part of #697. <!-- wayfinder-ci-repair --> Covered HEAD: abc","issue_dependencies_summary":{"blocked_by":0,"blocking":0},"assignees":[],"labels":[{"name":"wayfinder:task"},{"name":"ready-for-agent"}]}
JSON
cat >"$WORK/bin/gh-frontier" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"$GH_CALLS"
if [ "${1:-}" = repo ]; then printf 'fixture/repo'; exit 0; fi
endpoint=""; filter=""
prev=""
for arg in "$@"; do
    case "$arg" in repos/*) endpoint="$arg" ;; esac
    if [ "$prev" = "--jq" ]; then filter="$arg"; fi
    prev="$arg"
done
case "$endpoint" in
    */issues/*/sub_issues) map="${endpoint%%/sub_issues}"; map="${map##*/}" ;;
esac
if [[ "$endpoint" == */sub_issues ]]; then
    raw="$WORK/sub-${map}.json"
else
    n="${endpoint##*/}"
    raw="$WORK/issue-${n}.json"
fi
[ -f "$raw" ] || { printf 'missing fixture: %s\n' "$raw" >&2; exit 1; }
if [ -n "$filter" ]; then jq -r "$filter" "$raw"; else cat "$raw"; fi
EOF
chmod +x "$WORK/bin/gh-frontier"
WAYFINDER_GH_BIN="$WORK/bin/gh-frontier" bash "$CHIEF" --map 697 --once --max-workers 2 >"$WORK/cout" 2>"$WORK/cerr" \
    || bad "ticket pass failed: $(cat "$WORK/cerr")"
[ "$(grep -c 'maintenance' "$WAYFINDER_WORKER_REGISTRY" || true)" -eq 0 ] && ok "pending/green-equivalent pass spawns no maintenance" || bad "pass auto-spawned maintenance"
grep -q $'^wf-697-301\tticket\t' "$WAYFINDER_WORKER_REGISTRY" && ok "ticket frontier still fills alongside red CI" || bad "ticket fill blocked: $(cat "$WAYFINDER_WORKER_REGISTRY")"
[ "$(grep -c 'wf-697-900' "$WAYFINDER_WORKER_REGISTRY" || true)" -eq 0 ] && ok "repair issue never enters the ticket fill (maintenance lane only)" || bad "repair filled as ordinary ticket: $(cat "$WAYFINDER_WORKER_REGISTRY")"

echo
if [ $fail -eq 0 ]; then echo "maintenance-contract: OK"; else echo "maintenance-contract: FAILURES PRESENT"; exit 1; fi
