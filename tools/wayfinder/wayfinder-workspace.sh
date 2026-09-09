#!/usr/bin/env bash
# wayfinder-workspace.sh — pluggable isolated WorkspaceProvider for Wayfinder parallel workers (map #697, ticket #736).
#
# Every writable worker receives an isolated workspace at a known base revision
# and returns a reviewable Git change without sharing uncommitted state with
# another worker. Git worktrees are the initial/default provider; the contract
# stays provider-neutral so a future copy-on-write snapshot/reflink adapter can
# land without changing scheduler semantics.
#
# Subcommands:
#
#   create    [--base SHA] [--purpose TEXT] [--id ID]
#             [--provider P] [--root DIR] [--repo DIR]
#   inspect   <id> [--repo DIR]
#   path      <id> [--repo DIR]
#   reconcile [<id>] [--repo DIR]
#   cleanup   <id> [--force] [--repo DIR]
#   list      [--repo DIR]
#
# Provider-neutral metadata (every registry row carries all of these):
#
#   provider, workspace_id, workspace_path, base_sha, lifecycle_state
#
# Plus neutral purpose/detail for operability: purpose names why the workspace
# exists (ticket-<N>, scout-<slice>, maintenance-<sha>); detail carries
# provider-specific identity (git-worktree branch, future snapshot ID) and must
# never be parsed by scheduler code.
#
# Lifecycle: creating -> ready -> (cleaned, row removed). reconcile marks a
# missing path gone without deleting the row; cleanup removes the worktree,
# the provider identity (git branch), and the row deterministically for
# accepted, rejected, failed, and cancelled work. Sibling ticket #740 owns
# review/integration: it must integrate accepted work before calling cleanup —
# cleanup always deletes the provider branch.
#
# Git-worktree provider preserves current safety expectations:
# - separate temporary branch (wf/<id>) + worktree per writable worker;
# - workspace root defaults outside the canonical checkout;
# - canonical `git status --porcelain` stays empty across create/cleanup;
# - recorded base SHA is the full 40-char commit;
# - no two writable workers share one checkout.
#
# Chief compatibility: when the chief runs with --workspace-base ROOT it looks
# for per-ticket dirs at ROOT/wf-<ticket> and skips missing ones as
# awaiting-provider. Create that mapping explicitly:
#
#   wayfinder-workspace.sh create --purpose ticket-205 --id wf-205 --root ROOT
#
# Chief-driven workspaces must use an explicit --id matching wf-<ticket>;
# auto-generated ids (wf-<slug>-<short>) do not follow the chief layout.
#
# Read-only contract: bug scouts and other logically read-only agents use the
# canonical checkout read-only when safe. A scout that needs destructive
# experiments receives a disposable isolated workspace (e.g.
# --purpose scout-<slice>); those mutations are experimental only and never
# eligible for integration unless explicitly converted into a writable
# implementation task (sibling #740 owns the review gate).
#
# CoW compatibility: a future provider adds one `cow)` branch beside
# `git-worktree)` below plus its detail format. The `fake` provider (plain
# directories, no git worktrees or branches) exists for contract tests that
# must exercise lifecycle logic without depending on actual CoW filesystems
# (ticket #743): same registry shape, same isolation guarantees, none of the
# git mechanics. Scheduler code (chief, worker
# seam) must keep treating workspaces as opaque dirs + neutral metadata — no
# provider-specific branching there. Do not introduce OpenCode, Rift, or Lane
# dependencies to satisfy this ticket.
#
# Contract notes (ticket #736 acceptance):
# - Only the map chief creates/reconciles/destroys workspaces. create/cleanup/
#   reconcile refuse leaf roles (WAYFINDER_ROLE=ticket|maintenance|bug-scout|
#   helper) with a chief pointer; inspect/path/list stay observable to all so
#   workers can locate their own workspace.
# - Creation never dirties the canonical checkout and never shares uncommitted
#   state between workers.
# - Mutating subcommands (create/cleanup/reconcile) serialize on an exclusive
#   registry lock (flock when available; same-dir atomic renames otherwise),
#   so concurrent chiefs cannot interleave registry writes or share one id's
#   failure cleanup. Readers stay lock-free: the atomic rename publishes only
#   complete views.
#
# Env:
#   WAYFINDER_WORKSPACE_PROVIDER  provider name (default: git-worktree;
#                               `fake` = plain directories for contract
#                               tests, no git worktrees or branches)
#   WAYFINDER_WORKSPACE_ROOT      workspace root dir (default: <repo>-workspaces sibling)
#   WAYFINDER_WORKSPACE_REGISTRY  registry file (default: <repo>/.wayfinder/workspaces.tsv)
#   WAYFINDER_REPO                canonical checkout (default: repo containing this script)
#   WAYFINDER_ROLE                caller role; leaf roles cannot orchestrate
#
# Registry (TSV, runtime state under gitignored .wayfinder/):
#   workspace_id provider workspace_path base_sha lifecycle_state purpose detail created updated note
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DISCOVERED_REPO="$(cd "$SCRIPT_DIR/../.." && pwd)"

