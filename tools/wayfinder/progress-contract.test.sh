#!/usr/bin/env bash
# progress-contract.test.sh — contract for #578 chain-advancement supervision.
# Structural assertions on tools/wayfinder/wayfinder-loop.sh (progress state,
# dual-signal liveness, Park-Signal/Progress-Map conventions, frozen NUDGE)
# plus behavioral runs of strike-park, advancement reset, exit grace, wake,
# and poison classification against stubbed opencode2/gh binaries with
# mid-run fixture flips. No Gradle/DB/network.
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
script="$here/wayfinder-loop.sh"
fail=0

ok()   { echo "  ok: $1"; }
bad()  { echo "  FAIL: $1"; fail=1; }

contains() {
  local haystack="$1" needle="$2" label="$3"
  if grep -qF -- "$needle" "$haystack"; then ok "$label"; else bad "$label"; fi
}

echo "1. progress state round-trips through save/load with back-compat defaults"
for var in progress_map progress_fp progress_strikes progress_parked progress_gate progress_park_issue progress_park_class progress_gate_fp; do
  contains "$script" "$var=" "state field: $var"
done
contains "$script" 'Back-compat: states written before #578' "legacy-state defaults present"
echo "1b. review lockdowns: no poison fast-path, no marker attachment"
vfn="$(mktemp)"
awk '/^progress_verify_signal\(\)/{f=1} f{print; if (/^\}$/) exit}' "$script" > "$vfn"
if grep -q 'poison)' "$vfn"; then bad "verify_signal keeps a poison arm (spec: poison stays human-only)"; else ok "no poison arm in Park-Signal verification"; fi
rm -f "$vfn"
mfn="$(mktemp)"
awk '/^progress_park_issue\(\)/{f=1} f{print; if (/^\}$/) exit}' "$script" > "$mfn"
if grep -q 'sub_issues' "$mfn"; then
  bad "park marker attaches as native child (self-wake/frontier leak)"
else ok "park marker stays outside native machinery"; fi
rm -f "$mfn"

echo "2. dual-signal liveness replaces single-sample death reads"
contains "$script" 'session_gone()' "session_gone predicate present"
contains "$script" 'EXIT_GONE_TICKS="${WAYFINDER_EXIT_GONE_TICKS:-2}"' "exit grace default present"
contains "$script" 'if session_gone "$session_id"; then' "supervise liveness uses dual signal"
if grep -q 'while session_alive "\$session_id"' "$script"; then
  bad "old single-sample exit wait still present"
else ok "single-sample exit wait removed"; fi

echo "3. packet conventions are machine-readable and the NUDGE stays frozen"
contains "$script" 'ark-[Ss]ignal' "Park-Signal parsing present"
contains "$script" 'rogress-[Mm]ap' "Progress-Map parsing present"
tmp="$(mktemp)"
awk '/^NUDGE="/{f=1} f{print; if ($0 ~ /"$/) exit}' "$script" > "$tmp"
if grep -qE 'Park-Signal|Progress-Map|wayfinder-chain-parked' "$tmp"; then
  bad "canonical NUDGE gained packet-contract language"
else ok "NUDGE frozen (no packet-contract language)"; fi
rm -f "$tmp"

echo "4. park path writes no git commands (daemon never stages/commits)"
if grep -nE 'git (add|commit|stash)' "$script" | grep -viE 'status|rev-parse|remote|stash list' | grep -q .; then
  bad "git write commands present: $(grep -nE 'git (add|commit|stash)' "$script" | head -2)"
else ok "no git writes in daemon (status/rev-parse/remote only)"; fi

