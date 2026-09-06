#!/usr/bin/env bash
# Guard test for map #329 ticket #336: the wayfinder-loop daemon never stages
# or commits anything. Handoff packets are gitignored runtime state under
# .wayfinder/handoffs/, session implementation commits belong to the session,
# and a dirty worktree pauses the chain with a notification instead of being
# swept into an auto-commit (`git add --all` + `docs(wayfinder): checkpoint`
# are banned forever).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
die() { printf 'test-wayfinder-loop-no-git-writes: %s\n' "$*" >&2; exit 1; }

# Recording git shim: every invocation lands in $GIT_CALLS, then the real git runs.
export GIT_CALLS="$WORK/git-calls"
cat > "$WORK/git" <<SHIM
#!/usr/bin/env bash
printf '%s\n' "\$*" >> "\$GIT_CALLS"
exec /usr/bin/git "\$@"
SHIM
chmod +x "$WORK/git"

# Stub opencode2: the daemon only needs the binary to exist for these paths.
cat > "$WORK/opencode2" <<'STUB'
#!/usr/bin/env bash
exit 0
STUB
chmod +x "$WORK/opencode2"
export PATH="$WORK:$PATH"

# Sandbox repo mirrors the canonical layout: tools/wayfinder/wayfinder-loop.sh
# + compat wrapper scripts/wayfinder-loop.sh + .wayfinder/handoffs/.
mkdir -p "$WORK/repo/tools/wayfinder" "$WORK/repo/scripts" "$WORK/repo/.wayfinder/handoffs"
cp "$ROOT/tools/wayfinder/wayfinder-loop.sh" "$WORK/repo/tools/wayfinder/"
cp "$ROOT/scripts/wayfinder-loop.sh" "$WORK/repo/scripts/"
cd "$WORK/repo"
git init -q
git -c user.email=t@t -c user.name=t commit -q --allow-empty -m init

# Both entrypoints must resolve to the same canonical implementation.
[ -f tools/wayfinder/wayfinder-loop.sh ] || die "canonical daemon missing in sandbox"
[ -f scripts/wayfinder-loop.sh ] || die "compat wrapper missing in sandbox"
grep -q 'tools/wayfinder/wayfinder-loop.sh' scripts/wayfinder-loop.sh \
  || die "compat wrapper does not delegate to the canonical implementation"

assert_no_git_writes() {
  if grep -qE '(^| )(add|commit|stash|restore|checkout|reset|clean|merge|rebase)( |$)' "$GIT_CALLS" 2>/dev/null; then
    die "daemon invoked a git-writing command: $(grep -E '(^| )(add|commit|stash|restore|checkout|reset|clean|merge|rebase)( |$)' "$GIT_CALLS" | head -3)"
  fi
}

# --- Case 1: clean tree, dry-run bootstrap — seeds state, spawns nothing -----
# Direct canonical invocation.
printf 'packet\n' > .wayfinder/handoffs/wayfinder-900-handoff.md
: > "$GIT_CALLS"
WAYFINDER_DRY_RUN=1 ./tools/wayfinder/wayfinder-loop.sh --bootstrap wayfinder-900-handoff.md \
  >> "$WORK/case1.log" 2>&1 ||
  die "dry-run bootstrap failed: $(tail -5 "$WORK/case1.log")"
grep -q "dry-run bootstrap complete" "$WORK/case1.log" || die "bootstrap did not reach dry-run completion"
[ -f .wayfinder-loop.state ] || die "no runtime state written (crash recovery broken)"
assert_no_git_writes

# --- Case 1b: compat wrapper reaches the same dry-run endpoint -----
rm -f .wayfinder-loop.state
: > "$GIT_CALLS"
WAYFINDER_DRY_RUN=1 ./scripts/wayfinder-loop.sh --bootstrap wayfinder-900-handoff.md \
  >> "$WORK/case1b.log" 2>&1 ||
  die "wrapper dry-run bootstrap failed: $(tail -5 "$WORK/case1b.log")"
grep -q "dry-run bootstrap complete" "$WORK/case1b.log" || die "wrapper bootstrap did not reach dry-run completion"
[ -f .wayfinder-loop.state ] || die "wrapper run wrote no runtime state"
assert_no_git_writes

# --- Case 2: dirty tree, live spawn path — must pause+notify, never commit ---
# Exercised via the compat wrapper (the live daemon's stable launcher).
printf 'leftover\n' > untracked-leftover.txt
: > "$GIT_CALLS"
# timeout kills the paused wait loop; exit code 124 is the expected shape.
timeout 8 env WAYFINDER_NTFY_TOPIC="" ./scripts/wayfinder-loop.sh --bootstrap wayfinder-900-handoff.md \
  >> "$WORK/case2.log" 2>&1 && die "dirty-tree bootstrap exited 0 while spawn was paused" || true
grep -q "spawn paused" "$WORK/case2.log" || die "dirty tree did not pause the chain: $(tail -5 "$WORK/case2.log")"
assert_no_git_writes

echo "test-wayfinder-loop-no-git-writes: OK"
