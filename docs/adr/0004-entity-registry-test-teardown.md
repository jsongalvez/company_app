# ADR-0004: Entity-registry test teardown

**Status:** Accepted
**Date:** 2026-07-16

## Context

28 Postgres integration tests each carried their own `@BeforeTest`/`@AfterTest` with FK-ordered
`deleteTestRows()`. Adding a new table meant touching all 28 files. Simpler alternatives existed:

- **LIFO teardown** — delete rows in reverse order of insertion, assuming a single transaction per
  test. Rejected: tests open multiple transactions (service calls each open one), so LIFO can't
  track rows across transaction boundaries.
- **Database truncation** — `TRUNCATE` all tables in FK order between tests. Rejected: requires
  re-seeding shared reference data (roles, capabilities) in every test. The registry approach lets
  each test declare only what it created, keeping shared seed rows intact.
- **Flyway clean + migrate per test class** — unacceptably slow at ~2s per migration run.

## Decision

Extract `abstract class BasePostgresTest` with an entity-ID registry (`trackOwned`) and a single
FK-ordered teardown (`cleanTrackedRows`). Each test extends it and registers entity IDs during
setup. The teardown runs FK-safe deletes only for registered rows — shared seed data is untouched.

The FK deletion order is computed from a declarative `FK_GRAPH` (edge = child → parent) via a
topological sort, replacing the hand-maintained flat list. Schema FK changes auto-adjust the order.

`cleanTrackedRows()` is `protected` (not `private`) so subclasses can call it from lifecycle
overrides (e.g., ExportService wraps it in a trigger-disabled block — see ADR-0005).

## Consequences

- 27 test files now extend `BasePostgresTest`; ~600 lines of duplicated teardown removed.
- `trackOwned(table, column, id)` takes a `Column<UUID>` parameter, enabling cleanup by non-PK
  columns (e.g., `BranchDayTable.branchId`), which some tests need for cascading FK safety.
- `===` identity check on Table singletons in `cleanTrackedRows` — callers must pass the exact
  `object` reference, not a dynamically constructed Table instance.
- Adding a table requires only adding one entry to `FK_GRAPH`, not editing 28 files.
