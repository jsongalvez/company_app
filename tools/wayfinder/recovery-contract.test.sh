#!/usr/bin/env bash
# recovery-contract.test.sh — focused checks for the #355 wayfinder lifecycle contract.
# Structural assertions on tools/wayfinder/wayfinder-loop.sh (one canonical prompt, all three
# in-place paths use it, --retry refuses a live session) plus behavioral runs of the
# --retry guard, the transient-error nudge, the progress-budget reset, and the #657
# exit-wait outage-hold against stubbed opencode binaries. No Gradle/DB/network.
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
script="$here/wayfinder-loop.sh"
root="$(cd "$here/../.." && pwd)"
wrapper="$root/scripts/wayfinder-loop.sh"
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
  "compact pointer packet" \
  "Wedge self-heal" \
  "wayfinder-*-handoff.md" ; do
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
mkdir -p "$worktree/.wayfinder/handoffs" "$worktree/tools/wayfinder"
cp "$script" "$worktree/tools/wayfinder/wayfinder-loop.sh"
printf 'last_doc=test-handoff.md\nsession_id=ses_livetest\npending_doc=\nretries=2\nseen_docs=test-handoff.md@deadbeef\n' > "$worktree/.wayfinder-loop.state"
touch "$worktree/.wayfinder/handoffs/test-handoff.md"
PATH="$stubdir:$PATH" OPENCODE_BIN="$stubdir/opencode2" bash "$worktree/tools/wayfinder/wayfinder-loop.sh" --retry > "$worktree/retry.out" 2>&1
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
mkdir -p "$worktree/.wayfinder/handoffs" "$worktree/tools/wayfinder"
cp "$script" "$worktree/tools/wayfinder/wayfinder-loop.sh"
touch "$worktree/.wayfinder/handoffs/test-handoff.md"
fp="$(sha256sum "$worktree/.wayfinder/handoffs/test-handoff.md" | awk '{print $1}')"
printf 'last_doc=test-handoff.md\nsession_id=ses_errtest\npending_doc=\nretries=0\nseen_docs=test-handoff.md@%s\n' "$fp" \
  > "$worktree/.wayfinder-loop.state"
printf '{"data":[{"id":"msg_e1","type":"assistant","time":{"completed":1755861480},"finish":"error","error":{"type":"provider.invalid-output","message":"The provider response ended with an unknown finish reason."}}]}' \
  > "$stubdir/errmsg.json"

PATH="$stubdir:$PATH" OPENCODE_BIN="$stubdir/opencode2" WAYFINDER_TICK_SECS=1 WAYFINDER_STALL_SECS=9999 \
  timeout -s TERM 5 bash "$worktree/tools/wayfinder/wayfinder-loop.sh" > "$worktree/transient.out" 2>&1
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
  timeout -s TERM 10 bash "$worktree/tools/wayfinder/wayfinder-loop.sh" > "$worktree/terminal.out" 2>&1
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
mkdir -p "$worktree/.wayfinder/handoffs" "$worktree/tools/wayfinder"
cp "$script" "$worktree/tools/wayfinder/wayfinder-loop.sh"
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
  timeout -s TERM 7 bash "$worktree/tools/wayfinder/wayfinder-loop.sh" > "$worktree/budget.out" 2>&1
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

echo "8. compat wrapper delegates to the canonical implementation"
if [ -f "$wrapper" ] && grep -q 'tools/wayfinder/wayfinder-loop.sh' "$wrapper" \
  && grep -q 'exec.*"\$@"' "$wrapper"; then
  ok "scripts/wayfinder-loop.sh delegates via exec with arg forwarding"
else
  bad "compat wrapper missing or not an exec delegate: $wrapper"
