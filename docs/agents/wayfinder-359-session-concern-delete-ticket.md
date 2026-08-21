Part of #180 (native sub-issue link blocked by GitHub's 100-child limit; fallback issue #304).

## Question

Enforce `EDIT_BRANCH_DATA` on session-concern DELETE. The child route must use the exact session/Branch Day capability gate already used by concern GET/POST and promote flows. Unauthorized OPEN-day callers must receive 403; authorized branch or Branch Day holders retain deletion behavior. Add route regression coverage without changing concern or day-state semantics.

## Validation

- Add unauthorized and authorized DELETE route coverage on OPEN day, including Branch Day relief authorization where applicable.
- Run focused route tests, backend quality gates, OpenAPI/cleanliness checks, and the standard review profile.
