#!/usr/bin/env bash
# wayfinder-review-test.sh — contract tests for wayfinder-review.sh + chief review lanes + worker review guards (map #697 #740).
# Real git fixture repos for integration, fake-herdr/fake-gh fixtures for
# revision prompts and the chief pass: no real Herdr, tracker, Gradle, DB, or network.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REVIEW="$ROOT/tools/wayfinder/wayfinder-review.sh"
CHIEF="$ROOT/tools/wayfinder/wayfinder-chief.sh"
WORKER="$ROOT/tools/wayfinder/wayfinder-worker.sh"
WORK="$(mktemp -d /tmp/opencode/wayfinder-review-test.XXXXXX)"
trap 'rm -rf "$WORK"' EXIT
fail=0

ok() { echo "  ok: $1"; }
bad() { echo "  FAIL: $1"; fail=1; }

bash -n "$REVIEW" || { echo "FAIL: review fails bash -n"; exit 1; }
bash -n "$CHIEF" || { echo "FAIL: chief fails bash -n"; exit 1; }
bash -n "$WORKER" || { echo "FAIL: worker fails bash -n"; exit 1; }

mkdir -p "$WORK/ws" "$WORK/bin" "$WORK/logs"
export HERDR_CALLS="$WORK/herdr-calls" GH_CALLS="$WORK/gh-calls"
: > "$HERDR_CALLS"; : > "$GH_CALLS"

cat >"$WORK/bin/herdr" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"$HERDR_CALLS"
case "${1:-} ${2:-}" in
    "pane split") printf '{"result":{"pane":{"pane_id":"pane-7"}}}'; exit 0 ;;
    "agent start") printf '{"result":{"agent":{"name":"%s","status":"running"}}}' "$3"; exit 0 ;;
    "agent prompt") exit 0 ;;
    "agent get")
        st="running"; [ -f "$WORK/status-$3" ] && st="$(cat "$WORK/status-$3")"
        printf '{"result":{"agent":{"name":"%s","status":"%s"}}}' "$3" "$st"; exit 0 ;;
    "agent list")
        if [ -f "$WORK/list.json" ]; then cat "$WORK/list.json"; else printf '{"result":{"agents":[]}}'; fi
        exit 0 ;;
    "agent send-keys") exit 0 ;;
esac
printf 'unexpected herdr: %s\n' "$*" >&2; exit 1
EOF
chmod +x "$WORK/bin/herdr"

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
    raw="$WORK/sub-697.json"
else
    n="${endpoint##*/}"
    raw="$WORK/issue-${n}.json"
fi
[ -f "$raw" ] || { printf 'missing fixture: %s\n' "$raw" >&2; exit 1; }
if [ -n "$filter" ]; then jq -r "$filter" "$raw"; else cat "$raw"; fi
EOF
chmod +x "$WORK/bin/gh"

export HERDR_BIN="$WORK/bin/herdr" WAYFINDER_GH_BIN="$WORK/bin/gh"
export WAYFINDER_GH_REPO=fixture/repo WAYFINDER_WORKER_KIND=opencode
export PATH="$WORK/bin:$PATH" WORK
export WAYFINDER_WORKER_REGISTRY="$WORK/workers.tsv"
export WAYFINDER_INTEGRATION_GATE="$WORK/integration-gate"
export WAYFINDER_REVIEW_LOG_DIR="$WORK/logs"
export WAYFINDER_ROLE=chief
: > "$WAYFINDER_WORKER_REGISTRY"

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

row_status() { # <name> — print column 6.
    awk -F'\t' -v n="$1" '$1 == n { print $6; exit }' "$WAYFINDER_WORKER_REGISTRY"
}

review() { bash "$REVIEW" "$@" >"$WORK/out" 2>"$WORK/err" || { bad "review $* failed: $(cat "$WORK/err")"; return 1; }; }
review_expect_fail() { bash "$REVIEW" "$@" >"$WORK/out" 2>"$WORK/err" && { bad "review $* unexpectedly succeeded"; return 1; }; return 0; }