fi
# Behavioral: the wrapper reaches the same --retry guard without its own logic.
wstub="$(mktemp -d)"
cat > "$wstub/opencode2" <<STUB
#!/usr/bin/env bash
cmd="\$*"
if [[ "\$cmd" == *"get /api/session/active"* ]]; then echo '{"data":{"ses_wraptest":{"type":"user"}}}'; exit 0; fi
if [[ "\$cmd" == *"get /api/session/ses_wraptest"* ]]; then echo '{"data":{"id":"ses_wraptest"}}'; exit 0; fi
exit 1
STUB
chmod +x "$wstub/opencode2"
wwork="$(mktemp -d)"
mkdir -p "$wwork/.wayfinder/handoffs" "$wwork/tools/wayfinder" "$wwork/scripts"
cp "$script" "$wwork/tools/wayfinder/wayfinder-loop.sh"
cp "$wrapper" "$wwork/scripts/wayfinder-loop.sh"
printf 'last_doc=test-handoff.md\nsession_id=ses_wraptest\npending_doc=\nretries=2\nseen_docs=test-handoff.md@deadbeef\n' > "$wwork/.wayfinder-loop.state"
touch "$wwork/.wayfinder/handoffs/test-handoff.md"
if PATH="$wstub:$PATH" OPENCODE_BIN="$wstub/opencode2" bash "$wwork/scripts/wayfinder-loop.sh" --retry > "$wwork/wrap.out" 2>&1; then
  bad "wrapper --retry did not refuse a live session: $(head -3 "$wwork/wrap.out")"
elif grep -q "still exists" "$wwork/wrap.out"; then
  ok "wrapper --retry refused a live session like the canonical path"
else
  bad "wrapper --retry failed differently: $(head -3 "$wwork/wrap.out")"
fi
rm -rf "$wstub" "$wwork"

echo "9. exit-wait holds on API outage, still confirms a real exit (#657)"
contains "$script" 'EXIT_CONFIRM_TICKS' "confirmation-tick config present"
contains "$script" 'holding, not exiting' "outage-hold log present"

# 9a behavioral: /active UNREACHABLE (rate-limit class) while a successor packet
# waits must never read as session exit — no completion log, no fresh spawn.
stubdir="$(mktemp -d)"
cat > "$stubdir/opencode2" <<STUB
#!/usr/bin/env bash
exit 1
STUB
chmod +x "$stubdir/opencode2"

worktree="$(mktemp -d)"
mkdir -p "$worktree/.wayfinder/handoffs" "$worktree/tools/wayfinder"
cp "$script" "$worktree/tools/wayfinder/wayfinder-loop.sh"
touch "$worktree/.wayfinder/handoffs/wayfinder-test-handoff.md" "$worktree/.wayfinder/handoffs/wayfinder-second-handoff.md"
fp="$(sha256sum "$worktree/.wayfinder/handoffs/wayfinder-test-handoff.md" | awk '{print $1}')"
printf 'last_doc=wayfinder-test-handoff.md\nsession_id=ses_exitwait\npending_doc=\nretries=0\nseen_docs=wayfinder-test-handoff.md@%s\n' "$fp" \
  > "$worktree/.wayfinder-loop.state"

PATH="$stubdir:$PATH" OPENCODE_BIN="$stubdir/opencode2" WAYFINDER_TICK_SECS=1 WAYFINDER_STALL_SECS=9999 \
  timeout -s TERM 6 bash "$worktree/tools/wayfinder/wayfinder-loop.sh" > "$worktree/hold.out" 2>&1
if grep -q "holding, not exiting" "$worktree/hold.out" &&
   ! grep -q "completed; next handoff" "$worktree/hold.out" &&
   ! grep -q "created ses_" "$worktree/hold.out"; then
  ok "sustained /active outage held the exit-wait with no spawn"
else
  bad "outage ended the wait: $(head -3 "$worktree/hold.out")"
fi