# --- shared stub builders -------------------------------------------------
# Writes $stubdir/{opencode2,gh} plus fixture files under $fix. The opencode2
# stub keeps a live-set ($fix/live, one id per line); sessions are created
# into it and removed by the test driver to simulate exits. Direct GETs
# answer for any ever-known id (production truth: historical records),
# so only live-set absence reads as death. The gh stub serves
# tracker fixtures from files the driver rewrites mid-run.
make_stubs() { # $1=stubdir $2=fix
  local stubdir="$1" fix="$2"
  cat > "$stubdir/opencode2" <<STUB
#!/usr/bin/env bash
cmd="\$*"
FIX="$fix"
case "\$cmd" in
  *"/prompt"*)
    echo prompt >> "$fix/PROMPTS"; echo '{}'; exit 0 ;;
  *"post /api/session --data"*)
    n=\$(( \$(wc -l < "$fix/SPAWNS" 2>/dev/null || echo 0) + 1 ))
    id="ses_new\$n"; echo "\$id" >> "$fix/live"; echo "\$id" >> "$fix/ever"; echo "\$id" >> "$fix/SPAWNS"
    echo "{\\"data\\":{\\"id\\":\\"\$id\\"}}"; exit 0 ;;
  *"get /api/session/active"*)
    ids=\$(jq -R . 2>/dev/null < "$fix/live" | jq -s 'map({(.): {"type":"assistant"}}) | add // {}' 2>/dev/null || echo '{}')
    echo "{\\"data\\":\$ids}"; exit 0 ;;
  *"get /api/session/"*"/message"*)
    echo '{"data":[{"id":"m1","type":"user","text":"idle"}]}'; exit 0 ;;
  *"get /api/session/"*"/form"*|*"get /api/session/"*"/permission"*)
    echo '{"data":[]}'; exit 0 ;;
  *"get /api/session/"*)
    id=\$(printf '%s' "\$cmd" | grep -o 'ses_[A-Za-z0-9]*' | head -1)
    # Production truth: direct GET answers for any session that ever existed
    # (historical records), dead or alive — death is live-set absence only.
    if grep -qx "\$id" "$fix/live" 2>/dev/null || grep -qx "\$id" "$fix/ever" 2>/dev/null || [ "\$id" = ses_t ]; then
      echo "{\\"data\\":{\\"id\\":\\"\$id\\",\\"tokens\\":{\\"input\\":10,\\"output\\":5,\\"reasoning\\":2},\\"time\\":{\\"updated\\":100}}}"
      exit 0
    fi
    exit 1 ;;
esac
exit 1
STUB
  cat > "$stubdir/gh" <<STUB
#!/usr/bin/env bash
cmd="\$*"
FIX="$fix"
jqfilter() { # apply the --jq filter minimally for the call sites used
  case "\$cmd" in
    *".state"*) jq -r '.state // ""' ;;
    *".id"*) jq -r '.id // ""' ;;
    *) cat ;;
  esac
}
case "\$cmd" in
  *"dependencies/blocked_by"*)
    n=\$(printf '%s' "\$cmd" | sed -n 's#.*/issues/\\([0-9][0-9]*\\).*#\\1#p')
    [ -f "$fix/deps_\$n.json" ] && cat "$fix/deps_\$n.json" || echo '[]'
    exit 0 ;;
  *"sub_issues"*)
    case "\$cmd" in *"page=1"*|*"page="*) ;; esac
    if [[ "\$cmd" == *"page=1"* ]]; then cat "$fix/subissues.json"; else echo '[]'; fi
    exit 0 ;;
  *"issues?state=all"*) cat "$fix/search.json" 2>/dev/null || echo '[]'; exit 0 ;;
  *"issue create"*)
    echo "https://github.com/o/r/issues/999" >> "$fix/creates.log"
    echo "https://github.com/o/r/issues/999"; exit 0 ;;
  *"issue comment"*|*"issue close"*|*"issue reopen"*)
    echo "\$cmd" >> "$fix/ghwrites.log"; exit 0 ;;
  *"api --method POST"*) echo '{}'; exit 0 ;;
  *"auth status"*) exit 0 ;;
  *"api repos/"*"/issues/"*)
    n=\$(printf '%s' "\$cmd" | sed -n 's#.*/issues/\\([0-9][0-9]*\\).*#\\1#p')
    if [ -f "$fix/issue_\$n.json" ]; then jqfilter < "$fix/issue_\$n.json"; exit 0; fi
    exit 1 ;;
esac
exit 1
STUB
  chmod +x "$stubdir/opencode2" "$stubdir/gh"
  touch "$fix/live" "$fix/ever" "$fix/SPAWNS" "$fix/PROMPTS" "$fix/creates.log" "$fix/ghwrites.log"
  printf '[]\n' > "$fix/search.json"
}

make_worktree() { # $1=workdir $2=packet-name — copies loop.sh, seeds packet+state
  local workdir="$1" packet="$2" fp
  mkdir -p "$workdir/.wayfinder/handoffs" "$workdir/tools/wayfinder"
  cp "$script" "$workdir/tools/wayfinder/wayfinder-loop.sh"
  printf '# Wayfinder Handoff — test packet\n\nNo claim.\n' > "$workdir/.wayfinder/handoffs/$packet"
  fp="$(sha256sum "$workdir/.wayfinder/handoffs/$packet" | awk '{print $1}')"
  printf 'last_doc=%s\nsession_id=ses_t\npending_doc=\nretries=0\nseen_docs=%s@%s\n' \
    "$packet" "$packet" "$fp" > "$workdir/.wayfinder-loop.state"
}

