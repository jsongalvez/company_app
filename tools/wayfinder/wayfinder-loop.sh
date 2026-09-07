#!/usr/bin/env bash
# wayfinder-loop.sh — unattended wayfinder chain driver for opencode2.
#
# Watches .wayfinder/handoffs/ for new wayfinder-*-handoff.md files (the chain's
# completion signal — gitignored runtime packets, never committed; map #329
# ticket #336), spawns a fresh 0-context opencode2 session that reads the
# newest packet and drives the next session from the canonical map, and
# notifies the human when the agent parks on a human decision or the chain
# breaks.
#
# Usage:
#   wayfinder-loop.sh --bootstrap <doc>   first start: seed with <doc>, spawn immediately
#   wayfinder-loop.sh                     resume from state file (restart / crash recovery)
#
# Env:
#   WAYFINDER_NTFY_TOPIC   ntfy.sh topic for phone push (unset = desktop only)
#   WAYFINDER_MODEL        provider/model for spawned sessions (unset = server default)
#   WAYFINDER_VARIANT      model variant (default max; xhigh for muse-spark reasoning)
#   WAYFINDER_POLL_SECS    doc poll interval (default 15)
#   WAYFINDER_TICK_SECS    session poll interval — form/permission/completion checks (default 5)
#   WAYFINDER_STALL_SECS   no-progress zombie threshold in seconds (default 540 = old WAIT_SECS×STALL_SLICES)
#   WAYFINDER_PROGRESS_STRIKES consecutive no-advance sessions before the chain parks (default 3; #578)
#   WAYFINDER_EXIT_GONE_TICKS consecutive dual-signal-gone ticks before a handoff wait ends (default 2; #578)
#   WAYFINDER_DRY_RUN      non-empty = log transitions, never spawn
#   WAYFINDER_LOCAL_CI_DIR  local-ci state directory (default logs/local-ci)
#   WAYFINDER_LOCAL_CI_SCRIPT local-ci launcher (default scripts/local-ci.sh)
#   WAYFINDER_GH_BIN       gh executable used for red-verdict tracker writes
#   WAYFINDER_GH_REPO      repository for tracker writes (default from origin)
#   WAYFINDER_MAP_ISSUE    map whose frontier the repair ticket blocks (default 533)
set -euo pipefail

