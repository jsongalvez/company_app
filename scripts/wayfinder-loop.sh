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
#   WAYFINDER_POLL_SECS    doc poll interval (default 15)
#   WAYFINDER_TICK_SECS    session poll interval — form/permission/completion checks (default 5)
#   WAYFINDER_STALL_SECS   no-progress zombie threshold in seconds (default 540 = old WAIT_SECS×STALL_SLICES)
#   WAYFINDER_DRY_RUN      non-empty = log transitions, never spawn
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
HANDOFF_DIR="$REPO/.wayfinder/handoffs"
STATE_FILE="$REPO/.wayfinder-loop.state"
LOG_FILE="$REPO/.wayfinder-loop.log"
[ -f "$REPO/.wayfinder-loop.env" ] && set -a && . "$REPO/.wayfinder-loop.env" && set +a
NTFY_TOPIC="${WAYFINDER_NTFY_TOPIC:-}"
POLL_SECS="${WAYFINDER_POLL_SECS:-15}"
TICK_SECS="${WAYFINDER_TICK_SECS:-5}"
STALL_SECS="${WAYFINDER_STALL_SECS:-540}"
# Free-disk floor in GiB — below it the chain pings instead of silently wedging
# (the session-176 class: bun .so extractions filled /tmp, builds started dying
# with no signal). The daily tmp-bun-so-clean.timer + manual build-dir cleanup
# are the recovery; the loop is the tripwire.
DISK_FLOOR_GB="${WAYFINDER_DISK_FLOOR_GB:-5}"
DRY_RUN="${WAYFINDER_DRY_RUN:-}"
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
  [ -f "$STATE_FILE" ] || return 0
  # shellcheck disable=SC1090
  source "$STATE_FILE"
}

save_state() {
  {
    echo "last_doc=$last_doc"
    echo "session_id=$session_id"
    echo "pending_doc=${pending_doc:-}"
    echo "retries=${retries:-0}"
    echo "seen_docs=$seen_docs"
  } > "$STATE_FILE"
}

wait_for_session_exit() {
  local notified=0
  while session_alive "$session_id" || active_children "$session_id"; do
    if [ "$notified" -eq 0 ]; then
      log "handoff $pending_doc detected; waiting for session $session_id to exit before spawning"
      notify "wayfinder waiting" "handoff $pending_doc is ready; waiting for session $session_id to finish"
      notified=1
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
    api get /api/model > "$models_json" 2>/dev/null || true
    pid="$(jq -r --arg id "$model_id" --arg provider "$model_provider" '.data[] | select(.id == $id) | select(($provider == "") or (.providerID == $provider)) | .providerID' "$models_json" 2>/dev/null | head -1)" || true
    rm -f "$models_json"
    [ -n "$pid" ] || die "WAYFINDER_MODEL '$WAYFINDER_MODEL' lookup failed via /api/model (model/provider absent, or the API errored)"
    model_ref="$(jq -nc --arg id "$model_id" --arg p "$pid" '{id: $id, providerID: $p, variant: "max"}')"
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
    if [ -z "$sess" ]; then
      if api get "/api/session/active" >/dev/null 2>&1; then
        session_dead fresh
      else
        outages=$((outages+1))
        if [ "$outages" -eq 3 ]; then
          notify "opencode2 API unstable" "daemon will keep retrying — check 'opencode2 service status'"
          outages=0
        fi
        sleep 30
      fi
      continue
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
        notify "wayfinder chain paused" "session $session_id keeps stopping without a handoff — attach TUI to check, then run: ./scripts/wayfinder-loop.sh --retry"
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

    # liveness: session left the active set with no new doc = died without a
    # handoff — resume it in place. Grace period: a just-spawned session may
    # take a few ticks to appear in /active; and a parent awaiting parallel
    # sub-agents (the phased-review-loop shape) also leaves the set — never
    # resume while its children run.
    if ! session_alive "$session_id"; then
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
      notify "wayfinder chain paused" "session $session_id stalled after 2 resume attempts — attach TUI to check it, kill it if wedged, then run: ./scripts/wayfinder-loop.sh --retry"
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
    notify "wayfinder chain paused" "session $session_id died without writing a handoff. Run: ./scripts/wayfinder-loop.sh --retry"
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

if [ "${1:-}" = "--bootstrap" ]; then
  [ $# -ge 2 ] || die "--bootstrap requires <doc> (a .wayfinder/handoffs/ packet filename)"
  # Accept either a handoff basename or a path copied from a log/prompt.
  doc="${2##*/}"
  [ -f "$HANDOFF_DIR/$doc" ] || die "bootstrap doc not found: $HANDOFF_DIR/$doc"
  # Seed every existing handoff by content, not basename. A later session may
  # overwrite an existing numbered handoff filename.
  seen_docs="$(seed_seen_docs)"
  last_doc="$doc"
  mark_seen "$doc"
  retries=0
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
  save_state
  log "manual retry for $last_doc (prior session confirmed gone)"
  spawn_session "$last_doc" || exit 0
  supervise_session
else
  [ -n "$last_doc" ] || die "no state — first run needs: --bootstrap <doc>"
  if [ -n "$session_id" ]; then
    log "resuming supervision of $session_id"
    supervise_session
  fi
  wait_for_doc
  supervise_session
fi
