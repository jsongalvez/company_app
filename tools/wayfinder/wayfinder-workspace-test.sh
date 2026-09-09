#!/usr/bin/env bash
# wayfinder-workspace-test.sh — contract tests for wayfinder-workspace.sh (map #697 #736).
# Real git fixture repos only (isolated under /tmp/opencode): no real Herdr,
# Gradle, database, or network. Never touches the canonical checkout.
#
# NOTE: pipefail stays OFF here by design (#754). Every assertion below is
# `producer | grep -q pattern`, where only grep's verdict matters; with
# pipefail, grep -q's early exit SIGPIPEs the producer and the pipeline
# spuriously fails ~2% of runs (measured). Producers here are either
# file-backed (suite writes) or re-checked on the next line — a dead producer
# surfaces as empty output, which fails the grep anyway.
set -eu

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORKSPACE="$ROOT/tools/wayfinder/wayfinder-workspace.sh"
WORKER="$ROOT/tools/wayfinder/wayfinder-worker.sh"
CHIEF="$ROOT/tools/wayfinder/wayfinder-chief.sh"
WORK="$(mktemp -d /tmp/opencode/wayfinder-workspace-test.XXXXXX)"
trap 'rm -rf "$WORK"' EXIT
fail=0

ok() { echo "  ok: $1"; }
bad() { echo "  FAIL: $1"; fail=1; }

bash -n "$WORKSPACE" || { echo "FAIL: workspace fails bash -n"; exit 1; }

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
export WAYFINDER_REPO="$FIX"
export WAYFINDER_WORKSPACE_REGISTRY="$WORK/workspaces.tsv"
export WAYFINDER_WORKSPACE_ROOT="$WORK/ws-root"
export WAYFINDER_ROLE=chief
export WAYFINDER_WORKSPACE_PROVIDER=git-worktree

ws() { bash "$WORKSPACE" "$@" >"$WORK/out" 2>"$WORK/err" || { printf "%s" "$?"; return 0; }; printf "0"; }
reg_row() { awk -F'\t' -v id="$1" '$1 == id { print; exit }' "$WORK/workspaces.tsv"; }

echo "1. create records provider-neutral metadata with full base SHA"
rc="$(ws create --purpose ticket-101 --id wf-a)"
[ "$rc" = "0" ] || bad "create failed: $(cat "$WORK/err")"
grep -q $'^wf-a\tgit-worktree\t'"$WORK/ws-root/wf-a"$'\t[0-9a-f]\{40\}\tready\tticket-101\twf/wf-a\t' "$WORK/workspaces.tsv" \
    && ok "registry row carries provider/id/path/40-char base/ready/purpose/branch-detail" \
    || bad "registry row wrong: $(cat "$WORK/workspaces.tsv")"
head_sha="$(git -C "$FIX" rev-parse HEAD)"
grep -q "$head_sha" "$WORK/workspaces.tsv" && ok "default base is canonical HEAD" || bad "base not HEAD"

echo "2. creation does not dirty the canonical checkout"
[ -z "$(git -C "$FIX" status --porcelain)" ] && ok "canonical status clean after create" || bad "canonical dirtied: $(git -C "$FIX" status --porcelain)"

echo "3. writable workers never share uncommitted filesystem state"
ws create --purpose ticket-102 --id wf-b >/dev/null 2>&1
pa="$(bash "$WORKSPACE" path wf-a)"
pb="$(bash "$WORKSPACE" path wf-b)"
[ "$pa" != "$pb" ] && ok "distinct workspace paths" || bad "shared path $pa"
echo "secret-a" > "$pa/marker.txt"
[ ! -f "$pb/marker.txt" ] && ok "sibling workspace isolated" || bad "state leaked to sibling"
[ ! -f "$FIX/marker.txt" ] && ok "canonical isolated from worker writes" || bad "state leaked to canonical"
[ -z "$(git -C "$FIX" status --porcelain)" ] && ok "canonical status still clean with dirty workers" || bad "canonical dirtied by worker content"