REPO="$(cd "$(dirname "$0")/../.." && pwd)"
HANDOFF_DIR="$REPO/.wayfinder/handoffs"
STATE_FILE="$REPO/.wayfinder-loop.state"
LOG_FILE="$REPO/.wayfinder-loop.log"
[ -f "$REPO/.wayfinder-loop.env" ] && set -a && . "$REPO/.wayfinder-loop.env" && set +a
NTFY_TOPIC="${WAYFINDER_NTFY_TOPIC:-}"
POLL_SECS="${WAYFINDER_POLL_SECS:-15}"
TICK_SECS="${WAYFINDER_TICK_SECS:-5}"
STALL_SECS="${WAYFINDER_STALL_SECS:-540}"
LOCAL_CI_DIR="${WAYFINDER_LOCAL_CI_DIR:-$REPO/logs/local-ci}"
LOCAL_CI_SCRIPT="${WAYFINDER_LOCAL_CI_SCRIPT:-$REPO/scripts/local-ci.sh}"
GH_BIN="${WAYFINDER_GH_BIN:-$(command -v gh || true)}"
MAP_ISSUE="${WAYFINDER_MAP_ISSUE:-533}"
GH_REPO="${WAYFINDER_GH_REPO:-}"
REPAIR_MARKER="wayfinder-local-ci-repair"
PARK_MARKER="wayfinder-chain-parked"
PROGRESS_STRIKES="${WAYFINDER_PROGRESS_STRIKES:-3}"
EXIT_GONE_TICKS="${WAYFINDER_EXIT_GONE_TICKS:-2}"
[[ "$LOCAL_CI_DIR" = /* ]] || LOCAL_CI_DIR="$REPO/$LOCAL_CI_DIR"
[[ "$LOCAL_CI_SCRIPT" = /* ]] || LOCAL_CI_SCRIPT="$REPO/$LOCAL_CI_SCRIPT"
LOCAL_CI_PID_FILE="$LOCAL_CI_DIR/pid"
LOCAL_CI_HEAD_FILE="$LOCAL_CI_DIR/head.sha"
LOCAL_CI_RESULT_FILE="$LOCAL_CI_DIR/result.txt"
# Free-disk floor in GiB — below it the chain pings instead of silently wedging
# (the session-176 class: bun .so extractions filled /tmp, builds started dying
# with no signal). The daily tmp-bun-so-clean.timer + manual build-dir cleanup
# are the recovery; the loop is the tripwire.
DISK_FLOOR_GB="${WAYFINDER_DISK_FLOOR_GB:-5}"
DRY_RUN="${WAYFINDER_DRY_RUN:-}"
VARIANT="${WAYFINDER_VARIANT:-max}"
OC_BIN="${OPENCODE_BIN:-$(command -v opencode2 || command -v opencode || true)}"

# THE canonical in-place recovery prompt (#355). Every in-place recovery path —
# immediate-stop detector, stall/zombie resume, manual --resume — posts exactly this
# text; no path keeps independent recovery wording. Safe at any lifecycle point and
# any number of repeats: the session re-derives its next action from refreshed GitHub
# authority, never from this message's arrival or repetition. Fresh spawns are NOT
# recovery — they start normally through /wayfinder <handoff>.
NUDGE="Your previous execution ended before this session reached its required Wayfinder lifecycle endpoint. This is an automatic recovery message, not a user instruction and not a signal to wrap up, reduce scope, or stop. Do not infer from this message, or from receiving it repeatedly, that time, context, or execution budget is running out.

Resume from the session's existing Wayfinder lifecycle state. This message's arrival is not itself a reason to change phase, abandon work, select a different frontier ticket, or write a handoff.

Before further lifecycle-changing action, refresh the mutable GitHub authority: fetch the currently claimed issue's body and all comments chronologically, refresh the active map, and refresh any blocker, dependency, or human-decision issue whose current state affects the next action. Reconcile material later corrections into the applicable issue body when required. GitHub state outranks stale handoff text. This recovery is not a new session for CI purposes: do not rerun session-start-only CI reconciliation, poll asynchronous CI, or broadly restart repository hydration solely because this message arrived.

If this session has a currently claimed unfinished ticket, continue that ticket only. Do not claim or resolve another frontier ticket in this session. Normal lifecycle tracker writes remain allowed: create required blocker, needs-info, ready-for-human, correctness, architecture, or follow-up issues when the current ticket's workflow requires them, but do not switch execution to those issues.

If human input is required, never ask the user or invoke the question tool: record verified facts, the exact decision, why execution is blocked, and the smallest safe next action on a needs-info or ready-for-human issue, unclaim the blocked ticket so it stays deferred until answered, then write the successor handoff naming that issue as the exact next action and stop. The chain never holds a live session open waiting on a human answer.

If the claimed ticket has already been resolved, complete any remaining resolution comment, tracker reconciliation, map update, or durable decision pointer, then write the successor handoff as the session's final action. Do not claim another frontier ticket in this session.

If no ticket has yet been claimed, resume the normal Wayfinder lifecycle from the active map and incoming pointer packet: rehydrate current GitHub state, reconcile the frontier, continue the normal claim path. Do not create a checkpoint-only handoff merely because this recovery occurred.

Wedge self-heal: if this prompt keeps returning with no frontier progress (same stop reason repeating, packet revisions not chaining, or frontier queries returning the same blocked set), stop re-querying and diagnose the loop itself as the bug before further tracker work: check the daemon log tail (.wayfinder-loop.log), recorded state (.wayfinder-loop.state: last_doc/session_id/seen_docs), git status --short, and that your packet filename matches wayfinder-*-handoff.md (any other name is invisible to the daemon and reads as stopped without handoff forever). Fix the cause — rename the packet to the canonical pattern, commit or park a dirty tree, reconcile stale map text against native GitHub state, or record a poison packet — verify the fix, then write the successor packet and stop. Repairing the wedge IS the work when the chain is wedged.

A ticket may legitimately span sessions. A handoff remains allowed when required by the existing lifecycle, including approaching the context limit or reaching a concrete blocker with no safe continuation. Remaining task size, previous recovery messages, or speculation about remaining execution time are not by themselves reasons to hand off. Before any exit, leave the worktree clean: commit completed coherent slices normally; park unfinished changes with scripts/wayfinder-park.sh and record the stash reference for the successor.

The successor handoff is a compact pointer packet, not a second copy of GitHub state: record the active map/ticket, phase, last integrated or verified commit, concise verification state, blockers, parked-work reference if any, and exact next action — linking durable GitHub evidence instead of copying issue bodies, comments, map diaries, documentation, or diffs. Write the endpoint as handoff activity — write a new successor packet or revise your active packet in place — then stop."

log() { printf '%s %s\n' "$(date '+%F %T')" "$*" >> "$LOG_FILE"; echo "$(date '+%T') $*"; }
die() { log "FATAL: $*"; notify "wayfinder-loop FAILED" "$*"; exit 1; }

# Free space on the repo's filesystem, in GiB (string — caller compares numerically).
free_disk_gb() { df -BG --output=avail "$REPO" 2>/dev/null | awk 'NR==2{gsub(/G/,""); print $1}'; }

notify() {
  local title="$1" body="$2"
  log "notify: $title — $body"
  command -v notify-send >/dev/null 2>&1 && notify-send -a wayfinder-loop "$title" "$body" >/dev/null 2>&1 || true
  if [ -n "$NTFY_TOPIC" ]; then
    curl -sf -d "$body" -H "Title: $title" -H "Priority: high" "https://ntfy.sh/$NTFY_TOPIC" >/dev/null 2>&1 || true
  fi
}

# Only one daemon may own chain state. A second watcher could observe the same
# handoff and create a second active session before either watcher saves state.
exec 9>"$REPO/.wayfinder-loop.lock"
flock -n 9 || die "another wayfinder-loop daemon is already running"

handoff_docs() {
  find "$HANDOFF_DIR" -maxdepth 1 -name 'wayfinder-*-handoff.md' -printf '%f\n' 2>/dev/null |
    sort -t- -k2,2n
}

worktree_dirty() {
  git -C "$REPO" status --porcelain 2>/dev/null |
    grep -Ev '^[ MARC?]{2} \.wayfinder-loop\.(env|log|state|tmux\.log)$' || true
}

# Dirty-tree recovery: when the packet-writing session left the worktree dirty,
# that session owns the mess — send THE canonical NUDGE (its hygiene paragraph
# covers commit-or-park) and let it clean up, instead of freezing the spawn gate
# on a human. Repost only while the owner is stopped-and-still-dirty; a live
# session is mid-cleanup. A confirmed-gone owner falls back to the manual gate.
dirty_owner_recovery() {
  local dirty notified=0
  while :; do
    dirty="$(worktree_dirty)"
    if [ -z "$dirty" ]; then
      [ "$notified" -eq 0 ] || log "worktree clean — spawn resumes"
      return 0
    fi
    if ! api get "/api/session/$session_id" >/dev/null 2>&1; then
      log "owner session $session_id is gone with a dirty worktree — falling back to manual gate"
      return 1
    fi
    if [ "$notified" -eq 0 ]; then
      log "dirty worktree from $session_id — sending recovery prompt to commit or park"
      notify "wayfinder continuing" "session $session_id left a dirty worktree — hygiene recovery sent"
      notified=1
    fi
    if ! session_alive "$session_id"; then
      api post "/api/session/$session_id/prompt" --data "$(jq -nc --arg t "$NUDGE" '{text: $t}')" >/dev/null 2>&1 || true
    fi
    sleep 30
  done
}

# Spawn gate is a clean worktree only. The daemon never stages or commits —
# a session's implementation commits belong to that session, and the handoff
# packet is gitignored runtime state (map #329 ticket #336). A dirty tree
# pauses the chain with a notification until the owning agent cleans up.
wait_for_clean_handoff() {
  local dirty notified=0 pauses=0
  while :; do
    dirty="$(worktree_dirty)"
    if [ -z "$dirty" ]; then
      [ "$notified" -eq 0 ] || log "worktree clean — spawn resumes"
      return 0
    fi
    if [ "$notified" -eq 0 ]; then
      log "spawn paused before session creation for $1 — clean worktree to resume"
      notify "wayfinder paused" "dirty worktree — clean it before next session creation"
      notified=1
    fi
    sleep 60
    # Re-ping periodically: a one-shot notification is how an unattended chain
    # dies silently (the 2026-08-22 double-pause class — nobody saw the first
    # ping, the gate looked like a hang).
    pauses=$((pauses + 1))
    if [ $((pauses % 10)) -eq 0 ]; then
      dirty="$(worktree_dirty)"
      [ -n "$dirty" ] && notify "wayfinder still paused" "dirty worktree ($((pauses / 10))0+ min): $(echo "$dirty" | head -3 | tr '\n' ' ')"
    fi
  done
}

api() { "$OC_BIN" api "$@"; }

load_state() {
  last_doc=""; session_id=""; pending_doc=""
  local_ci_pending_sha=""; local_ci_verdicts=""; local_ci_repair_issues=""
  progress_map=""; progress_fp=""; progress_strikes=0; progress_parked=""; progress_gate=""; progress_park_issue=""
  progress_park_class=""; progress_gate_fp=""
  [ -f "$STATE_FILE" ] || return 0
  # shellcheck disable=SC1090
  source "$STATE_FILE"
  # Back-compat: states written before #578 lack the progress fields.
  progress_map="${progress_map:-}"; progress_fp="${progress_fp:-}"; progress_strikes="${progress_strikes:-0}"
  progress_parked="${progress_parked:-}"; progress_gate="${progress_gate:-}"; progress_park_issue="${progress_park_issue:-}"
  progress_park_class="${progress_park_class:-}"; progress_gate_fp="${progress_gate_fp:-}"
}

save_state() {
  {
    echo "last_doc=$last_doc"
    echo "session_id=$session_id"
    echo "pending_doc=${pending_doc:-}"
    echo "retries=${retries:-0}"
    echo "seen_docs=$seen_docs"
    echo "local_ci_pending_sha=${local_ci_pending_sha:-}"
    echo "local_ci_verdicts=${local_ci_verdicts:-}"
    echo "local_ci_repair_issues=${local_ci_repair_issues:-}"
    echo "progress_map=${progress_map:-}"
    echo "progress_fp=${progress_fp:-}"
    echo "progress_strikes=${progress_strikes:-0}"
    echo "progress_parked=${progress_parked:-}"
    echo "progress_gate=${progress_gate:-}"
    echo "progress_park_issue=${progress_park_issue:-}"
    echo "progress_park_class=${progress_park_class:-}"
    echo "progress_gate_fp=${progress_gate_fp:-}"
  } > "$STATE_FILE"
}

valid_sha() {
  [[ "$1" =~ ^[[:xdigit:]]{40,64}$ ]]
}

read_first_line() {
  local file="$1" line=""
  [ -s "$file" ] || return 0
  IFS= read -r line < "$file" || true
  printf '%s' "${line%$'\r'}"
}

local_ci_current_head() {
  git -C "$REPO" rev-parse HEAD 2>/dev/null || true
}

local_ci_run_sha() {
  read_first_line "$LOCAL_CI_HEAD_FILE"
}

local_ci_run_result() {
  local result
  result="$(read_first_line "$LOCAL_CI_RESULT_FILE")"
  case "$result" in
    PASS|FAIL) printf '%s' "$result" ;;
    *) return 1 ;;
  esac
}

local_ci_run_pid() {
  local pid
  pid="$(read_first_line "$LOCAL_CI_PID_FILE")"
  [[ "$pid" =~ ^[0-9]+$ ]] || return 1
  printf '%s' "$pid"
}

local_ci_run_active() {
  local pid
  pid="$(local_ci_run_pid || true)"
  [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null
}

local_ci_verdict_for() {
  local sha="$1" entry
  local -a entries=()
  IFS=',' read -ra entries <<< "${local_ci_verdicts:-}"
  for entry in "${entries[@]}"; do
    [[ "$entry" == "$sha="* ]] && {
      printf '%s' "${entry#*=}"
      return 0
    }
  done
  return 1
}

local_ci_record_verdict() {
  local sha="$1" verdict="$2" entry updated=""
  local -a entries=()
  IFS=',' read -ra entries <<< "${local_ci_verdicts:-}"
  for entry in "${entries[@]}"; do
    [ -n "$entry" ] || continue
    [[ "$entry" == "$sha="* ]] && continue
    [ -n "$updated" ] && updated="$updated,$entry" || updated="$entry"
  done
  [ -n "$updated" ] && updated="$updated,$sha=$verdict" || updated="$sha=$verdict"
  local_ci_verdicts="$updated"
}

local_ci_repair_for() {
  local sha="$1" entry
  local -a entries=()
  IFS=',' read -ra entries <<< "${local_ci_repair_issues:-}"
  for entry in "${entries[@]}"; do
    [[ "$entry" == "$sha:"* ]] && {
      printf '%s' "${entry#*:}"
      return 0
    }
  done
  return 1
}

local_ci_record_repair() {
  local sha="$1" issue="$2" entry updated=""
  local -a entries=()
  IFS=',' read -ra entries <<< "${local_ci_repair_issues:-}"
  for entry in "${entries[@]}"; do
    [ -n "$entry" ] || continue
    [[ "$entry" == "$sha:"* ]] && continue
    [ -n "$updated" ] && updated="$updated,$entry" || updated="$entry"
  done
  [ -n "$updated" ] && updated="$updated,$sha:$issue" || updated="$sha:$issue"
  local_ci_repair_issues="$updated"
}

tracker_repo() {
  local remote path
  if [ -n "$GH_REPO" ]; then
    printf '%s' "$GH_REPO"
    return 0
  fi
  remote="$(git -C "$REPO" remote get-url origin 2>/dev/null || true)"
  case "$remote" in
    *github.com:*) path="${remote#*github.com:}" ;;
    *github.com/*) path="${remote#*github.com/}" ;;
    *) path="" ;;
  esac
  path="${path%.git}"
  [ -n "$path" ] && printf '%s' "$path" || printf '%s' 'jsongalvez/company_app'
}

local_ci_launch() {
  local sha="$1"
  if [ -n "$DRY_RUN" ]; then
    log "DRY-RUN: would launch local-ci for HEAD $sha"
    return 0
  fi
  if [ ! -f "$LOCAL_CI_SCRIPT" ]; then
    log "local-ci launcher missing: $LOCAL_CI_SCRIPT"
    return 1
  fi
  if (cd "$REPO" && LOCAL_CI_STATE_DIR="$LOCAL_CI_DIR" bash "$LOCAL_CI_SCRIPT") >>"$LOG_FILE" 2>&1; then
    return 0
  fi
  log "local-ci launch failed for HEAD $sha — will retry without blocking"
  return 1
}

# Return every currently claimable child in the map's ordered section. A red
# verification must stop the normal frontier rather than merely blocking its
# first row: otherwise the next unblocked row would bypass the repair ticket.
# The ordered body remains authority for preference; live issue JSON supplies
# state, assignee, labels, and native dependency counts.
local_ci_frontier_issue() {
  local repo candidate state assignees blocked labels
  local map_file issue_file
  local -a candidates=()

  if [ -n "${WAYFINDER_FRONTIER_ISSUE:-}" ]; then
    printf '%s' "$WAYFINDER_FRONTIER_ISSUE"
    return 0
  fi
  [ -n "$GH_BIN" ] || return 1
  repo="$(tracker_repo)"
  map_file="$(mktemp)"
  if ! "$GH_BIN" api "repos/$repo/issues/$MAP_ISSUE" >"$map_file" 2>/dev/null; then
    rm -f "$map_file"
    return 1
  fi
  mapfile -t candidates < <(
    jq -r '.body // ""' "$map_file" 2>/dev/null |
      sed -n '/^## Ordered implementation children/,/^## /p' |
      sed -n 's/^- \[[ x]\] #\([0-9][0-9]*\).*/\1/p' || true
  )
  rm -f "$map_file"

  # A map created by an older connector may not have the ordered heading. The
  # native child list is a safe read-only fallback for that case.
  if [ "${#candidates[@]}" -eq 0 ]; then
    map_file="$(mktemp)"
    if "$GH_BIN" api "repos/$repo/issues/$MAP_ISSUE/sub_issues?per_page=100" >"$map_file" 2>/dev/null; then
      mapfile -t candidates < <(jq -r '.[].number // empty' "$map_file" 2>/dev/null || true)
    fi
    rm -f "$map_file"
  fi

  for candidate in "${candidates[@]}"; do
    issue_file="$(mktemp)"
    if ! "$GH_BIN" api "repos/$repo/issues/$candidate" >"$issue_file" 2>/dev/null; then
      rm -f "$issue_file"
      continue
    fi
    state="$(jq -r '.state // ""' "$issue_file" 2>/dev/null || true)"
    assignees="$(jq -r '(.assignees // []) | length' "$issue_file" 2>/dev/null || true)"
    blocked="$(jq -r '.issue_dependencies_summary.blocked_by // 0' "$issue_file" 2>/dev/null || true)"
    labels="$(jq -r '[.labels[]?.name] | join(",")' "$issue_file" 2>/dev/null || true)"
    rm -f "$issue_file"
    [[ "$state" = open ]] || continue
    [[ "$assignees" = 0 ]] || continue
    [[ "$blocked" =~ ^[0-9]+$ ]] || blocked=1
    [ "$blocked" -eq 0 ] || continue
    case ",$labels," in
      *,ready-for-human,*|*,needs-info,*) continue ;;
    esac
    printf '%s\n' "$candidate"
  done
  return 0
}

