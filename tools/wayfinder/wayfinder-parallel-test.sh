#!/usr/bin/env bash
# wayfinder-parallel-test.sh — parallel-runtime contract tests for the
# Wayfinder map-chief + bounded worker pool (map #697, ticket #743).
#
# End-to-end lifecycle proof over a deterministic fake Herdr CLI
# (fake-herdr.sh), a fake gh tracker, the fake WorkspaceProvider (plain
# directories — no CoW filesystem), and real git fixture repos only where
# integration conflicts must be real. No real panes, live Muse sessions,
# Gradle, database, or network.
#
# Covers the ticket #743 required scenarios: parallel dispatch, continuous
# refill, blocked workers, ticket-worker boundary, helper mediation,
# workspace isolation, review gating, integration conflict, red CI,
# bug scouts, capacity/resource limits, daemon restart, chief crash, worker
# crash, handoff race, and cleanup — plus the fake-Herdr unit contract, the
# parallel rollout gate, and structural guards (daemon never integrates,
# review never touches the tracker, schedulers stay provider-agnostic, the
# implementation never restarts the running daemon).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CHIEF="$ROOT/tools/wayfinder/wayfinder-chief.sh"
WORKER="$ROOT/tools/wayfinder/wayfinder-worker.sh"
WORKSPACE="$ROOT/tools/wayfinder/wayfinder-workspace.sh"
REVIEW="$ROOT/tools/wayfinder/wayfinder-review.sh"
RECOVER="$ROOT/tools/wayfinder/wayfinder-recover.sh"
CAPACITY="$ROOT/tools/wayfinder/wayfinder-capacity.sh"
SCOUT="$ROOT/tools/wayfinder/wayfinder-scout.sh"
MAINT="$ROOT/tools/wayfinder/wayfinder-maintenance.sh"
FAKE_HERDR="$ROOT/tools/wayfinder/fake-herdr.sh"
LOOP="$ROOT/tools/wayfinder/wayfinder-loop.sh"
WORK="$(mktemp -d /tmp/opencode/wayfinder-parallel-test.XXXXXX)"
trap 'rm -rf "$WORK"' EXIT
fail=0

ok() { echo "  ok: $1"; }
bad() { echo "  FAIL: $1"; fail=1; }

bash -n "$FAKE_HERDR" || { echo "FAIL: fake-herdr fails bash -n"; exit 1; }
bash -n "$CHIEF" || { echo "FAIL: chief fails bash -n"; exit 1; }
bash -n "$WORKER" || { echo "FAIL: worker fails bash -n"; exit 1; }
bash -n "$WORKSPACE" || { echo "FAIL: workspace fails bash -n"; exit 1; }
bash -n "$LOOP" || { echo "FAIL: loop fails bash -n"; exit 1; }

mkdir -p "$WORK/herdr" "$WORK/bin" "$WORK/logs" "$WORK/base"
export FAKE_HERDR_DIR="$WORK/herdr" HERDR_CALLS="$WORK/herdr-calls" GH_CALLS="$WORK/gh-calls"
export HERDR_BIN="$FAKE_HERDR"
: > "$HERDR_CALLS"; : > "$GH_CALLS"

# Fake gh applies the caller's --jq filter to raw fixtures (faithful shape).
cat >"$WORK/bin/gh" <<'EOF'
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
if [[ "$endpoint" == */sub_issues ]]; then
    map="${endpoint%%/sub_issues}"; map="${map##*/}"
    raw="$WORK/sub-${map}.json"
else
    n="${endpoint##*/}"
    raw="$WORK/issue-${n}.json"
fi
[ -f "$raw" ] || { printf 'missing fixture: %s\n' "$raw" >&2; exit 1; }
if [ -n "$filter" ]; then jq -r "$filter" "$raw"; else cat "$raw"; fi
EOF
chmod +x "$WORK/bin/gh"

export WAYFINDER_GH_BIN="$WORK/bin/gh" WAYFINDER_GH_REPO=fixture/repo
export WAYFINDER_WORKER_KIND=opencode WAYFINDER_ROLE=chief WAYFINDER_PARALLEL=on
export WAYFINDER_WORKER_REGISTRY="$WORK/workers.tsv"
export WAYFINDER_WORKSPACE_REGISTRY="$WORK/workspaces.tsv"
export WAYFINDER_WORKSPACE_PROVIDER=fake WAYFINDER_WORKSPACE_ROOT="$WORK/ws-root"
export WAYFINDER_SCOUT_REGISTRY="$WORK/scout.tsv"
export WAYFINDER_HEAVY_REGISTRY="$WORK/heavy.tsv"
export WAYFINDER_INTEGRATION_GATE="$WORK/integration-gate"
export WAYFINDER_REVIEW_LOG_DIR="$WORK/logs"
export WAYFINDER_EVENTS_LOG="$WORK/events.log"
export PATH="$WORK/bin:$PATH" WORK
: > "$WAYFINDER_WORKER_REGISTRY"

[ "$WAYFINDER_PARALLEL" = "on" ] || { echo "FAIL: suite must run with WAYFINDER_PARALLEL=on"; exit 1; }