# 9b behavioral: /active reachable with the session persistently absent must
# still confirm the exit (after consecutive ticks) and spawn the pending doc.
cat > "$stubdir/opencode2" <<STUB
#!/usr/bin/env bash
cmd="\$*"
case "\$cmd" in
  *"post /api/session/"*"/prompt"*) exit 0 ;;
  *"post /api/session"*) echo '{"data":{"id":"ses_new1"}}'; exit 0 ;;
  *"get /api/session/active"*) echo '{"data":{}}'; exit 0 ;;
  *"get /api/session/"*"message"*) echo '{"data":[{"id":"m1","type":"assistant","time":{},"finish":"tool-calls"}]}'; exit 0 ;;
  *"get /api/session/"*"form"*) echo '{"data":[]}'; exit 0 ;;
  *"get /api/session/"*"permission"*) echo '{"data":[]}'; exit 0 ;;
  *"get /api/session/"*) echo '{"data":{"id":"ses_new1","tokens":{"input":10,"output":5,"reasoning":0},"time":{"updated":1755861480}}}'; exit 0 ;;
esac
exit 1
STUB
chmod +x "$stubdir/opencode2"
printf 'last_doc=wayfinder-test-handoff.md\nsession_id=ses_exitgone\npending_doc=\nretries=0\nseen_docs=wayfinder-test-handoff.md@%s\n' "$fp" \
  > "$worktree/.wayfinder-loop.state"

PATH="$stubdir:$PATH" OPENCODE_BIN="$stubdir/opencode2" WAYFINDER_TICK_SECS=1 WAYFINDER_STALL_SECS=9999 \
  timeout -s TERM 12 bash "$worktree/tools/wayfinder/wayfinder-loop.sh" > "$worktree/confirm.out" 2>&1
if grep -q "exit confirmed" "$worktree/confirm.out" &&
   grep -q "created ses_new1 for wayfinder-second-handoff.md" "$worktree/confirm.out"; then
  ok "consecutive absence confirmed the exit and spawned the pending doc"
else
  bad "confirmed exit did not proceed: $(head -5 "$worktree/confirm.out")"
fi
rm -rf "$stubdir" "$worktree"

echo "10. rate-limit/server-abort failures resume, never advance past (#664)"
contains "$script" 'is_transient_failure' "shared transient classifier present"
contains "$script" 'refusing to advance' "wait-phase terminal refusal present"

# 10a behavioral: a rate-limit error in normal supervision gets the NUDGE
# (like invalid-output), not a chain pause.
stubdir="$(mktemp -d)"
prompts="$stubdir/prompts"
cat > "$stubdir/opencode2" <<STUB
#!/usr/bin/env bash
cmd="\$*"
case "\$cmd" in
  *"get /api/session/ses_rltest"*message*) cat "$stubdir/errmsg.json"; exit 0 ;;
  *"get /api/session/ses_rltest"*) echo '{"data":{"id":"ses_rltest"}}'; exit 0 ;;
  *"get /api/session/active"*) echo '{"data":{"ses_rltest":{"type":"assistant"}}}'; exit 0 ;;
  *"post /api/session/ses_rltest/prompt"*) echo nudged >> "$prompts"; exit 0 ;;
esac
exit 1
STUB
chmod +x "$stubdir/opencode2"

worktree="$(mktemp -d)"
mkdir -p "$worktree/.wayfinder/handoffs" "$worktree/tools/wayfinder"
cp "$script" "$worktree/tools/wayfinder/wayfinder-loop.sh"
touch "$worktree/.wayfinder/handoffs/wayfinder-test-handoff.md"
fp="$(sha256sum "$worktree/.wayfinder/handoffs/wayfinder-test-handoff.md" | awk '{print $1}')"
printf 'last_doc=wayfinder-test-handoff.md\nsession_id=ses_rltest\npending_doc=\nretries=0\nseen_docs=wayfinder-test-handoff.md@%s\n' "$fp" \
  > "$worktree/.wayfinder-loop.state"
printf '{"data":[{"id":"msg_r1","type":"assistant","time":{"completed":1788881207},"finish":"error","error":{"type":"provider.rate-limit","message":"Error from provider (Console Go): Upstream request failed: [rate_limit_exceeded] Rate limit exceeded."}}]}' \
  > "$stubdir/errmsg.json"

