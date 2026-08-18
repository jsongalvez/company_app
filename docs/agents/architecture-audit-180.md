# Architecture Audit - Task 180

**Date:** 2026-08-17  
**Scope:** Entire repository, read-only product audit  
**Reference:** [audit-your-codebase.md](https://gist.github.com/aarondfrancis/8735edbe48532f97ee5ea818db4dbd47)  
**Ticket:** [AFK architecture audit: reduce code and harden future-agent seams](https://github.com/jsongalvez/company_app/issues/180)

## Constitution

This audit seeks deletion that concentrates complexity, deeper modules with smaller interfaces, strong locality, explicit ownership, and safe change one year from now. Style-only cleanup, speculative abstractions, and pass-through wrappers are skipped. Product code, tests, migrations, and behavior were not modified.

## Coverage Contract

Every row below has a stable ID, an ownership boundary, implementation files, interfaces/call sites, tests, and a completed disposition. Reviews were bounded to non-overlapping ownership and each returned at most two findings.

| ID | Subsystem and ownership boundary | Key files | Interfaces, call sites, tests | Status |
|---|---|---|---|---|
| C-01 | Compose common UI, state, navigation, ViewModels | `composeApp/src/commonMain/**` | `App`, `AppNavHost`, `ApiCallHandler`, all ViewModels; `composeApp/src/commonTest/**` | recommend R1; explicit skips |
| C-02 | Compose Android bridge | `composeApp/src/androidMain/**` | `actual` composables, `MainActivity`; common tests plus Android compile | skip except R1 |
| C-03 | Compose Desktop bridge | `composeApp/src/desktopMain/**` | `actual` composables, `Main`; desktop route test | skip except R1 |
| C-04 | Compose iOS bridge and `iosApp` host | `composeApp/src/iosMain/**`, `iosApp/**` | `MainViewController`, platform actuals; iOS compile | skip except R1 |
| C-05 | Shared domain and DTO contract ownership | `shared/src/commonMain/**` | `ApiRoutes`, enums, DTOs; backend routes and Compose ViewModels consume them | recommend R2/R3 |
| C-06 | Backend route and HTTP mapping layer | `backend/src/main/kotlin/**/api/**`, `Main.kt` | route objects, filters, mappers; route authorization tests | duplicate of R2; explicit skips |
| C-07 | Backend business modules | `backend/src/main/kotlin/**/service/**` | session, attendance, inventory, finance, branch-day, auth services; service Postgres tests | recommend R5/R6; explicit skips |
| C-08 | Backend persistence and Exposed models | `backend/src/main/kotlin/**/repository/**` | repositories and model tables; repository/service Postgres tests | recommend R7; skips recorded |
| C-09 | Flyway schema and migrations | `backend/src/main/resources/db/migration/**` | V1-V19 schema, indexes, views, constraints | recommend R7; documentation finding R4 |
| C-10 | Authentication and startup lifecycle | `auth/**`, `database/**`, `config/**`, `Main.kt` | `JwtService`, `DenyList`, `DatabaseConfig`; auth tests | recommend R6 |
| C-11 | Backend and Compose test infrastructure | `backend/src/test/**`, `composeApp/src/commonTest/**`, `desktopTest/**` | Postgres helpers, MockEngine, ViewModel tests | explicit skip; existing CR-036 report linked below |
| C-12 | Benchmarks and load tests | `backend/src/jmh/**`, `tests/k6/**` | JMH benchmarks, k6 helpers and suites | recommend R9 |
| C-13 | Scripts, hooks, build, CI, generated OpenAPI contract | `scripts/**`, `.githooks/**`, `.github/**`, `build.gradle.kts`, `scripts/openapi-route-contract.json` | hook gates, OpenAPI normalizer/verifier, Gradle tasks | recommend R10; existing gate findings not duplicated |
| C-14 | Architecture, requirements, ADRs, agent docs | `docs/architecture.md`, `docs/business-requirements.md`, `docs/engines.md`, `docs/adr/**`, `AGENTS.md` files | agent pointers and decisions | recommend R4; explicit skips |

Repository inventory at review: 167 backend production Kotlin files, 64 backend test files, 72 Compose common-main Kotlin files, 30 Compose common-test Kotlin files, 43 shared Kotlin files, 23 ADRs, 7 k6 files, 16 material scripts/tooling files, and Android/Desktop/iOS bridges. Generated Gradle/build output was excluded from ownership coverage because it is derived and ignored.

## Confirmed Recommendations

### R1 - Remove unused `currentTimestamp` platform seam

- **Verdict:** recommend; **disposition:** implement; **priority:** P0; **confidence:** high.
- **Evidence:** `composeApp/src/commonMain/kotlin/com/companyb/companyapp/util/Log.kt:24` declares `expect fun currentTimestamp()`. Android and Desktop implement it at `Log.android.kt:45` and `Log.desktop.kt:55`; iOS implements it at `Log.ios.kt:13`. Repository search found no consumer.
- **Current complexity/invalid state:** public common logging interface exposes dead expect/actual behavior; Android/Desktop duplicate formatter and `formatNow` code.
- **Simpler representation:** delete declaration and three actual functions; retain iOS `formatNow` only because iOS logging uses it.
- **Smallest scope/interfaces:** four platform logging files; no replacement interface.
- **Risks/migration:** low; a future timestamp caller must introduce a deliberate utility.
- **Validation:** grep zero `currentTimestamp(`; compile JVM, Android, iOS; common tests.
- **Dependencies:** none. **Deletion test:** all current call sites disappear with the seam.

### R2 - Make shared route ownership complete

- **Verdict:** recommend; **disposition:** implement; **priority:** P1; **confidence:** high.
- **Evidence:** `shared/.../api/ApiRoutes.kt:3-7` owns only prefix, login, register. Backend hard-codes session paths in `backend/.../api/routes/SessionRoutes.kt:55-163,282-302`; Compose hard-codes paths in `composeApp/.../viewmodel/ProductViewModel.kt:47-110`. Root `shared/AGENTS.md:17` says route constants live in shared.
- **Current complexity/invalid state:** backend, client, and tests can drift while the supposed contract owner contains only auth paths.
- **Simpler representation:** one shared path-builder/route catalog for consumed paths, preserving path parameters and query construction; keep HTTP behavior in route code unless a complete contract is intentionally chosen.
- **Smallest scope/interfaces:** `ApiRoutes`, all consumers migrated by subsystem, route tests updated. Do not add speculative auth metadata or a generated client in this slice.
- **Risks/migration:** broad mechanical migration; malformed path/query encoding and route naming are risks. Existing URLs must remain byte-equivalent.
- **Validation:** compile shared/backend/Compose; route contract tests compare every registered route and client path; grep removes duplicate literals.
- **Dependencies:** none. **Deletion test:** deleting per-consumer route literals leaves callers using shared builders.

### R3 - Type finite wire values in shared DTOs

- **Verdict:** recommend; **disposition:** implement; **priority:** P1; **confidence:** medium-high.
- **Evidence:** `shared/.../dto/SessionDto.kt:20-31,64-71`, `DashboardDto.kt:12-19`, `RemittanceDto.kt:6-26,33-39,80-86,109-125,147-169`, and `ExpenseDto.kt:6-13,28-43` use `String` for finite values. Backend reparses strings in `SessionRoutes.kt:373-390` and `RemittanceRoutes.kt:441`. Persistence enums are separately declared in `backend/.../repository/model/BranchDay.kt:17`, `Remittance.kt:13-25`, `Expense.kt:12`, and related models.
- **Current complexity/invalid state:** invalid strings can cross the wire; enum ownership and repeated `valueOf` parsing are duplicated.
- **Simpler representation:** add serializable shared wire enums for client-visible values and type DTO fields; map persistence enums at the backend boundary.
- **Smallest scope/interfaces:** shared domain/DTOs, backend mappers/routes, Compose renderers/tests. Inventory the compatibility policy before changing serialized values.
- **Risks/migration:** serialized enum names are an external contract; unknown future server values can break old clients. Requires explicit unknown-value strategy and fixture migration.
- **Validation:** serialization round trips, malformed-value behavior, every route response fixture, all backend and Compose tests.
- **Dependencies:** R2 is useful but not required. **Deletion test:** route-local `valueOf` and duplicated wire-string constants disappear; persistence mapping remains necessary.

### R4 - Repair stale architecture and ADR pointers

- **Verdict:** recommend; **disposition:** implement; **priority:** P0; **confidence:** high.
- **Evidence:** `docs/architecture.md:283` names nonexistent `V2__seed_capabilities.sql`; `:347-355` lists only V1/V2 although migrations reach V19. Root `AGENTS.md:60` says ADRs are 0001-0015 although repository has ADRs through 0023. `docs/adr/0019-repository-owns-before-state-capture.md:5,15-19` amends ADR-0013, but architecture audit guidance at `docs/architecture.md:379-383` does not point to ADR-0018/0019.
- **Current complexity/invalid state:** agents can search wrong schema files or apply superseded audit ownership rules.
- **Simpler representation:** make migration directory and all `docs/adr/*.md` authoritative; state that agents inspect status/supersedes/amends; correct filename and add audit ADR pointers.
- **Smallest scope/interfaces:** `AGENTS.md`, `docs/architecture.md`; no migration content changes.
- **Risks/migration:** documentation-only; verify links and claims against tree.
- **Validation:** file existence checks, grep old ranges/names, pointer review.
- **Dependencies:** none. **Deletion test:** stale enumerated caches are removed, leaving authoritative paths.

### R5 - Bulk/idempotent scheduler notification creation

- **Verdict:** recommend; **disposition:** defer pending volume/deployment decision; **priority:** P2; **confidence:** high.
- **Evidence:** `backend/.../service/NextAppointmentScheduler.kt:72-89` loops session/coordinator pairs, checks existence, then inserts. `:149-161` opens a transaction per check. `NotificationRepository.kt:20-42,58-70` owns single-row insert/existence; schema supplies the unique `(session_id,user_id)` index noted at `:55-57`.
- **Current complexity/invalid state:** O(sessions x coordinators) round trips and non-atomic check-then-insert can duplicate work under concurrent scheduler instances.
- **Simpler representation:** repository bulk insert of candidate tuples using the existing unique key and conflict handling; scheduler computes candidates and receives inserted count.
- **Smallest scope/interfaces:** scheduler, NotificationRepository, migration only if constraint verification proves needed; no HTTP contract.
- **Risks/migration:** preserve created-count semantics and partial failure behavior; database uniqueness must be confirmed.
- **Validation:** empty inputs, repeated run, concurrent run, partial failure, exact count.
- **Dependencies:** confirm deployment can have multiple instances before prioritizing. **Deletion test:** `notificationExists` and N-transaction loop disappear.

### R6 - Atomic JWT runtime value

- **Verdict:** recommend; **disposition:** defer until lifecycle/reinitialization work; **priority:** P2; **confidence:** medium-high.
- **Evidence:** `backend/.../auth/JwtService.kt:20-23` has four mutable fields, with nullable algorithm/verifier. `:25-42` assigns them separately; `:46-47,66-67` independently fail on incomplete initialization.
- **Current complexity/invalid state:** generation can read issuer/audience from a different initialization snapshot than algorithm; lifecycle state is implicit.
- **Simpler representation:** one immutable nullable `Runtime(issuer, audience, algorithm, verifier)` assigned once per `init`; each operation reads one local runtime.
- **Smallest scope/interfaces:** `JwtService.kt`; preserve public methods and token format.
- **Risks/migration:** repeated init semantics and concurrent reinitialization must be tested.
- **Validation:** pre-init failure, repeated init, concurrent generate/verify, token compatibility.
- **Dependencies:** none. **Deletion test:** four fields collapse to one runtime value without caller changes.

### R7 - Remove redundant client trigram indexes

- **Verdict:** recommend; **disposition:** implement after query-plan verification; **priority:** P2; **confidence:** medium.
- **Evidence:** `backend/src/main/resources/db/migration/V1__full_schema.sql:193-195` defines separate first-name and last-name GIN trigram indexes beside composite `idx_client_trgm` covering first, middle, and last names. Current search reads all three name columns.
- **Current complexity/invalid state:** redundant write/storage overhead and unclear index ownership.
- **Simpler representation:** retain composite index; add migration dropping the two redundant indexes only after `EXPLAIN` confirms no single-column query depends on them.
- **Smallest scope/interfaces:** migration and client-search query-plan tests; no Exposed model change.
- **Risks/migration:** planner may prefer single-column indexes for selective queries; rollback requires recreating indexes.
- **Validation:** representative typo/name-prefix plans before/after, write benchmark, search integration tests.
- **Dependencies:** schema inventory and production-like data statistics. **Deletion test:** deleting two indexes leaves all required search predicates covered.

### R8 - Remove duplicate Exposed uniqueness metadata only if schema tooling is proven absent

- **Verdict:** skip as recommendation; **disposition:** reject.
- **Evidence:** Exposed `.uniqueIndex()` metadata appears in `backend/.../repository/model/AppUser.kt:28,42` and `CapabilityTable.kt:8`; Flyway owns equivalent constraints in V1. No current production `SchemaUtils` path was proven.
- **Reason rejected:** removing metadata may reduce discoverability without behavior benefit; adding it may help tests/tooling. This is ownership ambiguity, not a material simplification until schema tooling or generated DDL is introduced.
- **Deletion test:** inconclusive. **Validation needed before reopening:** search all build/test/runtime schema initialization and inspect generated DDL behavior.

### R9 - Centralize k6 threshold profiles

- **Verdict:** recommend; **disposition:** implement; **priority:** P2; **confidence:** high.
- **Evidence:** `tests/k6/helpers.js:44-66` defines thresholds; `tests/k6/baseline.js:8-16` redefines overlapping keys; `full-suite.js:3,8` consumes shared thresholds. `backend/AGENTS.md:441-442` calls helpers the single source.
- **Current complexity/invalid state:** baseline and full suite can silently drift.
- **Simpler representation:** export baseline profile from helpers; baseline keeps only unique additions.
- **Smallest scope/interfaces:** two JS files plus threshold tests/documentation.
- **Risks/migration:** preserve intentionally different profiles by naming them explicitly rather than silently merging.
- **Validation:** load both scripts, assert expected profiles and threshold values.
- **Dependencies:** none. **Deletion test:** duplicate baseline literals disappear.

### R10 - Share OpenAPI source parser

- **Verdict:** recommend; **disposition:** implement; **priority:** P2; **confidence:** high.
- **Evidence:** `scripts/normalize-openapi-spec.mjs:42-67,101-136` and `scripts/verify-openapi-spec.sh:35-90` independently implement comment stripping, balanced delimiter parsing, and annotation scanning. Their unclosed-delimiter behavior differs: normalizer returns partial/empty values while verifier throws.
- **Current complexity/invalid state:** generator and verifier can disagree as parser behavior evolves.
- **Simpler representation:** one `.mjs` parser module imported by both scripts; verifier consumes normalized source-binding metadata where possible.
- **Smallest scope/interfaces:** scripts and tests; generated contract unchanged.
- **Risks/migration:** shell-to-Node invocation and error-code compatibility; preserve current verification failures.
- **Validation:** fixtures for comments, nested delimiters, malformed annotations, and generated contract parity.
- **Dependencies:** none. **Deletion test:** one parser implementation remains.

### R11 - Delete unused `ReportViewModel`

- **Verdict:** recommend; **disposition:** implement; **priority:** P0; **confidence:** high.
- **Evidence:** `composeApp/src/commonMain/kotlin/com/companyb/companyapp/viewmodel/ReportViewModel.kt:14` is the only repository reference to `ReportViewModel`; independent grep found no production, test, reflection, or DI reference. Active report state is owned by `FinanceReportsViewModel`.
- **Current complexity/invalid state:** dead ViewModel adds an obsolete ownership seam and invites future callers to choose the wrong report owner.
- **Simpler representation:** delete the file; keep `FinanceReportsViewModel` as report owner.
- **Smallest scope/interfaces:** one file, followed by compile and source/reference search.
- **Risks/migration:** low; reflection/DI check is required before deletion.
- **Validation:** grep symbol and file path, compile Compose targets, common tests.
- **Dependencies:** none. **Deletion test:** complete; only declaration disappears.

## Explicit Skips and Rejected Leads

- No Compose state-machine rewrite: nullable fields and Boolean flags are widespread, but bounded review found no second concrete adapter or material invalid-state reduction beyond R1/R11. `ApiCallHandler`/KeepLast seams are recent deliberate decisions in ADRs and issue history.
- No new Compose UI-test harness: existing architecture intentionally uses ViewModel/common tests; the separate CR-036 audit records the gap and follow-up choices.
- No migration consolidation/reordering: Flyway checksums and deployment history make it unsafe.
- No Exposed foreign-key/check-constraint duplication: database remains authoritative and model duplication would increase drift.
- No generic repository/service interface: one adapter per concrete module; deletion test fails because interfaces would relocate, not concentrate, complexity.
- No broad route metadata registry or generated client in R2: that would be scope expansion beyond path ownership.
- No broad enum conversion in R3 without an unknown-value/backward-compatibility decision; unresolved contract choices are fog, not guessed.
- No scheduler bulk implementation in this audit: implementation belongs in a separate task after deployment-volume decision.

## Cross-Cutting Patterns

1. **Ownership drift:** shared routes and architecture docs claim ownership while literals and current migration history live elsewhere. R2 and R4 address the smallest authoritative seams.
2. **Duplicated parsers and finite values:** OpenAPI scripts and DTO/persistence enums independently encode the same wire facts. R3 and R10 reduce invalid states and parser disagreement.
3. **Lifecycle state hidden in mutable fields:** JWT runtime is the backend instance; scheduler idempotency is the database instance. R5 and R6 make state ownership explicit.
4. **Dead or redundant structures:** the Compose timestamp seam and client trigram indexes fail deletion tests in different ways: one is dead, one is potentially redundant and requires query-plan proof.

## Audit Validation

- **Coverage pass:** re-counted source/test/platform/schema/script/CI/docs roots and added explicit rows C-01 through C-14; the late dead-ViewModel review added R11. Generated output excluded as derived.
- **Duplication/ownership pass:** merged route-literal findings into R2, merged stale docs findings into R4, and rejected Exposed uniqueness metadata as unproven.
- **Materiality pass:** kept only deletion, invalid-state, atomicity, drift, or measurable query-cost findings. Removed style-only and speculative abstraction leads.
- **Schema pass:** checked V1 indexes, uniqueness, views, constraints, V1-V19 migration presence, and Exposed mappings. R7 remains gated on `EXPLAIN`.
- **Dependency/priority pass:** P0 dead seam/docs first; P1 contract changes next; P2 behavior/performance/tooling after compatibility or plan evidence.

## Audit Log

| Pass | Work | Result |
|---|---|---|
| 1 | Inventory and bounded subsystem reviews | C-01..C-14 complete; 10 candidate findings, explicit skips captured |
| 2 | Independent evidence verification | R1-R7, R9-R11 accepted; R8 rejected; route/parser/index duplicates narrowed |
| 3 | Falsification and deletion tests | No accepted finding lacks evidence, scope, risk, validation, confidence, dependency, or deletion test |
| 4 | Fresh coverage, duplication, materiality, schema, priority passes | Zero omissions or unresolved overlaps found |

## Follow-up Work

Implementation recommendations require separate child tasks of audit task #180. Task #180 is itself a child of Map #89, preserving one main issue for audit follow-ups. Human-choice fog remains for R3's unknown enum strategy and R5's deployment-volume priority; this AFK audit does not guess. The resolution comment links created tasks and their dependencies.

## Related Audit

`.scratch/code-review/issues/cr-036-quality-gate-effectiveness-audit.md` records the existing quality-gate effectiveness audit: 3/11 effective, 5/11 false-confidence, 3/11 manual-only. Its findings were not duplicated here except R9, which is a concrete code-count/drift simplification.

## Permanent-Map Refresh - Session 114

The repository was re-audited after the original recommendations and implementation children were completed. Four non-overlapping read-only lanes covered Compose/platform bridges, backend modules, shared/schema contracts, and tests/tooling/docs. Existing C-01..C-14 boundaries remain complete; no new subsystem omission, schema ownership conflict, or duplicate recommendation was found.

### Fresh candidates

#### R12 - Finish shared route ownership in Compose

- **Verdict:** recommend; **disposition:** implement; **priority:** P1; **confidence:** high; **child:** [Build: finish shared route ownership in Compose](https://github.com/jsongalvez/company_app/issues/192).
- **Evidence:** `FinanceReportsViewModel.kt:696` hard-codes branch-day users; `:1187-1197` constructs monthly, all-time, and range export URLs; `SessionDashboardViewModel.kt:413-435` constructs session type/status paths; `BranchViewModel.kt:108-112` constructs assignment deletion. Existing builders cover the first three families, and one smallest assignment builder can cover the fourth.
- **Current complexity:** shared `ApiRoutes` is a false-complete contract owner while production callers retain drift-prone literals.
- **Simpler representation:** migrate callers to existing builders and add only the missing assignment builder.
- **Scope/interface:** four Compose ViewModel call sites, shared route tests, and affected compilation; no generated client or metadata registry.
- **Risk/validation:** preserve byte-equivalent URLs and query encoding; grep literals, route byte tests, shared/Compose compilation and common tests.
- **Dependency/deletion test:** none; deleting duplicated literals leaves callers on shared builders.

#### R13 - Make production persistence time use one clock authority

- **Verdict:** recommend; **disposition:** defer; **priority:** P1; **confidence:** high.
- **Evidence:** `SessionBaseRateService.kt:45-56` uses JVM `OffsetDateTime.now`; `SessionService.kt:134-139`, `AttendanceRepository.kt:66-72`, and `ReliefAccessRepository.kt:65-74` use JVM time in rate, attendance, and capability-window paths. Backend guidance requires DB `CurrentTimestampWithTimeZone` for transaction writes.
- **Current complexity:** JVM/DB clock skew can shift effective rates, relief authorization windows, and clock-in eligibility.
- **Simpler representation:** use DB timestamp expressions inside transactions, or an explicit injected clock for pure time decisions.
- **Scope/interface:** affected time-owning services/repositories plus boundary tests; no broad time abstraction without a second adapter.
- **Risk/validation:** preserve transaction ordering and test clock skew, rate boundaries, relief windows, and commission eligibility.
- **Dependency/deletion test:** none; production persistence paths lose direct JVM clock reads without changing domain interfaces.

#### R14 - Enforce OpenAPI verification in mandatory gates

- **Verdict:** recommend; **disposition:** defer; **priority:** P1; **confidence:** high.
- **Evidence:** `backend/build.gradle.kts:59-66` normalizes OpenAPI only; `.githooks/pre-commit:49-50` and `.githooks/pre-push:23-108` do not invoke `scripts/verify-openapi-spec.sh`; only manual gate documentation records verification.
- **Current complexity:** generated contract verification is a documentation-only seam, so route/DTO drift can pass required gates.
- **Simpler representation:** invoke verifier after normalization in the existing gate and include its inputs in CI path coverage.
- **Scope/interface:** hook/build/CI wiring and one drift fixture; no parser changes.
- **Risk/validation:** avoid requiring generated output before normalization; mutate an annotation/route and assert the mandatory gate fails.
- **Dependency/deletion test:** parser sharing from R10 is complete; deleting manual-only verification leaves enforced contract validation.

#### R15 - Return actual inserted count from notification batch creation

- **Verdict:** recommend; **disposition:** defer; **priority:** P1; **confidence:** high.
- **Evidence:** `NotificationRepository.kt:25-47` prefilters candidates, uses `batchInsert(ignore = true)`, then returns `pending.size`; concurrent schedulers can conflict on the unique key while reporting rows they did not insert.
- **Current complexity:** scheduler observability diverges from database state under concurrency.
- **Simpler representation:** remove precheck/filtering and return the insert operation's actual inserted count while retaining the unique constraint.
- **Scope/interface:** `NotificationRepository.insertBatch`, scheduler result/log tests, and concurrent/repeated-run tests.
- **Risk/validation:** preserve one row per `(session_id,user_id)` and exact counts under empty, repeated, concurrent, and partial-failure cases.
- **Dependency/deletion test:** existing unique notification constraint; deleting the precheck concentrates idempotency in one database-backed operation.

#### R16 - Delete unused `SessionState.isLoggedIn` machinery

- **Verdict:** recommend; **disposition:** defer; **priority:** P1; **confidence:** high.
- **Evidence:** `composeApp/src/commonMain/kotlin/com/companyb/companyapp/state/SessionState.kt:44-47` defines `isLoggedIn` with `GlobalScope`, `SharingStarted`, `map`, and `stateIn`; repository search finds no consumer.
- **Current complexity:** dead public state exposes an unnecessary lifecycle seam and coroutine scope.
- **Simpler representation:** delete property and unused imports.
- **Scope/interface:** one common state file; compile and common tests.
- **Risk/validation:** low; verify zero symbol consumers and Compose compilation.
- **Dependency/deletion test:** no callers require migration; supporting machinery disappears with the dead property.

### Refresh audit log

| Pass | Work | Result |
|---|---|---|
| 5 | Fresh bounded lanes across all existing ownership rows | C-01..C-14 rechecked; no omission |
| 6 | Independent evidence and deletion-test verification | R12-R16 complete fields; R12 selected as next child; R13-R16 deferred by one-child frontier rule |
| 7 | Duplication, materiality, schema, and priority falsification | R12 is distinct from completed route work; R13-R16 are not style-only or speculative |

## Permanent-Map Refresh - Session 116

Fresh bounded read-only lanes rechecked C-01..C-14 across Compose/platform bridges, backend
services/routes/auth, shared contracts/schema, persistence/migrations, tests/tooling/CI, and
architecture documentation. Independent coverage, duplication, materiality, schema, and
dependency-priority passes completed.

### Accepted candidate

#### R17 - Enforce immutable session type snapshots

- **Verdict:** recommend; **disposition:** implement; **priority:** P0; **confidence:** high; **child:** [Build: enforce immutable session type snapshots](https://github.com/jsongalvez/company_app/issues/193).
- **Evidence:** `docs/business-requirements.md:172` says session type is never manually changed; `backend/src/main/kotlin/com/companyb/companyapp/api/routes/SessionRoutes.kt:282-286` registers `PATCH /api/sessions/{sessionId}/type`; `backend/src/main/kotlin/com/companyb/companyapp/service/session/SessionService.kt:193-235` persists arbitrary type changes; `shared/src/commonMain/kotlin/com/companyb/companyapp/dto/SessionDto.kt:29` owns the mutation request; `composeApp/src/commonMain/kotlin/com/companyb/companyapp/viewmodel/SessionDashboardViewModel.kt:409-419` sends it. Existing tests at `backend/src/test/kotlin/com/companyb/companyapp/service/SessionServicePostgresTest.kt:353-375` and `backend/src/test/kotlin/com/companyb/companyapp/api/routes/SessionEditAuthzTest.kt:125-209` assert the contradictory behavior.
- **Current complexity/invalid states:** a creation-time snapshot can be rewritten after client history and pricing decisions were applied, so stored session type no longer expresses the algorithm result. The route, DTO, service method, UI editor, and tests form an unnecessary mutation seam.
- **Simpler representation:** delete the type mutation route, request DTO, service/repository update path, and Compose type editor; retain creation-time computation and independent status/final-price edits.
- **Smallest scope/interfaces:** shared DTO/routes, backend route/service/repository and OpenAPI registration, Compose dashboard edit state, and affected tests. No schema change.
- **Risks/migration:** existing persisted rows remain unchanged; generated contract and UI field lists must stay consistent. Removing current test expectations is safe because migration cost is zero, but authorization/error tests need replacement with absence/contract assertions.
- **Validation:** grep zero production references to the type mutation; route/OpenAPI contract verification; session creation/type algorithm tests; status and final-price edit tests; backend/shared/Compose compilation and integration gates.
- **Dependencies:** none. **Deletion test:** pass; deleting the mutation seam removes contradictory behavior and leaves type computation localized at session creation.

### Deferred and rejected leads

- **Retain R13-R16:** JVM/DB clock authority, mandatory OpenAPI verification, notification inserted-count truth, and dead `SessionState.isLoggedIn` remain valid but are lower priority than the domain-contract breach.
- **Deferred:** scheduler executor lifecycle ownership, compensation insert race, logout completion ownership, duplicate unused route builder, JMH annotation duplication, and shared/backend `DayStatus` unification. Each needs a separate implementation slice; none displaces R17.
- **Rejected:** claimed missing iOS `actual` implementations were not accepted without current target/source verification; platform navigation duplication was skipped because Android/Desktop behavior differs; route ownership and finite-value findings duplicate completed work.

### Refresh audit log

| Pass | Work | Result |
|---|---|---|
| 8 | Bounded subsystem reviews | All C-01..C-14 reviewed; each lane returned findings or explicit skips |
| 9 | Independent evidence verification | R17 confirmed against business requirements, route/service/DTO/UI call sites, and tests |
| 10 | Coverage, duplication, materiality, schema, priority | No omission; completed findings retired; R17 selected as sole next child |

## Permanent-Map Refresh - Session 118

After implementation child R17, a focused read-only audit rechecked deferred
backend clock, notification, OpenAPI-gate, Compose-state, and lifecycle leads.
The existing C-01..C-14 coverage contract remains complete. Independent lanes
also rechecked the current implementation and tests for a fresh finance race.

### Accepted candidate

#### R18 - Make compensation creation conflict-safe

- **Verdict:** recommend; **disposition:** implement; **priority:** P0; **confidence:** high; **child:** [Build: make compensation creation conflict-safe](https://github.com/jsongalvez/company_app/issues/194).
- **Evidence:** `backend/src/main/kotlin/com/companyb/companyapp/service/CompensationService.kt:39-42` performs the `(user_id, paying_branch_day_id)` existence check before `CompensationRepository.create`. `backend/src/main/kotlin/com/companyb/companyapp/repository/CompensationRepository.kt:47-67` makes its transaction idempotent only by caller-supplied primary key, then inserts. `backend/src/main/resources/db/migration/V1__full_schema.sql:368-379` enforces business-key uniqueness.
- **Current complexity/invalid states:** concurrent requests with different client UUIDs can both pass the service pre-check; one then receives an unhandled database uniqueness failure instead of domain `ConflictException`. Same-ID retry and business-key conflict semantics are split across separate layers.
- **Simpler representation:** make business-key conflict handling part of the transaction-owned repository create operation, translating a losing unique-key race to the existing domain conflict while retaining same-ID idempotency. Audit only newly inserted rows.
- **Smallest scope/interfaces:** `CompensationService`, `CompensationRepository`, relevant exception mapping, and focused compensation conflict/concurrency tests. No generic repository interface or schema change.
- **Risks/migration:** preserve same-ID retries, remitted-day authorization and reason checks, audit atomicity, and distinction between duplicate primary key and duplicate business key. Database constraint remains the concurrency backstop.
- **Validation:** existing compensation tests; same-ID retry; existing business-key conflict; concurrent different-ID creation; audit-row count; full backend quality gate and test-data cleanliness.
- **Dependencies:** existing unique business-key constraint only. **Deletion test:** removing the service pre-check leaves one repository transaction and database constraint owning idempotency/conflict behavior; no caller must coordinate a separate race-prone lookup.

### Deferred and rejected leads

- **Retain R13:** direct JVM time remains in rate-window, session-price selection, attendance insert-ignore, relief grant, invite, and remittance paths. Focused review narrowed strongest first slice to rate-window authority (`SessionBaseRateService` plus `SessionService`); no universal clock abstraction is justified. Manila calendar-date consolidation remains deferred.
- **Retain R14:** OpenAPI verification remains absent from mandatory build/hooks/CI gates; parser sharing is complete, and a separate gate-wiring ticket remains appropriate after the current child.
- **Retain R15:** notification batch creation still returns candidate count rather than actual inserted count under concurrent schedulers; existing uniqueness remains correct. No displacement of R18.
- **Retain R16:** `SessionState.isLoggedIn` has zero Kotlin consumers and its `GlobalScope` machinery remains deletable; production navigation uses `currentUser`. Low-risk deletion remains deferred.
- **Reject or defer lifecycle leads:** scheduler executor shutdown lacks a current restart/test lifecycle requirement; logout completion has no active production consumer defect; duplicate route builder is unused and mechanical; JMH annotation repetition has no material invalid state; shared/backend `DayStatus` separation is intentional wire/persistence ownership; platform actual claims remain unverified.

### Refresh audit log

| Pass | Work | Result |
|---|---|---|
| 11 | Focused bounded lanes over deferred leads and fresh finance seam | R13-R16 rechecked; R18 independently evidenced |
| 12 | Coverage and duplication pass | Existing C-01..C-14 coverage complete; compensation race distinct from notification idempotency |
| 13 | Materiality and falsification pass | R18 is a concurrent financial-write failure, not style or hypothetical abstraction; deferred leads retained with narrowed scope |

## Candidate Verification Refresh - Session 120

The focused post-R18 audit retained every materially accepted candidate instead of selecting one early. Five non-overlapping lanes produced candidate dossiers; explicit skips and rejected leads remain outside the candidate set.

### Candidate ledger

| Candidate | Evidence | Exploration | Verification | Disposition | Next ticket |
|---|---|---|---|---|---|
| R13 - unify production persistence clock | complete | complete | Luna + deterministic evidence pending | implement/defer ordering | not created |
| R14 - enforce OpenAPI verification in mandatory gates | complete | complete | Luna + deterministic evidence pending | implement/defer ordering | not created |
| R15 - return actual inserted count from notification batch creation | complete | complete | Luna + deterministic evidence pending | implement/defer ordering | not created |
| R16 - delete unused `SessionState.isLoggedIn` machinery | complete | complete | Luna + deterministic evidence pending | implement/defer ordering | not created |
| R19 - own scheduler executor lifecycle | complete | complete | Luna + deterministic evidence pending | implement/defer ordering | not created |

### Verification contract

- GPT-5.6 Luna is sole verifier. Continuous scoring is used when OpenCode2 exposes scoring-token logprobs; structured repeated scoring is the fallback and is marked lower confidence.
- Every candidate receives position-blinded repeated checks across fact integrity, domain coherence, long-term architecture, adversarial falsification, and feasibility.
- Deterministic repository evidence is authoritative. Failed paths, symbols, requirements, ADR, schema, grep, build, or test checks block `verified` regardless of Luna score.
- No implementation child is created until all five candidates have a final disposition. The next implementation ticket is selected by dependency-aware ranking, not by deleting lower-ranked candidates.

### Refresh audit log

| Pass | Work | Result |
|---|---|---|
| 14 | Focused lanes over all retained deferred leads and lifecycle seams | R13-R16 reverified; R19 surfaced; no candidate hidden |
| 15 | Candidate completeness and overlap pass | Five materially distinct candidates retained; explicit skips remain excluded |
| 16 | Lifecycle protocol pass | Every candidate requires dossier, deterministic checks, Luna verification, falsification, and disposition before ticketing |

## Candidate Verification Refresh - Session 121

Five retained candidates received complete read-only dossiers, deterministic repository checks,
structured repeated rubric review (fallback mode; OpenCode2 scoring-token logprobs were not
available), adversarial falsification, and explicit dispositions. Deterministic repository facts
remain authoritative.

### Candidate dossiers and dispositions

#### R13 - make production persistence time use one clock authority

- **Lifecycle:** `identified -> evidenced -> explored -> falsified -> verified -> dispositioned`.
- **Deterministic evidence:** JVM `OffsetDateTime.now(UTC)` remains in `SessionBaseRateService.kt:45,72`, `SessionService.kt:134`, `AttendanceRepository.kt:71`, `ReliefAccessRepository.kt:72`, and `ReliefInviteRepository.kt:60`; most persistence writes use `CurrentTimestampWithTimeZone`; capability validity is evaluated by PostgreSQL `now()`.
- **Falsification:** universal clock replacement is rejected. Manila calendar decisions, JWT timing, and test-controlled pure decisions have different ownership. The implementation slice is limited to persistence timestamps and rate-window transaction locality.
- **Deletion test:** direct JVM persistence reads disappear from the selected paths; no universal clock module is introduced.
- **Disposition:** **implement**, P1. First implementation ticket covers rate-window authority and the explicitly timestamped `insertIgnore` paths. Defer calendar consolidation, JWT/DenyList timing, and broad clock abstraction.

#### R14 - enforce OpenAPI verification in mandatory gates

- **Lifecycle:** `identified -> evidenced -> explored -> falsified -> verified -> dispositioned`.
- **Deterministic evidence:** `backend/build.gradle.kts:59-66` normalizes only; `.githooks/pre-commit:49-51` and `.githooks/pre-push:13-108` do not verify; `scripts/verify-openapi-spec.sh:6-200` is fail-closed and currently manual; `docs/gates/178-openapi-documentation.md:18-25` is the only documented invocation.
- **Falsification:** invoking verification without explicit generation/normalization ordering can produce false failures, so one ordered Gradle task must own compile, normalize, and verify. Local hooks alone are insufficient because direct pushes bypass them.
- **Deletion test:** manual-only verification is removed while one shared mandatory gate remains; parser sharing from R10 is already complete.
- **Disposition:** **implement**, P1. Add one ordered Gradle gate reused by hooks and CI, with source-path coverage and a negative drift check.

#### R15 - return actual inserted count from notification batch creation

- **Lifecycle:** `identified -> evidenced -> explored -> falsified -> verified -> dispositioned`.
- **Deterministic evidence:** `NotificationRepository.kt:22-51` pre-queries and returns `pending.size` after `batchInsert(ignore = true)`, while `V1__full_schema.sql:567-581` correctly enforces unique `(session_id,user_id)`. Concurrent callers can report two creations for one committed row.
- **Falsification:** `Main.initializeScheduler()` uses one single-thread executor (`Main.kt:176-202`), and architecture docs describe one backend deployment. Sequential reruns are correct. The race is real for multiple instances, overlapping invocations, or future callers, but deployment topology is not confirmed.
- **Deletion test:** removing the precheck would concentrate idempotency in the database, but the correct Exposed bulk inserted-count API is not yet proven.
- **Disposition:** **defer**, retained. No child until deployment topology or a concrete overlapping-caller requirement makes priority actionable. Preferred future shape is repository-owned idempotency with reliable inserted counts, using transactional `insertIgnore` summation if bulk counts cannot be proven.

#### R16 - delete unused `SessionState.isLoggedIn` machinery

- **Lifecycle:** `identified -> evidenced -> explored -> falsified -> verified -> dispositioned`.
- **Deterministic evidence:** repository-wide Kotlin search finds only the declaration at `SessionState.kt:44-47`; active navigation uses `currentUser`, token validation uses `/api/me`, and tests assert explicit identity/clock state instead.
- **Falsification:** no production, platform, test, reflection, DI, or service-loader consumer exists. Historical scratch specs do not create an active interface.
- **Deletion test:** deleting the property and four supporting imports removes a `GlobalScope` coroutine seam without moving behavior.
- **Disposition:** **implement**, P2. One-file deletion with common/desktop/Android/iOS compilation and focused state tests.

#### R19 - own scheduler executor lifecycle

- **Lifecycle:** `identified -> evidenced -> explored -> falsified -> verified -> dispositioned`.
- **Deterministic evidence:** `Main.initializeScheduler():176-202` creates a local daemon executor, discards its reference, and exposes no stop/restart hook. `main(config):209-220` starts it before Javalin initialization. Only `NextAppointmentScheduler.run(clock)` is directly testable.
- **Falsification:** one normal process still delivers notifications, and the notification unique key limits duplicate rows. Those facts do not provide shutdown, startup-failure cleanup, reinitialization safety, or lifecycle tests.
- **Deletion test:** moving executor ownership behind an explicit lifecycle module deletes the ownerless local executor state; work computation remains in `NextAppointmentScheduler`.
- **Disposition:** **implement**, P1. Define explicit start/stop ownership and deterministic lifecycle tests. Keep notification count semantics separate from R15.

### Implementation order

1. R19 scheduler lifecycle, because it owns the currently ownerless startup resource and prevents duplicate scheduler instances.
2. R14 mandatory OpenAPI verification, because it hardens every later route/contract change.
3. R13 targeted persistence-clock cleanup, because it changes backend write/read time semantics and needs the full backend gate.
4. R16 dead `SessionState.isLoggedIn` deletion, because it is isolated and low-risk.
5. R15 notification inserted-count truth remains deferred until deployment topology or overlapping invocation requirements are confirmed.

### Verification notes

- Five independent bounded dossiers were completed before ranking; no candidate was suppressed by a higher-ranked candidate.
- Structured repeated rubric fallback covered fact integrity, domain coherence, long-term architecture, adversarial falsification, feasibility, and comprehension. Continuous Luna scoring was unavailable in this environment; no unsupported score is claimed.
- No product code, tests, migrations, or behavior changed during this audit refresh.

## Session 122 operational priority

The full `./gradlew :backend:test` gate took 12m50s on this VPS. Two earlier runs exceeded
15 minutes and were terminated by the runner without a test failure; the scheduler-focused
subset completed in 20s. This is now the highest-priority follow-up audit ahead of the remaining
R14/R13/R16 implementation children: explain test discovery and worker behavior, database setup
and cleanup cost, serial bottlenecks, and any hidden hangs; then optimize without weakening test
isolation or coverage. Tracking ticket: `Audit: diagnose slow Gradle backend tests`.

## Permanent-Map Refresh - Session 228

After implementation child #200, the frontier was empty. A fresh full read-only audit rechecked
all C-01..C-14 ownership areas through four bounded lanes: Compose/platform bridges, shared
contracts, backend production/schema, and tests/tooling/docs. Independent verification then
falsified or narrowed every lead before disposition. Product source, tests, migrations, and
behavior remain unchanged.

### Coverage and dispositions

| Candidate | Evidence | Exploration | Falsification / verification | Disposition |
|---|---|---|---|---|
| R20 - enforce remittance line parent-scoped idempotency | complete | complete | verified; `addLine` returns a line found by global ID before checking `remittanceId` | implement, P0 |
| R21 - enforce remittance day-breakdown branch ownership | complete | complete | verified; service checks day existence but not remittance branch; submission can include a foreign branch day | implement, P0 |
| R22 - fail closed on cleanliness discovery failure | complete | complete | verified; first `docker exec` failure becomes empty output and exit 0 | implement, P1 |
| R23 - pair selected branch and clock state flows | complete | complete | verified as real torn-state risk, but broad consumer migration and ADR-0021 interaction make it lower priority | defer |
| R24 - remove BranchSelect child ViewModel ownership | complete | complete | verified lifecycle split; parent route also uses `remember`, requiring a broader lifecycle decision | defer |
| R25 - remove duplicate route template constants | complete | complete | narrowed: templates are needed by Javalin registrations; only exact aliases are mechanical and low materiality | reject as broad finding |
| R26 - unify attendance response DTOs | complete | complete | narrowed: wire shapes match, but operation-specific types are intentional readable interfaces with plausible future divergence | defer |

### R20 - Enforce remittance line parent-scoped idempotency

- **Verdict:** recommend; **disposition:** implement; **priority:** P0; **confidence:** high.
- **Evidence:** `backend/src/main/kotlin/com/companyb/companyapp/service/finance/remittance/RemittanceLineRepository.kt:44-51` selects by line ID alone and returns the row before checking `params.remittanceId`. The route authorizes the URL remittance, so a UUID belonging to remittance A can be replayed through remittance B and return A's line. The later raced lookup at `:66-75` does include entity reference but still omits parent scope.
- **Current invalid state:** parent-child URL ownership and idempotency identity disagree; a child from another remittance can cross the parent seam.
- **Simpler representation:** treat `(remittanceId, clientLineId)` as the request identity. Scope both existing-row reads to the parent; reject a UUID collision with a different parent rather than returning the foreign line.
- **Smallest scope:** `RemittanceLineRepository.addLine`, its service/API tests, and any exception mapping needed for the explicit conflict. No schema change.
- **Risks and validation:** preserve same-parent retries, duplicate session/product conflict behavior, version/audit atomicity, and concurrent different-ID handling. Add same-parent retry and cross-parent UUID tests; verify foreign data, version, and audit rows remain unchanged.
- **Dependencies:** existing line primary key and ADR-0019 repository transaction ownership. **Deletion test:** removing the parent predicate makes the cross-parent UUID test return the foreign line.

### R21 - Enforce remittance day-breakdown branch ownership

- **Verdict:** recommend; **disposition:** implement; **priority:** P0; **confidence:** high.
- **Evidence:** `backend/src/main/kotlin/com/companyb/companyapp/service/finance/remittance/RemittanceService.kt:314-323` verifies remittance status and calls global `requireBranchDayExists`. Existing `BranchDayService.requireBranchDayForBranch` at `:67-72` is unused here. `RemittanceDayBreakdownRepository.kt:18-50` stores independent parent/day IDs, and `V1__full_schema.sql:456-461` has no cross-branch constraint. Remittance submission consumes breakdown day IDs, so branch A can attach branch B's day.
- **Current invalid state:** a remittance's day breakdown can point outside its branch, crossing financial ownership and day-state transitions.
- **Simpler representation:** resolve the day through `requireBranchDayForBranch(branchDayId, remittance.branchId)` before insertion; keep the existing schema and repository parent key.
- **Smallest scope:** one service call plus service/API tests for same-branch success and foreign-branch rejection. Add parent-scoped UUID-collision coverage for `RemittanceDayBreakdownRepository.addDayBreakdown`, which currently ignores `insertedCount` before selecting by global ID.
- **Risks and validation:** reject malformed existing writes without mutating them; verify no foreign breakdown, snapshot contamination, remitted-day transition, or audit row. Migration is unnecessary for new data; inspect disposable test data before any cleanup.
- **Dependencies:** existing branch-scoped resolver and parent-child URL rule in `backend/AGENTS.md`. **Deletion test:** restoring global existence allows a foreign branch day to enter the remittance.

### R22 - Fail closed on test-database discovery failure

- **Verdict:** recommend; **disposition:** implement after R20/R21; **priority:** P1; **confidence:** high.
- **Evidence:** `scripts/check-test-cleanliness.sh:23-35` appends `|| echo ""` to the first `docker exec psql` query. Lines `37-41` then interpret empty output as an uninitialized database and exit 0. Hooks propagate script status, so this is a false-success quality gate when the container, database, or authentication is unavailable.
- **Current invalid state:** “database is clean” and “database could not be inspected” share the same successful result.
- **Simpler representation:** let the discovery query fail under `set -euo pipefail`; retain the intentional successful empty-schema skip. Do not broaden this slice into container configuration.
- **Smallest scope:** script and shell-focused tests/mocks. **Risks:** unavailable Docker correctly blocks hooks; hardcoded container naming remains a separate operational concern.
- **Validation:** `bash -n`; mocked docker failure, valid empty discovery, leaked row, and count-query failure; live cleanliness check.
- **Dependencies:** none. **Deletion test:** restoring the fallback makes the mocked discovery-failure test pass incorrectly.

### Deferred and rejected leads

- R23 remains deferred: pair only selected-branch and clock-state values if a focused Compose lifecycle/state ticket becomes higher priority; do not redesign ADR-0021 capability timing.
- R24 remains deferred: child ViewModel scope is not parent-owned, but fixing it requires deciding whether the route-created parent itself becomes lifecycle-owned. No speculative helper seam is justified in this audit.
- R25 is rejected as a broad candidate: Javalin template constants and client builders are different interfaces. Keep only exact aliases/mechanical cleanup for a future route audit.
- R26 is deferred: identical current payloads do not prove one shared interface is better; operation-specific DTO names preserve locality and future divergence without material cost.

### Audit-of-audit

- Coverage pass: C-01..C-14 all rechecked; platform hosts, generated-contract ownership, migrations, hooks, and test infrastructure included.
- Duplication/ownership pass: route-template duplication narrowed to aliases; attendance DTO duplication not promoted; remittance findings kept separate because one is child idempotency and one is branch ownership.
- Materiality pass: retained only parent-crossing financial defects and a false-success mandatory gate; state/lifecycle leads deferred with explicit scope reasons.
- Schema pass: verified remittance/day foreign keys are independent and existing unique keys do not encode branch ownership.
- Dependency/priority pass: R20 and R21 are the next financial integrity slice; R22 follows as a tooling gate fix. No parallel implementation child is opened in this session.

| Pass | Work | Result |
|---|---|---|
| 17 | Fresh bounded repository audit | C-01..C-14 complete; seven leads recorded |
| 18 | Independent deterministic verification | R20-R22 verified; R23-R24 verified but deferred; R25-R26 narrowed/rejected or deferred |
| 19 | Adversarial and deletion-test pass | Cross-parent UUID, foreign day, DB outage, torn state, lifecycle ownership, and route-template counterexamples checked |
| 20 | Coverage, duplication, materiality, schema, priority | No omission or unresolved overlap; R20/R21 selected as one implementation slice, R22 next |

## Permanent-Map Refresh - Session 234

After implementation children #201 and #202, the frontier was empty again. A fresh full
read-only audit rechecked C-01..C-14 across Compose and platform bridges, shared contracts,
backend modules and schema, and tests/tooling/documentation. Product code, tests, migrations,
and behavior remained unchanged.

### Coverage and dispositions

| Candidate | Evidence | Exploration | Falsification / verification | Disposition |
|---|---|---|---|---|
| R22 - fail closed on test-database discovery failure | complete | complete | verified; discovery failure still becomes empty output and exit 0 | implement, P1 |
| R23 - pair selected branch and clock state flows | complete | complete | real torn-state risk, but broad lifecycle migration and ADR-0021 interaction remain | defer |
| R24 - remove BranchSelect child ViewModel ownership | complete | complete | lifecycle split remains, but parent route ownership decision is prerequisite | defer |
| R25 - remove duplicate route template constants | complete | complete | templates and client builders are different interfaces; only aliases are mechanical | reject |
| R26 - unify attendance response DTOs | complete | complete | identical current shapes do not prove shared ownership; future divergence remains plausible | defer |

### R22 - Fail closed on test-database discovery failure

- **Verdict:** recommend; **disposition:** implement; **priority:** P1; **confidence:** high.
- **Evidence:** `scripts/check-test-cleanliness.sh:23-35` appends `|| echo ""` to the
  discovery `docker exec psql` command. `:37-41` then treats empty output as an
  uninitialized database and exits 0. The script is called by mandatory pre-commit and
  pre-push hooks, so an unavailable container, database, or authentication path can report
  successful cleanliness without inspecting any tables.
- **Current invalid state:** “test database is clean” and “test database could not be
  inspected” share one successful result. `scripts/clean-test-db.sh:21-37` repeats the same
  discovery fallback on the pre-push cleanup path, so this candidate owns both scripts and
  one failure-policy test matrix.
- **Simpler representation:** let the discovery command fail under `set -euo pipefail`;
  retain the intentional empty-schema success path after a successful query. Keep count-query
  failures fail-closed as they already do.
- **Smallest credible scope:** `scripts/check-test-cleanliness.sh`,
  `scripts/clean-test-db.sh`, focused shell tests or command mocks, and hook validation. No
  product code, schema, Docker configuration, or generic gate abstraction.
- **Risks and migration:** Docker/database outages will correctly block commits and pushes;
  hardcoded container naming is a separate operational concern. Preserve successful empty
  schema handling and leaked-row failures.
- **Existing/additional validation:** existing live cleanliness invocation; `bash -n`;
  mocked discovery failure, successful empty discovery, leaked row, and count-query failure;
  pre-commit/pre-push cleanliness invocation.
- **Dependencies:** none. **Deletion test:** restoring the fallback makes the mocked
  discovery-failure test pass incorrectly; removing it concentrates the clean-versus-unread
  distinction in one command result.

### R27 - Preserve branch-day before state in remittance submission audit

- **Verdict:** recommend; **disposition:** implement; **priority:** P1; **confidence:** high.
- **Evidence:** `backend/src/main/kotlin/com/companyb/companyapp/service/finance/remittance/RemittanceRepository.kt:337-359`
  updates all covered days to `REMITTED` at `:338`, then reads each row at `:346-351` and
  labels that post-update row `before` at `:352`. Both values passed to `SubmitAuditContext`
  are therefore `REMITTED`, even when the mutation was `OPEN` or lazily `PAST` to
  `REMITTED`. The undo implementation at `:435-476` already captures rows before mutation.
- **Current invalid state:** the immutable Audit Log loses the actual branch-day status
  transition, so financial history cannot explain what submission changed.
- **Simpler representation:** capture covered `BranchDay` rows before calling
  `updateBranchDayStatuses`, then update and read after rows to form true before/after pairs.
- **Smallest credible scope:** `RemittanceRepository.submit` and focused submission-audit
  tests. No schema or public HTTP change.
- **Risks and validation:** preserve SERIALIZABLE transaction and audit callback atomicity;
  submit remittances covering OPEN and lazy-PAST days, assert old status is prior state and
  new status is `REMITTED`, and confirm remittance audit remains unchanged.
- **Dependencies:** none beyond current audit callback ownership. **Deletion test:** moving
  the pre-update read back below `updateBranchDayStatuses` reproduces the false old state.

### R28 - Complete iOS Compose expect/actual bridge

- **Verdict:** recommend; **disposition:** implement; **priority:** P0; **confidence:** high.
- **Evidence:** common `expect` declarations include `ClientResultList` and
  `ClientDetailLayout` (`composeApp/src/commonMain/kotlin/com/companyb/companyapp/ui/screen/ClientScreenParts.kt:12-25`),
  `RemittanceRowList` (`RemittanceScreenParts.kt:10`), `UserSlotOrderList`
  (`UserManagementScreen.kt:332`), `AppNavHost` (`navigation/AppNavHost.kt:10`), and
  `saveDownload` (`util/SaveDownload.kt:11`). `composeApp/src/iosMain` has actuals only for
  token storage, HTTP engine, logging, and app config (`grep '^actual ' .../iosMain`); it has
  none for those UI/navigation/download expects. `iosApp/iosApp/ContentView.swift:5-8`
  calls `MainViewControllerKt.MainViewController()`, while
  `composeApp/src/iosMain/kotlin/com/companyb/companyapp/MainViewController.kt:5` declares
  `mainViewController()`.
- **Current invalid state:** repository declares iOS as a Compose target and ADR-0020 lists
  iOS support, but the target has unresolved platform seams and a host symbol mismatch. iOS
  support cannot be treated as an implemented platform.
- **Simpler representation:** provide iOS actuals for every common expect at the smallest
  divergent subtree, implement user-visible download/share behavior, and make Swift call the
  generated controller symbol that Kotlin actually exports. Do not copy Android/Desktop
  behavior wholesale without checking iOS APIs.
- **Smallest credible scope:** all missing iOS actuals, `MainViewController.kt`/Swift host,
  iOS-specific UI and download adapters, and iOS compile/smoke validation. This is one
  platform-support child because partial actuals cannot produce a usable iOS target.
- **Risks and validation:** platform layout and UIKit/Swift export naming can diverge; compile
  `iosArm64` and `iosSimulatorArm64`, inspect generated framework symbol, and exercise host
  launch and route transitions. Existing common ViewModel tests remain applicable.
- **Dependencies:** none. **Deletion test:** removing any required actual or the iOS target
  exposes the missing seam; the current source already fails that compile-time contract.

### R29 - Make full k6 workflow use valid fixtures and count failures

- **Verdict:** recommend; **disposition:** implement; **priority:** P1; **confidence:** high.
- **Evidence:** `tests/k6/full-suite.js:193-210` sends random `branchDayId` values for
  restock and movement; `:213-223` sends random branch-day and client IDs for product sales;
  `:225-266` sends random branch-day/user IDs for compensation, expense, and allowance.
  `tests/k6/helpers.js:41` owns the error rate, but many full-suite requests do not add their
  failures to it and do not assert intended success status.
- **Current invalid state:** full load tests present as successful business workflows while
  most dependent writes exercise expected foreign-key/day-state failures; the global error
  threshold can pass without counting those failures. Latency measurements then describe
  invalid requests, not usable operational paths.
- **Simpler representation:** create one valid branch-day/client fixture per VU, carry those
  IDs through dependent calls, centralize response checks and error accounting, and preserve
  endpoint-specific latency metrics.
- **Smallest credible scope:** `tests/k6/full-suite.js`, shared k6 helpers, fixture cleanup
  assumptions, and focused k6 validation. No product endpoint changes.
- **Risks and validation:** setup changes load profile and increases test data; preserve test
  database isolation and cleanup. Assert intended 2xx responses, count every failed request,
  run full suite against `company_app_test`, and verify cleanup.
- **Dependencies:** test fixture API shape and dev seeder behavior. **Deletion test:** restore
  random IDs and the success assertions/error accounting checks must fail.

### R30 - Finish remaining Compose shared route ownership

- **Verdict:** skip; **disposition:** reject as duplicate/low-materiality mechanical cleanup.
- **Evidence:** the shared catalog owns route families in
  `shared/src/commonMain/kotlin/com/companyb/companyapp/api/ApiRoutes.kt:38-179`, while
  remaining Compose suffix concatenations are limited to call sites such as
  `FinanceReportsViewModel.kt:93` and exact path construction in other ViewModels.
- **Reason rejected:** prior route-ownership children completed the contract migration; the
  remaining literals are narrow aliases or suffixes, not a new invalid domain state or
  meaningful ownership defect. Backend registration templates satisfy a different Javalin
  registration interface. Keep exact aliases as future mechanical cleanup only.

### Updated audit-of-audit

- Coverage pass: C-01..C-14 all rechecked; iOS target source sets, generated framework host,
  k6 workflow dependencies, remittance audit callbacks, migrations, and mandatory hooks were
  included.
- Duplication/ownership pass: R22 includes both cleanliness discovery scripts; R30 is not
  promoted over completed route ownership work. R27 is distinct from remittance child-link
  ownership because it repairs audit truth, not authorization or idempotency.
- Materiality pass: retained R22, R27, R28, and R29; deferred R23/R24/R26 and rejected R25/R30.
- Dependency/priority pass: R22 is first because it hardens mandatory local gates; R27 follows
  as a financial audit-integrity defect; R28 is a high-risk platform slice; R29 follows as
  test-infrastructure correctness. Create native blockers in that order.

### Deferred and rejected leads

- R23 remains deferred: pairing selected-branch and clock-state values requires a focused
  Compose lifecycle decision and must not redesign ADR-0021's capability timing.
- R24 remains deferred: removing the child ViewModel seam requires deciding ownership of the
  parent route-created state first; no speculative helper module is justified.
- R25 remains rejected as a broad candidate: Javalin route templates and client route builders
  satisfy different interfaces. Exact aliases can remain future mechanical cleanup.
- R26 remains deferred: operation-specific attendance DTO interfaces preserve locality and may
  diverge without material current cost.

### Audit-of-audit

- Coverage pass: C-01..C-14 all rechecked; platform hosts, generated-contract ownership,
  migrations, hooks, and test infrastructure included.
- Duplication/ownership pass: R22 kept separate from cleanup-script discovery and from generic
  gate design; R23/R24 remain lifecycle decisions, not mechanical cleanup.
- Materiality pass: only the mandatory false-success gate remained actionable. State, lifecycle,
  route-alias, and DTO leads were deferred or rejected with explicit reasons.
- Schema pass: no new schema candidate; remittance parent ownership is implemented and tests
  cover the parent-scoped seams.
- Dependency/priority pass: R22 is the sole next child. No parallel implementation child is
  opened.

| Pass | Work | Result |
|---|---|---|
| 21 | Fresh bounded repository audit | C-01..C-14 complete; five leads recorded |
| 22 | Independent deterministic verification | R22 verified; R23/R24 deferred; R25 rejected; R26 deferred |
| 23 | Adversarial and deletion-test pass | DB discovery outage, torn state, lifecycle ownership, route-interface, and DTO counterexamples checked |
| 24 | Coverage, duplication, materiality, schema, priority | No omission or unresolved overlap; R22 selected as sole next implementation child |

## Permanent-Map Refresh - Session 240

After implementation child #206, the frontier was empty again. Four bounded read-only lanes
rechecked Compose/platform bridges, backend modules, shared/schema contracts, and tests/tooling/docs.
No product source, tests, migrations, or behavior were changed during this audit.

### Coverage and dispositions

| Candidate | Evidence | Exploration | Falsification / verification | Disposition |
|---|---|---|---|---|
| R28 - complete iOS Compose bridge | stale | complete | all required iOS actuals and Swift host symbol now exist | retire |
| R31 - provision scheduler notification capability | complete | complete | role-derived view excludes this branch-scoped capability; production grants absent | implement, P0 |
| R32 - fail closed on malformed JMH baseline output | complete | complete | empty/truncated parser output reaches success path with zero failures | implement, P1 |
| R33 - type relief-access status in shared DTO | complete | complete | finite persistence/wire enum has no competing shared type; extends completed R3 | implement, P1 |
| R34 - make registration uniqueness race explicit | complete | complete | DB uniqueness backstop exists, but concurrent loser can escape repository as 500 | implement, P1 |

### R31 - Provision scheduler notification capability

- **Verdict:** recommend; **disposition:** implement; **priority:** P0; **confidence:** high.
- **Evidence:** V5 seeds `RECEIVE_NEXT_APPOINTMENT_ALERTS` into `role_capability` for COORDINATOR
  (`backend/src/main/resources/db/migration/V5__add_next_appointment_alerts_capability.sql:8-16`).
  The role-derived `active_user_capabilities` view only derives the explicit GLOBAL capability
  allowlist in V16 (`backend/src/main/resources/db/migration/V16__role_derived_global_capabilities.sql:78-104`),
  and excludes this branch-scoped code. The scheduler queries that view for this code with
  `context_type = BRANCH` (`backend/src/main/kotlin/com/companyb/companyapp/service/NextAppointmentScheduler.kt:116-139`).
  Repository search found no production insert for this capability; current scheduler tests manually
  insert direct grants (`NextAppointmentSchedulerPostgresTest.kt:316-327`).
- **Current invalid state:** Coordinator role membership alone produces no active capability row,
  so the scheduler finds no recipients and next-appointment notifications are silently absent.
- **Simpler representation:** keep recipient selection capability-based and add one authoritative
  production path for the branch-scoped Coordinator grant, or revise the capability view to derive
  this one branch-scoped role capability from active branch assignments. Prefer the latter only if
  the view can preserve assignment end dates and the existing capability interface; otherwise add
  an assignment-owned direct grant transaction.
- **Smallest credible scope:** migration/view or user-branch assignment provisioning, scheduler
  integration fixture, and a Coordinator-role-only test. No scheduler query rewrite or new role check.
- **Risks and validation:** preserve Coordinator-only semantics, assignment end/reassignment behavior,
  inactive-user revocation, and branch context. Test a role-only Coordinator with active assignment,
  ended assignment, inactive user, and non-Coordinator role; run backend quality and cleanliness gates.
- **Dependencies:** none. **Deletion test:** deleting the scheduler capability join would over-notify
  every active branch-assigned user; the missing grant must instead be fixed at capability ownership.

### R32 - Fail closed on malformed JMH baseline output

- **Verdict:** recommend; **disposition:** implement; **priority:** P1; **confidence:** high.
- **Evidence:** `scripts/check-baselines.sh:33-40` parses zero rows for empty or malformed JMH output;
  `:56-88` iterates no rows, and `:90-97` reports `OK` when `FAILURES` remains zero.
- **Current invalid state:** a successful JMH task with truncated or changed output can bypass the
  regression gate without checking any baseline benchmark.
- **Simpler representation:** require at least one parsed result and require every baseline benchmark
  to appear before comparison; preserve explicit support for intentionally new benchmarks.
- **Smallest credible scope:** shell comparator and fixture tests for empty, truncated, missing,
  complete, and regressed output. No benchmark or threshold changes.
- **Risks and validation:** avoid rejecting legitimate new benchmark output; validate parser format,
  baseline names, exit codes, and CI invocation.
- **Dependencies:** none. **Deletion test:** restoring empty-output success reproduces the false pass.

### R33 - Type relief-access status in shared DTO

- **Verdict:** recommend; **disposition:** implement; **priority:** P1; **confidence:** high.
- **Evidence:** `shared/src/commonMain/kotlin/com/companyb/companyapp/dto/AttendanceDto.kt:57-64`
  exposes `requestStatus: String`; backend persistence and route output use finite `ReliefStatus`
  values (`backend/src/main/kotlin/com/companyb/companyapp/repository/model/ReliefStatus.kt:3`;
  `backend/src/main/kotlin/com/companyb/companyapp/api/routes/ReliefAccessRoutes.kt:57,81,115`),
  backed by the database enum in V1.
- **Current invalid state:** arbitrary status strings can cross the shared wire contract.
- **Simpler representation:** add serializable shared `ReliefStatus`, type the DTO field, and map
  persistence status at the backend boundary. Do not reuse distinct relief-invite statuses.
- **Smallest credible scope:** shared domain/DTO, route response mapping, fixtures, malformed-value
  tests, and shared/backend/Compose compilation.
- **Risks and validation:** explicit unknown-value policy and preserved uppercase wire values.
- **Dependencies:** completed R3 provides the enum contract pattern. **Deletion test:** leaving String
  preserves an invalid wire state; the typed field removes it without a new module seam.

### R34 - Make registration uniqueness race explicit

- **Verdict:** recommend; **disposition:** implement; **priority:** P1; **confidence:** high.
- **Evidence:** `backend/src/main/kotlin/com/companyb/companyapp/service/AuthService.kt:55-76`
  performs username/email prechecks before `UserRepository.createUser`; V1 enforces unique username
  and email (`backend/src/main/resources/db/migration/V1__full_schema.sql:31-38`). Concurrent
  registrations can both pass prechecks and let one unique violation escape as an unclassified error.
- **Current invalid state:** database-enforced uniqueness and HTTP/domain collision classification
  are separated by a race-prone check-then-insert sequence.
- **Simpler representation:** make the repository insert atomic and translate the losing unique
  violation into the existing username/email result without removing database constraints.
- **Smallest credible scope:** user repository/service exception mapping and concurrent registration
  tests for each unique field. No schema change.
- **Risks and validation:** preserve password validation ordering, distinguish username from email,
  and ensure failed inserts create no partial record.
- **Dependencies:** none. **Deletion test:** removing prechecks while retaining atomic uniqueness
  keeps correctness and concentrates collision ownership in the write transaction.

### Retired and deferred leads

- R28 is retired: current iOS actuals and Swift host match the common expect declarations.
- R23/R24 remain deferred lifecycle decisions; R15 remains deferred pending deployment topology or
  overlapping scheduler invocation requirements; R26 remains deferred; R25/R30 remain rejected as
  mechanical route-interface cleanup.

### Audit-of-audit

- Coverage pass: C-01..C-14 all rechecked, including iOS source sets, generated host, migrations,
  capability view, JMH comparator, hooks, CI, and shared wire DTOs.
- Duplication/ownership pass: R33 extends completed R3 rather than creating a second enum strategy;
  R31 is distinct from R15 because it repairs recipient capability ownership, not insertion counts.
- Materiality pass: retained only production notification absence, mandatory-gate false success,
  invalid finite wire state, and concurrent registration error classification.
- Schema pass: verified V5/V16 capability mismatch and V1 uniqueness; no new schema integrity issue.
- Priority pass: R31 first (production behavior absent), then R32, R33, R34. Only R31 is ticketed
  this session to preserve one active wayfinder ticket.

| Pass | Work | Result |
|---|---|---|
| 25 | Fresh bounded repository audit | C-01..C-14 complete; four fresh candidates and one stale lead recorded |
| 26 | Independent deterministic verification | R31-R34 verified; R28 retired; deferred/rejected leads retained |
| 27 | Adversarial and deletion-test pass | Capability scope, malformed JMH output, unknown enum values, and registration races falsified |
| 28 | Coverage, duplication, materiality, schema, priority | No omission or unresolved overlap; R31 selected as sole implementation child |

## Permanent-Map Refresh - Session 242

After implementation child #208, a focused read-only audit rechecked C-01..C-14 and every retained
candidate. Shared relief-access status remains the first actionable slice; registration uniqueness
remains an independent backend concurrency slice. No product source, tests, migrations, or behavior
changed during this audit.

### Coverage and dispositions

| Candidate | Evidence | Exploration | Falsification / verification | Disposition |
|---|---|---|---|---|
| R33 - type relief-access status in shared DTO | current | current | finite backend/database values and untyped shared field still verified | implement, P1 |
| R34 - make registration uniqueness race explicit | current | current | pre-check and unique database constraints still separated; loser can escape as 500 | implement, P1 |
| R35 - preserve CI retry status capture | current | current | separate workflow orchestration concern; no ticket until its exact retry contract is isolated | defer |

### R33 verification

- `AttendanceDto.ReliefRequestResponse.requestStatus` remains `String` at
  `shared/src/commonMain/kotlin/com/companyb/companyapp/dto/AttendanceDto.kt:57-64`.
- `ReliefStatus` remains the finite persistence/domain enum at
  `backend/src/main/kotlin/com/companyb/companyapp/repository/model/ReliefStatus.kt:3`, with
  `PENDING`, `GRANTED`, and `DENIED`; the V1 `relief_status` database enum is its backstop.
- `ReliefAccessRoutes` serializes `.name` at lines 57, 81, and 115, so current uppercase wire
  values can be preserved exactly by a shared serializable enum. `ReliefInvite` has a distinct
  status enum and must not be reused.
- Falsification found no competing shared status type or unknown-value policy that invalidates the
  completed finite-wire typing pattern. Existing malformed-value behavior must remain explicit at
  the shared/backend boundary.
- Deletion test passes: replacing the shared `String` with one shared finite value removes the
  invalid wire state without adding a new module or adapter seam.

### R34 verification

- `AuthService.register` still prechecks username and email before `UserRepository.createUser`;
  V1 still owns unique username/email constraints. Concurrent losers therefore remain capable of
  escaping repository code as an unclassified database error.
- The candidate is not a duplicate of R33: it owns atomic backend write conflict translation,
  while R33 owns shared finite wire representation.
- Deletion test passes: repository-owned atomic conflict handling can remove race-prone caller
  coordination without changing the database constraints or registration interface.

### Audit-of-audit

- Coverage: C-01..C-14 rechecked, including shared DTOs, backend route mapping, persistence enums,
  auth writes, migrations, Compose consumers, tests, hooks, CI, and architecture documentation.
- Duplication: R33 extends completed finite-wire typing; R34 remains a separate database-backed
  uniqueness concern; R35 remains workflow fog rather than an implementation child.
- Materiality: R33 and R34 both remove concrete invalid states; R35 is deferred until retry status
  ownership is narrowly evidenced.
- Priority: R33 first because it is a contained cross-module contract correction with no schema or
  concurrency migration; R34 follows as the higher-risk backend transaction slice.

| Pass | Work | Result |
|---|---|---|
| 29 | Focused bounded repository audit | C-01..C-14 complete; R33-R35 rechecked |
| 30 | Independent deterministic verification | R33/R34 remain verified; R35 remains deferred |
| 31 | Adversarial and deletion-test pass | Unknown wire values, distinct invite status, and registration races checked |
| 32 | Coverage, duplication, materiality, priority | R33 selected as sole next implementation child |

## Permanent-Map Refresh - Session 241

After implementation child #207, four independent read-only lanes re-audited C-01..C-14 and
retained R32-R34. R28 remains retired. R32 is selected as the sole implementation child; R33
and R34 remain evidenced, independent P1 candidates for later sessions. A new workflow retry
lead (R35) was recorded as deferred fog because it is separate orchestration scope.

### Audit checkpoint

- R32: `scripts/check-baselines.sh` accepted empty, truncated, and format-changed output as
  success. The parser also rejected fully qualified benchmark names and its noisy benchmark
  wildcard required explicit verification. Implemented fail-closed table/row checks, numeric
  score validation, required baseline coverage, and focused fixtures.
- R33: shared relief-access status remains an untyped `String`; backend and database values are
  finite. Retain for a later shared-contract child.
- R34: registration prechecks still race database username/email uniqueness. Retain for a later
  backend child.
- R35: CI retry status capture may terminate before recording failed comparator status under
  shell `-e`; deferred as separate workflow-orchestration candidate.

### Audit-of-audit

- Coverage: C-01..C-14 complete, including platform bridges, generated contracts, schema,
  benchmarks, hooks, CI, shared DTOs, and architecture docs.
- Duplication: R33 extends completed finite-wire typing; R32 is distinct from R35 and R15.
- Falsification: valid complete JMH output still compares; new benchmark rows remain allowed;
  missing baseline rows, malformed scores, and empty/truncated output fail.
- Priority: R32 first, then R33 and R34. No other implementation child opened.

## Permanent-Map Refresh - Session 243

After implementation child #209, four bounded read-only lanes rechecked C-01..C-14 and
retained candidates. R34 remained the only actionable P1 candidate and was implemented as
child #211. R35 was falsified as a correctness issue and remains workflow fog. Product
behavior was changed only through #211; the audit itself remained read-only.

### Coverage and dispositions

| Candidate | Evidence | Exploration | Falsification / verification | Disposition |
|---|---|---|---|---|
| R34 - make registration uniqueness race explicit | current | current | repository precheck/insert race verified; implementation child #211 resolved it | implemented, P1 |
| R35 - preserve CI retry status capture | current | current | retry failure exits non-zero and uploads logs; no false-success defect | defer as workflow fog |
| R36 - delete unused UserCapability data class | current | current | repository-wide Kotlin search finds declaration only; table representation remains used | defer, P2 |

### R34 implementation checkpoint

- `AuthService.register` still owns validation and existing result semantics.
- `UserRepository.createUser` now owns atomic uniqueness handling through `insertIgnore`,
  reads the conflicting username/email inside the same transaction, and throws a typed
  `RegistrationConflictException`.
- The successful insert path retains audit callback execution inside the transaction. The
  conflict path throws before callback invocation, so no partial user or audit callback effect
  can commit.
- Sequential, concurrent same-username, concurrent same-email, and audit-callback tests pass.
- The `UserCreateParams` parameter object preserves the repository convention for four or more
  non-PK creation parameters.

### R35 falsification

The JMH workflow explicitly captures retry comparator status and exits non-zero on retry
failure. Failed runs retain logs for upload. Missing diagnostic output is not a correctness
breach, so no implementation ticket is justified.

### R36 dossier

`backend/src/main/kotlin/com/companyb/companyapp/repository/model/UserCapability.kt` declares
`UserCapability`, but repository-wide search finds no constructor or type consumer. The
`UserCapabilityTable` and its enums remain active persistence representations. Deleting only
the dead data class passes the deletion test, but it is lower priority than concurrency and
was not ticketed under the one-child cadence.

### Audit-of-audit

- Coverage: C-01..C-14 rechecked across Compose/platform bridges, shared contracts,
  backend/auth/persistence/schema, tests/tooling, CI, and docs.
- Duplication: R34 is distinct from R33 wire typing and prior compensation/remittance races;
  R35 is workflow diagnostics; R36 is dead persistence representation.
- Materiality: R34 was the only P1 candidate. R35 was rejected as a correctness candidate;
  R36 was retained as P2 deferred work.
- Priority: complete R34 first; revisit R36 when no higher-risk candidate is available.

| Pass | Work | Result |
|---|---|---|
| 33 | Four bounded coverage lanes | C-01..C-14 complete; R34-R36 reviewed |
| 34 | Independent deterministic verification | R34 verified and implemented; R35 falsified; R36 verified |
| 35 | Adversarial and deletion-test pass | Registration races, retry failure status, and dead model references checked |
| 36 | Coverage, duplication, materiality, priority | No missing subsystem; one child resolved; R36 deferred |

## Permanent-Map Refresh - Session 244

After registration uniqueness child #211, four bounded read-only lanes rechecked C-01..C-14,
retained candidates, and current implementation state. The highest-risk actionable finding is a
relief access state-transition race; it is advanced as the sole next implementation child.

### Coverage and dispositions

| Candidate | Evidence | Falsification / verification | Disposition |
|---|---|---|---|
| Relief grant/deny transition race | `ReliefAccessService` pre-reads status; repository updates lack `PENDING` predicates | Repository locking does not prevent unconditional opposite transitions; sequential tests do not cover concurrent grant/deny | implement, P1 |
| R15 - notification inserted-count truth | Batch insert returns candidate count; concurrent schedulers can conflict; precheck pairs independent session/user sets | Unique constraint preserves rows but reported count and pair filtering remain false | retain, P1/P2 |
| R36 - delete unused `UserCapability` data class | Exact symbol search finds declaration only; `UserCapabilityTable` remains active | Deleting only data class leaves table, enums, grants, and view unchanged | defer, P2 |
| k6 concurrency script integrity | Script imports missing `thresholds` and ignores setup failures/fallback IDs | Current helper exports `thresholdProfiles`; script cannot provide reliable concurrency evidence | retain, P1/P2 |
| R35 - preserve CI retry status capture | Existing workflow captures retry status and exits non-zero | No false-success defect established | defer as workflow fog |

### Relief transition race dossier

`ReliefAccessService.grantAccess` and `denyAccess` read request status before invoking repository
mutations (`backend/src/main/kotlin/com/companyb/companyapp/service/ReliefAccessService.kt:26-38,88-105`).
The repository locks rows but its grant and deny updates are unconditional
(`backend/src/main/kotlin/com/companyb/companyapp/repository/ReliefAccessRepository.kt:119-153,168-191`).
Concurrent calls can therefore grant after deny, or leave `DENIED` while a capability was inserted.
The smallest credible slice is repository-owned compare-and-transition from `PENDING`, with
capability insertion and successful grant transition in one transaction, plus concurrent and
repeated-attempt tests. Preserve existing capability checks, audit callbacks, and response states.

Competing representation is a service mutex or application-wide lock. The deletion test rejects
that shape: it adds process-local state and cannot protect multiple backend instances, while the
database transaction already owns the authoritative row. Repository predicates concentrate the
state invariant at the persistence seam.

### Audit-of-audit

- C-01..C-14 remain complete across Compose/platform bridges, backend/auth/persistence, shared
  contracts, schema, tests/tooling, CI, and docs.
- No Compose candidate survived: platform divergence is intentional and existing state modules
  already own repeated lifecycle logic.
- Relief transition race is distinct from R15 batch-count truth, R36 dead model cleanup, and
  completed relief status wire typing.
- Priority is relief transition race first, k6 concurrency integrity second, R15 third, R36 fourth.

| Pass | Work | Result |
|---|---|---|
| 37 | Four bounded coverage lanes | C-01..C-14 complete; relief race, R15, R36, and k6 candidates reviewed |
| 38 | Independent deterministic verification | Relief predicates absent; R15 count/pair defect and R36 dead declaration confirmed |
| 39 | Adversarial and deletion-test pass | Grant/deny race survives; database ownership beats process lock; no candidate overlap |
| 40 | Coverage, duplication, materiality, priority | Relief race is sole next P1 child; lower candidates retained/deferred |

## Permanent-Map Refresh - Session 248

After scheduler notification capability scoping (#215), four bounded read-only lanes
rechecked C-01..C-14 and directly revalidated the retained R36 dead model candidate.
The scheduler/capability seam is now coherent: recipient selection correlates the
branch-scoped capability context with the same active assignment branch, V21 derives
the capability only for active Coordinator assignments, and lifecycle ownership remains
explicit in `SchedulerLifecycle`.

### Coverage and dispositions

| Candidate | Evidence | Falsification / verification | Disposition |
|---|---|---|---|
| R36 - delete unused `UserCapability` data class | `UserCapability.kt` declaration had no constructor or type consumers; table and enums remain active | symbol search is empty after deletion; backend compile, scheduler tests, detekt, ktlint, and full backend tests pass | implemented, P2 |
| C-05 - use shared capability enums in backend persistence models | `shared/.../domain/WireEnums.kt` and backend `UserCapability.kt` define duplicate `CapabilityContextType`/`CapabilitySourceType`; multiple backend consumers import the backend copies | wire names and PostgreSQL enum values match; migration ordering and active table/view usage remain compatible | retain, P1 candidate; no child under one-child cadence |
| C-13 - centralize test-database cleanup policy | `check-test-cleanliness.sh` and `clean-test-db.sh` duplicate seed-table and discovery predicates | shared `scripts/lib/common.sh` seam exists; current behavior is intentional but drift-prone | retain, P2 candidate; no child under one-child cadence |

### R36 implementation checkpoint

`UserCapability` was removed from `backend/src/main/kotlin/com/companyb/companyapp/repository/model/UserCapability.kt`.
`UserCapabilityTable`, `CapabilityContextType`, and `CapabilitySourceType` remain unchanged,
so direct grants, role-derived view rows, scheduler capability derivation, and authorization
behavior retain their existing ownership.

### Audit-of-audit

- Coverage: C-01..C-14 complete across Compose/platform bridges, shared contracts, backend/auth/persistence,
  schema, tests/tooling, CI, and docs.
- Duplication: shared capability enums are distinct from dead data-class deletion; cleanup-policy duplication
  is distinct from completed test-server lifecycle work.
- Materiality: mobile navigation and SessionList duplication are real P2 candidates but require a separate
  Compose implementation slice; no scheduler defect remains after #215.
- Priority: shared capability enum ownership is next P1 candidate; cleanup policy and Compose duplication remain
  lower-priority candidates. No second child was created this session.

| Pass | Work | Result |
|---|---|---|
| 41 | Four bounded coverage lanes plus direct R36/scheduler verification | C-01..C-14 complete; R36 and adjacent ownership rechecked |
| 42 | Independent deterministic verification | R36 deletion confirmed; shared enum and cleanup candidates retained |
| 43 | Adversarial and deletion-test pass | table/view/grant behavior remains active; scheduler branch pairing and role derivation verified |
| 44 | Coverage, duplication, materiality, priority | R36 implemented as sole child; no scheduler child; future candidates preserved |