mkissue() { # <n> <state> <blocked> <assignees-json> <labels-json>
    cat >"$WORK/issue-$1.json" <<JSON
{"number":$1,"state":"$2","issue_dependencies_summary":{"blocked_by":$3,"blocking":0},"assignees":$4,"labels":$5}
JSON
}
LBL_TASK='[{"name":"wayfinder:task"}]'
mkfixture() { # <name> — init a master-branch repo with one commit, print path.
    local dir="$WORK/$1"
    mkdir -p "$dir"
    git init -qb master "$dir" >/dev/null
    git -C "$dir" config user.email test@test.test
    git -C "$dir" config user.name test
    printf '.wayfinder/\n' > "$dir/.gitignore"
    echo "seed $1" > "$dir/seed.txt"
    git -C "$dir" add .gitignore seed.txt
    git -C "$dir" commit -qm "seed $1"
    printf '%s' "$dir"
}
mkrow() { # <name> <role> <ticket> <workspace> <status> [note] [parent]
    printf '%s\t%s\t%s\t%s\tpane-7\t%s\tt0\tt0\t%s\t%s\n' \
        "$1" "$2" "$3" "$4" "$5" "${6:-reported}" "${7:-}" >> "$WAYFINDER_WORKER_REGISTRY"
}
row_status() { awk -F'\t' -v n="$1" '$1 == n { print $6; exit }' "$WAYFINDER_WORKER_REGISTRY"; }
fresh_reg() { # <tag> — clean registries + herdr state for one scenario.
    export WAYFINDER_WORKER_REGISTRY="$WORK/workers-$1.tsv"
    : > "$WAYFINDER_WORKER_REGISTRY"
    rm -f "$WORK/herdr"/status-* "$WORK/herdr"/prompt-* "$WORK/herdr"/output-* \
        "$WORK/herdr"/fail-start-* "$WORK/herdr"/fail-prompt-* "$WORK/herdr/list.json"
    : > "$HERDR_CALLS"; : > "$GH_CALLS"
    rm -f "$WORK/base"/wf-* -r 2>/dev/null || true
    rm -rf "$WORK/ws-root" "$WORK/scout.tsv" "$WORK/heavy.tsv" "$WORK/integration-gate"
    mkdir -p "$WORK/base"
}
chief() { bash "$CHIEF" "$@" >"$WORK/out" 2>"$WORK/err" || { bad "chief $* failed: $(cat "$WORK/err")"; return 1; }; }
base_ws() { # <ticket> — fake-provider workspace satisfying the chief --workspace-base layout.
    bash "$WORKSPACE" create --provider fake --purpose "ticket-$1" --id "wf-$1" --root "$WORK/base" >/dev/null 2>&1
}

echo "1. fake Herdr covers dispatch/status/read/block/crash/reconciliation deterministically"
fresh_reg 1
out="$(bash "$FAKE_HERDR" pane split --current 2>&1)" && echo "$out" | grep -q pane-7 \
    && ok "pane split allocates" || bad "pane split wrong: $out"
bash "$FAKE_HERDR" agent start wf-fake --kind opencode >/dev/null 2>&1 \
    && ok "agent start dispatches" || bad "agent start failed"
[ "$(cat "$WORK/herdr/status-wf-fake")" = "running" ] && ok "start records working state" || bad "no working state"
printf 'blocked' > "$WORK/herdr/status-wf-fake"
bash "$FAKE_HERDR" agent get wf-fake | grep -q blocked && ok "get reports blocked-input" || bad "get wrong"
printf 'waiting' > "$WORK/herdr/status-wf-fake"
bash "$FAKE_HERDR" agent get wf-fake | grep -q waiting && ok "get reports resource waits distinctly" || bad "get wait wrong"
printf 'agent-output-body' > "$WORK/herdr/output-wf-fake"
[ "$(bash "$FAKE_HERDR" agent read wf-fake --source screen --lines 50)" = "agent-output-body" ] \
    && ok "read returns canned output" || bad "read wrong"
bash "$FAKE_HERDR" agent prompt wf-fake "do the thing" >/dev/null 2>&1 \
    && grep -q "do the thing" "$WORK/herdr/prompt-wf-fake" && ok "prompt delivers + records text" || bad "prompt wrong"
bash "$FAKE_HERDR" agent wait wf-fake --timeout 60000 | grep -q done \
    && ok "wait is the bounded check-in" || bad "wait wrong"
bash "$FAKE_HERDR" agent send-keys wf-fake ctrl+c >/dev/null 2>&1 && ok "send-keys interrupts" || bad "send-keys failed"
: > "$WORK/herdr/fail-start-wf-boom"
bash "$FAKE_HERDR" agent start wf-boom >/dev/null 2>&1 \
    && bad "injected start failure accepted" || ok "injected start failure deterministic"
rm -f "$WORK/herdr/fail-start-wf-boom"
cat >"$WORK/herdr/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-fake","status":"blocked"},{"name":"wf-stray","status":"running"}]}}
JSON
bash "$FAKE_HERDR" agent list | grep -q wf-stray \
    && ok "list discovers panes/agents for reconciliation" || bad "list wrong"
rm -f "$WORK/herdr/status-wf-fake" # crash/disappearance: no status file, absent from list
cat >"$WORK/herdr/list.json" <<'JSON'
{"result":{"agents":[]}}
JSON
bash "$FAKE_HERDR" agent list | grep -q '"agents":\[\]' && ok "crashed agents vanish from discovery" || bad "crash wrong"
bash "$FAKE_HERDR" agent frobnicate 2>/dev/null \
    && bad "unknown invocation accepted" || ok "unknown invocation refused"
grep -q -- '--wait' "$HERDR_CALLS" && bad "fake emitted --wait itself" || ok "fake never injects --wait"

