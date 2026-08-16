#!/usr/bin/env bash
# wayfinder-loop.sh — unattended wayfinder chain driver for opencode2.
#
# Watches docs/agents/ for new wayfinder-*-handoff.md files (the chain's
# completion signal), spawns a fresh 0-context opencode2 session that reads
# the newest handoff and drives the next session per its instructions, and
# notifies the human when the agent parks on a question or the chain breaks.
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
DOCS_DIR="$REPO/docs/agents"
STATE_FILE="$REPO/.wayfinder-loop.state"
LOG_FILE="$REPO/.wayfinder-loop.log"
[ -f "$REPO/.wayfinder-loop.env" ] && set -a && . "$REPO/.wayfinder-loop.env" && set +a
NTFY_TOPIC="${WAYFINDER_NTFY_TOPIC:-}"
POLL_SECS="${WAYFINDER_POLL_SECS:-15}"
TICK_SECS="${WAYFINDER_TICK_SECS:-5}"
STALL_SECS="${WAYFINDER_STALL_SECS:-540}"
DRY_RUN="${WAYFINDER_DRY_RUN:-}"
OC_BIN="${OPENCODE_BIN:-$(command -v opencode2 || command -v opencode || true)}"

log() { printf '%s %s\n' "$(date '+%F %T')" "$*" >> "$LOG_FILE"; echo "$(date '+%T') $*"; }
die() { log "FATAL: $*"; notify "wayfinder-loop FAILED" "$*"; exit 1; }

notify() {
  local title="$1" body="$2"
  log "notify: $title — $body"
  command -v notify-send >/dev/null 2>&1 && notify-send -a wayfinder-loop "$title" "$body" || true
  if [ -n "$NTFY_TOPIC" ]; then
    curl -sf -d "$body" -H "Title: $title" -H "Priority: high" "https://ntfy.sh/$NTFY_TOPIC" >/dev/null 2>&1 || true
  fi
}

handoff_docs() { find "$DOCS_DIR" -maxdepth 1 -name 'wayfinder-*-handoff.md' -printf '%f\n' 2>/dev/null | sort; }

api() { "$OC_BIN" api "$@"; }

load_state() {
  last_doc=""; session_id=""
  [ -f "$STATE_FILE" ] || return 0
  # shellcheck disable=SC1090
  source "$STATE_FILE"
}

save_state() {
  {
    echo "last_doc=$last_doc"
    echo "session_id=$session_id"
    echo "retries=${retries:-0}"
    echo "seen_docs=$seen_docs"
  } > "$STATE_FILE"
}

# seen_docs is a comma-separated list of processed handoff basenames
seen_docs=""
mark_seen() {
  if [ -n "$seen_docs" ]; then seen_docs="$seen_docs,$1"; else seen_docs="$1"; fi
}

is_new_doc() {
  # $1 = basename; true if not in seen_docs
  case ",$seen_docs," in
    *",$1,"*) return 1 ;;
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

pending_forms() { api get "/api/session/$1/form" 2>/dev/null | jq -r '.data[] | select(.metadata.kind == "question") | .id' 2>/dev/null || true; }
pending_perms() { api get "/api/session/$1/permission" 2>/dev/null | jq -r '.data[] | .id' 2>/dev/null || true; }
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