CANON_DEFAULT="${WAYFINDER_REPO:-$DISCOVERED_REPO}"
PROVIDER_DEFAULT="${WAYFINDER_WORKSPACE_PROVIDER:-git-worktree}"
REGISTRY_DEFAULT="${WAYFINDER_WORKSPACE_REGISTRY:-$DISCOVERED_REPO/.wayfinder/workspaces.tsv}"
CALLER_ROLE="${WAYFINDER_ROLE:-chief}"
CAPACITY="$SCRIPT_DIR/wayfinder-capacity.sh"
COMMON="$SCRIPT_DIR/wayfinder-common.sh"

LEAF_ROLES="ticket maintenance bug-scout helper"

usage() {
    sed -n '2,/^set -euo/p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
}

die() { printf 'wayfinder-workspace: %s\n' "$*" >&2; exit 1; }
usage_err() { printf 'wayfinder-workspace: %s\n' "$*" >&2; exit 2; }

[ -f "$COMMON" ] || die "shared hardening seam missing: $COMMON (ticket #746)"
# shellcheck disable=SC1090
. "$COMMON"

require_chief() { # <op>
    case " $LEAF_ROLES " in
        *" $CALLER_ROLE "*) die "$1 requires the map chief (WAYFINDER_ROLE=$CALLER_ROLE is a leaf role — request help via the chief instead)" ;;
    esac
}

valid_id() { [[ "${1:-}" =~ ^[a-z][a-z0-9_-]{0,63}$ ]]; }

# workspace_emit <event> [emit-flags...] — best-effort structured lifecycle
# event (ticket #742 observability). The events log sits beside this call's
# workspace registry (identical to the worker-registry side in production);
# a failed emit never breaks the workspace operation.
workspace_emit() { # <registry> <event> [emit-flags...]
    local registry="$1"; shift
    [ -x "$CAPACITY" ] || return 0
    WAYFINDER_EVENTS_LOG="${WAYFINDER_EVENTS_LOG:-$(dirname "$registry")/events.log}" \
        "$CAPACITY" emit "$@" >/dev/null 2>&1 || true
}

need_arg() { # <flag> <value> — a flag without its value is misuse (exit 2).
    [ -n "${2:-}" ] || usage_err "$1 requires a value"
}

resolve_registry() { # <canon> — print the registry file for a canonical checkout.
    # A per-call --repo must not silently use the default registry when the
    # caller points at another checkout: keep the registry beside the
    # canonical checkout unless WAYFINDER_WORKSPACE_REGISTRY is pinned.
    if [ -n "${WAYFINDER_WORKSPACE_REGISTRY:-}" ]; then
        printf '%s' "$WAYFINDER_WORKSPACE_REGISTRY"
    elif [ "$1" != "$DISCOVERED_REPO" ]; then
        printf '%s' "$1/.wayfinder/workspaces.tsv"
    else
        printf '%s' "$REGISTRY_DEFAULT"
    fi
}

# with_registry_lock <registry> — serialize mutating subcommands across
# concurrent chiefs. flock releases on process exit, so a killed writer never
# leaves stale state; without flock the single-chief assumption applies and
# same-dir atomic renames still keep readers on complete views.
with_registry_lock() {
    if command -v flock >/dev/null 2>&1; then
        mkdir -p "$(dirname "$1")"
        exec {WAYFINDER_WORKSPACE_LOCK_FD}>"$1.lock"
        flock -w 60 "$WAYFINDER_WORKSPACE_LOCK_FD" \
            || die "cannot lock registry: $1.lock"
    else
        wf_warn_no_flock "workspace registry rewrite"
    fi
}

valid_root() { # <root> — reject TSV-breaking control characters.
    case "$1" in
        *$'\t'*|*$'\n'*) die "workspace root must not contain tabs or newlines" ;;
    esac
    [ -n "$1" ] || die "workspace root is required"
}