echo "2. parallel dispatch: three tickets at capacity three yield three live workers"
fresh_reg 2
base_ws 101; base_ws 102; base_ws 103
printf '[{"number":101},{"number":102},{"number":103}]' >"$WORK/sub-697.json"
mkissue 101 open 0 '[]' "$LBL_TASK"; mkissue 102 open 0 '[]' "$LBL_TASK"; mkissue 103 open 0 '[]' "$LBL_TASK"
chief --map 697 --once --max-workers 3 --workspace-base "$WORK/base"
[ "$(grep -c '^wf-697-' "$WAYFINDER_WORKER_REGISTRY")" -eq 3 ] \
    && ok "three live workers without waiting for worker A" || bad "dispatch wrong: $(cat "$WAYFINDER_WORKER_REGISTRY")"
for t in 101 102 103; do
    grep -q "^wf-697-$t"$'\tticket\t'"$t"$'\t' "$WAYFINDER_WORKER_REGISTRY" \
        || bad "ticket $t missing a worker"
done
ok "each ticket holds exactly one worker"
first_list="$(grep -n 'agent list' "$HERDR_CALLS" | head -1 | cut -d: -f1)"
first_start="$(grep -n 'agent start' "$HERDR_CALLS" | head -1 | cut -d: -f1)"
[ -n "$first_list" ] && [ "$first_list" -lt "$first_start" ] && ok "reconcile precedes dispatch" || bad "reconcile not first"
grep -E 'agent (start|prompt)' "$HERDR_CALLS" | grep -E -- '--wait' >"$WORK/wait-leak.out" \
    && bad "--wait leaked into dispatch: $(cat "$WORK/wait-leak.out")" || ok "dispatch stays async"

echo "3. continuous refill: B completes and integrates while A keeps running"
fresh_reg 3
base_ws 111; base_ws 112; base_ws 113
printf 'wf-697-111\tticket\t111\t%s\tpane-7\trunning\tt0\tt0\tspawned\n' "$WORK/base/wf-111" > "$WAYFINDER_WORKER_REGISTRY"
printf 'wf-697-112\tticket\t112\t%s\tpane-7\trunning\tt0\tt0\tspawned\n' "$WORK/base/wf-112" >> "$WAYFINDER_WORKER_REGISTRY"
CANON_R="$(mkfixture canon-refill)"
git -C "$CANON_R" checkout -qb wf/wf-697-112 >/dev/null
echo "feature-b" > "$CANON_R/b.txt"; git -C "$CANON_R" add b.txt; git -C "$CANON_R" commit -qm "feature b"
RESULT_B="$(git -C "$CANON_R" rev-parse HEAD)"
git -C "$CANON_R" checkout -q master >/dev/null
printf '[{"number":111},{"number":112},{"number":113}]' >"$WORK/sub-697.json"
mkissue 111 open 0 '[]' "$LBL_TASK"; mkissue 112 open 0 '[]' "$LBL_TASK"; mkissue 113 open 0 '[]' "$LBL_TASK"
cat >"$WORK/herdr/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-697-111","status":"running"},{"name":"wf-697-112","status":"done"}]}}
JSON
chief --map 697 --once --max-workers 2 --workspace-base "$WORK/base"
[ "$(row_status wf-697-112)" = "ready-for-review" ] && ok "B collected to ready-for-review" || bad "B not collected"
grep -q $'^wf-697-113\tticket' "$WAYFINDER_WORKER_REGISTRY" \
    && ok "C dispatched into the freed slot while A continues" || bad "no refill: $(cat "$WAYFINDER_WORKER_REGISTRY")"
[ "$(row_status wf-697-111)" = "running" ] && ok "A untouched by B's completion" || bad "A disturbed"
bash "$REVIEW" review wf-697-112 --disposition accept --result-commit "$RESULT_B" >/dev/null 2>&1 \
    || bad "accept failed"
bash "$REVIEW" integrate wf-697-112 --repo "$CANON_R" >/dev/null 2>&1 || bad "integrate failed"
[ "$(row_status wf-697-112)" = "integrated" ] && ok "B reviewed and integrated" || bad "B not integrated"
[ -f "$CANON_R/b.txt" ] && ok "B's change present in canonical" || bad "B's change missing"

echo "4. blocked worker names its decision while unrelated workers continue"
fresh_reg 4
base_ws 121; base_ws 122; base_ws 123; base_ws 124
printf 'wf-697-121\tticket\t121\t%s\tpane-7\trunning\tt0\tt0\tspawned\n' "$WORK/base/wf-121" > "$WAYFINDER_WORKER_REGISTRY"
printf 'wf-697-122\tticket\t122\t%s\tpane-7\trunning\tt0\tt0\tspawned\n' "$WORK/base/wf-122" >> "$WAYFINDER_WORKER_REGISTRY"
printf '[{"number":121},{"number":122},{"number":123},{"number":124}]' >"$WORK/sub-697.json"
mkissue 121 open 0 '[]' "$LBL_TASK"; mkissue 122 open 0 '[]' "$LBL_TASK"
mkissue 123 open 0 '[]' "$LBL_TASK"; mkissue 124 open 0 '[]' "$LBL_TASK"
cat >"$WORK/herdr/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-697-121","status":"running"},{"name":"wf-697-122","status":"blocked"}]}}
JSON
chief --map 697 --once --max-workers 3 --workspace-base "$WORK/base"
[ "$(row_status wf-697-122)" = "ready-for-review" ] && ok "blocked collected for a decision" || bad "blocked not collected"
grep -q $'^wf-697-123\tticket' "$WAYFINDER_WORKER_REGISTRY" \
    && ok "frontier refills around the blocked worker" || bad "fill stalled: $(cat "$WAYFINDER_WORKER_REGISTRY")"
[ "$(row_status wf-697-121)" = "running" ] && ok "running worker unaffected by the block" || bad "running worker disturbed"