echo "1. queue is empty-safe and observable to leaf roles; leaves cannot disposition"
[ -z "$(bash "$REVIEW" queue 2>/dev/null)" ] && ok "empty queue prints no rows" || bad "empty queue printed rows"
WAYFINDER_ROLE=ticket bash "$REVIEW" queue >/dev/null 2>&1 && ok "leaf queue observable" || bad "leaf queue blocked"
WAYFINDER_ROLE=helper bash "$REVIEW" gate-status >/dev/null 2>&1 && ok "leaf gate-status observable" || bad "leaf gate-status blocked"
WAYFINDER_ROLE=ticket bash "$REVIEW" review wf-x --disposition reject --finding x >"$WORK/out" 2>"$WORK/err" \
    && bad "leaf review accepted" || grep -q "chief" "$WORK/err" && ok "leaf review refused with chief pointer" || bad "leaf refusal unclear"
WAYFINDER_ROLE=bug-scout bash "$REVIEW" integrate wf-x >"$WORK/out" 2>"$WORK/err" \
    && bad "leaf integrate accepted" || ok "leaf integrate refused"
WAYFINDER_ROLE=maintenance bash "$REVIEW" gate --reason x >"$WORK/out" 2>"$WORK/err" \
    && bad "leaf gate accepted" || ok "leaf gate refused"

echo "2. accept records the reviewed commit and awaits integration (done != accepted != integrated)"
mkdir -p "$WORK/ws-a"
mkrow wf-acc ticket 701 "$WORK/ws-a" ready-for-review
CANON_A="$(mkfixture canon-a)"
git -C "$CANON_A" checkout -qb wf/wf-acc >/dev/null
echo "feature" > "$CANON_A/feat.txt"
git -C "$CANON_A" add feat.txt
git -C "$CANON_A" commit -qm "add feature"
RESULT_A="$(git -C "$CANON_A" rev-parse HEAD)"
git -C "$CANON_A" checkout -q master >/dev/null
review_expect_fail review wf-acc --disposition accept && ok "accept without --result-commit refused" || true
grep -q "result-commit" "$WORK/err" && ok "refusal names --result-commit" || bad "wrong refusal: $(cat "$WORK/err")"
review review wf-acc --disposition accept --result-commit "$RESULT_A" --finding "scope matches #701"
[ "$(row_status wf-acc)" = "accepted-awaiting-integration" ] && ok "accept moves to accepted-awaiting-integration" || bad "accept state wrong: $(row_status wf-acc)"
grep -q "result=$RESULT_A" "$WAYFINDER_WORKER_REGISTRY" && ok "reviewed commit recorded in row" || bad "commit not recorded"
grep -q "accept ticket=#701 result=$RESULT_A" "$WORK/logs/wf-acc.log" && ok "accept logged with ticket+commit" || bad "review log missing: $(cat "$WORK/logs/wf-acc.log" 2>/dev/null)"
[ -z "$(git -C "$CANON_A" status --porcelain)" ] && ok "accept touches no canonical state" || bad "accept dirtied canonical"
[ ! -f "$CANON_A/feat.txt" ] && ok "accepted change not yet in canonical (awaiting window)" || bad "accept leaked into canonical"

echo "3. integrate applies the accepted result single-writer, verified, with closure guidance"
review integrate wf-acc --repo "$CANON_A"
[ "$(row_status wf-acc)" = "integrated" ] && ok "integrate marks integrated" || bad "integrate state wrong"
[ -f "$CANON_A/feat.txt" ] && ok "change present in canonical after integrate" || bad "change missing from canonical"
grep -q "integrated=.*verified=" "$WAYFINDER_WORKER_REGISTRY" && ok "integrated SHA + verification recorded" || bad "integration note missing"
grep -q "integrate ticket=#701" "$WORK/logs/wf-acc.log" && ok "integration logged" || bad "integration log missing"
grep -q "may now be closed" "$WORK/out" && ok "closure-after-integration guidance printed" || bad "no closure guidance: $(cat "$WORK/out")"
grep -q "safe to release" "$WORK/out" && ok "safe-cleanup guidance printed" || bad "no cleanup guidance"
[ -z "$(git -C "$CANON_A" status --porcelain)" ] && ok "canonical clean after integrate" || bad "canonical dirty"