default_root() { # <canon> — sibling "<basename>-workspaces" outside the checkout.
    printf '%s-workspaces' "$(dirname "$1")/$(basename "$1")"
}

reg_init() { # <registry>
    mkdir -p "$(dirname "$1")"
    [ -f "$1" ] || : > "$1"
}

reg_row() { # <registry> <id>
    [ -f "$1" ] || return 0
    awk -F'\t' -v id="$2" '$1 == id { print; exit }' "$1"
}

# reg_upsert <registry> <id> <provider> <path> <base> <state> <purpose> <detail> <note>
reg_upsert() {
    local registry="$1" id="$2" provider="$3" path="$4" base="$5" state="$6" purpose="$7" detail="$8" note="$9"
    local now existing created tmp
    now="$(date '+%F %T')"
    purpose="$(printf '%s' "$purpose" | tr '\t\n' '  ')"
    detail="$(printf '%s' "$detail" | tr '\t\n' '  ')"
    note="$(printf '%s' "$note" | tr '\t\n' '  ')"
    reg_init "$registry"
    existing="$(reg_row "$registry" "$id")"
    if [ -n "$existing" ]; then
        created="$(printf '%s' "$existing" | cut -f8)"
        [ -n "$created" ] || created="$now"
    else
        created="$now"
    fi
    tmp="$(mktemp -p "$(dirname "$registry")" reg.XXXXXX)"
    awk -F'\t' -v id="$id" '$1 != id' "$registry" > "$tmp"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$id" "$provider" "$path" "$base" "$state" "$purpose" "$detail" "$created" "$now" "$note" >> "$tmp"
    mv "$tmp" "$registry"
}

reg_remove() { # <registry> <id>
    local tmp
    tmp="$(mktemp -p "$(dirname "$1")" reg.XXXXXX)"
    awk -F'\t' -v id="$2" '$1 != id' "$1" > "$tmp"
    mv "$tmp" "$1"
}

sanitize_purpose() { # <purpose> — lowercase slug for generated ids.
    printf '%s' "${1:-manual}" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+//; s/-+$//' | cut -c1-24 | sed -E 's/-+$//'
    true
}

resolve_base() { # <canon> <base-or-empty> — print full 40-char SHA.
    local canon="$1" base="${2:-HEAD}" sha
    sha="$(git -C "$canon" rev-parse --verify "${base}^{commit}" 2>&1)" \
        || die "unknown base revision '$base': $sha"
    printf '%s' "$sha"
}

gen_id() { # <registry> <canon> <provider> <root> <purpose> <short> — unique wf-* id.
    local registry="$1" canon="$2" provider="$3" root="$4" purpose="$5" short="$6"
    local slug base cand n
    slug="$(sanitize_purpose "$purpose")"
    [ -n "$slug" ] || slug="ws"
    base="wf-${slug}-${short}"
    cand="$base" n=0
    while [ -n "$(reg_row "$registry" "$cand")" ] \
        || [ -e "$root/$cand" ] \
        || { [ "$provider" = "git-worktree" ] && git -C "$canon" rev-parse --verify --quiet "refs/heads/wf/$cand" >/dev/null 2>&1; }; do
        n=$((n + 1))
        cand="${base}-${n}"
        [ "$n" -lt 1000 ] || die "cannot allocate a free workspace id for '$base'"
    done
    printf '%s' "$cand"
}