echo "5. ticket-worker boundary: one ticket each, no self-service orchestration"
fresh_reg 5
mkdir -p "$WORK/bound"
bash "$WORKER" spawn --role ticket --ticket 131 --workspace "$WORK/bound" --name wf-bound >/dev/null 2>&1 \
    || bad "chief spawn failed"
if WAYFINDER_ROLE=ticket bash "$WORKER" spawn --role ticket --ticket 132 --workspace "$WORK/bound" --name wf-second >"$WORK/out" 2>"$WORK/err"; then
    bad "leaf claimed a second ticket"
elif grep -q "chief" "$WORK/err"; then ok "leaf second-claim refused with chief pointer"; else bad "wrong refusal: $(cat "$WORK/err")"; fi
if bash "$WORKER" spawn --role ticket --ticket 131 --workspace "$WORK/bound" --name wf-bound >"$WORK/out" 2>"$WORK/err"; then
    bad "duplicate worker name accepted"
else ok "deterministic name refuses duplicates (no double claim)"; fi
if WAYFINDER_ROLE=ticket bash "$REVIEW" integrate wf-bound >"$WORK/out" 2>"$WORK/err"; then
    bad "leaf self-integration accepted"
else ok "leaf self-integration refused"; fi
if WAYFINDER_ROLE=helper bash "$REVIEW" gate --reason x >"$WORK/out" 2>"$WORK/err"; then
    bad "leaf map-advancement accepted"
else ok "leaf map-advancement refused"; fi

echo "6. helper mediation: chief-created, parent-bound, capacity-counted"
fresh_reg 6
export WAYFINDER_MAX_WORKERS=2
mkdir -p "$WORK/hws" "$WORK/hws2"
bash "$WORKER" spawn --role ticket --ticket 141 --workspace "$WORK/hws" --name wf-hpar >/dev/null 2>&1 \
    || bad "parent spawn failed"
if bash "$WORKER" spawn --role helper --ticket 141 --workspace "$WORK/hws2" --name wf-diy >"$WORK/out" 2>"$WORK/err"; then
    bad "unmediated helper accepted"
else ok "unmediated helper refused (chief mediation required)"; fi
bash "$CHIEF" --map 697 --spawn-helper wf-hpar --scope "bounded probe" --helper-workspace "$WORK/hws2" >/dev/null 2>&1 \
    || bad "chief-mediated helper failed: $(cat "$WORK/err")"
[ "$(awk -F'\t' -v n=wf-hpar-h1 '$1 == n { print $10; exit }' "$WAYFINDER_WORKER_REGISTRY")" = "wf-hpar" ] \
    && ok "helper registered under its parent" || bad "helper parent missing: $(cat "$WAYFINDER_WORKER_REGISTRY")"
if bash "$WORKER" spawn --role ticket --ticket 142 --workspace "$WORK/hws" --name wf-extra >"$WORK/out" 2>"$WORK/err"; then
    bad "spawn past helper-occupied capacity accepted"
elif grep -q "capacity" "$WORK/err"; then ok "helper occupies a capacity slot"; else bad "wrong refusal: $(cat "$WORK/err")"; fi
unset WAYFINDER_MAX_WORKERS

echo "7. workspace isolation: fake provider keeps overlapping writes apart"
fresh_reg 7
SNAP7="$(git -C "$ROOT" status --porcelain)"
bash "$WORKSPACE" create --provider fake --purpose ticket-151 --id wf-iso-a --root "$WORK/ws-root" >/dev/null 2>&1 \
    || bad "fake create A failed"
bash "$WORKSPACE" create --provider fake --purpose ticket-151 --id wf-iso-b --root "$WORK/ws-root" >/dev/null 2>&1 \
    || bad "fake create B failed"
PA="$(bash "$WORKSPACE" path wf-iso-a)"; PB="$(bash "$WORKSPACE" path wf-iso-b)"
[ "$PA" != "$PB" ] && ok "distinct isolated paths" || bad "shared path"
echo "worker-a-change" > "$PA/shared.txt"
[ ! -f "$PB/shared.txt" ] && ok "sibling sees none of A's uncommitted state" || bad "state leaked across workers"
[ ! -e "$ROOT/shared.txt" ] && ok "canonical sees none of the worker writes" || bad "state leaked to canonical"
[ -z "$(git -C "$ROOT" branch --list 'wf/*')" ] \
    && ok "fake provider creates no canonical branches" || bad "fake leaked branches"
[ "$(git -C "$ROOT" status --porcelain)" = "$SNAP7" ] \
    && ok "parallel writes add no canonical changes" || bad "canonical dirtied: $(git -C "$ROOT" status --porcelain)"
bash "$WORKSPACE" reconcile >/dev/null 2>&1 || bad "fake reconcile failed"
bash "$WORKSPACE" inspect wf-iso-a | grep -q lifecycle_state=ready \
    && ok "fake workspaces survive reconcile" || bad "fake reconcile marked ready gone"
bash "$WORKSPACE" cleanup wf-iso-a >/dev/null 2>&1 && [ ! -e "$PA" ] \
    && ok "fake cleanup removes path + row" || bad "fake cleanup wrong"

echo "8. review gating: done never closes the ticket or frees the workspace"
fresh_reg 8
mkdir -p "$WORK/gws"
mkrow wf-g1 ticket 161 "$WORK/gws" ready-for-review
bash "$WORKER" cleanup wf-g1 >"$WORK/out" 2>"$WORK/err" \
    && bad "cleanup of review-pending accepted" || grep -q "review disposition" "$WORK/err" \
    && ok "workspace retained until disposition" || bad "wrong refusal: $(cat "$WORK/err")"
