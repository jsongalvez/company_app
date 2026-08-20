#!/usr/bin/env bash
set -euo pipefail
REPO="$(cd "$(dirname "$0")/.." && pwd)"
die() { printf 'wayfinder-ci: %s\n' "$*" >&2; exit 1; }
branch_name() { git -C "$REPO" branch --show-current; }
expected_branch() { printf 'ralph/wayfinder-%s\n' "$1"; }
validate_branch() {
  local ticket="$1" current expected allow_dirty="${2:-}"
  [[ "$ticket" =~ ^[0-9]+$ ]] || die "ticket must be numeric"
  current="$(branch_name)"; expected="$(expected_branch "$ticket")"
  [ "$current" = "$expected" ] || die "branch '$current' is not '$expected'"
  git -C "$REPO" merge-base --is-ancestor master HEAD || die "HEAD is not based on master"
  [ "$allow_dirty" = allow-dirty ] || [ -z "$(git -C "$REPO" status --porcelain)" ] || die "worktree is dirty"
}
prepare_branch() {
  local ticket="$1" current expected
  [[ "$ticket" =~ ^[0-9]+$ ]] || die "ticket must be numeric"
  expected="$(expected_branch "$ticket")"; current="$(branch_name)"
  if [ "$current" = master ]; then git -C "$REPO" switch -c "$expected" master >/dev/null
  elif [ "$current" != "$expected" ]; then die "refusing branch switch from '$current'; expected '$expected'"; fi
  if [ "$current" = master ]; then validate_branch "$ticket"; else validate_branch "$ticket" allow-dirty; fi
}
pr_metadata() {
  local pr="$1" current
  current="$(branch_name)"; [ -n "$pr" ] || die "pull-request URL is required"
  gh pr view "$pr" --json url,baseRefName,headRefName |
    jq -e --arg branch "$current" --arg url "$pr" \
      '.baseRefName == "master" and .headRefName == $branch and .url == $url' >/dev/null ||
    die "PR does not target master from '$current'"
  printf '%s\n' "$pr"
}
ci_green() {
  local pr="$1" output
  output="$(gh pr checks "$pr" --required 2>&1)" || die "CI checks failed or are pending for $pr: $output"
  printf '%s\n' "$output" | grep -Eiq '(^|[[:space:]])(pass|passed|success|successful)([[:space:]]|$)' || die "no passing required CI evidence for $pr"
}
wait_ci() {
  local pr="$1" timeout="${2:-1800}" output
  local deadline=$((SECONDS + timeout))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if output="$(gh pr checks "$pr" --required 2>&1)" &&
      printf '%s\n' "$output" | grep -Eiq '(^|[[:space:]])(pass|passed|success|successful)([[:space:]]|$)'; then
      return 0
    fi
    if printf '%s\n' "$output" | grep -Eiq '(^|[[:space:]])(fail|failed|cancelled|error)([[:space:]]|$)'; then
      die "required CI failed for $pr: $output"
    fi
    sleep 10
  done
  die "required CI did not finish green for $pr within ${timeout}s"
}
resolve_issue() {
  local ticket="$1" pr="$2" checks commit
  validate_branch "$ticket"
  pr_metadata "$pr" >/dev/null
  ci_green "$pr"
  checks="$(gh pr checks "$pr" --required)"
  commit="$(git -C "$REPO" rev-parse HEAD)"
  gh issue comment "$ticket" --body "AFK resolution evidence: branch=$(branch_name); base=master; commit=$commit; PR=$pr; required checks:\n\n$checks"
  gh issue close "$ticket" --comment "Resolved only after green required CI checks: $pr"
}
case "${1:-}" in
  prepare) [ $# -eq 2 ] || die "usage: prepare <ticket>"; prepare_branch "$2" ;;
  validate-branch) [ $# -eq 2 ] || die "usage: validate-branch <ticket>"; validate_branch "$2" ;;
  validate-branch-dirty) [ $# -eq 2 ] || die "usage: validate-branch-dirty <ticket>"; validate_branch "$2" allow-dirty ;;
  validate-pr) [ $# -eq 3 ] || die "usage: validate-pr <ticket> <pr>"; validate_branch "$2"; pr_metadata "$3" ;;
  validate-ci) [ $# -eq 2 ] || die "usage: validate-ci <pr>"; ci_green "$2" ;;
  wait-ci) [ $# -ge 2 ] || die "usage: wait-ci <pr> [timeout]"; wait_ci "$2" "${3:-1800}" ;;
  resolve) [ $# -eq 3 ] || die "usage: resolve <ticket> <pr>"; resolve_issue "$2" "$3" ;;
  *) die "usage: prepare|validate-branch|validate-pr|validate-ci" ;;
esac
