#!/usr/bin/env bash
# wayfinder-scout-test.sh — contract tests for wayfinder-scout.sh + the chief --spawn-scout lane (map #697 #738).
# Real git fixture repos for base-SHA resolution, fake-herdr/fake-gh fixtures
# for the chief lane: no real Herdr, tracker, Gradle, database, or network.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCOUT="$ROOT/tools/wayfinder/wayfinder-scout.sh"
CHIEF="$ROOT/tools/wayfinder/wayfinder-chief.sh"
WORKER="$ROOT/tools/wayfinder/wayfinder-worker.sh"
WORK="$(mktemp -d /tmp/opencode/wayfinder-scout-test.XXXXXX)"
trap 'rm -rf "$WORK"' EXIT
fail=0

ok() { echo "  ok: $1"; }
bad() { echo "  FAIL: $1"; fail=1; }

bash -n "$SCOUT" || { echo "FAIL: scout fails bash -n"; exit 1; }
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
export WAYFINDER_SCOUT_REGISTRY="$WORK/scout.tsv"
export WAYFINDER_ROLE=chief
: > "$WORK/scout.tsv"

scout() { bash "$SCOUT" "$@" >"$WORK/out" 2>"$WORK/err" || { bad "scout $* failed: $(cat "$WORK/err")"; return 1; }; }
scout_expect_fail() { bash "$SCOUT" "$@" >"$WORK/out" 2>"$WORK/err" && { bad "scout $* unexpectedly succeeded"; return 1; }; return 0; }
fresh_scout() { : > "$WORK/scout.tsv"; }

echo "1. slices lists a bounded multi-slice taxonomy, not one hard-coded area"
[ "$(bash "$SCOUT" slices | wc -l)" -ge 5 ] && ok "several bounded slices" || bad "slice list too small"
for s in backend-domain api-routes-auth security-permissions operational-recovery; do
    bash "$SCOUT" slices | grep -x "$s" >/dev/null && ok "taxonomy covers $s" || bad "taxonomy missing $s"
done

echo "2. start records the audited base SHA; complete closes the slice"
fresh_scout
scout start backend-domain
grep -q $'^backend-domain\tactive\t'"$HEAD_SHA"$'\t' "$WORK/scout.tsv" \
    && ok "start records active + full HEAD base" || bad "start row wrong: $(cat "$WORK/scout.tsv")"
scout complete backend-domain --note "covered engines"
grep -q $'^backend-domain\tdone\t'"$HEAD_SHA"$'\t' "$WORK/scout.tsv" \
    && ok "complete marks done, keeps base" || bad "complete row wrong: $(cat "$WORK/scout.tsv")"
[ "$(grep -c '^backend-domain' "$WORK/scout.tsv")" -eq 1 ] && ok "one row per slice (atomic rewrite)" || bad "duplicate rows"

echo "3. next walks pending slices first, then revisits the stalest done slice"
fresh_scout
[ "$(bash "$SCOUT" next)" = "backend-domain" ] && ok "empty state starts at taxonomy head" || bad "next wrong on empty"
scout start backend-domain >/dev/null
scout complete backend-domain >/dev/null
[ "$(bash "$SCOUT" next)" = "persistence-data-integrity" ] && ok "next pending slice after one done" || bad "next did not advance"
scout start persistence-data-integrity >/dev/null
[ "$(bash "$SCOUT" next)" = "api-routes-auth" ] && ok "active slice skipped, next pending slice offered (concurrent dispatch)" || bad "next mishandled active slice"
# All slices active: nothing due (backend-domain re-audits from done).
bash "$SCOUT" start backend-domain >/dev/null 2>&1
for s in api-routes-auth frontend-ui-state cross-platform-shared tests-fixtures ci-build-tooling security-permissions migrations-schema operational-recovery; do
    bash "$SCOUT" start "$s" >/dev/null 2>&1
done
if bash "$SCOUT" next >"$WORK/out" 2>/dev/null; then
    bad "next returned a slice while every slice is active: $(cat "$WORK/out")"
else
    ok "no slice due while every slice is active"