echo "4. explicit base SHA is recorded verbatim"
git -C "$FIX" commit -q --allow-empty -m second
second="$(git -C "$FIX" rev-parse HEAD)"
first="$(git -C "$FIX" rev-parse HEAD~1)"
ws create --purpose ticket-103 --id wf-c --base "$first" >/dev/null 2>&1
bash "$WORKSPACE" inspect wf-c | grep -q "base_sha=$first" && ok "explicit base recorded" || bad "base not recorded"

echo "5. provider-specific metadata stays behind the seam"
bash "$WORKSPACE" inspect wf-a | grep -q "^detail=wf/wf-a$" && ok "branch identity in detail field" || bad "detail missing branch"
bash "$WORKSPACE" inspect wf-a | grep -Eq "^(provider|workspace_id|workspace_path|base_sha|lifecycle_state|purpose|detail)=" \
    && ok "inspect exposes neutral keys" || bad "inspect shape wrong"
if grep -v '^#' "$WORKER" | grep -Eq 'WAYFINDER_WORKSPACE_PROVIDER|workspaces\.tsv'; then
    bad "worker seam branches on provider state"
else ok "worker seam treats workspaces as opaque dirs"; fi
if grep -v '^#' "$CHIEF" | grep -Eq 'WAYFINDER_WORKSPACE_PROVIDER|workspaces\.tsv'; then
    bad "chief branches on provider state"
else ok "chief stays provider-agnostic (dir-exists gate only)"; fi

echo "6. chief workspace-base convention maps to provider ids"
mkdir -p "$WORK/base"
WAYFINDER_WORKSPACE_ROOT="$WORK/base" bash "$WORKSPACE" create --purpose ticket-205 --id wf-205 >/dev/null 2>&1
[ -d "$WORK/base/wf-205" ] && ok "provider dir satisfies chief --workspace-base/wf-<ticket> layout" || bad "chief layout not satisfied"
WAYFINDER_WORKSPACE_ROOT="$WORK/base" bash "$WORKSPACE" cleanup wf-205 >/dev/null 2>&1 \
    && ok "chief-layout workspace cleans up" || bad "cleanup failed: $(cat "$WORK/err")"
export WAYFINDER_WORKSPACE_ROOT="$WORK/ws-root"

echo "7. ids and providers are validated; unknown workspaces refused"
run() { local rc=0; bash "$WORKSPACE" "$@" >"$WORK/out" 2>"$WORK/err" || rc=$?; printf "%s" "$rc" >"$WORK/rc"; }
rc_of() { cat "$WORK/rc"; }
run create --purpose x --id 'Bad; rm -rf' --root "$WORK/ws-root"; [ "$(rc_of)" -ne 0 ] && ok "injection id refused" || bad "injection id accepted"
run create --purpose x --id wf-a --root "$WORK/ws-root"; [ "$(rc_of)" -ne 0 ] && ok "duplicate id refused" || bad "duplicate id accepted"
run create --purpose x --id wf-zzz --provider rift --root "$WORK/ws-root"; [ "$(rc_of)" -ne 0 ] && ok "unknown provider refused" || bad "unknown provider accepted"
run inspect wf-nope; [ "$(rc_of)" -ne 0 ] && ok "inspect of unknown refused" || bad "inspect of unknown accepted"
run path wf-nope; [ "$(rc_of)" -ne 0 ] && ok "path of unknown refused" || bad "path of unknown accepted"
run cleanup wf-nope; [ "$(rc_of)" -ne 0 ] && ok "cleanup of unknown refused" || bad "cleanup of unknown accepted"

echo "8. cleanup is deterministic for dirty workspaces"
pd="$(bash "$WORKSPACE" path wf-b)"
echo "dirty" > "$pd/seed.txt"
echo "untracked" > "$pd/extra.txt"
run cleanup wf-b; [ "$(rc_of)" -eq 0 ] && ok "dirty workspace cleaned without extra flags" || bad "dirty cleanup failed: $(cat "$WORK/err")"
[ ! -e "$pd" ] && ok "workspace path removed" || bad "path survived cleanup"
git -C "$FIX" rev-parse --verify --quiet refs/heads/wf/wf-b >/dev/null 2>&1 \
    && bad "branch survived cleanup" || ok "provider branch removed"
[ -z "$(reg_row wf-b)" ] && ok "registry row removed" || bad "row survived cleanup"