cmd_create() {
    require_chief "workspace_create"
    local base="" purpose="manual" id="" provider="$PROVIDER_DEFAULT" root="${WAYFINDER_WORKSPACE_ROOT:-}" canon="$CANON_DEFAULT"
    while [ $# -gt 0 ]; do
        case "$1" in
            --base) need_arg "$1" "${2:-}"; base="$2"; shift 2 ;;
            --purpose) need_arg "$1" "${2:-}"; purpose="$2"; shift 2 ;;
            --id) need_arg "$1" "${2:-}"; id="$2"; shift 2 ;;
            --provider) need_arg "$1" "${2:-}"; provider="$2"; shift 2 ;;
            --root) need_arg "$1" "${2:-}"; root="$2"; shift 2 ;;
            --repo) need_arg "$1" "${2:-}"; canon="$2"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "create: unknown flag $1" ;;
        esac
    done
    [ -d "$canon" ] || die "create: canonical repo not found: $canon (set WAYFINDER_REPO)"
    git -C "$canon" rev-parse --git-dir >/dev/null 2>&1 || die "create: not a git checkout: $canon"
    [ -n "$root" ] || root="$(default_root "$canon")"
    valid_root "$root"
    case "$provider" in
        git-worktree|fake) ;;
        *) die "create: unknown provider '$provider' (want git-worktree or fake; add a future CoW adapter beside it without changing scheduler semantics)" ;;
    esac
    local sha short registry
    sha="$(resolve_base "$canon" "$base")"
    short="$(printf '%s' "$sha" | cut -c1-7)"
    registry="$(resolve_registry "$canon")"
    with_registry_lock "$registry"
    if [ -z "$id" ]; then
        id="$(gen_id "$registry" "$canon" "$provider" "$root" "$purpose" "$short")"
        valid_id "$id" || die "create: generated id is not usable: '$id'"
    else
        valid_id "$id" || die "create: invalid workspace id '$id' (want [a-z][a-z0-9_-]{0,63})"
        [ -z "$(reg_row "$registry" "$id")" ] || die "create: workspace id already registered: $id"
        [ ! -e "$root/$id" ] || die "create: workspace path already exists: $root/$id"
        if [ "$provider" = "git-worktree" ]; then
            git -C "$canon" rev-parse --verify --quiet "refs/heads/wf/$id" >/dev/null 2>&1 \
                && die "create: branch already exists: wf/$id" || true
        fi
    fi
    local path branch out
    mkdir -p "$root"
    root="$(cd "$root" && pwd)" # absolute: reconcile compares worktree-list paths
    path="$root/$id"
    case "$provider" in
        git-worktree) branch="wf/$id" ;;
        fake) branch="fake:$id" ;; # no git branch; detail stays opaque to schedulers
    esac
    reg_upsert "$registry" "$id" "$provider" "$path" "$sha" "creating" "$purpose" "$branch" "allocating $provider workspace"
    case "$provider" in
        fake)
            if mkdir -p "$path" 2>/dev/null; then
                printf '%s\n' "$sha" >"$path/.base" 2>/dev/null || true
                reg_upsert "$registry" "$id" "$provider" "$path" "$sha" "ready" "$purpose" "$branch" "created at $short"
                workspace_emit "$registry" workspace-created --workspace "$path" --detail "id=$id provider=$provider base=$sha purpose=$purpose"
                printf 'created %s provider=%s path=%s base=%s\n' "$id" "$provider" "$path" "$sha"
            else
                reg_remove "$registry" "$id"
                die "create: fake provider mkdir failed for $id"
            fi
            return 0
            ;;
    esac
    if out="$(git -C "$canon" worktree add -b "$branch" "$path" "$sha" 2>&1)"; then
        reg_upsert "$registry" "$id" "$provider" "$path" "$sha" "ready" "$purpose" "$branch" "created at $short"
        workspace_emit "$registry" workspace-created --workspace "$path" --detail "id=$id provider=$provider base=$sha purpose=$purpose"
        printf 'created %s provider=%s path=%s base=%s\n' "$id" "$provider" "$path" "$sha"
    else
        git -C "$canon" worktree remove --force "$path" >/dev/null 2>&1 || true
        git -C "$canon" branch -D "$branch" >/dev/null 2>&1 || true
        reg_remove "$registry" "$id"
        die "create: git worktree add failed for $id: $out"
    fi
}