echo "4. already-present results integrate without a new commit (sequential fallback)"
# Sequential fallback: the worker committed directly on canonical master, so
# the reviewed result already IS canonical history — verification still runs.
echo "direct" > "$CANON_A/direct.txt"
git -C "$CANON_A" add direct.txt
git -C "$CANON_A" commit -qm "direct sequential work"
RESULT_SEQ="$(git -C "$CANON_A" rev-parse HEAD)"
BEFORE_A="$RESULT_SEQ"
mkdir -p "$WORK/ws-a2"
mkrow wf-already ticket 702 "$WORK/ws-a2" ready-for-review
review review wf-already --disposition accept --result-commit "$RESULT_SEQ" >/dev/null
review integrate wf-already --repo "$CANON_A"
[ "$(row_status wf-already)" = "integrated" ] && ok "already-present marks integrated" || bad "already-present state wrong"
[ "$(git -C "$CANON_A" rev-parse HEAD)" = "$BEFORE_A" ] && ok "no new commit for already-present change" || bad "spurious commit created"
grep -q "already-present" "$WAYFINDER_WORKER_REGISTRY" && ok "already-present recorded honestly" || bad "no already-present note"

echo "5. conflicts are surfaced and recoverable — canonical stays clean, nothing discarded"
CANON_B="$(mkfixture canon-b)"
git -C "$CANON_B" checkout -qb wf/wf-con >/dev/null
echo "worker line" > "$CANON_B/shared.txt"
git -C "$CANON_B" add shared.txt
git -C "$CANON_B" commit -qm "worker change"
RESULT_B="$(git -C "$CANON_B" rev-parse HEAD)"
git -C "$CANON_B" checkout -q master >/dev/null
echo "chief line" > "$CANON_B/shared.txt"
git -C "$CANON_B" add shared.txt
git -C "$CANON_B" commit -qm "canonical change first"
mkdir -p "$WORK/ws-con"
mkrow wf-con ticket 703 "$WORK/ws-con" ready-for-review
review review wf-con --disposition accept --result-commit "$RESULT_B" >/dev/null
review_expect_fail integrate wf-con --repo "$CANON_B" && ok "conflicting integrate refused" || true
[ "$(row_status wf-con)" = "accepted-awaiting-integration" ] && ok "conflicted row stays queued (not discarded)" || bad "conflict row lost: $(row_status wf-con)"
grep -q "conflict at" "$WAYFINDER_WORKER_REGISTRY" && ok "conflict + new base recorded in row" || bad "no conflict note"
[ -z "$(git -C "$CANON_B" status --porcelain)" ] && ok "canonical clean after aborted conflict" || bad "canonical dirty after conflict"
[ ! -f "$CANON_B/shared.txt" ] || grep -q "chief line" "$CANON_B/shared.txt" && ok "canonical change untouched by conflict" || bad "canonical change disturbed"
grep -q "recover with:.*review wf-con --disposition revision" "$WORK/err" && ok "recovery invocation printed" || bad "no recovery hint: $(cat "$WORK/err")"

echo "6. revision returns to the same worker/workspace with concrete findings"
: > "$HERDR_CALLS"
review review wf-con --disposition revision --finding "rebase onto new base, keep worker line below chief line"
[ "$(row_status wf-con)" = "revision-requested" ] && ok "revision moves to revision-requested" || bad "revision state wrong"
grep -q "agent prompt wf-con" "$HERDR_CALLS" && ok "same worker re-prompted via seam" || bad "no re-prompt: $(cat "$HERDR_CALLS")"
grep -q "rebase onto new base" "$HERDR_CALLS" && ok "findings travel with the prompt" || bad "findings missing from prompt"
grep -q -- "$WORK/ws-con" "$HERDR_CALLS" && ok "prompt preserves the workspace" || bad "workspace missing from prompt"
grep -q "agent prompt wf-con" "$HERDR_CALLS" && ! grep "agent prompt wf-con" "$HERDR_CALLS" | grep -q -- "--wait" \
    && ok "revision prompt stays async" || bad "--wait leaked into revision"