PATH="$stubdir:$PATH" OPENCODE_BIN="$stubdir/opencode2" WAYFINDER_TICK_SECS=1 WAYFINDER_STALL_SECS=9999 \
  timeout -s TERM 5 bash "$worktree/tools/wayfinder/wayfinder-loop.sh" > "$worktree/ratelimit.out" 2>&1
if [ -s "$prompts" ] && grep -q "transient provider error" "$worktree/ratelimit.out" &&
   ! grep -q "chain paused" "$worktree/ratelimit.out"; then
  ok "rate-limit error sent recovery prompt and kept supervising"
else
  bad "rate-limit did not nudge cleanly: $(head -3 "$worktree/ratelimit.out")"
fi

# 10b behavioral: wait-phase with the worker absent from /active BUT failed
# transiently must nudge and hold — never log completion or spawn.
cat > "$stubdir/opencode2" <<STUB
#!/usr/bin/env bash
cmd="\$*"
case "\$cmd" in
  *"get /api/session/ses_rlwait"*message*) cat "$stubdir/errmsg.json"; exit 0 ;;
  *"get /api/session/active"*) echo '{"data":{}}'; exit 0 ;;
  *"post /api/session/ses_rlwait/prompt"*) echo nudged >> "$prompts"; exit 0 ;;
esac
exit 1
STUB
chmod +x "$stubdir/opencode2"
touch "$worktree/.wayfinder/handoffs/wayfinder-second-handoff.md"
printf 'last_doc=wayfinder-test-handoff.md\nsession_id=ses_rlwait\npending_doc=\nretries=0\nseen_docs=wayfinder-test-handoff.md@%s\n' "$fp" \
  > "$worktree/.wayfinder-loop.state"
rm -f "$prompts"

PATH="$stubdir:$PATH" OPENCODE_BIN="$stubdir/opencode2" WAYFINDER_TICK_SECS=1 WAYFINDER_STALL_SECS=9999 \
  timeout -s TERM 8 bash "$worktree/tools/wayfinder/wayfinder-loop.sh" > "$worktree/rlhold.out" 2>&1
if [ -s "$prompts" ] && grep -q "transient failure" "$worktree/rlhold.out" &&
   ! grep -q "completed; next handoff" "$worktree/rlhold.out" &&
   ! grep -q "created ses_" "$worktree/rlhold.out"; then
  ok "wait-phase rate-limit failure nudged and held with no advance"
else
  bad "wait-phase did not hold on transient failure: $(head -3 "$worktree/rlhold.out")"
fi

# 10c behavioral: wait-phase with a TERMINAL failure must pause, not spawn.
printf '{"data":[{"id":"msg_t1","type":"assistant","time":{"completed":1788881207},"finish":"error","error":{"type":"auth.invalid-key","message":"bad key"}}]}' \
  > "$stubdir/errmsg.json"
printf 'last_doc=wayfinder-test-handoff.md\nsession_id=ses_rlwait\npending_doc=\nretries=0\nseen_docs=wayfinder-test-handoff.md@%s\n' "$fp" \
  > "$worktree/.wayfinder-loop.state"

PATH="$stubdir:$PATH" OPENCODE_BIN="$stubdir/opencode2" WAYFINDER_TICK_SECS=1 WAYFINDER_STALL_SECS=9999 \
  timeout -s TERM 8 bash "$worktree/tools/wayfinder/wayfinder-loop.sh" > "$worktree/rlterm.out" 2>&1
rc=$?
if [ $rc -eq 0 ] && grep -q "refusing to advance" "$worktree/rlterm.out" &&
   ! grep -q "created ses_" "$worktree/rlterm.out"; then
  ok "wait-phase terminal failure paused without spawning (exit $rc)"
else
  bad "terminal failure advanced or errored (exit $rc): $(head -3 "$worktree/rlterm.out")"
fi
rm -rf "$stubdir" "$worktree"

echo
if [ $fail -eq 0 ]; then echo "ALL PASS"; else echo "FAILURES PRESENT"; exit 1; fi
