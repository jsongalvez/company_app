# ADR-0027: Every route declares its gate

**Status:** Accepted
**Date:** 2026-11-04
**Supersedes:** the mechanism of [ADR-0007](0007-route-level-capability-gates.md) (where the check is registered); its rule (capability checks at the route layer, day state in services) stands.

## Context

Route access lived in `config.routes.before(path)` filters. A filter fires for every request whose path matches its pattern, so a route's gate could sit anywhere in the file, cover sibling verbs it did not mean to, or miss a deeper path (the `{branchId}/export/*` wildcard and the `branches/accessible` skip existed only for this). On master `3d1ee781`, 95 of 156 routes had a matching filter and 61 had none, and nothing said whether those 61 were open on purpose.

## Decision

Every route is declared with a gate argument: `config.get(path, gate) { ... }` (also `post`, `put`, `patch`, `delete`) from `api/Gate.kt`. A gate is one of:

- `Gate.Open(why)`: no sign-in (health, sign-in, invite and reset links).
- `Gate.SignedIn(why)`: anyone signed in; the route only touches the caller's own things or filters to what they may see.
- `Gate.InHandler(why)`: the handler or its service asks `Access` once it has loaded the row that names the branch or day.
- `Gate.Before { context -> Access.require(...) }`: the check runs before the handler, for that route only.

`RouteGateTest` fails when any route is registered without a gate or any path filter is registered. `RouteAccessTest` records the status code every route gives ten kinds of people for an empty body, with each route's gate, in `backend/src/test/resources/route-access.txt`; a change to who can do what shows as a diff of that file (`WRITE_ROUTE_ACCESS=1` rewrites it).

## Consequences

- A gate runs after the global filters (security headers, request log, NUL-character check), so refusals now carry the security headers and a NUL character in the URL gives 400 before 403.
- A method that has no route on a gated path now gives 404 instead of the filter's 403.
- `InHandler` routes are listed, not proven: moving their checks into `Gate.Before` is follow-up work, checked against `route-access.txt`.