review_expect_fail review wf-con --disposition revision && ok "revision without --finding refused" || true
mkdir -p "$WORK/ws-gone-note"
mkrow wf-gonews ticket 704 "$WORK/ws-missing-dir" ready-for-review
review_expect_fail review wf-gonews --disposition revision --finding x && ok "revision with gone workspace refused" || true
review review wf-con --disposition revision --finding "superseding guidance: keep the diff minimal" >/dev/null
[ "$(row_status wf-con)" = "revision-requested" ] && ok "re-nudge from revision-requested re-prompts with superseding findings" || bad "re-nudge wrong"
review_expect_fail review wf-con --disposition revision --finding "pipe | trick" && ok "finding with pipe refused (token guard)" || true
review_expect_fail review wf-con --disposition revision --finding "reviewed=accept result=$RESULT_A" && ok "finding forging accept tokens refused" || true
bash "$REVIEW" gate --reason "pipe | trick" >"$WORK/out" 2>"$WORK/err" \
    && bad "gate reason with pipe accepted" || ok "gate reason with pipe refused"

echo "7. reject and cancel are explicit terminals with reasons (never from live work)"
review review wf-gonews --disposition reject --finding "out of scope for #704"
[ "$(row_status wf-gonews)" = "rejected" ] && ok "reject is terminal" || bad "reject state wrong"
review_expect_fail review wf-con --disposition cancel --finding "too hasty" && ok "cancel of live rework refused (stop first)" || true
grep -q "stop the worker first" "$WORK/err" && ok "refusal points at worker stop" || bad "wrong refusal: $(cat "$WORK/err")"
review_expect_fail review wf-con --disposition reject --finding "too hasty" && ok "reject of live rework refused" || true
review_expect_fail review wf-running --disposition cancel --finding "too hasty" && ok "cancel of running worker refused" || true
bash "$WORKER" stop wf-con >/dev/null 2>&1 || bad "stop of reworking worker failed"
review review wf-con --disposition cancel --finding "superseded by #705"
[ "$(row_status wf-con)" = "cancelled" ] && ok "cancel from stopped recorded (double-l spelling)" || bad "cancel state wrong: $(row_status wf-con)"
grep -q "release the tracker claim" "$WORK/out" && ok "cancel prints claim-release guidance" || bad "no claim guidance"
review_expect_fail review wf-acc --disposition reject --finding x && ok "reject after integrated refused" || true
review_expect_fail review wf-gonews --disposition cancel --finding x && ok "cancel of rejected refused" || true
review_expect_fail review wf-acc --disposition cancel && ok "cancel without reason refused" || true

echo "8. role boundaries: helpers integrate via parents, scouts never disposition"
mkdir -p "$WORK/ws-par" "$WORK/ws-h"
mkrow wf-par ticket 710 "$WORK/ws-par" ready-for-review
mkrow wf-par-h1 helper 710 "$WORK/ws-h" ready-for-review "helper work" wf-par
review_expect_fail review wf-par-h1 --disposition accept --result-commit "$RESULT_A" && ok "direct helper accept refused" || true
grep -q "wf-par" "$WORK/err" && ok "helper refusal points at the parent" || bad "no parent pointer: $(cat "$WORK/err")"
mkrow wf-sc1 bug-scout 697 "$WORK/ws" ready-for-review
review_expect_fail review wf-sc1 --disposition reject --finding x && ok "scout disposition refused" || true
grep -q "read-only" "$WORK/err" && ok "scout refusal names read-only" || bad "wrong scout refusal"
review_expect_fail review wf-running --disposition accept --result-commit "$RESULT_A" && ok "unknown worker refused" || true
mkrow wf-running ticket 711 "$WORK/ws" running
review_expect_fail review wf-running --disposition accept --result-commit "$RESULT_A" && ok "accept of running worker refused" || true