spawn_session() {
  local doc="$1" sid
  if [ -n "$DRY_RUN" ]; then
    log "DRY-RUN: would spawn session for $doc"
    return 1
  fi
  local model_ref="null" pid
  if [ -n "${WAYFINDER_MODEL:-}" ]; then
    # `|| true` keeps the die below reachable: under `set -e`, a failing pipeline would abort
    # the whole script BEFORE the guard (silent chain death — no FATAL log, no push).
    pid="$(api get /api/model 2>/dev/null | jq -r --arg id "$WAYFINDER_MODEL" '.data[] | select(.id == $id) | .providerID' | head -1)" || true
    [ -n "$pid" ] || die "WAYFINDER_MODEL '$WAYFINDER_MODEL' lookup failed via /api/model (model absent, or the API errored)"
    model_ref="$(jq -nc --arg id "$WAYFINDER_MODEL" --arg p "$pid" '{id: $id, providerID: $p}')"
  fi
  sid="$(api post /api/session --data "$(jq -nc --arg d "$doc" --arg dir "$REPO" --argjson ref "$model_ref" \
    '{title: ("wayfinder-loop: " + $d), location: {directory: $dir}, model: $ref}')" | jq -r '.data.id' || true)"
  [ -n "$sid" ] && [ "$sid" != "null" ] || die "session create failed for $doc"
  log "created $sid for $doc${WAYFINDER_MODEL:+ (model $WAYFINDER_MODEL)}"
  if [ -z "$DRY_RUN" ] && [ -z "${WAYFINDER_ALLOW_DIRTY:-}" ]; then
    if [ -n "$(git -C "$REPO" status --porcelain 2>/dev/null | head -1)" ]; then
      log "worktree dirty — spawn paused for $doc"
      notify "wayfinder paused" "worktree has uncommitted changes — commit or stash; the chain resumes automatically"
      while [ -n "$(git -C "$REPO" status --porcelain 2>/dev/null | head -1)" ]; do sleep 60; done
      notify "wayfinder resumed" "worktree clean — spawning $doc"
      log "worktree clean — spawn resumes"
    fi
  fi
  session_id="$sid"
  save_state
  local prompt
  prompt="Fresh session with zero prior context. You are driven by an unattended chain runner.

Read docs/agents/$doc — the latest wayfinder handoff — and follow its \"How to drive the next session\" instructions exactly (load the wayfinder skill, claim before work, one ticket per session, map #89 discipline).

Operating rules for this automated run:
1. Work autonomously as far as you can. Prefer AFK-capable work from the map's own \"Recommended next pick\" when no human decision is truly needed.
2. If you need a human decision (grilling, fog-graduation pick, HITL design), ask via the question tool and WAIT. Do not guess.
3. When done, write docs/agents/wayfinder-<N>-handoff.md (next session number) following the existing format. That file is the chain's completion signal — it MUST exist before you stop.
4. Then stop. Do not start follow-up work."
  api post "/api/session/$sid/prompt" --data "$(jq -nc --arg t "$prompt" '{text: $t}')" >/dev/null || die "prompt failed for session $sid"
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
  local upd prog d f p sess not_alive_ticks=0
  while :; do
    sleep "$TICK_SECS"
    # completion first: a finished session writes its handoff doc as its final act
    d="$(newest_unprocessed || true)"
    if [ -n "$d" ]; then
      log "session $session_id completed; next handoff: $d"
      notify "wayfinder session done" "handoff written: $d — starting next"
      last_doc="$d"
      mark_seen "$d"
      retries=0
      save_state
      spawn_session "$d" || { log "dry-run: chain would continue"; exit 0; }
      # keep supervising the freshly spawned session (the old `return 0` left it
      # to wait_for_doc, which only picks sessions up after their handoff lands —
      # session-176 ran ~5h unsupervised until a manual daemon restart)
      notified=0; notified_perm=0; outages=0; idle_secs=0; last_prog=0; last_updated=0; not_alive_ticks=0
      continue
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

    # liveness: session left the active set with no new doc = died without a
    # handoff — resume it in place. Grace period: a just-spawned session may
    # take a few ticks to appear in /active; and a parent awaiting parallel
    # sub-agents (the phased-review-loop shape) also leaves the set — never
    # resume while its children run.
    if ! session_alive "$session_id"; then
      not_alive_ticks=$((not_alive_ticks + 1))
      if [ "$not_alive_ticks" -ge 12 ] && ! active_children "$session_id"; then
        not_alive_ticks=0
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
    upd="$(printf '%s' "$sess" | jq -r '.data.time.updated' 2>/dev/null || echo -1)"
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
      log "session $session_id stalled (no activity across $STALL_SECS seconds) — resuming in place"
      idle_secs=0
      session_dead resume
      continue
    fi
  done
}

session_dead() {
  # resume: the session still exists but stalled/interrupted — ask it to continue in place
  # fresh: session gone (404) — spawn a new one from the handoff doc
  local mode="${1:-fresh}"
  if [ "$mode" = "resume" ]; then
    if [ "${retries:-0}" -ge 2 ]; then
      log "chain paused: $session_id stalled after $retries resume attempts"
      notify "wayfinder chain paused" "session $session_id stalled after 2 resume attempts — attach TUI to check, or run: ./scripts/wayfinder-loop.sh --retry"
      exit 0
    fi
    retries=$(( ${retries:-0} + 1 ))
    save_state
    log "resuming stalled session $session_id (attempt $retries/2)"
    notify "wayfinder resuming" "session $session_id stalled — asking it to continue where it left off"
    if ! api post "/api/session/$session_id/prompt" --data "$(jq -nc '{text: "You were interrupted mid-session. Continue exactly where you left off, per your session instructions. Do not restart or re-read the handoff doc unless required."}')" >/dev/null 2>&1; then
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
[ -d "$DOCS_DIR" ] || die "docs/agents not found at $DOCS_DIR"
command -v jq >/dev/null 2>&1 || die "jq not found"

load_state

if [ "${1:-}" = "--bootstrap" ]; then
  [ $# -ge 2 ] || die "--bootstrap requires <doc> (e.g. wayfinder-162-handoff.md)"
  doc="$2"
  [ -f "$DOCS_DIR/$doc" ] || die "bootstrap doc not found: $DOCS_DIR/$doc"
  # seed: every existing handoff is seen; the bootstrap doc spawns immediately
  seen_docs="$(handoff_docs | paste -sd, -)"
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
  api post "/api/session/$session_id/prompt" --data "$(jq -nc '{text: "You were interrupted mid-session. Continue exactly where you left off, per your session instructions. Do not restart or re-read the handoff doc unless required."}')" >/dev/null || die "resume prompt failed for $session_id"
  supervise_session
elif [ "${1:-}" = "--retry" ]; then
  [ -n "$last_doc" ] || die "--retry needs a processed doc in state (bootstrap first)"
  session_id=""
  retries=0
  save_state
  log "manual retry for $last_doc"
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
