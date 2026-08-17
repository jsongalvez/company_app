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
