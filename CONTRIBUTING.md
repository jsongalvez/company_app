# Contributing

Development integrates **directly to `master`** for ordinary AFK work: commit with
`ref #<issue>` in the message and push immediately. Hooks are bookkeeping only —
they never run gates. Pull requests exist only for explicit escalation triggers:
human request, external contributor, unusually risky or irreversible integration,
or a multi-agent concurrency boundary.

## Normal Flow

1. Implement with targeted, warm validation (`bash tools/quality/validate.sh`
   auto-selects; see `backend/AGENTS.md` "Targeted validation") — run the
   smallest check that answers the current question, once per meaningful slice.
2. Commit with issue references (`ref #<number>`; enforced by the local
   `commit-msg` hook) and push directly to `master`. Do not rewrite history or
   force-push `master`.
3. CI runs asynchronously and never blocks the session; failures are consumed
   and repaired by the next session (one check-runs API call at session start).
   JMH and broad k6/e2e are **not** in CI's automatic path — JMH runs only via
   manual `workflow_dispatch` on `.github/workflows/jmh.yml` when a performance
   question exists, and it never blocks a session or ticket.

## Gate Classification

Git hooks are bookkeeping, not build pipelines (map #329). Pre-commit formats staged
Kotlin with the standalone ktlint CLI (warn + skip when missing) and syntax-checks
staged shell files. Pre-push runs no gates. Neither hook starts Gradle, Postgres, the
backend, k6, JMH, OpenAPI generation, or Compose compilation, and neither queries the
database. CI owns complete test, contract, integration, target-matrix, and test-data
cleanliness checks, and the active agent never polls it — failures are consumed by the
next session.

While implementing, run the smallest warm Gradle/test task that answers the current
question (`bash tools/quality/validate.sh` auto-selects; see `backend/AGENTS.md` "Targeted
validation"); do not rerun broad suites because commit or push is next.

## Emergency Or AFK Work

If direct-to-master is unavailable (e.g. review is explicitly requested, or the
change is unusually risky), open a pull request, record reason and owner in the
pull request, and leave a handoff when review or follow-up is unavailable. Do
not merge with failed checks or use force-push/history rewriting as an
emergency shortcut.