echo "9. integration gating pauses canonical writes without stopping the queue"
CANON_C="$(mkfixture canon-c)"
git -C "$CANON_C" checkout -qb wf/wf-g >/dev/null
echo "gated" > "$CANON_C/g.txt"
git -C "$CANON_C" add g.txt
git -C "$CANON_C" commit -qm "gated change"
RESULT_C="$(git -C "$CANON_C" rev-parse HEAD)"
git -C "$CANON_C" checkout -q master >/dev/null
mkdir -p "$WORK/ws-g" "$WORK/ws-m"
mkrow wf-gated ticket 720 "$WORK/ws-g" ready-for-review
mkrow wf-697-maint maintenance 721 "$WORK/ws-m" running
review review wf-gated --disposition accept --result-commit "$RESULT_C" >/dev/null
review_expect_fail integrate wf-gated --repo "$CANON_C" && ok "live maintenance gates integration" || true
grep -q "gated" "$WORK/err" && ok "gate refusal names the cause" || bad "wrong gate refusal"
[ "$(row_status wf-gated)" = "accepted-awaiting-integration" ] && ok "gated row stays queued" || bad "gated row moved"
bash "$REVIEW" gate-status | grep -q "^gated:" && ok "gate-status reports maintenance gate" || bad "gate-status wrong"
bash "$WORKER" stop wf-697-maint >/dev/null 2>&1
bash "$REVIEW" gate --reason "red baseline, repair not yet dispatched" >/dev/null
bash "$REVIEW" gate-status | grep -q "red baseline" && ok "explicit gate recorded with reason" || bad "explicit gate missing"
review_expect_fail integrate wf-gated --repo "$CANON_C" && ok "explicit gate blocks integration" || true
review integrate wf-gated --repo "$CANON_C" --allow-gated >/dev/null
[ "$(row_status wf-gated)" = "integrated" ] && ok "--allow-gated overrides explicitly" || bad "override failed"
grep -q "allow-gated override" "$WAYFINDER_WORKER_REGISTRY" && ok "override recorded in row audit" || bad "override not audited"
grep -q "allow-gated override" "$WORK/logs/wf-gated.log" && ok "override recorded in review log" || bad "override not logged"
bash "$REVIEW" gate-clear >/dev/null
bash "$REVIEW" gate-status | grep -q "^ready$" && ok "gate-clear resumes readiness" || bad "gate not cleared"

echo "10. failed verification restores canonical and keeps the row queued"
CANON_D="$(mkfixture canon-d)"
git -C "$CANON_D" checkout -qb wf/wf-v >/dev/null
echo "verify me" > "$CANON_D/v.txt"
git -C "$CANON_D" add v.txt
git -C "$CANON_D" commit -qm "needs verification"
RESULT_D="$(git -C "$CANON_D" rev-parse HEAD)"
git -C "$CANON_D" checkout -q master >/dev/null
HEAD_D="$(git -C "$CANON_D" rev-parse HEAD)"
mkdir -p "$WORK/ws-v"
mkrow wf-verify ticket 730 "$WORK/ws-v" ready-for-review
review review wf-verify --disposition accept --result-commit "$RESULT_D" >/dev/null
WAYFINDER_VERIFY_CMD=false bash "$REVIEW" integrate wf-verify --repo "$CANON_D" >"$WORK/out" 2>"$WORK/err" \
    && bad "failing verification accepted" || grep -q "verification failed" "$WORK/err" && ok "verification failure reported" || bad "wrong failure: $(cat "$WORK/err")"
[ "$(git -C "$CANON_D" rev-parse HEAD)" = "$HEAD_D" ] && ok "applied commit removed after failed verification" || bad "canonical not restored"
[ -z "$(git -C "$CANON_D" status --porcelain)" ] && ok "canonical clean after failed verification" || bad "canonical dirty"
[ "$(row_status wf-verify)" = "accepted-awaiting-integration" ] && ok "row kept queued after failed verification" || bad "row lost"
WAYFINDER_VERIFY_CMD=true bash "$REVIEW" integrate wf-verify --repo "$CANON_D" >/dev/null 2>&1 \
    && [ "$(row_status wf-verify)" = "integrated" ] && ok "passing verification integrates" || bad "passing verify failed"

