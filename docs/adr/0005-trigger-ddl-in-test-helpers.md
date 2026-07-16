# ADR-0005: Trigger DDL in test helpers via raw exec()

**Status:** Accepted
**Date:** 2026-07-16

## Context

The `remittance_financial_snapshot` table has a `trg_remittance_snapshot_immutable` trigger that
blocks `DELETE` and `UPDATE`. Integration tests that create remittance data (ExportService,
RemittanceService, MonthlyRemittanceSummaryService) must disable this trigger before teardown or
FK constraints will cascade-fail.

The trigger is Postgres session-level. The `ALTER TABLE ... DISABLE TRIGGER` DDL and the subsequent
DML deletes must execute on the same JDBC connection. The alternative of issuing the DDL on one
connection and the DML on another (via separate `transaction {}` calls) would not work — the trigger
disable only affects the issuing connection.

## Decision

Centralise the trigger disable/enable in a single `DatabaseTestHelper.withSnapshotTriggerDisabled {}`
adapter. The method opens an Exposed `transaction {}`, issues the DDL via raw `exec()` inside that
transaction, runs the caller's block (which can use Exposed DSL or raw SQL — all share the same
connection), and re-enables the trigger in a `finally` block.

Raw `exec()` is used for the DDL because Exposed has no API surface for trigger management — no
`Table.disableTrigger()`, nothing in `SchemaUtils`. This is the only place in the test codebase
where raw SQL is permitted, and it is isolated to this one helper.

The table and trigger names are held in private constants to prevent the `DISABLE` and `ENABLE`
statements from drifting out of sync with a typo.

## Consequences

- If `block()` throws, the `finally` ENABLE runs but the enclosing `transaction {}` rolls back.
  PostgreSQL `ALTER TABLE` is transactional, so the ENABLE is rolled back too — meaning the trigger
  would stay disabled on that specific pooled connection. In practice this is harmless: HikariCP
  supplies a fresh connection from the pool for each `transaction {}` call, and the next call to
  `withSnapshotTriggerDisabled` re-disables and then correctly re-enables the trigger.
- ExportServicePostgresTest overrides `tearDownBase()` to wrap both untracked remittance cleanup
  and `cleanTrackedRows()` inside a single `withSnapshotTriggerDisabled` block — single teardown,
  single connection, correct trigger state.
