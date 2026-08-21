Part of #310

## Question

What changes make Wayfinder implementation sessions fully AFK-safe for branch selection, pull-request creation, CI failure repair, and CI-gated resolution?

## Acceptance

- Every implementation session works on a dedicated `ralph/<ticket-slug>` branch based on `master`, never directly on `master` or an unrelated branch.
- The branch, base, ticket, commit, and pull-request URL are explicit in the handoff state.
- The daemon or session bootstrap validates branch ownership before editing and never auto-commits to an unverified branch.
- Agents read and follow `CONTRIBUTING.md`; branch creation, push, PR creation, and early PR updates are executable AFK steps, not merely prose.
- CI is polled after push; failures are fed back into the same ticket/session for repair and retry.
- Ticket resolution requires green applicable CI checks and records immutable check evidence. Pending or failed CI cannot produce a successor frontier handoff.
- Existing one-ticket-per-session, clean-worktree, retry, and human-blocker behavior remains intact.
- Add deterministic tests or fixtures for branch mismatch, missing PR metadata, pending CI, failed CI, and green CI transitions.

## Constraints

- Reuse existing `scripts/wayfinder-loop.sh` daemon and GitHub CLI workflow; do not add separate runner infrastructure.
- Preserve disposable database and least-privilege behavior. Never expose production credentials to CI or agent repair sessions.
- Keep human notification for genuine blockers only; routine CI failures must remain AFK-repairable.
