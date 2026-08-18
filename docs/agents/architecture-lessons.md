# Architecture Lessons Ledger

Durable lessons from repository architecture audits. Future agents consume this before proposing simplifications.

## Current Lessons

- **Authoritative paths beat cached inventories.** Flyway migration directory is schema history; `docs/adr/*.md` is decision history. Documentation must point to these authorities instead of enumerating a stale subset.
- **Shared contract ownership must be complete or absent.** A partial `ApiRoutes` object creates false confidence while backend and Compose literals drift. Either migrate a route family fully or leave ownership explicit until its implementation ticket is ready.
- **Type finite wire values once.** Shared DTO strings duplicate persistence enums and permit invalid states. Convert only with an explicit unknown-value and serialized-name compatibility policy.
- **One adapter is a hypothetical seam.** Do not introduce generic repository/service interfaces or registries without a second concrete adapter and a deletion test showing complexity concentrates.
- **Deletion test is necessary but not sufficient for schema changes.** Removing apparently redundant indexes requires query plans and representative data; removing Exposed metadata requires proving no schema tooling consumes it.
- **Idempotency belongs beside uniqueness.** Scheduler check-then-insert logic is not atomic. When duplicate prevention matters, repository bulk operations and database uniqueness should own it together.
- **Lifecycle state should be one snapshot.** Related JWT algorithm, verifier, issuer, and audience fields must not be independently observable during initialization.
- **Tooling parsers need one implementation.** Normalizer and verifier duplicate source parsing; any syntax rule change must be made once and tested against malformed input.
- **Thresholds need one source.** k6 helper thresholds are authoritative; suite-specific profiles must be named rather than copied.
- **Dead ownership seams should be deleted, not documented.** `ReportViewModel` had no consumers while `FinanceReportsViewModel` owned active report state; an unused module is an invitation to future-agent misrouting.

## Rejected Recommendations

- Exposed unique-index metadata removal rejected: no material behavior or ownership gain proven without a schema-tooling path.
- Broad Compose state refactor rejected: no second adapter or specific invalid-state reduction proven; recent `ApiCallHandler` and KeepLast decisions remain authoritative.
- Generic interface/registry abstractions rejected: deletion test relocates complexity instead of concentrating it.

## Reopen Markers

- R3 reopens when enum unknown-value and backward-client policy is decided.
- R5 reopens when multi-instance scheduler volume or deployment concurrency becomes material.
- R7 reopens after representative query plans prove the two single-column trigram indexes redundant.
- A third transport-pin consumer reopens shared fixture design; existing marker is preserved in Map #89 fog.
- **Route ownership audits must scan callers after migration.** A shared route catalog can be structurally broad while a few production literals remain; grep every client path family after each route-contract child.
- **Transaction time is part of a module interface.** Mixing JVM and database clocks in rate, attendance, or capability-window paths creates boundary behavior that callers cannot observe or test reliably.
- **Gate ownership must be executable.** A generated-contract verifier documented as manual evidence is not a quality gate; hook/build/CI invocation must own drift detection.
- **Conflict-safe batch operations must report database truth.** A precheck followed by `ignore` insertion can preserve uniqueness while returning false creation counts under concurrent schedulers.
- **Snapshot fields need one-way ownership.** If business requirements define session type as a creation-time snapshot, exposing a later mutation route creates an invalid state; remove the mutation seam instead of adding more authorization around it.
- **Business-key uniqueness must own conflict behavior.** A service pre-check before insert does not serialize concurrent compensation creation; the unique database constraint must be translated into deterministic domain conflict handling inside the transaction-owned repository operation.
- **Persistence time belongs to the persistence owner.** JVM timestamps mixed with PostgreSQL `now()` create untestable boundary behavior; narrow fixes should use the database clock without inventing a universal clock abstraction.
- **Generated-contract checks must run in one ordered gate.** Normalization, verification, and source freshness are one dependency chain; documenting the verifier without invoking it in hooks and CI leaves drift unchecked.
- **Ownerless executors are hidden application state.** A scheduler executor created in startup but never retained cannot be stopped, restarted safely, or tested; lifecycle ownership must be explicit while work logic stays separate.
- **Parent-child idempotency must include URL parent.** A globally unique child UUID is not enough: idempotent lookup must scope by the parent embedded in the route, or a retry can return a foreign child.
- **Financial child links need domain ownership checks.** Independent foreign keys do not prove a remittance day belongs to remittance branch; resolve child through parent branch before writing.
- **Required gates must fail closed at discovery.** Empty output after an infrastructure/query failure is not an empty database; mandatory cleanliness checks must distinguish “clean” from “not inspected.”