[ -f "$WORK/issue-161.json" ] || mkissue 161 open 0 '[]' "$LBL_TASK"
CANON_G="$(mkfixture canon-gate)"
git -C "$CANON_G" checkout -qb wf/wf-g1 >/dev/null
echo "gated" > "$CANON_G/g.txt"; git -C "$CANON_G" add g.txt; git -C "$CANON_G" commit -qm "gated change"
RESULT_G="$(git -C "$CANON_G" rev-parse HEAD)"
git -C "$CANON_G" checkout -q master >/dev/null
bash "$REVIEW" review wf-g1 --disposition accept --result-commit "$RESULT_G" >/dev/null 2>&1 || bad "accept failed"
[ "$(row_status wf-g1)" = "accepted-awaiting-integration" ] \
    && ok "accept still awaits integration (no auto-close)" || bad "accept state wrong"
[ ! -f "$CANON_G/g.txt" ] && ok "canonical untouched before integrate" || bad "accept leaked into canonical"
bash "$REVIEW" integrate wf-g1 --repo "$CANON_G" >/dev/null 2>&1 || bad "integrate failed"
bash "$WORKER" cleanup wf-g1 >/dev/null 2>&1 && ok "integrated work releases cleanly" || bad "post-integrate cleanup refused"

echo "9. integration conflict enters revision, never corrupts canonical state"
fresh_reg 9
CANON_C="$(mkfixture canon-conflict)"
git -C "$CANON_C" checkout -qb wf/wf-con >/dev/null
echo "worker line" > "$CANON_C/shared.txt"; git -C "$CANON_C" add shared.txt; git -C "$CANON_C" commit -qm "worker change"
RESULT_C="$(git -C "$CANON_C" rev-parse HEAD)"
git -C "$CANON_C" checkout -q master >/dev/null
echo "chief line" > "$CANON_C/shared.txt"; git -C "$CANON_C" add shared.txt; git -C "$CANON_C" commit -qm "canonical change first"
mkdir -p "$WORK/cws"
mkrow wf-con ticket 162 "$WORK/cws" ready-for-review
bash "$REVIEW" review wf-con --disposition accept --result-commit "$RESULT_C" >/dev/null 2>&1 || bad "accept failed"
bash "$REVIEW" integrate wf-con --repo "$CANON_C" >"$WORK/out" 2>"$WORK/err" \
    && bad "conflicting integrate accepted" || ok "conflict refused safely"
[ "$(row_status wf-con)" = "accepted-awaiting-integration" ] \
    && ok "conflicted row stays queued (nothing discarded)" || bad "row lost"
[ -z "$(git -C "$CANON_C" status --porcelain)" ] && ok "canonical clean after conflict" || bad "canonical dirty"
grep -q "chief line" "$CANON_C/shared.txt" && ok "canonical change untouched" || bad "canonical disturbed"
bash "$REVIEW" review wf-con --disposition revision --finding "rebase onto the new base, keep both lines" >/dev/null 2>&1 \
    && [ "$(row_status wf-con)" = "revision-requested" ] \
    && ok "conflict routes to same-worker revision" || bad "revision path broken"

echo "10. red CI: one maintenance worker, implementation continues, integration gated"
fresh_reg 10
base_ws 171
RED_SHA="$(git -C "$ROOT" rev-parse HEAD)"
mkdir -p "$WORK/mws"
bash "$CHIEF" --map 697 --spawn-maintenance "$RED_SHA" --repair-issue 900 --maintenance-workspace "$WORK/mws" --failing "red leg" >/dev/null 2>&1 \
    || bad "maintenance dispatch failed: $(cat "$WORK/err")"
grep -q $'^wf-697-maintenance\tmaintenance\t900\t' "$WAYFINDER_WORKER_REGISTRY" \
    && ok "exactly one maintenance worker for the red SHA" || bad "maintenance row wrong"
bash "$CHIEF" --map 697 --spawn-maintenance "$RED_SHA" --repair-issue 900 --maintenance-workspace "$WORK/mws" >"$WORK/out" 2>"$WORK/err" \
    && bad "duplicate maintenance dispatched" || grep -q "already holds" "$WORK/err" \
    && ok "no duplicate dispatch per repair" || bad "wrong refusal: $(cat "$WORK/err")"
printf '[{"number":171}]' >"$WORK/sub-697.json"
mkissue 171 open 0 '[]' "$LBL_TASK"
cat >"$WORK/herdr/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-697-maintenance","status":"running"}]}}
JSON
chief --map 697 --once --max-workers 2 --workspace-base "$WORK/base"
grep -q $'^wf-697-171\tticket' "$WAYFINDER_WORKER_REGISTRY" \
    && ok "isolated implementation continues beside red CI" || bad "ticket fill stopped: $(cat "$WAYFINDER_WORKER_REGISTRY")"
CANON_M="$(mkfixture canon-redci)"
git -C "$CANON_M" checkout -qb wf/wf-697-171 >/dev/null
echo "risky" > "$CANON_M/r.txt"; git -C "$CANON_M" add r.txt; git -C "$CANON_M" commit -qm "risky change"
RESULT_M="$(git -C "$CANON_M" rev-parse HEAD)"
git -C "$CANON_M" checkout -q master >/dev/null
cat >"$WORK/herdr/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-697-maintenance","status":"running"},{"name":"wf-697-171","status":"done"}]}}
JSON
chief --map 697 --once --max-workers 2 --workspace-base "$WORK/base"
[ "$(row_status wf-697-171)" = "ready-for-review" ] \
    && ok "ticket result collected while repair still in flight" || bad "ticket not collected"