echo "9. reconcile marks missing workspaces gone and reports unmanaged"
ws create --purpose ticket-104 --id wf-d >/dev/null 2>&1
pd_d="$(bash "$WORKSPACE" path wf-d)"
rm -rf "$pd_d"
bash "$WORKSPACE" reconcile >"$WORK/out" 2>"$WORK/err" || bad "reconcile failed: $(cat "$WORK/err")"
grep -q $'^wf-d\t.*\tgone\t' "$WORK/workspaces.tsv" && ok "missing workspace marked gone, row kept" || bad "gone handling wrong"
grep -q "reconciled:" "$WORK/out" && ok "reconcile summary printed" || bad "no summary"
bash "$WORKSPACE" cleanup wf-d >/dev/null 2>&1 && ok "gone workspace still cleans up deterministically" || bad "gone cleanup failed"
git -C "$FIX" worktree add --detach "$WORK/stray" HEAD >/dev/null 2>&1
bash "$WORKSPACE" reconcile >"$WORK/out" 2>&1
grep -q "unmanaged: $WORK/stray" "$WORK/out" && ok "unmanaged worktree reported, not adopted" || bad "unmanaged not reported"
git -C "$FIX" worktree remove --force "$WORK/stray" >/dev/null 2>&1 || true

echo "10. leaf roles cannot orchestrate but stay observable"
if WAYFINDER_ROLE=ticket bash "$WORKSPACE" create --purpose x --id wf-leaf --root "$WORK/ws-root" >"$WORK/out" 2>"$WORK/err"; then
    bad "leaf create accepted"
elif grep -q "chief" "$WORK/err"; then ok "leaf create refused with chief pointer"; else bad "leaf refusal unclear: $(cat "$WORK/err")"; fi
if WAYFINDER_ROLE=helper bash "$WORKSPACE" cleanup wf-a >"$WORK/out" 2>"$WORK/err"; then
    bad "leaf cleanup accepted"
else ok "leaf cleanup refused"; fi
if WAYFINDER_ROLE=ticket bash "$WORKSPACE" reconcile >"$WORK/out" 2>"$WORK/err"; then
    bad "leaf reconcile accepted"
else ok "leaf reconcile refused"; fi
WAYFINDER_ROLE=ticket bash "$WORKSPACE" inspect wf-a >/dev/null 2>&1 && ok "leaf inspect stays observable" || bad "leaf inspect blocked"
WAYFINDER_ROLE=bug-scout bash "$WORKSPACE" path wf-a >/dev/null 2>&1 && ok "leaf path stays observable" || bad "leaf path blocked"
WAYFINDER_ROLE=maintenance bash "$WORKSPACE" list >/dev/null 2>&1 && ok "leaf list stays observable" || bad "leaf list blocked"

echo "11. no integration verbs and no Rift/Lane/OpenCode dependency"
if grep -v '^#' "$WORKSPACE" | grep -Ev '\^\{commit\}' | grep -w -Eq 'merge|cherry-pick|push|pull|rebase|stash'; then
    bad "provider performs Git integration"
elif grep -v '^#' "$WORKSPACE" | grep -Eq 'git -C[^#]* commit( |$)'; then
    bad "provider commits"
else ok "provider never integrates (branch/worktree verbs only)"; fi
if grep -vi '^#' "$WORKSPACE" | grep -Eq 'lane|opencode'; then
    bad "provider depends on Lane/OpenCode"
else ok "no Lane/OpenCode dependency"; fi
if grep -vi '^#' "$WORKSPACE" | grep -Ei 'rift' | grep -Ev 'cow|RIFT_BIN' | grep -q .; then
    bad "snapshot-tool reference outside cow provider: $(grep -vi '^#' "$WORKSPACE" | grep -Ei 'rift' | grep -Ev 'cow|RIFT_BIN' | head -n 3)"
else ok "snapshot tool isolated to cow provider"; fi
if grep -v '^#' "$WORKSPACE" | grep -Eq 'worktree (add|remove|list|prune)'; then
    ok "git-worktree verbs isolated in the provider"
else bad "expected git worktree verbs missing"; fi