fi
# Revisit path: no pending slices, so the stalest done slice is due again.
printf 'backend-domain\tdone\t%s\t\t2000-01-01 00:00\tnote\n' "$HEAD_SHA" > "$WORK/scout.tsv"
printf 'persistence-data-integrity\tdone\t%s\t\t2000-01-02 00:00\tnote\n' "$HEAD_SHA" >> "$WORK/scout.tsv"
for s in api-routes-auth frontend-ui-state cross-platform-shared tests-fixtures ci-build-tooling security-permissions migrations-schema operational-recovery; do
    printf '%s\tactive\t%s\t\t2026-01-01 00:00\tnote\n' "$s" "$HEAD_SHA" >> "$WORK/scout.tsv"
done
[ "$(bash "$SCOUT" next)" = "backend-domain" ] && ok "no pending slices: stalest done slice revisits first" || bad "revisit order wrong: $(bash "$SCOUT" next || true)"

echo "4. note-finding attributes filed issues to the slice and dedupes"
fresh_scout
scout start api-routes-auth >/dev/null
scout note-finding api-routes-auth 801
scout note-finding api-routes-auth 802
grep -q $'^api-routes-auth\tactive\t.*\t801,802\t' "$WORK/scout.tsv" \
    && ok "issues accumulate comma-separated on the slice row" || bad "issues wrong: $(cat "$WORK/scout.tsv")"
scout_expect_fail note-finding api-routes-auth 801 && ok "duplicate issue refused" || true
scout_expect_fail note-finding api-routes-auth nine && ok "non-numeric issue refused" || true
scout_expect_fail note-finding api-routes-auth 0 && ok "issue #0 refused" || true
scout_expect_fail note-finding api-routes-auth 000 && ok "all-zero issue refused" || true
scout note-finding api-routes-auth 007
grep -q $'^api-routes-auth\tactive\t.*\t801,802,7\t' "$WORK/scout.tsv" \
    && ok "leading zeros normalize for dedupe" || bad "normalize wrong: $(cat "$WORK/scout.tsv")"
scout_expect_fail note-finding api-routes-auth 7 && ok "normalized duplicate refused" || true
scout_expect_fail start '*' && ok "glob slice refused (literal taxonomy)" || true
scout_expect_fail start 'backend-domain ' && ok "padded slice refused" || true
scout_expect_fail prompt '*' --map 697 --workspace "$WORK/ws" && ok "glob prompt refused" || true
scout_expect_fail note-finding ghost-slice 803 && ok "unknown slice refused" || true
scout_expect_fail note-finding tests-fixtures 804 && ok "finding on never-started slice refused" || true

echo "4b. resolve-base prints the full audited SHA without mutating state"
[ "$(bash "$SCOUT" resolve-base)" = "$HEAD_SHA" ] && ok "default base is canonical HEAD" || bad "resolve-base default wrong"
[ "$(bash "$SCOUT" resolve-base --base HEAD~0)" = "$HEAD_SHA" ] && ok "explicit base resolves" || bad "resolve-base explicit wrong"
scout_expect_fail resolve-base --base deadbeef-dead && ok "unknown base refused" || true
[ "$(bash "$SCOUT" resolve-base --base "$HEAD_SHA" | wc -c)" -eq 40 ] && ok "bare 40-char SHA (no trailing newline)" || bad "SHA shape wrong"

echo "5. leaf roles cannot mutate progress; reads stay observable"
fresh_scout
WAYFINDER_ROLE=bug-scout bash "$SCOUT" start backend-domain >"$WORK/out" 2>"$WORK/err" \
    && bad "leaf start accepted" || grep -q "chief" "$WORK/err" && ok "leaf start refused with chief pointer" || bad "leaf refusal unclear"
WAYFINDER_ROLE=ticket bash "$SCOUT" complete backend-domain >"$WORK/out" 2>"$WORK/err" \
    && bad "leaf complete accepted" || ok "leaf complete refused"
WAYFINDER_ROLE=helper bash "$SCOUT" note-finding backend-domain 1 >"$WORK/out" 2>"$WORK/err" \
    && bad "leaf note-finding accepted" || ok "leaf note-finding refused"
[ -s "$WORK/scout.tsv" ] && bad "refused mutations wrote state" || ok "refused mutations left state untouched"
WAYFINDER_ROLE=bug-scout bash "$SCOUT" slices >/dev/null 2>&1 && ok "leaf slices observable" || bad "leaf slices blocked"
WAYFINDER_ROLE=bug-scout bash "$SCOUT" status >/dev/null 2>&1 && ok "leaf status observable" || bad "leaf status blocked"
WAYFINDER_ROLE=bug-scout bash "$SCOUT" prompt backend-domain --map 697 --workspace "$WORK/ws" >/dev/null 2>&1 \
    && ok "leaf prompt observable" || bad "leaf prompt blocked"