# cycle_once: simulate a session writing its handoff then exiting — kill and
# revise back-to-back so one daemon tick observes both together.
cycle_once() { # $1=workdir $2=fix $3=packet $4=rev-tag
  : > "$2/live"
  printf '\nrev %s\n' "$4" >> "$1/.wayfinder/handoffs/$3"
}

run_loop() { # $1=workdir $2=stubdir $3=fix $4=timeout-secs $5=extra-env... — runs daemon, returns log path
  local workdir="$1" stubdir="$2" fix="$3" secs="$4"; shift 4
  PATH="$stubdir:$PATH" OPENCODE_BIN="$stubdir/opencode2" WAYFINDER_GH_BIN="$stubdir/gh" \
    WAYFINDER_TICK_SECS=1 WAYFINDER_STALL_SECS=9999 WAYFINDER_EXIT_GONE_TICKS=2 \
    env "$@" timeout -s TERM "$secs" bash "$workdir/tools/wayfinder/wayfinder-loop.sh" \
    > "$workdir/loop.out" 2>&1
  printf '%s' "$workdir/loop.out"
}

starved_fixtures() { # $1=fix — 535/554 open+blocked; gates external (#532) and internal (#535)
  local fix="$1"
  cat > "$fix/subissues.json" <<'JSON'
[{"number":535,"state":"open"},{"number":554,"state":"open"}]
JSON
  cat > "$fix/issue_535.json" <<'JSON'
{"number":535,"state":"open","assignees":[],"labels":[{"name":"ready-for-agent"},{"name":"wayfinder:task"}],"issue_dependencies_summary":{"blocked_by":1}}
JSON
  cat > "$fix/issue_554.json" <<'JSON'
{"number":554,"state":"open","assignees":[],"labels":[{"name":"ready-for-agent"},{"name":"wayfinder:task"}],"issue_dependencies_summary":{"blocked_by":1}}
JSON
  printf '[{"number":532,"state":"open","title":"external gate"}]\n' > "$fix/deps_535.json"
  printf '[{"number":535,"state":"open","title":"internal gate"}]\n' > "$fix/deps_554.json"
  cat > "$fix/issue_532.json" <<'JSON'
{"number":532,"state":"open","assignees":[],"labels":[{"name":"wayfinder:task"}],"issue_dependencies_summary":{"blocked_by":0}}
JSON
}

echo "5. K consecutive no-advance sessions park a starved chain (no 4th spawn)"
stubdir="$(mktemp -d)"; fix="$(mktemp -d)"; worktree="$(mktemp -d)"
make_stubs "$stubdir" "$fix"; starved_fixtures "$fix"
make_worktree "$worktree" "wayfinder-533-park-handoff.md"
printf 'ses_t\n' > "$fix/live"
( for i in 1 2 3; do
    sleep 8
    cycle_once "$worktree" "$fix" "wayfinder-533-park-handoff.md" "$i"
  done ) &
out="$(run_loop "$worktree" "$stubdir" "$fix" 70 WAYFINDER_PROGRESS_STRIKES=3 WAYFINDER_POLL_SECS=2)"
if grep -q "chain parked (starved" "$out"; then ok "starved park logged"; else bad "no starved park: $(tail -3 "$out")"; fi
if [ "$(wc -l < "$fix/SPAWNS")" -eq 2 ]; then ok "completions spawned, parking one did not (2 spawns)"; else bad "spawn count $(wc -l < "$fix/SPAWNS"), expected 2"; fi
if grep -q "progress_parked=wayfinder-533-park-handoff.md" "$worktree/.wayfinder-loop.state"; then ok "park recorded in state"; else bad "park missing from state"; fi
if [ -s "$fix/creates.log" ]; then ok "park marker issue created"; else bad "no park marker created"; fi
wait || true

echo "6. tracker movement between sessions clears strikes (advancing chain never parks)"
stubdir="$(mktemp -d)"; fix="$(mktemp -d)"; worktree="$(mktemp -d)"
make_stubs "$stubdir" "$fix"; starved_fixtures "$fix"
make_worktree "$worktree" "wayfinder-533-park-handoff.md"
printf 'ses_t\n' > "$fix/live"
( sleep 8
  cycle_once "$worktree" "$fix" "wayfinder-533-park-handoff.md" 1
  sleep 8
  cycle_once "$worktree" "$fix" "wayfinder-533-park-handoff.md" 2
  sleep 8
  cat > "$fix/issue_554.json" <<'JSON'
{"number":554,"state":"closed","assignees":[],"labels":[{"name":"wayfinder:task"}],"issue_dependencies_summary":{"blocked_by":0}}
JSON
  cycle_once "$worktree" "$fix" "wayfinder-533-park-handoff.md" 3
  sleep 10 ) &