echo "12. review-hardening: roots, flags, promotion, guards, fail-closed reconcile"
run create --purpose x --id wf-tab --root "$(printf 'a\tb')"; [ "$(rc_of)" -ne 0 ] && ok "tab-in-root rejected" || bad "tab-in-root accepted"
run create --purpose x --id wf-e1 --root "$WORK/ws-root" --base; [ "$(rc_of)" -eq 2 ] && ok "missing --base value is misuse (2)" || bad "missing --base value exited $(rc_of)"
run cleanup wf-a --repo; [ "$(rc_of)" -eq 2 ] && ok "missing --repo value is misuse (2)" || bad "missing --repo value exited $(rc_of)"
run inspect; [ "$(rc_of)" -eq 2 ] && ok "inspect without id is misuse (2)" || bad "inspect without id exited $(rc_of)"
run cleanup Bad_Id; [ "$(rc_of)" -eq 2 ] && ok "cleanup message path uses the id (exit 2)" || bad "cleanup Bad_Id exited $(rc_of)"
(cd "$WORK" && run create --purpose rel --id wf-rel --root rel-root); [ "$(rc_of)" -eq 0 ] && ok "relative root accepted" || bad "relative root failed: $(cat "$WORK/err")"
bash "$WORKSPACE" inspect wf-rel | grep -q "workspace_path=$WORK/rel-root/wf-rel" \
    && ok "relative root normalized to absolute" || bad "root not normalized: $(bash "$WORKSPACE" inspect wf-rel | grep workspace_path)"
bash "$WORKSPACE" reconcile >/dev/null 2>&1
bash "$WORKSPACE" inspect wf-rel | grep -q "lifecycle_state=ready" \
    && ok "normalized workspace survives reconcile" || bad "normalized workspace marked gone"
bash "$WORKSPACE" cleanup wf-rel >/dev/null 2>&1 && ok "normalized workspace cleans up" || bad "normalized cleanup failed"
ws create --purpose promote --id wf-prom >/dev/null 2>&1
awk -F'\t' -v now="$(date '+%F %T')" 'BEGIN { OFS="\t" } $1 == "wf-prom" { $5 = "creating"; $9 = now } { print }' \
    "$WORK/workspaces.tsv" > "$WORK/tsv-tmp" && mv "$WORK/tsv-tmp" "$WORK/workspaces.tsv"
bash "$WORKSPACE" reconcile >/dev/null 2>&1
bash "$WORKSPACE" inspect wf-prom | grep -q "lifecycle_state=ready" \
    && ok "interrupted creating adopted to ready" || bad "creating not promoted"
bash "$WORKSPACE" cleanup wf-prom >/dev/null 2>&1 || bad "promoted cleanup failed"
ws create --purpose corrupt --id wf-corr >/dev/null 2>&1
git -C "$FIX" branch keepme HEAD >/dev/null 2>&1
awk -F'\t' -v now="$(date '+%F %T')" 'BEGIN { OFS="\t" } $1 == "wf-corr" { $7 = "keepme"; $9 = now } { print }' \
    "$WORK/workspaces.tsv" > "$WORK/tsv-tmp" && mv "$WORK/tsv-tmp" "$WORK/workspaces.tsv"
bash "$WORKSPACE" cleanup wf-corr >"$WORK/out" 2>"$WORK/err" || bad "corrupt-detail cleanup failed"
grep -q "unexpected detail" "$WORK/err" && ok "corrupt detail warned, branch delete skipped" || bad "no detail warning"
git -C "$FIX" rev-parse --verify --quiet refs/heads/keepme >/dev/null 2>&1 \
    && ok "unrelated branch survived corrupt-detail cleanup" || bad "unrelated branch deleted"
git -C "$FIX" branch -D keepme >/dev/null 2>&1 || true
FIX2="$(mkfixture repo2)"
env -u WAYFINDER_WORKSPACE_REGISTRY bash "$WORKSPACE" create --repo "$FIX2" --purpose x --id wf-r2 --root "$WORK/r2" >/dev/null 2>&1 \
    || bad "create --repo failed"
env -u WAYFINDER_WORKSPACE_REGISTRY bash "$WORKSPACE" inspect wf-r2 --repo "$FIX2" >/dev/null 2>&1 \
    && ok "--repo readers follow the canonical checkout" || bad "--repo inspect failed"
