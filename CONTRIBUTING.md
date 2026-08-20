# Contributing

Use short-lived `ralph/<feature-name>` branches from `master`. Push branch and
open PR targeting `master`; never push directly to `master`.

## Wayfinder AFK Flow

Set `WAYFINDER_TICKET`; loop prepares and validates dedicated branch before
spawning work:

```bash
WAYFINDER_TICKET=312 ./scripts/wayfinder-loop.sh --bootstrap docs/agents/wayfinder-367-handoff.md
```

After pushing and opening PR, verify metadata, wait for required checks, then
resolve ticket:

```bash
scripts/wayfinder-ci.sh validate-pr 312 <pr-url>
scripts/wayfinder-ci.sh wait-ci <pr-url>
scripts/wayfinder-ci.sh resolve 312 <pr-url>
```

Commands fail closed on wrong branch/base, missing PR metadata, pending or
failed CI, dirty worktrees, and non-master history.