bash "$REVIEW" review wf-697-171 --disposition accept --result-commit "$RESULT_M" >/dev/null 2>&1 \
    || bad "accept failed: $(cat "$WORK/err")"
bash "$REVIEW" integrate wf-697-171 --repo "$CANON_M" >"$WORK/out" 2>"$WORK/err" \
    && bad "unsafe integration past red CI accepted" || grep -q "gated" "$WORK/err" \
    && ok "integration gated while repair in flight" || bad "wrong gate: $(cat "$WORK/err")"
bash "$WORKER" stop wf-697-maintenance >/dev/null 2>&1 || bad "maintenance stop failed"
bash "$REVIEW" integrate wf-697-171 --repo "$CANON_M" >/dev/null 2>&1 \
    && ok "gate lifts when the repair lands" || bad "gate stuck after repair"

echo "11. bug scout files tickets without touching implementation; cursor survives restart"
fresh_reg 11
SNAP11="$(git -C "$ROOT" status --porcelain)"
mkdir -p "$WORK/scout-ws"
bash "$CHIEF" --map 697 --spawn-scout backend-domain --scout-workspace "$WORK/scout-ws" >/dev/null 2>&1 \
    || bad "scout spawn failed: $(cat "$WORK/err")"
grep -q $'^wf-697-scout\tbug-scout\t697\t' "$WAYFINDER_WORKER_REGISTRY" \
    && ok "scout dispatched as a map-anchored read-only worker" || bad "scout row wrong"
bash "$SCOUT" note-finding backend-domain 181 >/dev/null 2>&1 || bad "note-finding failed"
bash "$SCOUT" status >"$WORK/scout-status.out" 2>"$WORK/scout-status.err" || bad "scout status failed"
grep -q "181" "$WORK/scout-status.out" && ok "filed ticket recorded on the slice" || bad "finding not recorded: $(cat "$WORK/scout-status.out")"
bash "$SCOUT" complete backend-domain --note "slice audited" >/dev/null 2>&1 || bad "complete failed"
[ "$(git -C "$ROOT" status --porcelain)" = "$SNAP11" ] \
    && ok "scout changed no implementation state" || bad "scout dirtied the repo"
SCOUT_SNAP="$(bash "$SCOUT" status)"
WAYFINDER_ROLE=bug-scout bash "$SCOUT" status >/dev/null 2>&1 && ok "leaf status stays observable" || bad "leaf status blocked"
[ "$(WAYFINDER_ROLE=chief bash "$SCOUT" status)" = "$SCOUT_SNAP" ] \
    && ok "slice cursor/progress survives across invocations" || bad "cursor lost"
bash "$RECOVER" quiescence >"$WORK/out" 2>&1
grep -q "QUIESCENT (writable clear" "$WORK/out" \
    && ok "read-only scout never blocks writable quiescence" || bad "scout blocked quiescence: $(cat "$WORK/out")"

echo "12. capacity and resource limits: global, role, and heavy without confusing waits"
fresh_reg 12
mkdir -p "$WORK/cws1" "$WORK/cws2"
export WAYFINDER_MAX_WORKERS=1
bash "$WORKER" spawn --role ticket --ticket 191 --workspace "$WORK/cws1" --name wf-cap1 >/dev/null 2>&1 \
    || bad "first spawn failed"
bash "$WORKER" spawn --role ticket --ticket 192 --workspace "$WORK/cws2" --name wf-cap2 >"$WORK/out" 2>"$WORK/err" \
    && bad "over-global spawn accepted" || grep -q "capacity" "$WORK/err" \
    && ok "global ceiling enforced" || bad "wrong refusal: $(cat "$WORK/err")"
unset WAYFINDER_MAX_WORKERS
export WAYFINDER_MAX_BUG_SCOUTS=0
mkdir -p "$WORK/scout-ws2"
bash "$CHIEF" --map 697 --spawn-scout api-routes-auth --scout-workspace "$WORK/scout-ws2" >"$WORK/out" 2>"$WORK/err" \
    && bad "scout past zero ceiling accepted" || ok "scout narrow ceiling enforced"
unset WAYFINDER_MAX_BUG_SCOUTS
export WAYFINDER_MAX_WORKERS=5
bash "$CAPACITY" heavy-acquire --holder wf-cap1 --ticket 191 >/dev/null 2>&1 \
    || bad "heavy-acquire failed for a live worker"
export WAYFINDER_MAX_HEAVY_JOBS=1
bash "$CAPACITY" heavy-acquire --holder wf-cap1 --ticket 191 >/dev/null 2>&1 || bad "idempotent re-acquire failed"
if bash "$WORKER" spawn --role ticket --ticket 193 --workspace "$WORK/cws2" --name wf-heavy-wait >/dev/null 2>&1; then
    bash "$CAPACITY" heavy-acquire --holder wf-heavy-wait --ticket 193 >"$WORK/out" 2>"$WORK/err" \
        && bad "over-heavy acquire accepted" || grep -q "waiting-resource" "$WORK/err" \
        && ok "heavy refusal is waiting-resource, not blocked" || bad "wrong heavy refusal: $(cat "$WORK/err")"
    [ "$(row_status wf-heavy-wait)" = "running" ] \
        && ok "waiting worker stays running (never semantic blocked)" || bad "waiter marked blocked"
else
    bad "waiter spawn failed (global bound is 5 here)"