cmd_inspect() {
    local id="" canon="$CANON_DEFAULT" registry
    [ $# -ge 1 ] || usage_err "inspect: workspace id is required"
    id="$1"; shift
    while [ $# -gt 0 ]; do
        case "$1" in
            --repo) need_arg "$1" "${2:-}"; canon="$2"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "inspect: unknown flag $1" ;;
        esac
    done
    valid_id "$id" || usage_err "inspect: invalid workspace id '$id'"
    registry="$(resolve_registry "$canon")"
    local row
    row="$(reg_row "$registry" "$id")"
    [ -n "$row" ] || die "inspect: unknown workspace '$id' (not in registry)"
    printf 'provider=%s\nworkspace_id=%s\nworkspace_path=%s\nbase_sha=%s\nlifecycle_state=%s\npurpose=%s\ndetail=%s\n' \
        "$(printf '%s' "$row" | cut -f2)" "$(printf '%s' "$row" | cut -f1)" \
        "$(printf '%s' "$row" | cut -f3)" "$(printf '%s' "$row" | cut -f4)" \
        "$(printf '%s' "$row" | cut -f5)" "$(printf '%s' "$row" | cut -f6)" \
        "$(printf '%s' "$row" | cut -f7)"
}

cmd_path() {
    local id="" canon="$CANON_DEFAULT" registry
    [ $# -ge 1 ] || usage_err "path: workspace id is required"
    id="$1"; shift
    while [ $# -gt 0 ]; do
        case "$1" in
            --repo) need_arg "$1" "${2:-}"; canon="$2"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "path: unknown flag $1" ;;
        esac
    done
    valid_id "$id" || usage_err "path: invalid workspace id '$id'"
    registry="$(resolve_registry "$canon")"
    local row
    row="$(reg_row "$registry" "$id")"
    [ -n "$row" ] || die "path: unknown workspace '$id' (not in registry)"
    printf '%s\n' "$(printf '%s' "$row" | cut -f3)"
}

cmd_reconcile() {
    require_chief "workspace_reconcile"
    local only="" canon="$CANON_DEFAULT" registry
    while [ $# -gt 0 ]; do
        case "$1" in
            --repo) need_arg "$1" "${2:-}"; canon="$2"; shift 2 ;;
            -h|--help) usage ;;
            --*) usage_err "reconcile: unknown flag $1" ;;
            *)
                [ -z "$only" ] || usage_err "reconcile: at most one workspace id"
                valid_id "$1" || usage_err "reconcile: invalid workspace id '$1'"
                only="$1"; shift ;;
        esac
    done
    registry="$(resolve_registry "$canon")"
    reg_init "$registry"
    with_registry_lock "$registry"
    git -C "$canon" rev-parse --git-dir >/dev/null 2>&1 || die "reconcile: not a git checkout: $canon"
    local live kept=0 gone=0
    # Fail closed: a transient git failure must never mass-mark rows gone
    # (contrast: an empty successful listing still lists the main checkout).
    live="$(git -C "$canon" worktree list --porcelain 2>&1)" \
        || die "reconcile: cannot list worktrees for $canon: $live"
    live="$(printf '%s' "$live" | awk '/^worktree /{ print substr($0, 10) }')"
    [ -n "$live" ] || die "reconcile: empty worktree list for $canon"
    local id provider path base state purpose detail created updated note
    local tmp
    tmp="$(mktemp -p "$(dirname "$registry")" rec.XXXXXX)"
    cp "$registry" "$tmp"
    while IFS=$'\t' read -r id provider path base state purpose detail created updated note; do
        [ -n "$id" ] || continue
        if [ -n "$only" ] && [ "$id" != "$only" ]; then
            kept=$((kept + 1))
            continue
        fi
        # Liveness is provider-dispatched: a future CoW adapter adds its own
        # branch here without changing scheduler semantics.
        case "$provider" in
            fake)
                if [ -d "$path" ]; then
                    if [ "$state" = "creating" ]; then
                        reg_upsert "$registry" "$id" "$provider" "$path" "$base" "ready" "$purpose" "$detail" "adopted after interrupted create"
                    fi
                    kept=$((kept + 1))
                else
                    case "$state" in
                        ready|creating)
                            reg_upsert "$registry" "$id" "$provider" "$path" "$base" "gone" "$purpose" "$detail" "path missing — result may be salvageable"
                            gone=$((gone + 1))
                            ;;
                        *) kept=$((kept + 1)) ;;
                    esac
                fi
                ;;
            git-worktree)
                if [ -d "$path" ] && printf '%s\n' "$live" | grep -qxF "$path"; then
                    if [ "$state" = "creating" ]; then
                        reg_upsert "$registry" "$id" "$provider" "$path" "$base" "ready" "$purpose" "$detail" "adopted after interrupted create"
                    fi
                    kept=$((kept + 1))
                else
                    case "$state" in
                        ready|creating)
                            reg_upsert "$registry" "$id" "$provider" "$path" "$base" "gone" "$purpose" "$detail" "path or worktree missing — result may be salvageable"
                            gone=$((gone + 1))
                            ;;
                        *) kept=$((kept + 1)) ;;
                    esac
                fi
                ;;
            *) kept=$((kept + 1)) ;; # unknown provider: leave to its own adapter
        esac
    done < "$tmp"
    rm -f "$tmp"
    if [ -n "$only" ]; then
        [ -n "$(reg_row "$registry" "$only")" ] || die "reconcile: unknown workspace '$only' (not in registry)"
    fi
    # Live worktrees the registry never saw are reported, never adopted here —
    # adoption across a chief restart is sibling ticket #741's recovery path.
    local w row_found
    while IFS= read -r w; do
        [ -n "$w" ] || continue
        row_found=0
        while IFS=$'\t' read -r id _p _path _b _s _pu _d _c _u _n; do
            [ -n "$id" ] || continue
            [ "$_path" = "$w" ] && row_found=1
        done < "$registry"
        [ "$row_found" -eq 0 ] && printf 'unmanaged: %s\n' "$w"
    done <<< "$live"
    printf 'reconciled: %d kept, %d gone\n' "$kept" "$gone"
}