echo "11. integrate refusals fail closed"
review_expect_fail integrate wf-verify --repo "$CANON_D" && ok "re-integrate of integrated refused" || true
mkdir -p "$WORK/ws-r"
mkrow wf-noaccept ticket 731 "$WORK/ws-r" ready-for-review
review_expect_fail integrate wf-noaccept --repo "$CANON_D" && ok "integrate before accept refused" || true
grep -q "accepted-awaiting-integration" "$WORK/err" && ok "refusal names the required state" || bad "wrong refusal"
CANON_E="$(mkfixture canon-e)"
git -C "$CANON_E" checkout -qb wf/wf-mrg >/dev/null
echo "a" > "$CANON_E/a.txt"; git -C "$CANON_E" add a.txt; git -C "$CANON_E" commit -qm "side"
git -C "$CANON_E" checkout -q master >/dev/null
echo "b" > "$CANON_E/b.txt"; git -C "$CANON_E" add b.txt; git -C "$CANON_E" commit -qm "main"
git -C "$CANON_E" merge --no-ff -qm "merge side" wf/wf-mrg >/dev/null
MERGE_E="$(git -C "$CANON_E" rev-parse HEAD)"
mkrow wf-merge ticket 732 "$WORK/ws-r" ready-for-review
review review wf-merge --disposition accept --result-commit "$MERGE_E" >/dev/null
review_expect_fail integrate wf-merge --repo "$CANON_E" && ok "merge-commit result refused" || true
echo "dirty" > "$CANON_E/seed.txt"
review_expect_fail integrate wf-merge --repo "$CANON_E" && ok "dirty canonical refused" || true
git -C "$CANON_E" checkout -q -- seed.txt >/dev/null
BOGUS="$(printf 'dead%.0s' 1 2 3 4 5 6 7 8 9 10)"
mkrow wf-bogus ticket 733 "$WORK/ws-r" ready-for-review
review review wf-bogus --disposition accept --result-commit "$BOGUS" >/dev/null
review_expect_fail integrate wf-bogus --repo "$CANON_E" && ok "missing result commit refused" || true

echo "12. worker seam protects reviewable rows and preserves review state"
mkrow wf-prot ticket 740 "$WORK/ws" ready-for-review
bash "$WORKER" cleanup wf-prot >"$WORK/out" 2>"$WORK/err" \
    && bad "cleanup of ready-for-review accepted" || grep -q "review disposition" "$WORK/err" && ok "cleanup of review-pending refused" || bad "wrong cleanup refusal"
bash "$WORKER" cleanup wf-gated >/dev/null 2>&1 && ok "cleanup of integrated allowed" || bad "cleanup of integrated refused"
bash "$WORKER" cleanup wf-gonews >/dev/null 2>&1 && ok "cleanup of rejected allowed" || bad "cleanup of rejected refused"
bash "$WORKER" stop wf-prot >"$WORK/out" 2>"$WORK/err" \
    && bad "stop of ready-for-review accepted" || grep -q "review dispositions" "$WORK/err" && ok "stop of review-pending refused" || bad "wrong stop refusal"
cat >"$WORK/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-prot","status":"done"},{"name":"wf-missing","status":"running"}]}}
JSON
printf 'wf-stale\tticket\t741\t%s\tpane-7\trunning\tt0\tt0\tspawned\n' "$WORK/ws" >> "$WAYFINDER_WORKER_REGISTRY"
bash "$WORKER" reconcile >/dev/null 2>&1
[ "$(row_status wf-prot)" = "ready-for-review" ] && ok "reconcile never rewrites ready-for-review (herdr said done)" || bad "review state clobbered"
[ "$(row_status wf-stale)" = "gone" ] && ok "missing running worker still marked gone" || bad "gone marking broken: $(row_status wf-stale)"
printf 'done' > "$WORK/status-wf-f2"
cat >"$WORK/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-f2","status":"failed"}]}}
JSON
printf 'wf-f2\tticket\t742\t%s\tpane-7\trunning\tt0\tt0\tspawned\n' "$WORK/ws" >> "$WAYFINDER_WORKER_REGISTRY"
bash "$WORKER" reconcile >/dev/null 2>&1
[ "$(row_status wf-f2)" = "failed" ] && ok "herdr failed adopted as failed" || bad "failed mapping wrong: $(row_status wf-f2)"
rm -f "$WORK"/list.json "$WORK"/status-*