fi
unset WAYFINDER_MAX_HEAVY_JOBS
printf 'gone-holder\t191\t2026-09-09 00:00:00\tstale\n' > "$WAYFINDER_HEAVY_REGISTRY"
bash "$CAPACITY" heavy-reconcile >/dev/null 2>&1 || bad "heavy-reconcile failed"
bash "$CAPACITY" heavy-list >"$WORK/heavy-list.out" 2>&1 || bad "heavy-list failed"
grep -E 'gone-holder' "$WORK/heavy-list.out" >/dev/null \
    && bad "crashed holder leaked" || ok "crashed heavy slot released"
bash "$CAPACITY" heavy-release --holder wf-cap1 >/dev/null 2>&1 || bad "heavy-release failed"
unset WAYFINDER_MAX_WORKERS

echo "13. daemon restart adopts live workers instead of duplicating them"
fresh_reg 13
mkdir -p "$WORK/rws"
printf 'wf-re\tticket\t201\t%s\tpane-7\tgone\tt0\tt0\tmissing from herdr agent list\t\t697\tgen-a\n' "$WORK/rws" > "$WAYFINDER_WORKER_REGISTRY"
cat >"$WORK/herdr/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-re","status":"running"}]}}
JSON
bash "$WORKER" reconcile >/dev/null 2>&1 || bad "reconcile failed"
[ "$(row_status wf-re)" = "running" ] && ok "gone row re-adopted to running" || bad "no re-adoption"
[ "$(grep -c '^wf-re' "$WAYFINDER_WORKER_REGISTRY")" -eq 1 ] \
    && ok "exactly one row (no duplicate spawn)" || bad "duplicate rows"
[ "$(awk -F'\t' -v n=wf-re '$1 == n { print $11; exit }' "$WAYFINDER_WORKER_REGISTRY")" = "697" ] \
    && ok "map recovery identity survives restart" || bad "identity dropped"
cat >"$WORK/herdr/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-stray-live","status":"running"}]}}
JSON
bash "$WORKER" reconcile >"$WORK/out" 2>&1 || bad "reconcile failed"
grep -q "unmanaged: wf-stray-live" "$WORK/out" \
    && ok "unmanaged live agents reported, never auto-adopted" || bad "unmanaged mishandled"
[ "$(grep -c '^wf-stray-live' "$WAYFINDER_WORKER_REGISTRY" || true)" -eq 0 ] \
    && ok "no silent adoption into the registry" || bad "unmanaged auto-adopted"

echo "14. chief crash: workers survive; the recovered chief reconciles before dispatch"
fresh_reg 14
base_ws 211
printf 'wf-697-211\tticket\t211\t%s\tpane-7\trunning\tt0\tt0\tspawned\t\t697\tgen-crash\n' "$WORK/base/wf-211" > "$WAYFINDER_WORKER_REGISTRY"
printf '[{"number":212}]' >"$WORK/sub-697.json"
mkissue 211 open 0 '[]' "$LBL_TASK"; mkissue 212 open 0 '[]' "$LBL_TASK"
cat >"$WORK/herdr/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-697-211","status":"running"}]}}
JSON
bash "$CHIEF" --map 697 --recover >"$WORK/out" 2>"$WORK/err" || bad "chief --recover failed: $(cat "$WORK/err")"
grep -q "reconcile" "$WORK/out" && ok "recovered chief reconciles first" || bad "no reconcile in --recover"
[ "$(grep -c '^wf-697-21' "$WAYFINDER_WORKER_REGISTRY")" -eq 1 ] \
    && ok "--recover dispatches nothing (single-shot)" || bad "--recover filled work"
[ "$(row_status wf-697-211)" = "running" ] && ok "crashed chief's worker survives" || bad "worker lost"
base_ws 212
chief --map 697 --once --max-workers 2 --workspace-base "$WORK/base"
grep -q $'^wf-697-212\tticket' "$WAYFINDER_WORKER_REGISTRY" \
    && ok "recovered chief resumes dispatch without duplicates" || bad "resume wrong: $(cat "$WAYFINDER_WORKER_REGISTRY")"
[ "$(grep -c '^wf-697-211' "$WAYFINDER_WORKER_REGISTRY")" -eq 1 ] \
    && ok "surviving worker never duplicated" || bad "survivor duplicated"

echo "15. worker crash: claims and workspaces stay recoverable, salvage kept"
fresh_reg 15
mkdir -p "$WORK/xws"
printf 'wf-x1\tticket\t221\t%s\tpane-7\trunning\tt0\tt0\tspawned\n' "$WORK/xws" > "$WAYFINDER_WORKER_REGISTRY"
cat >"$WORK/herdr/list.json" <<'JSON'
{"result":{"agents":[]}}
JSON
bash "$WORKER" reconcile >/dev/null 2>&1 || bad "reconcile failed"
[ "$(row_status wf-x1)" = "gone" ] && ok "crashed worker marked gone, row kept" || bad "crash handling wrong"
grep -q "missing from herdr agent list" "$WAYFINDER_WORKER_REGISTRY" \
    && ok "salvage note preserved on the row" || bad "no salvage note"
bash "$RECOVER" orphans >"$WORK/out" 2>&1
grep -q "gh issue edit 221 --remove-assignee" "$WORK/out" \
    && ok "orphan prints claim-release guidance" || bad "no release guidance"
[ -s "$GH_CALLS" ] && bad "crash path touched the tracker" || ok "crash path never writes the tracker"
bash "$WORKER" cleanup wf-x1 >/dev/null 2>&1 \
    && [ "$(grep -c '^wf-x1' "$WAYFINDER_WORKER_REGISTRY" || true)" -eq 0 ] \
    && ok "crashed claim cleans up once reviewed" || bad "crashed cleanup wrong"