env -u WAYFINDER_WORKSPACE_REGISTRY bash "$WORKSPACE" inspect wf-r2 >/dev/null 2>&1 \
    && bad "--repo-less inspect leaked across checkouts" || ok "registries stay per-checkout"
env -u WAYFINDER_WORKSPACE_REGISTRY bash "$WORKSPACE" cleanup wf-r2 --repo "$FIX2" >/dev/null 2>&1 \
    || bad "cleanup --repo failed"
mkdir -p "$WORK/fakebin"
cat >"$WORK/fakebin/git" <<EOF
#!/usr/bin/env bash
if printf '%s ' "\$@" | grep -q "worktree list"; then
    printf 'simulated git failure\n' >&2; exit 1
fi
exec /usr/bin/git "\$@"
EOF
chmod +x "$WORK/fakebin/git"
ws create --purpose failclosed --id wf-fc >/dev/null 2>&1
PATH="$WORK/fakebin:$PATH" run reconcile; [ "$(rc_of)" -ne 0 ] && ok "worktree-list failure fails closed" || bad "reconcile swallowed git failure"
bash "$WORKSPACE" inspect wf-fc | grep -q "lifecycle_state=ready" \
    && ok "no mass-gone on git failure" || bad "rows marked gone on git failure"
bash "$WORKSPACE" cleanup wf-fc >/dev/null 2>&1 || bad "fail-closed workspace cleanup failed"

echo "13. cow provider requires a seed; live round-trip when a seed is configured"
(unset WAYFINDER_COW_SEED; run create --purpose x --id wf-cownoseed --provider cow --root "$WORK/ws-root")
[ "$(rc_of)" -ne 0 ] && ok "cow without seed refused" || bad "cow without seed accepted"
grep -q "WAYFINDER_COW_SEED" "$WORK/err" && ok "refusal names the missing seed env" || bad "refusal unclear: $(cat "$WORK/err")"
if [ -n "${WAYFINDER_COW_TEST_SEED:-}" ] && command -v "${COW_RIFT_BIN:-rift}" >/dev/null 2>&1; then
    export WAYFINDER_COW_SEED="$WAYFINDER_COW_TEST_SEED"
    seed_head="$(git -C "$WAYFINDER_COW_TEST_SEED" rev-parse HEAD)"
    run create --repo "$WAYFINDER_COW_TEST_SEED" --purpose ticket-900 --id wf-cowlive --provider cow --base "$seed_head" --root "$WORK/ws-root"
    [ "$(rc_of)" -eq 0 ] && ok "cow live create succeeded" || bad "cow live create failed: $(cat "$WORK/err")"
    cow_path="$(bash "$WORKSPACE" path wf-cowlive --repo "$WAYFINDER_COW_TEST_SEED" 2>/dev/null)"
    [ -n "$cow_path" ] && [ -d "$cow_path" ] && ok "cow snapshot path exists: $cow_path" || bad "cow path missing"
    [ "$(git -C "$cow_path" rev-parse HEAD 2>/dev/null)" = "$seed_head" ] && ok "cow snapshot at requested base" || bad "cow base mismatch"
    echo "cow-secret" > "$cow_path/cow-marker.txt"
    [ ! -f "$WAYFINDER_COW_TEST_SEED/cow-marker.txt" ] && ok "cow snapshot isolated from seed" || bad "cow write leaked to seed"
    run cleanup wf-cowlive --repo "$WAYFINDER_COW_TEST_SEED"
    [ "$(rc_of)" -eq 0 ] && ok "cow cleanup succeeded" || bad "cow cleanup failed: $(cat "$WORK/err")"
    [ ! -e "$cow_path" ] && ok "cow snapshot path removed" || bad "cow path survived cleanup"
    [ -z "$(reg_row wf-cowlive)" ] && ok "cow registry row removed" || bad "cow row survived cleanup"
    unset WAYFINDER_COW_SEED
else
    ok "cow live round-trip skipped (set WAYFINDER_COW_TEST_SEED with a rift binary present)"
fi

echo
if [ $fail -eq 0 ]; then echo "workspace-contract: OK"; else echo "workspace-contract: FAILURES PRESENT"; exit 1; fi