echo "13. chief pass collects failed results and logs queue depth (no auto-integrate)"
printf '[{"number":801}]' >"$WORK/sub-697.json"
cat >"$WORK/issue-801.json" <<'JSON'
{"number":801,"state":"open","issue_dependencies_summary":{"blocked_by":0,"blocking":0},"assignees":[],"labels":[{"name":"wayfinder:task"}]}
JSON
printf 'wf-697-801\tticket\t801\t%s\tpane-7\tfailed\tt0\tt0\tworker reported failure\n' "$WORK/ws" >> "$WAYFINDER_WORKER_REGISTRY"
bash "$CHIEF" --map 697 --once --max-workers 2 >"$WORK/out" 2>"$WORK/err" || bad "chief pass failed: $(cat "$WORK/err")"
[ "$(row_status wf-697-801)" = "ready-for-review" ] && ok "failed collected to ready-for-review" || bad "failed not collected"
grep -q "review queue: .* ready-for-review" "$WORK/out" && ok "pass logs queue depth" || bad "no queue log: $(cat "$WORK/out")"
[ "$(grep -c '^wf-697-801' "$WAYFINDER_WORKER_REGISTRY")" -eq 1 ] && ok "collected row not duplicated" || bad "duplicate rows"
CANON_F="$(mkfixture canon-f)"
[ -z "$(git -C "$CANON_F" status --porcelain)" ] && ok "chief pass leaves canonical fixtures clean" || bad "chief pass dirtied fixtures"

echo "14. chief review lanes delegate to the queue"
bash "$CHIEF" --map 697 --queue >"$WORK/out" 2>"$WORK/err" || bad "chief --queue failed: $(cat "$WORK/err")"
grep -q "wf-697-801" "$WORK/out" && ok "--queue lists pending rows" || bad "queue lane empty: $(cat "$WORK/out")"
bash "$CHIEF" --map 697 --queue --all >"$WORK/out" 2>"$WORK/err" || bad "chief --queue --all failed"
grep -q "wf-acc" "$WORK/out" && ok "--queue --all shows terminal rows" || bad "terminals missing from --all"
bash "$REVIEW" queue --role bogus >"$WORK/out" 2>"$WORK/err" \
    && bad "queue with bogus role accepted" || ok "queue --role validated"
bash "$REVIEW" status wf-acc >"$WORK/out" 2>"$WORK/err" || bad "status failed: $(cat "$WORK/err")"
grep -q "^status=integrated$" "$WORK/out" && ok "status shows row state" || bad "status state missing"
grep -q "accept ticket=#701" "$WORK/out" && ok "status shows review-log tail" || bad "status log tail missing"
bash "$CHIEF" --map 697 --review wf-697-801 --disposition reject --finding "not reproducible" >"$WORK/out" 2>"$WORK/err" \
    || bad "chief --review failed: $(cat "$WORK/err")"
[ "$(row_status wf-697-801)" = "rejected" ] && ok "--review dispositions through the queue" || bad "lane disposition wrong"
if bash "$CHIEF" --map 697 --queue --review wf-x >"$WORK/out" 2>"$WORK/err"; then
    bad "combined single-shot lanes accepted"
else ok "lanes stay one-per-invocation"; fi
if bash "$CHIEF" --map 697 --once --disposition accept >"$WORK/out" 2>"$WORK/err"; then
    bad "stray --disposition without --review accepted"
else ok "stray review flags without --review refused"; fi