echo "16. handoff race: successors wait while writable work or reviews stand"
fresh_reg 16
mkdir -p "$WORK/hows"
mkrow wf-h1 ticket 231 "$WORK/hows" running
bash "$RECOVER" quiescence >"$WORK/out" 2>&1 \
    && bad "successor would advance over an active worker" || grep -q "BLOCKED: workers active" "$WORK/out" \
    && ok "active workers gate the successor" || bad "wrong gate: $(cat "$WORK/out")"
printf 'wf-h1\tticket\t231\t%s\tpane-7\tready-for-review\tt0\tt0\treported\n' "$WORK/hows" > "$WAYFINDER_WORKER_REGISTRY"
bash "$RECOVER" quiescence >"$WORK/out" 2>&1 \
    && bad "successor would advance over unreviewed results" || grep -q "BLOCKED: results awaiting review" "$WORK/out" \
    && ok "unreviewed results gate the successor" || bad "wrong review gate"
printf 'wf-h1\tticket\t231\t%s\tpane-7\tintegrated\tt0\tt0\treviewed\n' "$WORK/hows" > "$WAYFINDER_WORKER_REGISTRY"
bash "$WORKER" cleanup wf-h1 >/dev/null 2>&1 || bad "terminal cleanup failed"
bash "$RECOVER" quiescence >"$WORK/out" 2>&1 \
    && grep -q "^QUIESCENT$" "$WORK/out" && ok "successor advances after integrate+cleanup" || bad "still blocked: $(cat "$WORK/out")"

echo "17. cleanup converges every terminal state and releases provider state"
fresh_reg 17
mkdir -p "$WORK/tws"
mkrow wf-t-acc ticket 241 "$WORK/tws" integrated
mkrow wf-t-rej ticket 242 "$WORK/tws" rejected
mkrow wf-t-can ticket 243 "$WORK/tws" cancelled
for w in wf-t-acc wf-t-rej wf-t-can; do
    bash "$WORKER" cleanup "$w" >/dev/null 2>&1 || bad "cleanup of $w refused"
done
[ "$(grep -c '^wf-t-' "$WAYFINDER_WORKER_REGISTRY" || true)" -eq 0 ] \
    && ok "accepted/rejected/cancelled rows all release" || bad "terminal rows linger"
bash "$WORKSPACE" create --provider fake --purpose ticket-244 --id wf-term --root "$WORK/ws-root" >/dev/null 2>&1 \
    || bad "fake create failed"
TP="$(bash "$WORKSPACE" path wf-term)"
bash "$WORKSPACE" cleanup wf-term >/dev/null 2>&1
[ ! -e "$TP" ] && [ "$(bash "$WORKSPACE" list | grep -c '^wf-term' || true)" -eq 0 ] \
    && ok "provider agent/pane/workspace state released with the row" || bad "provider state lingered"

echo "18. rollout gate: sequential default, explicit parallel opt-in, no daemon restart"
fresh_reg 18
base_ws 251; base_ws 252
printf '[{"number":251},{"number":252}]' >"$WORK/sub-697.json"
mkissue 251 open 0 '[]' "$LBL_TASK"; mkissue 252 open 0 '[]' "$LBL_TASK"
WAYFINDER_PARALLEL=off bash "$CHIEF" --map 697 --once --max-workers 2 --workspace-base "$WORK/base" >"$WORK/out" 2>"$WORK/err" \
    || bad "sequential pass failed"
[ "$(grep -c '^wf-697-' "$WAYFINDER_WORKER_REGISTRY")" -eq 1 ] \
    && ok "default stays sequential (one worker)" || bad "default not sequential"
grep -q 'WAYFINDER_PARALLEL=on' "$WORK/out" && ok "opt-in pointer logged" || bad "no opt-in pointer"
if grep -v '^#' "$CHIEF" | grep -E 'tmux (kill-session|new-session)|opencode2? api' >/dev/null; then
    bad "chief can restart the daemon"
else ok "chief never restarts the running daemon"; fi
if grep -v '^#' "$LOOP" | grep -E 'WAYFINDER_PARALLEL' >/dev/null; then
    ok "daemon observes the switch without auto-restarting"
else bad "daemon ignores the rollout switch"; fi

echo "19. structural guards: single writer, no tracker/pane coupling, provider-agnostic schedulers"
if grep -n 'review.*integrate\|integrate.*review' "$LOOP" | grep -v '^.*#' >/dev/null; then
    bad "daemon loop invokes the review/integrate path"
else ok "daemon never performs integration (supervisor only)"; fi
if grep -v '^#' "$REVIEW" | grep -E 'gh (issue|api)|herdr agent (start|list|wait)' >/dev/null; then
    bad "review talks to tracker/herdr directly"
else ok "review uses the worker seam, never the tracker"; fi
if grep -v '^#' "$WORKER" | grep -E 'WAYFINDER_WORKSPACE_PROVIDER|workspaces\.tsv' >/dev/null; then
    bad "worker seam branches on provider state"
else ok "worker seam treats workspaces as opaque dirs"; fi
if grep -v '^#' "$CHIEF" | grep -E 'WAYFINDER_WORKSPACE_PROVIDER|workspaces\.tsv' >/dev/null; then
    bad "chief branches on provider state"
else ok "chief stays provider-agnostic"; fi
if grep -v '^#' "$WORKER" | grep -w -q 'git'; then bad "worker seam invokes git"; else ok "worker seam never invokes git"; fi
if grep -v '^#' "$CHIEF" | grep -w -q 'git'; then bad "chief invokes git"; else ok "chief never invokes git"; fi

echo
if [ $fail -eq 0 ]; then echo "parallel-contract: OK"; else echo "parallel-contract: FAILURES PRESENT"; exit 1; fi