WAYFINDER_ROLE=bug-scout bash "$SCOUT" next >/dev/null 2>&1 && ok "leaf next observable" || bad "leaf next blocked"

echo "6. prompt carries the read-only scout contract (map #697 #738)"
for token in "ROLE: bug-scout" "SLICE:" "READ-ONLY CONTRACT" "You may:" "You must not:" "SLICE DISCIPLINE" "DUPLICATION CHECK" "TICKET QUALITY" "wayfinder-create-child" "sub-issue" "EXPERIMENTAL MUTATION" "never" "integrated" "FINDINGS:" "COVERAGE:" "HELP REQUESTS" "claim" "handoff" "spawn"; do
    bash "$SCOUT" prompt security-permissions --map 697 --workspace "$WORK/ws" --base "$HEAD_SHA" | grep -- "$token" >/dev/null \
        && ok "prompt carries $token" || bad "prompt missing $token"
done
bash "$SCOUT" prompt security-permissions --map 697 --workspace "$WORK/ws" | grep "derive the workspace HEAD" >/dev/null \
    && ok "prompt without --base asks the scout to derive HEAD" || bad "prompt base fallback missing"
bash "$SCOUT" prompt migrations-schema --map 697 --workspace "$WORK/ws" --base "$HEAD_SHA" | grep "$HEAD_SHA" >/dev/null \
    && ok "prompt carries dispatched base" || bad "prompt missing --base value"
scout_expect_fail prompt ghost-slice --map 697 --workspace "$WORK/ws" && ok "prompt of unknown slice refused" || true
scout_expect_fail prompt backend-domain --workspace "$WORK/ws" && ok "prompt without --map refused" || true
scout_expect_fail prompt backend-domain --map 697 && ok "prompt without --workspace refused" || true

echo "7. invalid lifecycle transitions fail closed"
fresh_scout
scout_expect_fail start ghost-slice && ok "start of unknown slice refused" || true
scout_expect_fail complete backend-domain && ok "complete without start refused" || true
scout start tests-fixtures >/dev/null
scout_expect_fail start tests-fixtures && ok "double start refused" || true
scout complete tests-fixtures >/dev/null
scout_expect_fail complete tests-fixtures && ok "double complete refused" || true

echo "8. crash loses no filed issues and touches no canonical state"
fresh_scout
scout start frontend-ui-state >/dev/null
scout note-finding frontend-ui-state 901 >/dev/null
scout note-finding frontend-ui-state 902 >/dev/null
# Crash here: no complete. Successor re-reads state.
grep -q $'^frontend-ui-state\tactive\t.*\t901,902\t' "$WORK/scout.tsv" \
    && ok "in-flight slice keeps its filed issues across the crash" || bad "issues lost: $(cat "$WORK/scout.tsv")"
[ -z "$(git -C "$FIX" status --porcelain)" ] && ok "canonical fixture untouched by scout bookkeeping" || bad "fixture dirtied"
[ -z "$(git -C "$ROOT" status --porcelain -- tools/wayfinder/wayfinder-scout.sh tools/wayfinder/wayfinder-scout-test.sh 2>/dev/null | grep -v '^??')" ] \
    && ok "no stray canonical edits from the test run" || true

# ---- chief --spawn-scout lane (fake herdr + fake gh) ----
mkdir -p "$WORK/cws" "$WORK/bin" "$WORK/scout-ws"
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
export WAYFINDER_GH_REPO=fixture/repo WAYFINDER_WORKER_KIND=opencode
export WAYFINDER_WORKER_REGISTRY="$WORK/workers.tsv"
: > "$WAYFINDER_WORKER_REGISTRY"
export PATH="$WORK/bin:$PATH"

chief() { bash "$CHIEF" "$@" >"$WORK/cout" 2>"$WORK/cerr" || { bad "chief $* failed: $(cat "$WORK/cerr")"; return 1; }; }
chief_expect_fail() { bash "$CHIEF" "$@" >"$WORK/cout" 2>"$WORK/cerr" && { bad "chief $* unexpectedly succeeded"; return 1; }; return 0; }