local_ci_ensure_map_child() {
  local repo="$1" issue_number="$2" issue_id="$3" expected actual
  expected="https://api.github.com/repos/$repo/issues/$MAP_ISSUE"
  actual="$("$GH_BIN" api "repos/$repo/issues/$issue_number" --jq '.parent_issue_url // ""' 2>/dev/null || true)"
  [ "$actual" = "$expected" ] && return 0
  [ -z "$actual" ] || {
    log "repair issue #$issue_number already belongs to another parent: $actual"
    return 1
  }
  if ! "$GH_BIN" api --method POST "repos/$repo/issues/$MAP_ISSUE/sub_issues" \
    -F "sub_issue_id=$issue_id" >/dev/null 2>&1; then
    log "could not attach repair issue #$issue_number to map #$MAP_ISSUE"
    return 1
  fi
  actual="$("$GH_BIN" api "repos/$repo/issues/$issue_number" --jq '.parent_issue_url // ""' 2>/dev/null || true)"
  [ "$actual" = "$expected" ] || {
    log "repair issue #$issue_number parent verification failed: ${actual:-empty}"
    return 1
  }
}

local_ci_ensure_frontier_block() {
  local repo="$1" frontier="$2" repair_id="$3" deps_file
  [ -n "$frontier" ] || {
    log "local-ci red verdict has no claimable frontier; repair ticket remains unblocked"
    return 0
  }
  [ "$frontier" != "$repair_id" ] || return 0
  deps_file="$(mktemp)"
  if ! "$GH_BIN" api "repos/$repo/issues/$frontier/dependencies/blocked_by" >"$deps_file" 2>/dev/null; then
    rm -f "$deps_file"
    log "could not read dependencies for frontier #$frontier"
    return 1
  fi
  if jq -e --arg id "$repair_id" 'any(.[]?; ((.id // "") | tostring) == $id)' "$deps_file" >/dev/null 2>&1; then
    rm -f "$deps_file"
    return 0
  fi
  rm -f "$deps_file"
  if ! "$GH_BIN" api --method POST "repos/$repo/issues/$frontier/dependencies/blocked_by" \
    -F "issue_id=$repair_id" >/dev/null 2>&1; then
    log "could not add repair issue #$repair_id as blocker of frontier #$frontier"
    return 1
  fi
  log "repair issue #$repair_id now blocks frontier #$frontier"
}