echo "15. revision round-trips: rework re-enters review and the newest accept wins"
CANON_R="$(mkfixture canon-r)"
git -C "$CANON_R" checkout -qb wf/wf-rt >/dev/null
echo "v1" > "$CANON_R/rt.txt"
git -C "$CANON_R" add rt.txt
git -C "$CANON_R" commit -qm "rework v1"
RESULT_R1="$(git -C "$CANON_R" rev-parse HEAD)"
git -C "$CANON_R" checkout -q master >/dev/null
mkdir -p "$WORK/ws-rt"
mkrow wf-rt ticket 750 "$WORK/ws-rt" ready-for-review
review review wf-rt --disposition accept --result-commit "$RESULT_R1" >/dev/null
review review wf-rt --disposition revision --finding "needs v2 header" >/dev/null
[ "$(row_status wf-rt)" = "revision-requested" ] && ok "revision parks the accepted result" || bad "revision park wrong"
git -C "$CANON_R" checkout -q wf/wf-rt >/dev/null
echo "v2" > "$CANON_R/rt.txt"
git -C "$CANON_R" commit -q --amend -am "rework v2 (squashed reviewable commit)"
RESULT_R2="$(git -C "$CANON_R" rev-parse HEAD)"
git -C "$CANON_R" checkout -q master >/dev/null
cat >"$WORK/list.json" <<'JSON'
{"result":{"agents":[{"name":"wf-rt","status":"done"}]}}
JSON
bash "$WORKER" reconcile >/dev/null 2>&1
[ "$(row_status wf-rt)" = "done" ] && ok "reconcile adopts the rework report (round-trip edge)" || bad "rework stuck: $(row_status wf-rt)"
rm -f "$WORK/list.json"
printf '[{"number":750}]' >"$WORK/sub-697.json"
cat >"$WORK/issue-750.json" <<'JSON'
{"number":750,"state":"open","issue_dependencies_summary":{"blocked_by":0,"blocking":0},"assignees":[],"labels":[{"name":"wayfinder:task"}]}
JSON
bash "$CHIEF" --map 697 --once --max-workers 2 >/dev/null 2>&1 || bad "collect pass failed"
[ "$(row_status wf-rt)" = "ready-for-review" ] && ok "rework collected for a fresh disposition" || bad "rework not collected"
review review wf-rt --disposition accept --result-commit "$RESULT_R2" >/dev/null
review integrate wf-rt --repo "$CANON_R" >/dev/null
[ "$(row_status wf-rt)" = "integrated" ] && ok "re-accepted result integrates" || bad "re-accept integrate wrong"
[ "$(cat "$CANON_R/rt.txt")" = "v2" ] && ok "newest accepted commit applied (no stale v1)" || bad "stale result integrated"
grep -q "integrated=.*result=$RESULT_R2" "$WAYFINDER_WORKER_REGISTRY" && ok "integrated note names the newest result" || bad "wrong result recorded"

echo "16. root-commit results and empty gate files behave"
CANON_T="$(mkfixture canon-t)"
ROOT_T="$(git -C "$CANON_T" rev-parse HEAD)"
mkdir -p "$WORK/ws-root-t"
mkrow wf-root ticket 751 "$WORK/ws-root-t" ready-for-review
review review wf-root --disposition accept --result-commit "$ROOT_T" >/dev/null
review integrate wf-root --repo "$CANON_T" >/dev/null
[ "$(row_status wf-root)" = "integrated" ] && ok "root commit integrates via already-present" || bad "root integrate wrong"
grep -q "diff-check-na-root" "$WAYFINDER_WORKER_REGISTRY" && ok "root verification scope recorded honestly" || bad "root scope missing"
: > "$WORK/empty-gate"
WAYFINDER_INTEGRATION_GATE="$WORK/empty-gate" bash "$REVIEW" gate-status >"$WORK/out" 2>"$WORK/err"
grep -q "^ready$" "$WORK/out" && ok "empty gate file reads ready (fail-open documented)" || bad "empty gate misread"

echo "17. structural guards: single-writer and no daemon-side integration"
[ "$(grep -v '^#' "$REVIEW" | grep -c 'cherry-pick')" -ge 1 ] && ok "review owns the cherry-pick path" || bad "no cherry-pick in review"
if grep -vE '^\s*#' "$REVIEW" | grep -q 'HEAD~1'; then bad "reset targets HEAD~1 instead of the recorded base"; else ok "restore targets the recorded pre-integration base"; fi
if grep -v '^#' "$WORKER" | grep -Eq 'cherry-pick|reset --hard'; then bad "worker seam touches integration"; else ok "worker seam never integrates"; fi
if grep -n 'review.*integrate\|integrate.*review' "$ROOT/tools/wayfinder/wayfinder-loop.sh" | grep -v '^.*#' >/dev/null; then
    bad "daemon loop invokes the review/integrate path"
else ok "daemon never performs integration (supervisor only)"; fi
if grep -v '^#' "$REVIEW" | grep -Eq 'gh (issue|api)|herdr agent (start|list|wait)'; then
    bad "review talks to tracker/herdr directly"
else ok "review uses the worker seam for prompts, never the tracker"; fi

echo
if [ $fail -eq 0 ]; then echo "review-contract: OK"; else echo "review-contract: FAILURES PRESENT"; exit 1; fi
