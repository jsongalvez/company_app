#!/usr/bin/env bash
# recovery-contract.test.sh — focused checks for the #355 wayfinder lifecycle contract.
# Structural assertions on scripts/wayfinder-loop.sh (one canonical prompt, all three
# in-place paths use it, --retry refuses a live session) plus one behavioral run of the
# --retry guard against a stubbed opencode binary. No Gradle/DB/network.
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
script="$here/../../scripts/wayfinder-loop.sh"
fail=0

ok()   { echo "  ok: $1"; }
bad()  { echo "  FAIL: $1"; fail=1; }

contains() { # file|var-content check via stdin-safe grep -F
  local haystack="$1" needle="$2" label="$3"
  if grep -qF -- "$needle" "$haystack"; then ok "$label"; else bad "$label"; fi
}

echo "1. canonical NUDGE carries the full recovery contract"
tmp="$(mktemp)"
# The NUDGE assignment spans multiple lines with internal blanks; it ends on the
# first line whose LAST character is the closing quote.
awk '/^NUDGE="/{f=1} f{print; if ($0 ~ /"$/) exit}' "$script" > "$tmp"
for phrase in \
  "automatic recovery message, not a user instruction" \
  "from receiving it repeatedly, that time, context, or execution budget is running out" \
  "refresh the mutable GitHub authority" \
  "all comments chronologically" \
  "GitHub state outranks stale handoff text" \
  "do not rerun session-start-only CI reconciliation" \
  "Do not claim or resolve another frontier ticket in this session" \
  "create required blocker, needs-info, ready-for-human, correctness, architecture, or follow-up issues" \
  "never ask the user or invoke the question tool" \
  "approaching the context limit or reaching a concrete blocker with no safe continuation" \
  "park unfinished changes with scripts/wayfinder-park.sh" \
  "compact pointer packet" ; do
  contains "$tmp" "$phrase" "NUDGE: $phrase"
done

echo "2. no independent manual-resume wording remains"
if grep -q 'Continue exactly where you left off' "$script"; then
  bad "old bespoke --resume prompt still present"
else ok "bespoke --resume prompt removed"; fi

echo "3. all three in-place paths post \$NUDGE"
nudge_uses="$(grep -c '\$NUDGE' "$script")"
[ "$nudge_uses" -ge 3 ] && ok "NUDGE referenced at $nudge_uses prompt sites (immediate-stop, stall resume, --resume)" || bad "expected >=3 NUDGE uses, found $nudge_uses"

echo "4. --retry guard: live recorded session blocks fresh spawn"
stubdir="$(mktemp -d)"
cat > "$stubdir/opencode2" <<STUB
#!/usr/bin/env bash
# stub api: session ses_livetest exists AND is active; anything else 404s
cmd="\$*"
case "\$cmd" in
  *"api get /api/session/ses_livetest"*"active"*) echo '{"data":{"ses_livetest":{}}}'; exit 0 ;;
esac
if [[ "\$cmd" == *"get /api/session/active"* ]]; then echo '{"data":{"ses_livetest":{"type":"user"}}}'; exit 0; fi
if [[ "\$cmd" == *"get /api/session/ses_livetest"* ]]; then echo '{"data":{"id":"ses_livetest"}}'; exit 0; fi
exit 1
STUB
chmod +x "$stubdir/opencode2"

worktree="$(mktemp -d)"
mkdir -p "$worktree/.wayfinder/handoffs" "$worktree/scripts"
cp "$script" "$worktree/scripts/wayfinder-loop.sh"
printf 'last_doc=test-handoff.md\nsession_id=ses_livetest\npending_doc=\nretries=2\nseen_docs=test-handoff.md@deadbeef\n' > "$worktree/.wayfinder-loop.state"
touch "$worktree/.wayfinder/handoffs/test-handoff.md"
PATH="$stubdir:$PATH" OPENCODE_BIN="$stubdir/opencode2" bash "$worktree/scripts/wayfinder-loop.sh" --retry > "$worktree/retry.out" 2>&1
rc=$?
if [ $rc -ne 0 ] && grep -q "still exists" "$worktree/retry.out"; then
  ok "--retry refused while recorded session is alive (exit $rc)"
else
  bad "--retry did not refuse a live session (exit $rc): $(head -3 "$worktree/retry.out")"
fi
rm -rf "$stubdir" "$worktree" "$tmp"

echo
if [ $fail -eq 0 ]; then echo "ALL PASS"; else echo "FAILURES PRESENT"; exit 1; fi