out="$(run_loop "$worktree" "$stubdir" "$fix" 70 WAYFINDER_PROGRESS_STRIKES=3 WAYFINDER_POLL_SECS=2)"
if grep -q "chain parked" "$out"; then bad "advancing chain parked"; else ok "advancing chain kept spawning"; fi
if grep -q '^progress_strikes=0$' "$worktree/.wayfinder-loop.state"; then ok "strikes cleared after movement"; else bad "strikes not cleared: $(grep progress_strikes "$worktree/.wayfinder-loop.state")"; fi
wait || true

echo "7. exit wait needs dual-signal grace (flapping session is not death)"
stubdir="$(mktemp -d)"; fix="$(mktemp -d)"; worktree="$(mktemp -d)"
make_stubs "$stubdir" "$fix"; starved_fixtures "$fix"
make_worktree "$worktree" "wayfinder-533-park-handoff.md"
printf 'ses_t\n' > "$fix/live"
( sleep 4
  printf '\nrev 1\n' >> "$worktree/.wayfinder/handoffs/wayfinder-533-park-handoff.md"
  : > "$fix/live"          # flap: gone from /active…
  sleep 0.5
  printf 'ses_t\n' > "$fix/live"   # …restored inside the grace window
  sleep 6
  : > "$fix/live" ) &       # then truly gone
out="$(run_loop "$worktree" "$stubdir" "$fix" 45 WAYFINDER_PROGRESS_STRIKES=9 WAYFINDER_POLL_SECS=2)"
if [ "$(grep -c "completed; next handoff" "$out")" -eq 1 ]; then ok "single completion after true exit"; else bad "completion count wrong: $(grep -c "completed; next handoff" "$out")"; fi
if grep -q "died without handoff" "$out"; then bad "flap declared death"; else ok "flap never declared death"; fi
if [ "$(wc -l < "$fix/SPAWNS")" -eq 1 ]; then ok "resume-supervise + 1 completion spawn"; else bad "spawn count $(wc -l < "$fix/SPAWNS"), expected 1"; fi
wait || true

echo "8. parked chain wakes when the tracker moves (gate close resumes)"
stubdir="$(mktemp -d)"; fix="$(mktemp -d)"; worktree="$(mktemp -d)"
make_stubs "$stubdir" "$fix"; starved_fixtures "$fix"
make_worktree "$worktree" "wayfinder-533-park-handoff.md"
: > "$fix/live"
# Seed parked state on top of the fresh state (empty session, current fp).
sed -i 's/^session_id=ses_t$/session_id=/' "$worktree/.wayfinder-loop.state"
cat >> "$worktree/.wayfinder-loop.state" <<'STATE'
progress_map=533
progress_fp=
progress_strikes=3
progress_parked=wayfinder-533-park-handoff.md
progress_gate=#532
progress_park_issue=
progress_park_class=starved
progress_gate_fp=
STATE
( sleep 5
  cat > "$fix/issue_535.json" <<'JSON'
{"number":535,"state":"closed","assignees":[],"labels":[{"name":"wayfinder:task"}],"issue_dependencies_summary":{"blocked_by":0}}
JSON
  sleep 8 ) &
out="$(run_loop "$worktree" "$stubdir" "$fix" 45 WAYFINDER_PROGRESS_STRIKES=3 WAYFINDER_POLL_SECS=2)"
if grep -q "chain resumed" "$out"; then ok "wake on tracker movement"; else bad "no wake: $(tail -3 "$out")"; fi
if [ "$(wc -l < "$fix/SPAWNS")" -ge 1 ]; then ok "spawn after wake"; else bad "no spawn after wake"; fi
if grep -q "progress_parked=$" "$worktree/.wayfinder-loop.state"; then ok "park cleared on wake"; else bad "park not cleared"; fi
wait || true

echo "8b. parked chain wakes when a named gate closes (gate-only change)"
stubdir="$(mktemp -d)"; fix="$(mktemp -d)"; worktree="$(mktemp -d)"
make_stubs "$stubdir" "$fix"; starved_fixtures "$fix"
make_worktree "$worktree" "wayfinder-533-park-handoff.md"
: > "$fix/live"
sed -i 's/^session_id=ses_t$/session_id=/' "$worktree/.wayfinder-loop.state"
cat >> "$worktree/.wayfinder-loop.state" <<'STATE'
progress_map=533
progress_fp=
progress_strikes=3
progress_parked=wayfinder-533-park-handoff.md
progress_gate=#532
progress_park_issue=
progress_park_class=starved
progress_gate_fp=
STATE
( sleep 6
  cat > "$fix/issue_532.json" <<'JSON'
{"number":532,"state":"closed","assignees":[],"labels":[{"name":"wayfinder:task"}],"issue_dependencies_summary":{"blocked_by":0}}
JSON
  sleep 8 ) &