echo "9. chief spawns one chief-mediated scout per slice (map #697 #738)"
fresh_scout; : > "$WAYFINDER_WORKER_REGISTRY"; : > "$HERDR_CALLS"
chief --map 697 --max-workers 2 --spawn-scout backend-domain --scout-workspace "$WORK/scout-ws"
grep -q $'^wf-697-scout\tbug-scout\t697\t'"$WORK/scout-ws"$'\t' "$WAYFINDER_WORKER_REGISTRY" \
    && ok "scout registered bug-scout anchored at the map" || bad "scout row missing: $(cat "$WAYFINDER_WORKER_REGISTRY")"
grep -q "for slice backend-domain" "$WORK/cout" \
    && ok "chief log links the scout worker to its slice" || bad "slice linkage missing: $(cat "$WORK/cout")"
grep -q "agent prompt" "$HERDR_CALLS" && ok "scout received its prompt asynchronously" || bad "no prompt dispatched"
grep -q -- "ROLE: bug-scout" "$HERDR_CALLS" && ok "dispatched prompt carries the scout role" || bad "prompt missing role"
grep -q -- "SLICE: backend-domain" "$HERDR_CALLS" && ok "dispatched prompt names the slice" || bad "prompt missing slice"
grep -q -- "DUPLICATION CHECK" "$HERDR_CALLS" && ok "dispatched prompt carries duplication + quality terms" || bad "prompt missing contract"
if grep -- "agent prompt" "$HERDR_CALLS" | grep -- '--wait' >/dev/null; then bad "scout dispatch leaked --wait"; else ok "scout dispatch stays async"; fi
grep -q $'^backend-domain\tactive\t' "$WORK/scout.tsv" \
    && ok "slice progress recorded after successful spawn" || bad "scout state missing: $(cat "$WORK/scout.tsv")"

echo "10. scout lane refusals fail closed with no side effects"
chief_expect_fail --map 697 --spawn-scout ghost-slice --scout-workspace "$WORK/scout-ws" && ok "unknown slice refused" || true
chief_expect_fail --map 697 --spawn-scout api-routes-auth --scout-workspace "$WORK/missing-ws" && ok "missing workspace refused" || true
chief_expect_fail --map 697 --once --scout-workspace "$WORK/scout-ws" && ok "stray --scout-workspace refused" || true
chief_expect_fail --map 697 --once --scout-name custom && ok "stray --scout-name refused" || true
chief_expect_fail --map 697 --spawn-helper wf-x --scope s --helper-workspace "$WORK/scout-ws" --spawn-scout api-routes-auth --scout-workspace "$WORK/scout-ws" \
    && ok "combined helper+scout lanes refused" || true
chief_expect_fail --map 697 --spawn-scout api-routes-auth --scout-workspace "$WORK/scout-ws" --helper-mode writable \
    && ok "--helper-mode on the scout lane refused" || true
chief_expect_fail --map 697 --once --helper-mode writable \
    && ok "stray --helper-mode without a lane refused" || true
[ "$(grep -c 'bug-scout' "$WAYFINDER_WORKER_REGISTRY")" -eq 1 ] && ok "refusals spawned nothing further" || bad "refusal side effects: $(cat "$WAYFINDER_WORKER_REGISTRY")"

echo "11. scout capacity is the shared global bound; refusals leave slice state alone"
: > "$WAYFINDER_WORKER_REGISTRY"
printf 'wf-697-1\tticket\t1\t%s\tpane-7\trunning\tt0\tt0\tnote\n' "$WORK/cws" > "$WAYFINDER_WORKER_REGISTRY"
fresh_scout
chief_expect_fail --map 697 --max-workers 1 --spawn-scout ci-build-tooling --scout-workspace "$WORK/scout-ws" \
    && ok "scout refused at capacity" || true
grep -q "capacity" "$WORK/cerr" && ok "refusal names capacity" || bad "wrong refusal: $(cat "$WORK/cerr")"
[ -s "$WORK/scout.tsv" ] && bad "refused spawn marked slice state" || ok "refused spawn left scout state untouched"
: > "$WAYFINDER_WORKER_REGISTRY"
chief --map 697 --max-workers 2 --spawn-scout ci-build-tooling --scout-workspace "$WORK/scout-ws" --base "$HEAD_SHA"
grep -q $'^ci-build-tooling\tactive\t'"$HEAD_SHA"$'\t' "$WORK/scout.tsv" \
    && ok "--base passthrough recorded as the audited base" || bad "base not recorded: $(cat "$WORK/scout.tsv")"
