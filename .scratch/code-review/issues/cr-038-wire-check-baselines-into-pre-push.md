# CR-038: Wire check-baselines.sh into pre-push hook

**Source:** CR-036 audit follow-up (P1)

## What to build

Add one line to `.githooks/pre-push` calling `bash scripts/check-baselines.sh /tmp/company-app-jmh.log` after JMH runs. If baselines regressed >20%, the hook fails and blocks the push. The `check-baselines.sh` script already exists and is fully functional — it just isn't called by the hook.

## Acceptance criteria

- [ ] `.githooks/pre-push` calls `check-baselines.sh` after `./gradlew :backend:jmh` exits zero
- [ ] If `check-baselines.sh` exits non-zero (regression >20%), the hook fails and blocks the push with a clear error message
- [ ] If `check-baselines.sh` exits zero, the existing reminder message is unchanged (or updated to show all-clear)
- [ ] Hook still fails if JMH itself fails (existing behavior preserved)

## Blocked by

None — can start immediately.

## Status: ready-for-agent

**Status:** ✅ done