out="$(run_loop "$worktree" "$stubdir" "$fix" 45 WAYFINDER_PROGRESS_STRIKES=3 WAYFINDER_POLL_SECS=2)"
if grep -q "gate changed" "$out"; then ok "wake on gate close"; else bad "no gate wake: $(tail -3 "$out")"; fi
if [ "$(wc -l < "$fix/SPAWNS")" -ge 1 ]; then ok "spawn after gate wake"; else bad "no spawn after gate wake"; fi
wait || true

echo "9. poison sleeps through tracker movement, wakes on a new packet only"
stubdir="$(mktemp -d)"; fix="$(mktemp -d)"; worktree="$(mktemp -d)"
make_stubs "$stubdir" "$fix"; starved_fixtures "$fix"
make_worktree "$worktree" "wayfinder-533-park-handoff.md"
: > "$fix/live"
sed -i 's/^session_id=ses_t$/session_id=/' "$worktree/.wayfinder-loop.state"
cat >> "$worktree/.wayfinder-loop.state" <<'STATE'
progress_map=533
progress_fp=
progress_strikes=2
progress_parked=wayfinder-533-park-handoff.md
progress_gate=
progress_park_issue=
progress_park_class=poison
progress_gate_fp=
STATE
( sleep 5
  cat > "$fix/issue_554.json" <<'JSON'
{"number":554,"state":"closed","assignees":[],"labels":[{"name":"wayfinder:task"}],"issue_dependencies_summary":{"blocked_by":0}}
JSON
  sleep 9
  [ -s "$fix/SPAWNS" ] && echo "SPAWN-LEAK" > "$fix/leak.flag" || true
  printf '# Wayfinder Handoff — manual advance\n' > "$worktree/.wayfinder/handoffs/wayfinder-533-manual-handoff.md"
  sleep 12 ) &
out="$(run_loop "$worktree" "$stubdir" "$fix" 55 WAYFINDER_PROGRESS_STRIKES=2 WAYFINDER_POLL_SECS=2)"
if [ -f "$fix/leak.flag" ]; then bad "poison woke on tracker movement"; else ok "poison slept through movement"; fi
if grep -q "new packet wayfinder-533-manual-handoff.md" "$out"; then ok "poison woke on new packet"; else bad "no manual wake: $(tail -3 "$out")"; fi
if [ "$(wc -l < "$fix/SPAWNS")" -eq 1 ]; then ok "exactly one post-wake spawn"; else bad "spawn count $(wc -l < "$fix/SPAWNS"), expected 1"; fi
wait || true

echo "10. empty frontier with no external gate parks as poison, not starved"
stubdir="$(mktemp -d)"; fix="$(mktemp -d)"; worktree="$(mktemp -d)"
make_stubs "$stubdir" "$fix"
cat > "$fix/subissues.json" <<'JSON'
[{"number":535,"state":"open"},{"number":554,"state":"open"}]
JSON
cat > "$fix/issue_535.json" <<'JSON'
{"number":535,"state":"open","assignees":[],"labels":[{"name":"wayfinder:task"}],"issue_dependencies_summary":{"blocked_by":1}}
JSON
cat > "$fix/issue_554.json" <<'JSON'
{"number":554,"state":"open","assignees":[],"labels":[{"name":"wayfinder:task"}],"issue_dependencies_summary":{"blocked_by":1}}
JSON
printf '[{"number":554,"state":"open","title":"internal"}]\n' > "$fix/deps_535.json"
printf '[{"number":535,"state":"open","title":"internal"}]\n' > "$fix/deps_554.json"
make_worktree "$worktree" "wayfinder-533-park-handoff.md"
printf 'ses_t\n' > "$fix/live"
( for i in 1 2; do
    sleep 8
    cycle_once "$worktree" "$fix" "wayfinder-533-park-handoff.md" "$i"
  done ) &
out="$(run_loop "$worktree" "$stubdir" "$fix" 60 WAYFINDER_PROGRESS_STRIKES=2 WAYFINDER_POLL_SECS=2)"
if grep -q "chain parked (poison" "$out"; then ok "poison park logged"; else bad "no poison park: $(tail -3 "$out")"; fi
wait || true

rm -rf "$stubdir" "$fix" "$worktree" 2>/dev/null || true

echo
if [ $fail -eq 0 ]; then echo "ALL PASS"; else echo "FAILURES PRESENT"; exit 1; fi