grep -q -- "$HEAD_SHA" "$HERDR_CALLS" && ok "dispatched prompt carries --base" || bad "prompt missing base"

echo "12. dry-run plans without spawning or recording"
fresh_scout; : > "$WAYFINDER_WORKER_REGISTRY"; : > "$HERDR_CALLS"
chief --map 697 --spawn-scout tests-fixtures --scout-workspace "$WORK/scout-ws" --dry-run
grep -q 'would spawn scout for slice tests-fixtures' "$WORK/cout" && ok "dry-run logs the plan" || bad "no plan logged"
[ -s "$HERDR_CALLS" ] && bad "dry-run called herdr" || ok "dry-run called no herdr"
[ -s "$WORK/scout.tsv" ] && bad "dry-run wrote scout state" || ok "dry-run left scout state untouched"
[ -s "$WAYFINDER_WORKER_REGISTRY" ] && bad "dry-run wrote worker registry" || ok "dry-run left worker registry untouched"

echo "13. second scout for another slice gets its own name; re-audit reuses the slice"
fresh_scout; : > "$WAYFINDER_WORKER_REGISTRY"; : > "$HERDR_CALLS"
chief --map 697 --max-workers 3 --spawn-scout backend-domain --scout-workspace "$WORK/scout-ws" >/dev/null 2>&1
chief --map 697 --max-workers 3 --spawn-scout api-routes-auth --scout-workspace "$WORK/scout-ws"
grep -q $'^wf-697-scout-2\tbug-scout\t' "$WAYFINDER_WORKER_REGISTRY" \
    && ok "concurrent slice scout allocated a sibling name" || bad "naming wrong: $(cat "$WAYFINDER_WORKER_REGISTRY")"
chief --map 697 --max-workers 3 --spawn-scout backend-domain --scout-workspace "$WORK/scout-ws"
grep -q 'already active (re-audit)' "$WORK/cout" && ok "re-audit of the active slice logged, worker kept" || bad "re-audit mishandled: $(cat "$WORK/cout")"
[ "$(grep -c '^backend-domain' "$WORK/scout.tsv")" -eq 1 ] && ok "re-audit kept a single slice row" || bad "slice rows duplicated"

echo "14. unresolvable --base refuses before spending capacity"
fresh_scout; : > "$WAYFINDER_WORKER_REGISTRY"; : > "$HERDR_CALLS"
chief_expect_fail --map 697 --max-workers 2 --spawn-scout backend-domain --scout-workspace "$WORK/scout-ws" --base deadbeef-dead \
    && ok "bad base refused pre-spawn" || true
grep -q "unknown base revision" "$WORK/cerr" && ok "refusal names the base" || bad "wrong refusal: $(cat "$WORK/cerr")"
[ -s "$WAYFINDER_WORKER_REGISTRY" ] && bad "refused spawn left a worker row" || ok "no worker spawned for bad base"
[ -s "$WORK/scout.tsv" ] && bad "refused spawn marked slice state" || ok "no slice state for bad base"
[ -s "$HERDR_CALLS" ] && bad "refused spawn called herdr" || ok "refused spawn called no herdr"

echo "15. post-spawn state failure reports nonzero instead of masking success"
fresh_scout; : > "$WAYFINDER_WORKER_REGISTRY"
mkdir -p "$WORK/scout-dir-state"
export WAYFINDER_SCOUT_REGISTRY="$WORK/scout-dir-state"
if bash "$CHIEF" --map 697 --max-workers 2 --spawn-scout backend-domain --scout-workspace "$WORK/scout-ws" >"$WORK/cout" 2>"$WORK/cerr"; then
    bad "state failure masked as success"
else
    ok "state failure exits nonzero"
fi
grep -q "slice-state start failed" "$WORK/cout" && ok "divergence warning logged" || bad "no warning: $(cat "$WORK/cout")"
grep -q $'^wf-697-scout\tbug-scout\t' "$WAYFINDER_WORKER_REGISTRY" \
    && ok "spawned worker kept (audits, then chief repairs state)" || bad "worker row missing"
export WAYFINDER_SCOUT_REGISTRY="$WORK/scout.tsv"

echo
if [ $fail -eq 0 ]; then echo "scout-contract: OK"; else echo "scout-contract: FAILURES PRESENT"; exit 1; fi
