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

echo "5. transient provider.invalid-output nudges instead of pausing"
contains "$script" 'provider.invalid-output:*' "transient case arm present"
contains "$script" 'last_err_id' "per-message dedupe variable present"

# Behavioral: a session whose last assistant turn ended finish=error with
# provider.invalid-output must receive the NUDGE prompt and keep being supervised
# (no chain-paused exit). Run under timeout — surviving to the kill signal IS the pass.
stubdir="$(mktemp -d)"
prompts="$stubdir/prompts"
cat > "$stubdir/opencode2" <<STUB
#!/usr/bin/env bash
cmd="\$*"
case "\$cmd" in
  *"get /api/session/ses_errtest"*message*) cat "$stubdir/errmsg.json"; exit 0 ;;
  *"get /api/session/ses_errtest"*) echo '{"data":{"id":"ses_errtest"}}'; exit 0 ;;
  *"get /api/session/active"*) echo '{"data":{"ses_errtest":{"type":"assistant"}}}'; exit 0 ;;
  *"post /api/session/ses_errtest/prompt"*) echo nudged >> "$prompts"; exit 0 ;;
esac
exit 1
STUB
chmod +x "$stubdir/opencode2"

worktree="$(mktemp -d)"
mkdir -p "$worktree/.wayfinder/handoffs" "$worktree/scripts"
cp "$script" "$worktree/scripts/wayfinder-loop.sh"
touch "$worktree/.wayfinder/handoffs/test-handoff.md"
fp="$(sha256sum "$worktree/.wayfinder/handoffs/test-handoff.md" | awk '{print $1}')"
printf 'last_doc=test-handoff.md\nsession_id=ses_errtest\npending_doc=\nretries=0\nseen_docs=test-handoff.md@%s\n' "$fp" \
  > "$worktree/.wayfinder-loop.state"
printf '{"data":[{"id":"msg_e1","type":"assistant","time":{"completed":1755861480},"finish":"error","error":{"type":"provider.invalid-output","message":"The provider response ended with an unknown finish reason."}}]}' \
  > "$stubdir/errmsg.json"

PATH="$stubdir:$PATH" OPENCODE_BIN="$stubdir/opencode2" WAYFINDER_TICK_SECS=1 WAYFINDER_STALL_SECS=9999 \
  timeout -s TERM 5 bash "$worktree/scripts/wayfinder-loop.sh" > "$worktree/transient.out" 2>&1
if [ -s "$prompts" ] && grep -q "transient provider error" "$worktree/transient.out" &&
   ! grep -q "chain paused" "$worktree/transient.out"; then
  ok "invalid-output sent recovery prompt and kept supervising (nudges: $(wc -l < "$prompts"))"
else
  bad "invalid-output did not nudge cleanly: $(head -3 "$worktree/transient.out")"
fi

echo "6. other assistant errors stay terminal"
printf '{"data":[{"id":"msg_e2","type":"assistant","time":{"completed":1755861480},"finish":"error","error":{"type":"auth.invalid-key","message":"bad key"}}]}' \
  > "$stubdir/errmsg.json"
rm -f "$prompts"
PATH="$stubdir:$PATH" OPENCODE_BIN="$stubdir/opencode2" WAYFINDER_TICK_SECS=1 \
  timeout -s TERM 10 bash "$worktree/scripts/wayfinder-loop.sh" > "$worktree/terminal.out" 2>&1
rc=$?
if [ $rc -eq 0 ] && grep -qF "ended with assistant error — chain paused" "$worktree/terminal.out"; then
  ok "non-transient error paused the chain immediately (exit $rc)"
else
  bad "terminal error did not pause (exit $rc): $(head -3 "$worktree/terminal.out")"
fi
rm -rf "$stubdir" "$worktree"

echo "7. real work after a nudge clears the fruitless-attempt budget"
contains "$script" 'tool_work_since' "progress predicate wired"
contains "$script" 'has_running_tool' "running-tool stall exemption present"

# Behavioral: stop-without-handoff gets nudged; when the session then completes a
# tool-call turn (real work), the budget clears (retries back to 0) instead of
# accumulating toward the pause cap.
stubdir="$(mktemp -d)"
prompts="$stubdir/prompts"
cat > "$stubdir/opencode2" <<STUB
#!/usr/bin/env bash
cmd="\$*"
case "\$cmd" in
  *"get /api/session/ses_worktest"*message*) cat "$stubdir/msg.json"; exit 0 ;;
  *"get /api/session/ses_worktest"*) echo '{"data":{"id":"ses_worktest","tokens":{"input":10}}}'; exit 0 ;;
  *"get /api/session/active"*) echo '{"data":{"ses_worktest":{"type":"assistant"}}}'; exit 0 ;;
  *"post /api/session/ses_worktest/prompt"*) echo nudged >> "$prompts"; exit 0 ;;
esac
exit 1
STUB
chmod +x "$stubdir/opencode2"

worktree="$(mktemp -d)"
mkdir -p "$worktree/.wayfinder/handoffs" "$worktree/scripts"
cp "$script" "$worktree/scripts/wayfinder-loop.sh"
touch "$worktree/.wayfinder/handoffs/test-handoff.md"
fp="$(sha256sum "$worktree/.wayfinder/handoffs/test-handoff.md" | awk '{print $1}')"
printf 'last_doc=test-handoff.md\nsession_id=ses_worktest\npending_doc=\nretries=1\nseen_docs=test-handoff.md@%s\n' "$fp" \
  > "$worktree/.wayfinder-loop.state"
printf '{"data":[{"id":"msg_a","type":"assistant","time":{"completed":1755861480},"finish":"stop"}]}' \
  > "$stubdir/msg.json"
printf '{"data":[{"id":"msg_a","type":"assistant","time":{"completed":1755861480},"finish":"stop"},{"id":"msg_b","type":"assistant","time":{"completed":1755861490},"finish":"tool-calls"}]}' \
  > "$stubdir/msg-work.json"
( sleep 3; cp "$stubdir/msg-work.json" "$stubdir/msg.json" ) &

PATH="$stubdir:$PATH" OPENCODE_BIN="$stubdir/opencode2" WAYFINDER_TICK_SECS=1 WAYFINDER_STALL_SECS=9999 \
  timeout -s TERM 7 bash "$worktree/scripts/wayfinder-loop.sh" > "$worktree/budget.out" 2>&1
if grep -q "worked since last recovery prompt" "$worktree/budget.out"; then
  ok "tool-call work after nudge cleared the budget"
else
  bad "progress reset never fired: $(head -5 "$worktree/budget.out")"
fi
if grep -q '^retries=0$' "$worktree/.wayfinder-loop.state"; then
  ok "retries reset to 0 after observed work"
else
  bad "state retries not cleared: $(grep retries "$worktree/.wayfinder-loop.state")"
fi
if [ "$(wc -l < "$prompts")" -ge 1 ] && ! grep -q "chain paused" "$worktree/budget.out"; then
  ok "session kept supervised across recovery ($(wc -l < "$prompts") nudges)"
else
  bad "chain paused or never nudged: $(head -3 "$worktree/budget.out")"
fi
wait || true
rm -rf "$stubdir" "$worktree"

echo
if [ $fail -eq 0 ]; then echo "ALL PASS"; else echo "FAILURES PRESENT"; exit 1; fi