cmd_cleanup() {
    require_chief "workspace_cleanup"
    local id="" force=0 canon="$CANON_DEFAULT" registry
    [ $# -ge 1 ] || usage_err "cleanup: workspace id is required"
    id="$1"; shift
    while [ $# -gt 0 ]; do
        case "$1" in
            --force) force=1; shift ;;
            --repo) need_arg "$1" "${2:-}"; canon="$2"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "cleanup: unknown flag $1" ;;
        esac
    done
    valid_id "$id" || usage_err "cleanup: invalid workspace id '$id'"
    registry="$(resolve_registry "$canon")"
    with_registry_lock "$registry"
    local row state path provider base purpose detail
    row="$(reg_row "$registry" "$id")"
    [ -n "$row" ] || die "cleanup: unknown workspace '$id' (not in registry)"
    state="$(printf '%s' "$row" | cut -f5)"
    path="$(printf '%s' "$row" | cut -f3)"
    provider="$(printf '%s' "$row" | cut -f2)"
    base="$(printf '%s' "$row" | cut -f4)"
    purpose="$(printf '%s' "$row" | cut -f6)"
    detail="$(printf '%s' "$row" | cut -f7)"
    : "$force" # cleanup is deterministic by default: --force is accepted for
    # symmetry with the worker seam but changes nothing (dirty checkouts still go).
    case "$provider" in
        fake)
            # No git identity to delete: the directory is the workspace.
            # Accepted, rejected, failed, and cancelled converge here.
            case "$path" in
                *$'\t'*|*$'\n'*) die "cleanup: refusing to remove a path with control characters for '$id'" ;;
            esac
            rm -rf "$path" 2>/dev/null || true
            ;;
        git-worktree)
            # Deterministic removal: dirty worktrees still go (accepted,
            # rejected, failed, and cancelled converge here). Sibling #740
            # must integrate accepted work before calling cleanup: the
            # provider branch below is always deleted.
            case "$path" in
                *$'\t'*|*$'\n'*) die "cleanup: refusing to remove a path with control characters for '$id'" ;;
            esac
            git -C "$canon" worktree remove --force "$path" >/dev/null 2>&1 || rm -rf "$path" 2>/dev/null || true
            if [ "$detail" = "wf/$id" ]; then
                git -C "$canon" branch -D "$detail" >/dev/null 2>&1 || true
            else
                printf 'wayfinder-workspace: warning: skipping branch delete for %s (unexpected detail %s)\n' "$id" "$detail" >&2
            fi
            git -C "$canon" worktree prune >/dev/null 2>&1 || true
            ;;
        *) die "cleanup: unknown provider '$provider' for workspace '$id'" ;;
    esac
    reg_remove "$registry" "$id"
    workspace_emit "$registry" workspace-cleaned --workspace "$path" --detail "id=$id was=$state base=$base purpose=$purpose"
    printf 'cleaned %s (was %s base=%s purpose=%s)\n' "$id" "$state" "$(printf '%s' "$base" | cut -c1-7)" "$purpose"
}

cmd_list() {
    local canon="$CANON_DEFAULT" registry
    while [ $# -gt 0 ]; do
        case "$1" in
            --repo) need_arg "$1" "${2:-}"; canon="$2"; shift 2 ;;
            -h|--help) usage ;;
            *) usage_err "list: unknown flag $1" ;;
        esac
    done
    registry="$(resolve_registry "$canon")"
    [ -f "$registry" ] && cat "$registry" || true
}

[ $# -ge 1 ] || usage
sub="$1"; shift
case "$sub" in
    create) cmd_create "$@" ;;
    inspect) cmd_inspect "$@" ;;
    path) cmd_path "$@" ;;
    reconcile) cmd_reconcile "$@" ;;
    cleanup) cmd_cleanup "$@" ;;
    list) cmd_list "$@" ;;
    -h|--help|help) usage ;;
    *) usage_err "unknown subcommand '$sub'" ;;
esac