local_ci_repair_ticket() {
  local sha="$1" repo issue_number issue_id issue_state issue_url body title
  local issue_file found frontiers frontier failed=0 cached=""

  [ -n "$GH_BIN" ] || {
    log "local-ci red verdict for $sha — gh is unavailable; no frontier block"
    return 1
  }
  if ! "$GH_BIN" auth status >/dev/null 2>&1; then
    log "local-ci red verdict for $sha — gh auth is unavailable in daemon environment; no frontier block"
    return 1
  fi
  repo="$(tracker_repo)"
  cached="$(local_ci_repair_for "$sha" || true)"
  if [ -n "$cached" ]; then
    issue_number="$cached"
  else
    issue_file="$(mktemp)"
    if ! "$GH_BIN" api "repos/$repo/issues?state=all&per_page=100" >"$issue_file" 2>/dev/null; then
      rm -f "$issue_file"
      log "could not search repair issues for local-ci red HEAD $sha"
      return 1
    fi
    found="$(jq -r --arg marker "$REPAIR_MARKER" --arg sha "$sha" '
      [.[] | select(.pull_request == null)
       | select(((.title // "") | contains($marker)) or ((.body // "") | contains($marker)))
       | select((.body // "") | contains($sha))]
      | first
      | if . == null then "" else ((.number | tostring) + "\t" + (.id | tostring) + "\t" + (.state // "")) end
    ' "$issue_file" 2>/dev/null || true)"
    rm -f "$issue_file"
    if [ -n "$found" ]; then
      issue_number="${found%%$'\t'*}"
    else
      title="[local-ci] repair red detached verification for $sha"
      body="Part of #$MAP_ISSUE.
Blocked by: none.

<!-- $REPAIR_MARKER -->
Covered HEAD: \`$sha\`
Verdict: FAIL

This repair ticket was created by the Wayfinder daemon after the detached local-CI
run for the pinned HEAD returned FAIL. Repair the failing local gate before normal
frontier work proceeds. The daemon does not poll hosted CI."
      # Use --body, not --body-file: a tracker body is never sourced from an
      # unchecked/possibly empty temporary file.
      issue_url="$("$GH_BIN" issue create --repo "$repo" --title "$title" --body "$body" \
        --label wayfinder:task --label ready-for-agent 2>/dev/null || true)"
      issue_number="$(printf '%s\n' "$issue_url" | sed -n 's#.*issues/\([0-9][0-9]*\).*#\1#p' | tail -1)"
      [ -n "$issue_number" ] || {
        log "could not create repair issue for local-ci red HEAD $sha"
        return 1
      }
    fi
  fi

  issue_file="$(mktemp)"
  if ! "$GH_BIN" api "repos/$repo/issues/$issue_number" >"$issue_file" 2>/dev/null; then
    rm -f "$issue_file"
    log "repair issue #$issue_number disappeared before it could block the frontier"
    return 1
  fi
  issue_id="$(jq -r '.id // ""' "$issue_file" 2>/dev/null || true)"
  issue_state="$(jq -r '.state // ""' "$issue_file" 2>/dev/null || true)"
  rm -f "$issue_file"
  [[ "$issue_id" =~ ^[0-9]+$ ]] || {
    log "repair issue #$issue_number has no numeric database id"
    return 1
  }
  if [ "$issue_state" = closed ]; then
    if ! "$GH_BIN" issue reopen "$issue_number" --repo "$repo" >/dev/null 2>&1; then
      log "could not reopen closed repair issue #$issue_number for red HEAD $sha"
      return 1
    fi
  fi
  if ! local_ci_ensure_map_child "$repo" "$issue_number" "$issue_id"; then
    return 1
  fi
  local_ci_record_repair "$sha" "$issue_number"
  if ! frontiers="$(local_ci_frontier_issue)"; then
    log "could not reconcile the map frontier for local-ci red HEAD $sha"
    return 1
  fi
  while IFS= read -r frontier; do
    [ -n "$frontier" ] || continue
    if ! local_ci_ensure_frontier_block "$repo" "$frontier" "$issue_id"; then
      failed=1
    fi
  done <<< "$frontiers"
  [ "$failed" -eq 0 ]
}

local_ci_process_verdict() {
  local sha="$1" verdict="$2" seen
  seen="$(local_ci_verdict_for "$sha" || true)"
  if [ "$verdict" = PASS ]; then
    if [ "$seen" != PASS ]; then
      log "local-ci verdict PASS for HEAD $sha"
      local_ci_record_verdict "$sha" PASS
      save_state
    fi
    return 0
  fi
  [ "$verdict" = FAIL ] || return 1
  [ "$seen" != FAIL ] || return 0
  if [ -n "$DRY_RUN" ]; then
    log "DRY-RUN: local-ci verdict FAIL for HEAD $sha — would create/update one repair ticket"
    local_ci_record_verdict "$sha" FAIL
    save_state
    return 0
  fi
  if local_ci_repair_ticket "$sha"; then
    local_ci_record_verdict "$sha" FAIL
    save_state
    return 0
  fi
  return 1
}

ensure_local_ci_run() {
  local head covered result
  head="$(local_ci_current_head)"
  valid_sha "$head" || {
    [ -n "$head" ] && log "local-ci watcher could not use non-SHA HEAD: $head"
    return 0
  }
  covered="$(local_ci_run_sha)"
  if local_ci_run_active; then
    if [ "$covered" != "$head" ] && [ "${local_ci_pending_sha:-}" != "$head" ]; then
      local_ci_pending_sha="$head"
      save_state
      log "HEAD moved to $head while local-ci run covers ${covered:-unknown}; queued a fresh run"
    fi
    return 0
  fi
  if [ "$covered" = "$head" ]; then
    result="$(local_ci_run_result || true)"
    if [ "$result" = PASS ] || [ "$result" = FAIL ]; then
      local_ci_pending_sha=""
      local_ci_process_verdict "$head" "$result" || true
      return 0
    fi
  fi
  if [ -n "$DRY_RUN" ] && [ "${local_ci_pending_sha:-}" = "$head" ]; then
    log "DRY-RUN: local-ci launch for HEAD $head already planned"
    return 0
  fi
  log "local-ci has no completed run for HEAD $head (covered=${covered:-none}) — launching"
  if local_ci_launch "$head"; then
    local_ci_pending_sha="$head"
    save_state
  fi
}

# --- Chain advancement supervision (#578) ------------------------------------
# The daemon used to supervise session exhaust: any session that exited having
# revised its packet counted as progress, so an externally-starved chain
# respawned forever (the 2026-09-07 empty-frontier spin: 8+ sessions, zero
# advancement, all gated on external #532). These functions supervise map
# advancement instead: snapshot the map's tracker state per session, park the
# chain when consecutive sessions advance nothing, and sleep until the tracker
# moves again. Advisory only — every tracker failure fails open to normal
# spawning; the spawn gate and session supervision above stay authoritative.

# progress_map_for_doc <doc>: map issue number for a packet. Precedence:
# explicit `Progress-Map: <n>` packet line (sessions know their map), the
# wayfinder-<map>-* / wayfinder-map<map>-* basename convention, then MAP_ISSUE.
progress_map_for_doc() {
  local doc="$1" map="" base=""
  map="$(sed -n 's/^[Pp]rogress-[Mm]ap:[[:space:]]*#\?\([0-9][0-9]*\).*/\1/p' "$HANDOFF_DIR/$doc" 2>/dev/null | head -1 || true)"
  if [ -n "$map" ]; then printf '%s' "$map"; return 0; fi
  base="$(printf '%s' "$doc" | sed -n 's/^wayfinder-\(map\)\?\([0-9][0-9]*\)-.*/\2/p' || true)"
  if [ -n "$base" ]; then printf '%s' "$base"; return 0; fi
  printf '%s' "$MAP_ISSUE"
}

# session_gone <sid>: proven absence from the live set. The direct session GET
# is USELESS for death detection — the API returns historical records for
# long-dead sessions (rc=0). The authority signal is /active membership, and
# only a SUCCESSFUL fetch counts: an unreadable /active answers "alive"
# (the 2026-09-07 phantom-completion class came from treating one failed poll
# as death while the worker was merely paused on user input). Callers only
# ever spawn/complete on proven absence, never on unknown.
session_gone() {
  local sid="$1" active
  active="$(api get /api/session/active 2>/dev/null || true)"
  [ -n "$active" ] || return 1
  printf '%s' "$active" | jq -e --arg s "$sid" '.data | has($s) | not' >/dev/null 2>&1
}

# progress_snapshot <map>: canonical `number|state|assignees|labels|blocked`
# lines for every native child except the daemon's own `[parked] wayfinder
# chain:` markers (runtime state, not map work — snapshotting them would let a
# park wake itself). Claims, closures, label changes, and unblocks all move
# the fingerprint; comments do not. Fails on ANY tracker failure, including a
# single unreadable open child: partial reads are worse than no reads, and
# every caller fails open to normal spawning on failure.
progress_snapshot() {
  local map="$1" repo page list_file det_file num state line
  repo="$(tracker_repo)"
  [ -n "$GH_BIN" ] || return 1
  list_file="$(mktemp)"; det_file="$(mktemp)"
  page=1
  while [ "$page" -le 5 ]; do
    if ! "$GH_BIN" api "repos/$repo/issues/$map/sub_issues?per_page=100&page=$page" >"$list_file" 2>/dev/null; then
      rm -f "$list_file" "$det_file" "$list_file.nums"
      return 1
    fi
    [ "$(jq 'length' "$list_file" 2>/dev/null || echo 0)" -gt 0 ] || break
    jq -r '.[] | select(((.title // "") | startswith("[parked] wayfinder chain:")) | not) | .number // empty' "$list_file" 2>/dev/null >"$list_file.nums" || true
    while IFS= read -r num; do
      [ -n "$num" ] || continue
      state="$(jq -r --argjson n "$num" '.[] | select(.number == $n) | .state // ""' "$list_file" 2>/dev/null || true)"
      if [ "$state" = "open" ]; then
        if "$GH_BIN" api "repos/$repo/issues/$num" >"$det_file" 2>/dev/null; then
          line="$(jq -r --argjson n "$num" '"\($n)|\(.state // "open")|\([.assignees[]?.login] | join(","))|\([.labels[]?.name] | sort | join(","))|\(.issue_dependencies_summary.blocked_by // 0)"' "$det_file" 2>/dev/null || true)"
          if [ -n "$line" ]; then
            printf '%s\n' "$line"
          else
            rm -f "$list_file" "$det_file" "$list_file.nums"
            return 1
          fi
        else
          rm -f "$list_file" "$det_file" "$list_file.nums"
          return 1
        fi
      else
        printf '%s|%s|||\n' "$num" "$state"
      fi
    done < "$list_file.nums"
    [ "$(jq 'length' "$list_file" 2>/dev/null || echo 0)" -lt 100 ] && break
    page=$((page + 1))
  done
  rm -f "$list_file" "$det_file" "$list_file.nums"
  return 0
}

# progress_fp <map>: sha256 over the sorted snapshot; fails when untrackable.
progress_fp() {
  local map="$1" snap fp
  snap="$(progress_snapshot "$map" | sort -n || true)"
  [ -n "$snap" ] || return 1
  fp="$(printf '%s' "$snap" | sha256sum | awk '{print $1}' || true)"
  [ -n "$fp" ] && printf '%s' "$fp"
}

# progress_line_claimable <line>: 0 when a snapshot line is frontier-claimable
# under the issue-tracker.md rule (open, unassigned, unblocked, no
# human-deferral label). Shared by the claimable listing and the classifier so
# the two can never disagree on what the frontier is.
progress_line_claimable() {
  local num state assignees labels blocked
  IFS='|' read -r num state assignees labels blocked <<< "$1"
  [ "$state" = "open" ] || return 1
  [ -z "$assignees" ] || return 1
  [[ "$blocked" =~ ^[0-9]+$ ]] || blocked=1
  [ "$blocked" -eq 0 ] || return 1
  case ",$labels," in *,ready-for-human,*|*,needs-info,*) return 1 ;; esac
  return 0
}

# progress_claimable <map>: open, unassigned, unblocked children without a
# human-deferral label — the same frontier rule issue-tracker.md states.
# Prints numbers, one per line; fails when the tracker is unreadable.
progress_claimable() {
  local map="$1" snap line
  snap="$(progress_snapshot "$map" || true)"
  [ -n "$snap" ] || return 1
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    progress_line_claimable "$line" || continue
    printf '%s\n' "${line%%|*}"
  done <<< "$snap"
  return 0
}

# progress_classify <map>: `starved #g1 #g2...` when the frontier is empty and
# at least one open child is gated by an OPEN blocker outside the map's own
# subtree; `active` when frontier exists (caller must not park); `poison`
# when the frontier is verifiably empty with no external gate. FAILS (nonzero,
# no verdict) on anything unverifiable — no gh, unreadable children, or zero
# successful dependency reads: a park needs positive evidence, never a blip.
# Reads, never writes.
progress_classify() {
  local map="$1" repo children child state blocked gate gates=" " open_ext deps_ok=0 line deps_file
  repo="$(tracker_repo)"
  [ -n "$GH_BIN" ] || return 1
  children="$(progress_snapshot "$map" || true)"
  [ -n "$children" ] || return 1
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    progress_line_claimable "$line" && { printf 'active\n'; return 0; }
  done <<< "$children"
  deps_file="$(mktemp)"
  while IFS='|' read -r child state _ _ blocked; do
    [ "$state" = "open" ] || continue
    [[ "$blocked" =~ ^[0-9]+$ ]] || blocked=0
    [ "$blocked" -gt 0 ] || continue
    if "$GH_BIN" api "repos/$repo/issues/$child/dependencies/blocked_by" >"$deps_file" 2>/dev/null; then
      deps_ok=1
      open_ext="$(jq -r '.[]? | select(.state == "open") | .number // empty' "$deps_file" 2>/dev/null || true)"
    else
      continue
    fi
    for gate in $open_ext; do
      grep -q "^${gate}|" <<< "$children" && continue
      case "$gates" in *" #$gate "*) ;; *) gates="$gates#$gate " ;; esac
    done
  done <<< "$children"
  rm -f "$deps_file"
  gates="$(printf '%s' "$gates" | tr -s ' ' | sed 's/^ //;s/ $//')"
  if [ -n "$gates" ]; then
    printf 'starved %s\n' "$gates"
    return 0
  fi
  [ "$deps_ok" -eq 1 ] || return 1
  printf 'poison\n'
  return 0
}

# progress_verify_signal <map> <signal...>: validate a session-declared
# `Park-Signal: starved-on #a #b` line against live tracker state. Accepts
# only when the daemon's own classifier returns exactly the named gate set —
# proving an empty frontier AND that those gates actually block map children.
# Prints the normalized `starved #..` verdict; fails otherwise (caller falls
# back to the strike path, never parks on words alone). There is deliberately
# no poison signal: poison stays parked for a human, and no session fast-path
# may claim it.
progress_verify_signal() {
  local map="$1"; shift
  local have have_gates want g
  [ "${1:-}" = "starved-on" ] || return 1
  shift
  [ $# -gt 0 ] || return 1
  for g in "$@"; do
    [[ "${g#"#"}" =~ ^[0-9]+$ ]] || return 1
  done
  want="$(for g in "$@"; do printf '%s\n' "${g#"#"}"; done | sort -n | tr '\n' ' ')"
  have="$(progress_classify "$map" || true)"
  case "$have" in starved*) ;; *) return 1 ;; esac
  have_gates="$(printf '%s' "${have#starved }" | tr ' ' '\n' | sed 's/^#//' | sort -n | tr '\n' ' ')"
  [ "$have_gates" = "$want" ] || return 1
  printf '%s\n' "$have"
  return 0
}

# progress_gate_fingerprint <refs...>: sha over `number|state|labels` lines
# for named external gates (refs with or without `#`). A gate close AND a
# gate label change both move it. Fails when any gate is unreadable — an
# unreadable gate never wakes a chain.
progress_gate_fingerprint() {
  local g repo det lines="" line fp
  [ $# -gt 0 ] || return 1
  repo="$(tracker_repo)"
  [ -n "$GH_BIN" ] || return 1
  det="$(mktemp)"
  for g in "$@"; do
    g="${g#"#"}"
    [[ "$g" =~ ^[0-9]+$ ]] || { rm -f "$det"; return 1; }
    "$GH_BIN" api "repos/$repo/issues/$g" >"$det" 2>/dev/null || { rm -f "$det"; return 1; }
    line="$(jq -r --argjson n "$g" '"\($n)|\(.state // "unknown")|\([.labels[]?.name] | sort | join(","))"' "$det" 2>/dev/null || true)"
    [ -n "$line" ] || { rm -f "$det"; return 1; }
    lines="$lines$line
"
  done
  rm -f "$det"
  fp="$(printf '%s' "$lines" | sha256sum | awk '{print $1}' || true)"
  [ -n "$fp" ] && printf '%s' "$fp"
}

# progress_park_issue <park|resume> <doc> <class> <detail>: find-or-create one
# marker issue per parked packet (precedent: #577 repair issues), comment and
# close it on resume. Operator-facing state, never agent work: labelled
# wayfinder:task WITHOUT ready-for-agent, and deliberately NOT attached as a
# native map child — attachment would expose it to frontier queries and its
# creation would move the map fingerprint (a park waking itself). Traceability
# is the marker string plus the daemon's progress_park_issue state field.
# Fail-open everywhere — a park must never depend on a tracker write.
progress_park_issue() {
  local mode="$1" doc="$2" class="$3" detail="$4"
  local repo issue_file found number state title body url
  if [ -n "$DRY_RUN" ]; then
    log "DRY-RUN: park marker $mode for $doc ($class $detail) — no tracker write"
    return 0
  fi
  [ -n "$GH_BIN" ] || { log "park marker $mode for $doc — gh unavailable"; return 1; }
  "$GH_BIN" auth status >/dev/null 2>&1 || { log "park marker $mode for $doc — gh auth unavailable"; return 1; }
  repo="$(tracker_repo)"
  issue_file="$(mktemp)"
  "$GH_BIN" api "repos/$repo/issues?state=all&per_page=100" >"$issue_file" 2>/dev/null || {
    rm -f "$issue_file"; log "could not search park markers for $doc"; return 1
  }
  found="$(jq -r --arg marker "$PARK_MARKER" --arg doc "$doc" '
    [.[] | select(.pull_request == null)
     | select(((.body // "") | contains($marker)))
     | select(((.body // "") | contains($doc)))]
    | first | if . == null then "" else ((.number | tostring) + "\t" + (.state // "")) end
  ' "$issue_file" 2>/dev/null || true)"
  rm -f "$issue_file"
  number="${found%%$'\t'*}"
  state="${found#*$'\t'}"
  if [ "$mode" = park ]; then
    if [ -z "$number" ]; then
      title="[parked] wayfinder chain: $doc"
      body="<!-- $PARK_MARKER -->
Packet: \`$doc\`
Map: #${progress_map:-unknown}
Class: $class
Detail: $detail

The daemon parked this chain: consecutive sessions advanced the map nothing
($class). Starved chains resume automatically when the tracker moves; poison
chains need an operator. This issue is tracker state, not agent work: do not
claim it, and it is intentionally not attached as a map child."
      url="$("$GH_BIN" issue create --repo "$repo" --title "$title" --body "$body" \
        --label wayfinder:task 2>/dev/null || true)"
      number="$(printf '%s\n' "$url" | sed -n 's#.*issues/\([0-9][0-9]*\).*#\1#p' | tail -1)"
      [ -n "$number" ] || { log "could not create park marker for $doc"; return 1; }
      progress_park_issue="$number"
      save_state
      log "park marker #$number created for $doc ($class)"
    elif [ "$state" = closed ]; then
      "$GH_BIN" issue reopen "$number" --repo "$repo" >/dev/null 2>&1 || true
      "$GH_BIN" issue comment "$number" --repo "$repo" --body "Chain re-parked on \`$doc\` ($class $detail)." >/dev/null 2>&1 || true
      progress_park_issue="$number"
      save_state
    else
      "$GH_BIN" issue comment "$number" --repo "$repo" --body "Chain still parked on \`$doc\` ($class $detail)." >/dev/null 2>&1 || true
      progress_park_issue="$number"
      save_state
    fi
    return 0
  fi
  [ -n "$number" ] || return 0
  [ "$state" != "closed" ] || return 0
  "$GH_BIN" issue comment "$number" --repo "$repo" --body "Chain resumed on \`$doc\` ($detail)." >/dev/null 2>&1 || true
  "$GH_BIN" issue close "$number" --repo "$repo" >/dev/null 2>&1 || true
  return 0
}

# progress_park <doc> <class> [gates...]: record the park, escalate via the
# marker issue, then sleep until a class-appropriate wake. Starved chains
# wake on tracker movement; poison chains sleep through it (operator-only
# wake). Returns with last_doc set to the packet to resume and the park
# cleared.
progress_park() {
  local doc="$1" class="$2"; shift 2
  progress_parked="$doc"; progress_gate="$*"; progress_park_class="$class"
  progress_gate_fp="$(progress_gate_fingerprint "$@" || true)"
  save_state
  # shellcheck disable=SC2086
  progress_park_issue park "$doc" "$class" "$*" || true
  if [ "$class" = starved ]; then
    log "chain parked (starved): $doc advanced nothing for ${progress_strikes} sessions; gated on$([ -n "$*" ] && printf ' %s' "$*" || printf ' an unverified gate') — sleeping until the tracker moves"
    notify "wayfinder parked" "$doc is starved$([ -n "$*" ] && printf ' on %s' "$*" || true) — chain sleeps until it clears"
  else
    log "chain parked (poison): $doc advanced nothing for ${progress_strikes} sessions with no external gate — operator inspect, then restart or --retry"
    notify "wayfinder chain parked" "$doc shows no advancement and no external gate — attach TUI to inspect"
  fi
  progress_park_watch
}

# progress_park_watch: sleep until a class-appropriate wake. Any chain wakes on
# a manually written new packet. Starved chains additionally wake on tracker
# movement: a named-gate change (close or label change) or any map fingerprint
# movement (unblocks, new children). Poison chains sleep through tracker
# movement — only a human (restart/--retry) or a new packet wakes them.
# Local-CI watch duties continue while parked.
progress_park_watch() {
  local doc fp_now parked_fp nd gate_now
  doc="$progress_parked"
  parked_fp="${progress_fp:-}"
  while :; do
    sleep "$POLL_SECS"
    ensure_local_ci_run
    nd="$(newest_unprocessed || true)"
    if [ -n "$nd" ] && [ "$nd" != "$doc" ]; then
      sleep 10
      last_doc="$nd"
      mark_seen "$nd"
      progress_note_baseline "$last_doc"
      progress_unpark "new packet $nd"
      return 0
    fi
    [ "${progress_park_class:-}" = poison ] && continue
    if [ -n "${progress_gate:-}" ]; then
      # shellcheck disable=SC2086
      gate_now="$(progress_gate_fingerprint $progress_gate || true)"
      if [ -z "$gate_now" ]; then
        : # unreadable gate never wakes — keep sleeping
      elif [ -z "${progress_gate_fp:-}" ]; then
        progress_gate_fp="$gate_now"
        save_state
      elif [ "$gate_now" != "$progress_gate_fp" ]; then
        last_doc="$doc"
        progress_fp="$(progress_fp "${progress_map:-$MAP_ISSUE}" || true)"
        progress_gate_fp="$gate_now"
        progress_unpark "gate changed (${progress_gate:-unknown})"
        return 0
      fi
    fi
    fp_now="$(progress_fp "${progress_map:-$MAP_ISSUE}" || true)"
    [ -n "$fp_now" ] || continue
    if [ -n "$parked_fp" ] && [ "$fp_now" != "$parked_fp" ]; then
      last_doc="$doc"
      progress_fp="$fp_now"
      progress_unpark "tracker moved on map #${progress_map:-$MAP_ISSUE}"
      return 0
    fi
    parked_fp="$fp_now"
  done
}

# progress_unpark <reason>: clear park state, close the marker, log the wake.
progress_unpark() {
  local reason="$1"
  progress_park_issue resume "$progress_parked" "" "$reason" || true
  log "chain resumed on $last_doc — $reason"
  notify "wayfinder resumed" "$last_doc — $reason"
  progress_parked=""; progress_gate=""; progress_strikes=0; progress_park_class=""; progress_gate_fp=""
  save_state
}

# progress_note_baseline <doc>: (re)base advancement tracking after a spawn or
# a map switch. External movement between sessions clears stale strikes; an
# unreadable tracker pauses accounting without touching the counters.
progress_note_baseline() {
  local doc="$1" map fp_now
  map="$(progress_map_for_doc "$doc")"
  if [ "$map" != "${progress_map:-}" ]; then
    progress_map="$map"; progress_strikes=0
    log "progress tracking (re)based on map #$map for $doc"
  fi
  fp_now="$(progress_fp "$map" || true)"
  if [ -z "$fp_now" ]; then
    log "progress snapshot unavailable for map #$map — spawning without advancement accounting"
    save_state
    return 0
  fi
  if [ -z "${progress_fp:-}" ] || [ "$fp_now" != "$progress_fp" ]; then
    [ -n "${progress_fp:-}" ] && log "map #$map moved outside the chain — progress strikes cleared"
    progress_fp="$fp_now"; progress_strikes=0
  fi
  save_state
}

# progress_note_completion <doc>: advancement gate between session exit and
# the next spawn. Returns 1 only in DRY_RUN (caller exits); otherwise always
# returns 0 — parking happens inside via progress_park_watch.
progress_note_completion() {
  local doc="$1" map fp_now signal verdict
  map="$(progress_map_for_doc "$doc")"
  if [ -n "$DRY_RUN" ]; then
    log "DRY-RUN: progress note for $doc (map #$map, strikes ${progress_strikes:-0}) — no advancement accounting"
    return 1
  fi
  if [ "$map" != "${progress_map:-}" ]; then
    progress_note_baseline "$doc"
    map="$progress_map"
  fi
  fp_now="$(progress_fp "$map" || true)"
  if [ -z "$fp_now" ]; then
    log "progress snapshot unavailable for map #$map — spawning without advancement accounting"
    return 0
  fi
  if [ -z "${progress_fp:-}" ]; then
    progress_fp="$fp_now"; progress_strikes=0; save_state
    return 0
  fi
  signal="$(sed -n 's/^[Pp]ark-[Ss]ignal:[[:space:]]*\(.*\)/\1/p' "$HANDOFF_DIR/$doc" 2>/dev/null | head -1 || true)"
  if [ -n "$signal" ]; then
    # shellcheck disable=SC2086
    if verdict="$(progress_verify_signal "$map" $signal)" && [ -n "$verdict" ]; then
      # shellcheck disable=SC2086
      progress_fp="$fp_now"; save_state
      log "session declared park ($signal) — verified against the tracker"
      # shellcheck disable=SC2086
      progress_park "$doc" $verdict
      return 0
    fi
    log "session-declared park ($signal) did not verify — falling back to strikes"
  fi
  if [ "$fp_now" != "$progress_fp" ]; then
    progress_fp="$fp_now"; progress_strikes=0; save_state
    log "map #$map advanced this session — progress strikes cleared"
    return 0
  fi
  progress_strikes=$(( ${progress_strikes:-0} + 1 ))
  save_state
  if [ "$progress_strikes" -lt "$PROGRESS_STRIKES" ]; then
    log "map #$map unchanged for $progress_strikes/$PROGRESS_STRIKES sessions — spawning"
    return 0
  fi
  verdict="$(progress_classify "$map" || true)"
  case "$verdict" in
    "") log "map #$map unreadable at strike $progress_strikes — spawning without accounting (fail open)"; return 0 ;;
    active*)
      progress_strikes=0; save_state
      log "map #$map has frontier despite unchanged fingerprint — spawning (sessions may be idle, not starved)"
      return 0
      ;;
    starved*)
      # shellcheck disable=SC2086
      progress_park "$doc" $verdict
      return 0
      ;;
    *)
      progress_park "$doc" poison
      return 0
      ;;
  esac
}

wait_for_session_exit() {
  local notified=0 gone_ticks=0
  while :; do
    # Proven-absence grace: one failed /active poll (or a session paused on
    # user input) must not read as death. Exit only after EXIT_GONE_TICKS
    # consecutive ticks of proven /active absence (successful fetch, id
    # unlisted). A parent awaiting sub-agents counts as alive throughout.
    if session_gone "$session_id" && ! active_children "$session_id"; then
      gone_ticks=$((gone_ticks + 1))
      [ "$gone_ticks" -ge "$EXIT_GONE_TICKS" ] && break
    else
      gone_ticks=0
      if [ "$notified" -eq 0 ]; then
        log "handoff $pending_doc detected; waiting for session $session_id to exit before spawning"
        notify "wayfinder waiting" "handoff $pending_doc is ready; waiting for session $session_id to finish"
        notified=1
      fi
    fi
    sleep "$TICK_SECS"
  done
  # API active-state removal and final filesystem writes can cross one poll tick.
  sleep "$TICK_SECS"
}

# seen_docs is a comma-separated list of processed handoff basename@sha256 entries.
# Hashes matter: historical handoff basenames already exist, and a later session may
# overwrite one of them. Basename-only tracking misses that successor handoff.
seen_docs=""
handoff_fingerprint() {
  sha256sum "$HANDOFF_DIR/$1" | awk '{print $1}'
}

seed_seen_docs() {
  local d fp entries=""
  while IFS= read -r d; do
    [ -n "$d" ] || continue
    fp="$(handoff_fingerprint "$d")"
    if [ -n "$entries" ]; then entries="$entries,$d@$fp"; else entries="$d@$fp"; fi
  done < <(handoff_docs)
  printf '%s' "$entries"
}

normalize_seen_docs() {
  local entry d fp normalized=""
  [ -n "$seen_docs" ] || return 0
  IFS=',' read -ra entries <<< "$seen_docs"
  for entry in "${entries[@]}"; do
    [ -n "$entry" ] || continue
    if [[ "$entry" == *@* ]]; then
      d="${entry%@*}"
      fp="${entry##*@}"
    else
      d="$entry"
      [ -f "$HANDOFF_DIR/$d" ] || continue
      fp="$(handoff_fingerprint "$d")"
    fi
    if [ -n "$normalized" ]; then normalized="$normalized,$d@$fp"; else normalized="$d@$fp"; fi
  done
  seen_docs="$normalized"
}

mark_seen() {
  local fp
  fp="$(handoff_fingerprint "$1")"
  if [ -n "$seen_docs" ]; then seen_docs="$seen_docs,$1@$fp"; else seen_docs="$1@$fp"; fi
}

is_new_doc() {
  # $1 = basename; true if absent or changed since last processing.
  local fp
  fp="$(handoff_fingerprint "$1")"
  case ",$seen_docs," in
    *",$1@$fp,"*) return 1 ;;
    *) return 0 ;;
  esac
}

newest_unprocessed() {
  local d
  for d in $(handoff_docs); do
    if is_new_doc "$d"; then echo "$d"; return 0; fi
  done
  return 1
}

session_tree() {
  local active child parent ids="$1" changed=1
  active="$(api get /api/session/active 2>/dev/null | jq -r '.data | keys[]' 2>/dev/null || true)"
  while [ "$changed" -eq 1 ]; do
    changed=0
    for child in $active; do
      case ",$ids," in *",$child,"*) continue ;; esac
      parent="$(api get "/api/session/$child" 2>/dev/null | jq -r '.data.parentID // ""' 2>/dev/null || true)"
      case ",$ids," in
        *",$parent,"*) ids="$ids $child"; changed=1 ;;
      esac
    done
  done
  printf '%s\n' $ids
}
pending_forms() {
  local session
  while read -r session; do
    [ -n "$session" ] || continue
    api get "/api/session/$session/form" 2>/dev/null |
      jq -r '.data[] | select(.metadata.kind == "question") | .id' 2>/dev/null || true
  done < <(session_tree "$1")
}
pending_perms() {
  local session
  while read -r session; do
    [ -n "$session" ] || continue
    api get "/api/session/$session/permission" 2>/dev/null |
      jq -r '.data[] | .id' 2>/dev/null || true
  done < <(session_tree "$1")
}
# active is an OBJECT keyed by session id ({sid: {type: ...}}), not an array of {id:...}
# objects — has($s) is the membership test (the old any(.id == $s) never matched, so a
# live session read as dead).
session_alive() { api get "/api/session/active" 2>/dev/null | jq -e --arg s "$1" '.data | has($s)' >/dev/null 2>&1; }
# Live chain workers: active sessions titled "wayfinder-loop: <doc>", from any daemon
# generation. --bootstrap must refuse while one exists: killing the old tmux releases
# the flock but orphans its worker, and bootstrapping the same packet then spawns a
# second live session on the chain (the 2026-09-04 duplicate class).
wayfinder_workers() {
  local id title
  for id in $(api get /api/session/active 2>/dev/null | jq -r '.data | keys[]' 2>/dev/null || true); do
    title="$(api get "/api/session/$id" 2>/dev/null | jq -r '.data.title // ""' 2>/dev/null || true)"
    case "$title" in
      wayfinder-loop:*) printf '%s\n' "$id" ;;
    esac
  done
}
# active_children: true when any ACTIVE session lists $1 as its parent — the session is
# awaiting parallel sub-agent results (the phased-review-loop shape: P1–P4 run as 4 child
# sessions). Its own time.updated legitimately lags while the children do the work, so the
# zombie detector must not treat the wait as a stall.
active_children() {
  local active child
  active="$(api get "/api/session/active" 2>/dev/null | jq -r '.data | keys[]' 2>/dev/null || true)"
  [ -n "$active" ] || return 1
  for child in $active; do
    [ "$child" = "$1" ] && continue
    if [ "$(api get "/api/session/$child" 2>/dev/null | jq -r '.data.parentID // ""' 2>/dev/null || true)" = "$1" ]; then
      return 0
    fi
  done
  return 1
}

# True when the session's latest turn still has a tool call in flight. A long
# compile/test/load run produces no model tokens while it executes — that is
# work in progress, never a stall.
has_running_tool() {
  api get "/api/session/$1/message?limit=1" 2>/dev/null |
    jq -e '([.data[-1].content[]? | select(.type == "tool" and
        ((.state.status // "") == "running" or (.state.status // "") == "pending"))] | length) > 0' >/dev/null 2>&1
}

# Id of the session's newest message ("" when unknown).
latest_message_id() {
  api get "/api/session/$1/message?limit=1" 2>/dev/null | jq -r '.data[-1].id // ""' 2>/dev/null || true
}

# True when any assistant turn completed AFTER base message id $2 ended with
# tool calls — the recovery prompt bought execution work rather than another
# parked prose stop. Token growth alone cannot decide this: every futile
# stop-cycle also grows the transcript.
tool_work_since() {
  api get "/api/session/$1/message?limit=15" 2>/dev/null | jq -e --arg base "$2" '
    ([.data[]]) as $all
    | ($all | to_entries | map(select(.value.id == $base)) | first | .key // -1) as $cut
    | [$all[($cut + 1):][]?
       | select((.type // .role) == "assistant" and .time.completed != null and .finish == "tool-calls")]
      | length > 0' >/dev/null 2>&1
}

# Return completed assistant message id only when its turn stopped without a tool call.
# A completed `tool-calls` turn is normal — the next model turn follows it. A completed
# `stop`/`length`/`error` turn with no handoff is the unattended-chain failure: prompt it
# immediately instead of waiting for the 540-second zombie threshold.
stopped_assistant_message() {
  api get "/api/session/$1/message?limit=1" 2>/dev/null |
    jq -r '
      .data[-1] |
      if ((.type // .role) != "assistant") or
         (.time.completed == null) or
         ((.finish // "") == "") or
         .finish == "tool-calls" then ""
      elif ([.content[]? | select(.type == "tool" and
          ((.state.status // "") == "running" or (.state.status // "") == "pending"))] | length) > 0 then ""
      else .id // ""
      end
    ' 2>/dev/null || true
}

# Return a completed assistant error as "message-id<TAB>type: message", empty when the
# last message is not an errored assistant turn. The id lets the supervisor dedupe its
# recovery prompt (one nudge per failed turn, never a loop on the same message).
assistant_error() {
  api get "/api/session/$1/message?limit=1" 2>/dev/null |
    jq -r '
      .data[-1] |
      if ((.type // .role) == "assistant") and
         (.time.completed != null) and
         ((.finish // "") == "error") then
        ((.id // "") + "\t" + (.error.type // "assistant.error") + ": " + (.error.message // "unknown error"))
      else ""
      end
    ' 2>/dev/null || true
}

spawn_session() {
  local doc="$1" sid
  if [ -n "$DRY_RUN" ]; then
    log "DRY-RUN: would spawn session for $doc"
    return 1
  fi
  if [ -z "${WAYFINDER_ALLOW_DIRTY:-}" ]; then
    if [ -n "$session_id" ]; then
      dirty_owner_recovery || wait_for_clean_handoff "$doc"
    else
      # Bootstrap first spawn: no owner session to nudge — manual gate.
      wait_for_clean_handoff "$doc"
    fi
  fi
  local model_ref="null" pid model_id model_provider
  if [ -n "${WAYFINDER_MODEL:-}" ]; then
    # Split on the FIRST slash only: model ids may contain slashes.
    model_id="${WAYFINDER_MODEL#*/}"
    model_provider=""
    [[ "$WAYFINDER_MODEL" == */* ]] && model_provider="${WAYFINDER_MODEL%%/*}"
    # `|| true` keeps the die below reachable: under `set -e`, a failing pipeline would abort
    # the whole script BEFORE the guard (silent chain death — no FATAL log, no push).
    # /api/model has grown past 64KB and `opencode2 api` truncates piped stdout at 64KB,
    # so jq always saw malformed JSON and the lookup always failed (the 2026-08-21
    # bootstrap FATAL on a configured model. Buffer through a temp file.
    local models_json
    models_json="$(mktemp)"
    pid=""
    for _model_try in 1 2 3; do
      api get /api/model > "$models_json" 2>/dev/null || true
      pid="$(jq -r --arg id "$model_id" --arg provider "$model_provider" '.data[] | select(.id == $id) | select(($provider == "") or (.providerID == $provider)) | .providerID' "$models_json" 2>/dev/null | head -1)" || true
      [ -n "$pid" ] && break
      log "model lookup miss for '$WAYFINDER_MODEL' (try $_model_try/3) — retrying"
      sleep 10
    done
    rm -f "$models_json"
    [ -n "$pid" ] || die "WAYFINDER_MODEL '$WAYFINDER_MODEL' lookup failed via /api/model (model/provider absent, or the API errored)"
    model_ref="$(jq -nc --arg id "$model_id" --arg p "$pid" --arg v "$VARIANT" '{id: $id, providerID: $p, variant: $v}')"
  fi
  sid="$(api post /api/session --data "$(jq -nc --arg d "$doc" --arg dir "$REPO" --argjson ref "$model_ref" \
    '{title: ("wayfinder-loop: " + $d), location: {directory: $dir}, model: $ref}')" | jq -r '.data.id' || true)"
  [ -n "$sid" ] && [ "$sid" != "null" ] || die "session create failed for $doc"
  log "created $sid for $doc${WAYFINDER_MODEL:+ (model $WAYFINDER_MODEL)}"
  # Disk floor gate: a session started half-full of disk dies mid-build (the session-176
  # /tmp .so fill) — hold the spawn and ping until the space is back, then proceed.
  local free_gb
  free_gb="$(free_disk_gb)"
  if [ -n "$free_gb" ] && [ "$free_gb" -lt "$DISK_FLOOR_GB" ]; then
    log "free disk ${free_gb}G < ${DISK_FLOOR_GB}G — spawn paused for $doc"
    notify "wayfinder paused" "low disk (${free_gb}G free) — free space on the VPS (see /tmp, composeApp/build, ~/.gradle); the chain resumes automatically"
    while [ -n "$(free_disk_gb)" ] && [ "$(free_disk_gb)" -lt "$DISK_FLOOR_GB" ]; do sleep 60; done
    log "disk space recovered — spawn resumes"
  fi
  session_id="$sid"
  save_state
  local prompt
  prompt="/wayfinder .wayfinder/handoffs/$doc"
  api post "/api/session/$sid/prompt" --data "$(jq -nc --arg t "$prompt" '{text: $t}')" >/dev/null || die "prompt failed for session $sid"
  # Fingerprint the doc AS SPAWNED: this baseline makes only edits AFTER the
  # spawn count as activity — a later revision of the same filename chains as
  # the successor link on session exit, while the untouched packet never re-fires.
  mark_seen "$doc"
  # Advancement baseline (#578): snapshot the map now so the completion gate
  # can tell work from exhaust. External movement between sessions clears
  # stale strikes here; an unreadable tracker pauses accounting, never parks.
  progress_note_baseline "$doc"
  save_state
  log "spawned $sid reading $doc"
  notify "wayfinder session started" "session $sid — reading $doc"
}

supervise_session() {
  # Short-tick poll loop. Decouples form/permission detection from the blocking
  # /wait: the old structure checked pending_forms/pending_perms ONLY after
  # wait_idle (a 180s blocking POST /wait) returned, so a question asked and
  # answered inside one wait slice was never observed — "wayfinder needs you"
  # never fired for quick questions (the session-74 live probe: a ~5s question
  # produced no ping). Every tick checks completion/forms/perms fresh, so a
  # pending question pings within TICK_SECS even if answered moments later.
  local notified=0 notified_perm=0 outages=0 idle_secs=0 last_prog=0 last_updated=0
  local upd prog d f p sess stop_message not_alive_ticks=0 disk_notified=0 free_gb="" last_stop_message="" last_err_id="" stop_nudges=0
  # work_msg_id = newest-message id recorded when the latest recovery prompt
  # fired. Any completed tool-call turn after it proves the nudge bought real
  # work and clears the fruitless-attempt budget — see the reset block below.
  local work_msg_id=""
  while :; do
    sleep "$TICK_SECS"
    # Detached verification belongs to the daemon, not the supervised worker.
    # Poll before handoff/session handling so a pushed HEAD is queued even while
    # the worker is still doing implementation work.
    ensure_local_ci_run
    # completion first: a finished session signals via handoff activity — either
    # a new packet file or an in-place revision of its own packet
    d="$(newest_unprocessed || true)"
    if [ -n "$d" ]; then
      if [ "$d" = "$last_doc" ]; then
        # In-place revision of the CURRENT link's own packet = the successor link.
        # Content change under handoffs/ is the work-item signal; the filename is
        # not. Spawn waits for session exit below, so the 2026-08-22 double-spawn
        # class (edit between detection and spawn firing a concurrent chain) cannot
        # recur — there is exactly one supervised session and one pending slot.
        log "handoff $d revised in place — revision chains as the successor link"
      fi
      pending_doc="$d"
      retries=0
      save_state
      wait_for_session_exit
      [ -f "$HANDOFF_DIR/$pending_doc" ] || die "pending handoff disappeared: $pending_doc"
      log "session $session_id completed; next handoff: $pending_doc"
      notify "wayfinder session done" "handoff written: $pending_doc — starting next"
      # NOT marked seen here: spawn_session marks the doc AS SPAWNED, after its
      # prompt lands. Marking earlier opened a crash window (2026-08-22: daemon
      # killed while the dirty-tree gate held the spawn — packet swallowed,
      # never requeued, supervisor resumed a finished session instead).
      last_doc="$pending_doc"
      pending_doc=""
      save_state
      # Advancement gate (#578): park starved/poison chains instead of
      # respawning them. Returns after a park-watch wake with last_doc set.
      progress_note_completion "$last_doc" || { log "dry-run: chain would continue"; exit 0; }
      spawn_session "$last_doc" || { log "dry-run: chain would continue"; exit 0; }
      # keep supervising the freshly spawned session (the old `return 0` left it
      # to wait_for_doc, which only picks sessions up after their handoff lands —
      # session-176 ran ~5h unsupervised until a manual daemon restart)
       notified=0; notified_perm=0; outages=0; idle_secs=0; last_prog=0; last_updated=0; not_alive_ticks=0; disk_notified=0; last_stop_message=""; last_err_id=""; stop_nudges=0
      work_msg_id=""
      continue
    fi

    # disk floor tripwire: a mid-session fill (the session-176 /tmp .so class, or a
    # runaway build dir) kills the build with no signal — ping once per crossing,
    # keep supervising (the daily .so timer is the auto-recovery).
    if [ "$disk_notified" -eq 0 ]; then
      free_gb="$(free_disk_gb)"
      if [ -n "$free_gb" ] && [ "$free_gb" -lt "$DISK_FLOOR_GB" ]; then
        notify "wayfinder low disk" "session $session_id running on ${free_gb}G free — free space or builds will start failing"
        disk_notified=1
      fi
    elif [ -n "$(free_disk_gb)" ] && [ "$(free_disk_gb)" -ge "$DISK_FLOOR_GB" ]; then
      log "disk space recovered — supervisor resumes watching"
      disk_notified=0
    fi

    # pending question / permission — notify-once each until answered
    f="$(pending_forms "$session_id")"
    p="$(pending_perms "$session_id")"
    if [ -n "$f" ] && [ "$notified" -eq 0 ]; then
      notify "wayfinder needs you" "session $session_id is asking a question — attach TUI and answer"
      notified=1
    elif [ -z "$f" ] && [ "$notified" -eq 1 ]; then
      log "session $session_id question answered — resuming"
      notified=0
    fi
    if [ -n "$p" ] && [ "$notified_perm" -eq 0 ]; then
      notify "wayfinder needs permission" "session $session_id blocked on a permission request — attach TUI to approve"
      notified_perm=1
    elif [ -z "$p" ] && [ "$notified_perm" -eq 1 ]; then
      log "session $session_id permission granted — resuming"
      notified_perm=0
    fi
    # a parked question/permission is not a stall — skip the detector while one is open
    if [ -n "$f" ] || [ -n "$p" ]; then
      idle_secs=0
      continue
    fi

    # API health + session state: one fetch per tick, reused below. Fetch
    # failing while the service answers = session gone (respawn fresh); failing
    # while the service itself is down = transient outage (count, notify at 3,
    # retry — don't kill a healthy session on a blip).
    sess="$(api get "/api/session/$session_id" 2>/dev/null || true)"
    if [ -z "$sess" ] && ! api get "/api/session/active" >/dev/null 2>&1; then
      outages=$((outages+1))
      if [ "$outages" -eq 3 ]; then
        notify "opencode2 API unstable" "daemon will keep retrying — check 'opencode2 service status'"
        outages=0
      fi
      sleep 30
      continue
    fi
    # Proof-of-death gate (#578): the direct GET answers for historical
    # sessions too, so only proven /active absence (with a blip filter) may
    # fresh-respawn. Anything else — listed, child-running, or unreadable —
    # is alive-or-unknown and keeps supervision.
    if session_gone "$session_id" && ! active_children "$session_id"; then
      not_alive_ticks=$(( ${not_alive_ticks:-0} + 1 ))
      if [ "$not_alive_ticks" -ge 2 ]; then
        not_alive_ticks=0
        session_dead fresh
        continue
      fi
    else
      not_alive_ticks=0
    fi
    outages=0

    # Progress clears the fruitless-attempt budget: a recovery prompt that
    # bought real work must not count toward the pause caps. The signal is a
    # completed tool-call turn newer than the nudge-time message — the futile
    # cycle (nudge -> prose stop -> nudge) never produces one and stays capped.
    if [ -n "${work_msg_id:-}" ] && tool_work_since "$session_id" "$work_msg_id"; then
      log "session $session_id worked since last recovery prompt — fruitless-attempt budget cleared"
      work_msg_id=""
      stop_nudges=0
      last_stop_message=""
      retries=0
      save_state
    fi

    # Assistant errors split by recoverability. provider.invalid-output is the truncated-
    # stream class (the free model ending a turn mid-stream): a canonical NUDGE resumes it —
    # manual continuations after every 2026-08-22 truncation worked, while each pause took
    # the whole chain down. Deduped per message id (one nudge per failed turn) and UNBOUNDED:
    # this class never exhausts a retry budget and never pauses the chain — the daemon keeps
    # nudging for as long as the provider keeps truncating. Every other error class stays
    # terminal: prompting cannot repair auth/quota-style failures.
    assistant_failure="$(assistant_error "$session_id")"
    if [ -n "$assistant_failure" ]; then
      err_id="${assistant_failure%%$'\t'*}"
      err_text="${assistant_failure#*$'\t'}"
      case "$err_text" in
        provider.invalid-output:*)
          if [ "$err_id" != "$last_err_id" ]; then
            if api post "/api/session/$session_id/prompt" --data "$(jq -nc --arg t "$NUDGE" '{text: $t}')" >/dev/null 2>&1; then
              last_err_id="$err_id"
              work_msg_id="$(latest_message_id "$session_id")"
              log "session $session_id hit a transient provider error — sent recovery prompt: $err_text"
              notify "wayfinder continuing" "session $session_id hit a truncated provider response — recovery sent"
            else
              log "recovery prompt failed for $session_id — retrying next tick"
            fi
          fi
          continue
          ;;
        *)
          log "session $session_id ended with assistant error — chain paused: $err_text"
          notify "wayfinder chain paused" "session $session_id failed: $err_text"
          exit 0
          ;;
      esac
    fi

    # Immediate stop detector: a final assistant turn without a handoff is not a healthy
    # parked state. Send the same continuation prompt used by the slower zombie path as soon
    # as the completed message appears; the stop_nudges cap bounds the cycle.
    stop_message="$(stopped_assistant_message "$session_id")"
    if [ -n "$stop_message" ] && [ "$stop_message" != "$last_stop_message" ]; then
      # Bounded per fruitless streak: each nudge mints a NEW stopped message (new id), so
      # message-id dedupe alone cannot stop a parked-state cycle — the 2026-08-23 loop ran
      # 570 nudges. The count resets whenever the session shows real tool work since its
      # last nudge (the progress block above), so only BACK-TO-BACK fruitless continuations
      # reach this cap; then pause the chain and page the operator.
      if [ "$stop_nudges" -ge 2 ]; then
        log "chain paused: $session_id stopped without handoff after $stop_nudges continuation prompts"
        notify "wayfinder chain paused" "session $session_id keeps stopping without a handoff — attach TUI to check, then run: ./tools/wayfinder/wayfinder-loop.sh --retry"
        exit 0
      fi
      stop_nudges=$((stop_nudges + 1))
      work_msg_id="$(latest_message_id "$session_id")"
      if api post "/api/session/$session_id/prompt" --data "$(jq -nc --arg t "$NUDGE" '{text: $t}')" >/dev/null 2>&1; then
        last_stop_message="$stop_message"
        log "session $session_id stopped without handoff at $stop_message — sent immediate continuation prompt"
        notify "wayfinder continuing" "session $session_id stopped without handoff — continuation sent"
      else
        log "immediate continuation prompt failed for $session_id — zombie detector remains active"
      fi
      continue
    fi

    # liveness: session provably gone with no new doc = died without a
    # handoff — resume it in place. Dual-signal (#578): a missing /active
    # entry alone is not death while the direct GET still answers (API blips
    # and user-input pauses both look like silence). Grace period: a
    # just-spawned session may take a few ticks to appear in /active; and a
    # parent awaiting parallel sub-agents (the phased-review-loop shape) also
    # leaves the set — never resume while its children run.
    if session_gone "$session_id"; then
      not_alive_ticks=$((not_alive_ticks + 1))
      if [ "$not_alive_ticks" -ge 12 ] && ! active_children "$session_id"; then
        not_alive_ticks=0
        work_msg_id="$(latest_message_id "$session_id")"
        session_dead resume
      fi
      continue
    fi
    not_alive_ticks=0

    # zombie detection: a live loop advances its token counters as work lands
    # (tool results, model output, reasoning — time.updated is not a reliable
    # signal: this daemon bumps it only on USER-message landings, so an actively
    # working session shows a frozen time.updated). STALL_SECS without ANY
    # progress = stalled, resume it in place.
    prog="$(printf '%s' "$sess" | jq -r '(.data.tokens.input // 0) + (.data.tokens.output // 0) + (.data.tokens.reasoning // 0)' 2>/dev/null || echo -1)"
    upd="$(printf '%s' "$sess" | jq -r '.data.time.updated // -1' 2>/dev/null || echo -1)"
    # stalled only when BOTH the token fingerprint and time.updated are unchanged
    if [ "$prog" -ge 0 ] && [ "$last_prog" -gt 0 ] && [ "$prog" -eq "$last_prog" ] &&
       [ "$upd" -ge 0 ] && [ "$last_updated" -gt 0 ] && [ "$upd" -eq "$last_updated" ]; then
      idle_secs=$((idle_secs + TICK_SECS))
    else
      idle_secs=0
    fi
    [ "$prog" -ge 0 ] && last_prog="$prog"
    [ "$upd" -ge 0 ] && last_updated="$upd"
    if [ "$idle_secs" -ge "$STALL_SECS" ]; then
      # A parent session awaiting parallel sub-agents (the phased-review-loop shape) does not
      # advance its own counters while its children run — not a stall. Reset the tick counter
      # and keep waiting; the resume path (session_dead resume) must not fire on sub-agent work.
      if active_children "$session_id"; then
        idle_secs=0
        continue
      fi
      # A long build/test/load run inside one tool call also freezes both counters
      # for its whole duration — work in progress, never a stall (the detector
      # above only sees model tokens, which nothing emits mid-execution).
      if has_running_tool "$session_id"; then
        idle_secs=0
        continue
      fi
      log "session $session_id stalled (no activity across $STALL_SECS seconds) — resuming in place"
      idle_secs=0
      work_msg_id="$(latest_message_id "$session_id")"
      session_dead resume
      continue
    fi
  done
}

session_dead() {
  # resume: the session still exists but stalled/interrupted — ask it to continue in place
  # fresh: session gone (404) — spawn a new one from the handoff doc
  # Bounded at 2 CONSECUTIVE attempts each: the supervise loop clears the counter
  # whenever the session shows real work since its last recovery prompt, so only a
  # session that stays wedged or dies repeatedly with zero progress reaches the cap —
  # that is a wedge or a poison packet: pause the chain and page the operator instead
  # of nudging forever (the 2026-08-23 parked-ticket nudge loop ran 570 cycles before
  # a human noticed).
  local mode="${1:-fresh}"
  if [ "$mode" = "resume" ]; then
    if [ "${retries:-0}" -ge 2 ]; then
      log "chain paused: $session_id stalled after $retries resume attempts"
      notify "wayfinder chain paused" "session $session_id stalled after 2 resume attempts — attach TUI to check it, kill it if wedged, then run: ./tools/wayfinder/wayfinder-loop.sh --retry"
      exit 0
    fi
    retries=$(( ${retries:-0} + 1 ))
    save_state
    log "resuming stalled session $session_id (attempt $retries/2)"
    notify "wayfinder resuming" "session $session_id stalled — asking it to continue where it left off"
    if ! api post "/api/session/$session_id/prompt" --data "$(jq -nc --arg t "$NUDGE" '{text: $t}')" >/dev/null 2>&1; then
      log "resume prompt failed for $session_id — falling back to fresh spawn"
      session_dead fresh
    fi
    return 0
  fi
  if [ "${retries:-0}" -ge 2 ]; then
    log "chain paused: $session_id died without handoff (retries=$retries)"
    notify "wayfinder chain paused" "session $session_id died without writing a handoff. Run: ./tools/wayfinder/wayfinder-loop.sh --retry"
    exit 0
  fi
  retries=$(( ${retries:-0} + 1 ))
  session_id=""
  save_state
  log "session died without handoff — respawn for $last_doc (retry $retries/2)"
  notify "wayfinder retrying" "session died — spawning a fresh session for $last_doc (retry $retries/2)"
  spawn_session "$last_doc" || { log "dry-run retry"; exit 0; }
}

wait_for_doc() {
  local d
  while :; do
    ensure_local_ci_run
    d="$(newest_unprocessed || true)"
    if [ -n "$d" ]; then
      # settle: the file may still be mid-write
      sleep 10
      last_doc="$d"
      mark_seen "$d"
      save_state
      if spawn_session "$d"; then
        return 0
      fi
      log "dry-run: would continue supervising $d"
      exit 0
    fi
    sleep "$POLL_SECS"
  done
}

[ -n "$OC_BIN" ] || die "opencode2 binary not found"
[ -d "$HANDOFF_DIR" ] || die ".wayfinder/handoffs not found at $HANDOFF_DIR (create it; handoff packets are gitignored runtime state)"
command -v jq >/dev/null 2>&1 || die "jq not found"

load_state
normalize_seen_docs

# One-shot fixture probe used by the script-level watcher tests. It exercises
# the same poll path without creating a session or asking an active agent to
# inspect CI state.
if [ "${1:-}" = "--local-ci-once" ]; then
  ensure_local_ci_run
  save_state
  exit 0
fi

ensure_local_ci_run

if [ "${1:-}" = "--bootstrap" ]; then
  [ $# -ge 2 ] || die "--bootstrap requires <doc> (a .wayfinder/handoffs/ packet filename)"
  # Accept either a handoff basename or a path copied from a log/prompt.
  doc="${2##*/}"
  [ -f "$HANDOFF_DIR/$doc" ] || die "bootstrap doc not found: $HANDOFF_DIR/$doc"
  # A killed daemon's worker stays alive: flock dies with the tmux session, so the
  # lock cannot catch kill-plus-bootstrap. Refuse while any chain worker runs —
  # plain restart resumes supervision of the live chain instead.
  workers="$(wayfinder_workers)"
  [ -z "$workers" ] || die "live chain worker(s) still active ($(echo "$workers" | tr '\n' ' ')) — resume supervision instead of --bootstrap, or stand them down first (see docs/agents/wayfinder-loop.md)"
  # Seed every existing handoff by content, not basename. A later session may
  # overwrite an existing numbered handoff filename.
  seen_docs="$(seed_seen_docs)"
  last_doc="$doc"
  mark_seen "$doc"
  retries=0
  progress_map=""; progress_fp=""; progress_strikes=0; progress_parked=""; progress_gate=""; progress_park_issue=""
  progress_park_class=""; progress_gate_fp=""
  save_state
  log "bootstrap with $doc — spawning first session"
  if ! spawn_session "$doc"; then
    log "dry-run bootstrap complete"
    exit 0
  fi
  supervise_session
elif [ "${1:-}" = "--resume" ]; then
  if [ $# -ge 2 ]; then
    session_id="$2"
  fi
  [ -n "$session_id" ] || die "--resume needs a session id (state has none; pass it: --resume <session-id>)"
  api get "/api/session/$session_id" >/dev/null 2>&1 || die "--resume: session $session_id not found"
  retries=0
  save_state
  log "manual resume of $session_id"
  # Manual resume is an in-place recovery path: it posts THE canonical recovery prompt,
  # same as the automatic detectors (#355) — no independent manual-resume wording.
  api post "/api/session/$session_id/prompt" --data "$(jq -nc --arg t "$NUDGE" '{text: $t}')" >/dev/null || die "resume prompt failed for $session_id"
  supervise_session
elif [ "${1:-}" = "--retry" ]; then
  [ -n "$last_doc" ] || die "--retry needs a processed doc in state (bootstrap first)"
  # One-worker/one-packet invariant (#355): --retry fresh-spawns ONLY a confirmed-gone
  # session. A stalled-but-existing worker must be resumed in place (--resume), never
  # silently duplicated — two live sessions on one packet is the duplicate-session
  # class the daemon playbook warns about.
  if [ -n "$session_id" ] && { session_alive "$session_id" || active_children "$session_id"; }; then
    die "session $session_id still exists — resume it in place (--resume) or kill it, then retry"
  fi
  session_id=""
  retries=0
  if [ -n "${progress_parked:-}" ] || [ -n "${progress_park_issue:-}" ]; then
    progress_park_issue resume "${progress_parked:-$last_doc}" "" "manual --retry" || true
  fi
  progress_parked=""; progress_strikes=0; progress_park_class=""; progress_gate=""; progress_gate_fp=""
  save_state
  log "manual retry for $last_doc (prior session confirmed gone)"
  spawn_session "$last_doc" || exit 0
  supervise_session
else
  [ -n "$last_doc" ] || die "no state — first run needs: --bootstrap <doc>"
  # Parked chains resume their watch, not a spawn (#578): the tracker, not a
  # timer, decides when work exists again. progress_park_watch returns with
  # last_doc set to the packet to resume.
  if [ -n "${progress_parked:-}" ] && [ -f "$HANDOFF_DIR/$progress_parked" ]; then
    last_doc="$progress_parked"
    save_state
    log "daemon restart resumes parked watch for $last_doc"
    progress_park_watch
    spawn_session "$last_doc" || exit 0
    supervise_session
  fi
  if [ -n "$session_id" ]; then
    log "resuming supervision of $session_id"
    supervise_session
  fi
  wait_for_doc
  supervise_session
fi
