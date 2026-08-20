# Handoff - Map #310, Session 364

## Authority

- Map #310 is continuation authority for retained Map #180 work.
- Loaded `/wayfinder`, Map #180 handoff, tracker operations, and current CI workflow state.

## Session outcome

- Claimed and resolved [Define PR integration boundary for full-build branch](https://github.com/jsongalvez/company_app/issues/307).
- PR target is `master`; head is `ralph/company-app-full-build`.
- Normal merge commit is required after checks pass. No history rewrite, force-push, squash, or dropped commits.
- No PR was opened because current branch is not merge-ready.
- Map #310 Decisions so far now links #307. Parent #305 remains open with rollout children #306, #308, and #309.

## Evidence

- Branch is 740 commits ahead and 8 commits behind `master`; merge base `d2f75d5e547cdbb29858fcacbeaec433d027be86`.
- Branch changes 996 paths and has no open PR.
- Latest quality CI run: 187 of 970 backend tests failed.
- Latest JMH CI run reproduced eight below-threshold benchmarks across both attempts.
- Latest OpenAPI CI run passed.
- Branch protection API unavailable on current GitHub plan; workflow files remain status-check evidence.

## Next frontier

- Parent #305 rollout remains. Repair/classify gate failures before opening draft PR.
- Map #310 explicitly keeps #304, #301, and #311 deferred until #305 is resolved or released.

## Verification

- `git diff --check master...HEAD`: pre-existing tracked whitespace failures; no files changed by implementation.
- No production code changed; no Gradle or database gate run.

**Status:** PR boundary resolved; gate repair pending.
