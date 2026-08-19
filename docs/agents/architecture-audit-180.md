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

## Focused Empty-Frontier Audit - Session 297

After implementation child #239, the native Map #180 frontier is empty. A focused read-only
audit rechecked the remaining Compose lifecycle fog, deferred notification-count condition, and
the k6 threshold ownership just implemented.

- **R24 / Branch Select lifecycle:** #238 removed nested `AttendanceViewModel` ownership and
  preserved standalone Drawer ownership. The remaining `remember`-owned parent lifecycle choice
  is a broader Compose lifecycle decision, not a safe mechanical follow-up; no new candidate was
  ticketed.
- **R23 / paired selected-branch state:** remains deferred because migration would cross the
  ADR-0021 capability-refresh seam without a newly evidenced ownership boundary.
- **R15 / notification inserted-count truth:** remains fog pending deployment topology or an
  overlapping scheduler invocation requirement, as previously recorded.
- **Authz k6 thresholds:** all workflow consumers now use named helper profiles; deterministic
  `k6 inspect` confirms `authz-test.js` resolves the shared authz profile. No residual duplicate
  threshold definition remains.

### Clean-audit evidence

- Native child query: zero open `wayfinder:task` children; no open frontier.
- Open task query: zero Map #180 implementation tasks.
- Coverage: remaining R23/R24/R15 leads rechecked; no material new seam, invalid state, or
  actionable concurrency defect found.

## Full Audit - Session 298

The empty frontier triggered a fresh full read-only audit across C-01..C-14. Four bounded
lanes reviewed Compose/platform ownership, backend routes/services/persistence/auth, shared
contracts and tooling, and schema/tests/docs. Independent verification re-read every retained
finding against current source, requirements, ADRs, tests, and the lesson-class register.

### R59 - Session create idempotency must preserve request ownership

- **Verdict:** recommend; **disposition:** implement; **priority:** P0; **confidence:** high.
- **Evidence:** `backend/src/main/kotlin/com/companyb/companyapp/service/session/SessionService.kt:79-83`
  returns any existing UUID before validating the request's branch/day or caller. The route gate
  at `backend/src/main/kotlin/com/companyb/companyapp/api/routes/SessionRoutes.kt:181-203` authorizes
  the requested branch, not the already-existing session. Same-ID retry coverage exists at
  `backend/src/test/kotlin/com/companyb/companyapp/service/SessionServicePostgresTest.kt:117`.
- **Invalid state:** a caller with access to branch B who knows a session UUID from branch A can
  receive the foreign session through an idempotent create request. The UUID-only fallback is too
  narrow for the request ownership context.
- **Competing representations:** scope the idempotent lookup to request-owned branch/day and
  caller policy, or remove the early return and classify duplicate UUID collisions in the
  repository. The smallest safe shape preserves same-request retries while rejecting mismatched
  branch/caller context; no new generic interface.
- **Deletion test:** deleting the UUID-only early return removes the leak but loses idempotency;
  ownership-scoped lookup concentrates the collision policy without relocating session rules.
- **Scope:** SessionService/repository, route/service regression tests. Preserve capability gates,
  client-generated UUID retries, and no mutation/audit on rejected collisions.
- **Risks/validation:** define and test same-user same-branch retry, foreign branch, foreign caller,
  wrong day, duplicate UUID race, and audit invariants. Run session tests and backend gates.
- **Verifier packet:**
  `candidate: R59; mode: structured; model: GPT-5.6 Luna; position: ALPHA;`
  `L1 fact integrity: pass (source and test lines independently re-read);`
  `L2 domain coherence: pass (capability and Branch vocabulary preserved);`
  `L3 long-term architecture: pass (ownership stays at idempotent write seam);`
  `L4 adversarial falsification: pass for foreign branch/caller and repeated UUID;`
  `L5 comprehension: pass (request/context mismatch is explicit);`
  `deterministic gate: pass (UUID early return and same-ID test reproduced by source inspection);`
  `HARD findings: zero untriaged; SOFT findings: one, exact retry-owner policy must be encoded in tests;`
  `confidence: high; artifact: this section, Session 298 audit ledger.`

### R60 - Attendance clock-in idempotency must preserve caller and branch ownership

- **Verdict:** recommend; **disposition:** implement; **priority:** P0; **confidence:** high.
- **Evidence:** `backend/src/main/kotlin/com/companyb/companyapp/service/attendance/AttendanceService.kt:105-109`
  returns any existing attendance UUID before checking `existing.userId == callerId` or requested
  `branchId`. The clock-in route is JWT-only at `backend/src/main/kotlin/com/companyb/companyapp/api/routes/AttendanceRoutes.kt:57-65`.
  Same-ID coverage exists at `backend/src/test/kotlin/com/companyb/companyapp/service/AttendanceServicePostgresTest.kt:104-112`,
  while clock-out explicitly checks ownership at `AttendanceService.kt:50-57`.
- **Invalid state:** an authenticated caller who knows another attendance UUID can receive foreign
  attendance and branch-day state through a clock-in retry; requested branch is ignored.
- **Competing representations:** ownership-scoped idempotent lookup versus removing the shortcut and
  classifying duplicate UUID collisions in the repository. Keep clock-in open to authenticated users;
  add no capability gate.
- **Deletion test:** removing early return breaks legitimate retry semantics; an ownership predicate
  deepens the existing attendance write seam and preserves idempotency.
- **Scope:** AttendanceService/repository, attendance regression tests. Preserve same-user retries,
  one-active-clock-in enforcement, assignment/audit behavior, and commission side effects.
- **Risks/validation:** foreign caller, wrong branch, same-user retry, repeated attempts, and audit/
  assignment invariants. Run attendance tests and backend gates.
- **Verifier packet:**
  `candidate: R60; mode: structured; model: GPT-5.6 Luna; position: BETA;`
  `L1 fact integrity: pass (source, route, and tests independently re-read);`
  `L2 domain coherence: pass (attendance owner and Branch terms match requirements);`
  `L3 long-term architecture: pass (symmetry with existing clock-out ownership gate);`
  `L4 adversarial falsification: pass for foreign UUID, wrong branch, and repeated attempt;`
  `L5 comprehension: pass (caller/branch ownership failure is direct);`
  `deterministic gate: pass (unconditional UUID return verified);`
  `HARD findings: zero untriaged; SOFT findings: one, duplicate-collision policy needs explicit tests;`
  `confidence: high; artifact: this section, Session 298 audit ledger.`

### R61 - Share test-database discovery policy

- **Verdict:** recommend; **disposition:** implement; **priority:** P2; **confidence:** medium.
- **Evidence:** `scripts/check-test-cleanliness.sh:20-35,46-58` and `scripts/clean-test-db.sh:19-33`
  duplicate seed-table lists and PostgreSQL table discovery predicates, differing only in output
  delimiter and downstream operation.
- **Invalid state:** schema or seed-policy changes can update one gate script and leave the other
  with a divergent table set.
- **Competing representations:** retain duplication, or add one newline-delimited discovery helper
  in `scripts/lib/common.sh` while callers retain separate count/truncate operations. The helper
  must preserve fail-closed discovery and safe identifier handling.
- **Deletion test:** removing duplicated `SEED_TABLES`, `pg_tables`, and `information_schema`
  clauses leaves both scripts using one policy; assertion and mutation logic remain local.
- **Scope:** common shell helper and two scripts; no database abstraction or product behavior.
- **Risks/validation:** shell word splitting, identifier quoting, empty schema, unavailable DB,
  seed preservation, and cleanup verification. Run `bash -n`, deterministic fixtures, cleanliness,
  and disposable DB checks.
- **Verifier packet:**
  `candidate: R61; mode: structured; model: GPT-5.6 Luna; position: GAMMA;`
  `L1 fact integrity: pass (both scripts and shared shell seam re-read);`
  `L2 domain coherence: pass (test database remains disposable and production data untouched);`
  `L3 long-term architecture: pass (one discovery policy, local operation interfaces);`
  `L4 adversarial falsification: pass for empty DB, command failure, quoting, and seed tables;`
  `L5 comprehension: pass (policy helper versus operation responsibilities are clear);`
  `deterministic gate: pass (duplicated predicates and delimiters verified by source inspection);`
  `HARD findings: zero untriaged; SOFT findings: one, shell output contract requires focused tests;`
  `confidence: reduced; artifact: this section, Session 298 audit ledger.`

### R62 - Capability catalog must include later-seeded capability

- **Verdict:** recommend; **disposition:** implement; **priority:** P1; **confidence:** high.
- **Evidence:** `docs/architecture.md:283-295` says all capability codes are seeded in V2 and
  lists nine codes, omitting `RECEIVE_NEXT_APPOINTMENT_ALERTS`. The capability is inserted in
  `backend/src/main/resources/db/migration/V5__add_next_appointment_alerts_capability.sql:8-16`
  and owned in Kotlin by `shared/src/commonMain/kotlin/com/companyb/companyapp/domain/CapabilityCodes.kt:13`.
- **Invalid state:** agents consulting the architecture capability catalog can omit a live
  branch-scoped authorization path or search the wrong migration for its ownership.
- **Competing representations:** update the catalog to list the capability and distinguish V2 seed
  values from later additions, or delete the table and point agents to migrations/shared constants.
  Listing the live contract is more useful and smaller than removing the domain explanation.
- **Deletion test:** deleting the stale catalog removes the contradiction but also removes scope/role
  guidance; correcting the table concentrates the authoritative explanation in architecture docs.
- **Scope:** architecture documentation only. No ADR: correction applies existing ownership.
- **Risks/validation:** ensure scope and role assignment match V5/V21 and no capability is omitted.
  Compare table entries with shared constants and migration capability writes.
- **Verifier packet:**
  `candidate: R62; mode: structured; model: GPT-5.6 Luna; position: DELTA;`
  `L1 fact integrity: pass (architecture, shared constant, and V5 evidence match);`
  `L2 domain coherence: pass (capability-based authorization and Coordinator vocabulary preserved);`
  `L3 long-term architecture: pass (docs distinguish authoritative migrations from catalog guidance);`
  `L4 adversarial falsification: pass (checked V5 insertion and V21 derivation, no duplicate candidate);`
  `L5 comprehension: pass (catalog correction is plain and localized);`
  `deterministic gate: pass (grep/source comparison proves omitted live code);`
  `HARD findings: zero untriaged; SOFT findings: zero;`
  `confidence: high; artifact: this section, Session 298 audit ledger.`

### Session 298 synthesis and audit-of-audit

- Coverage pass: C-01..C-14 rechecked; Compose lifecycle and ADR-0021 leads remain deferred,
  shared enum/k6/OpenAPI ownership is complete, and C-09/C-11 produced no non-duplicate candidate.
- Duplication pass: R59/R60 are separate ownership seams despite shared UUID-idempotency class;
  R61 is tooling-only; R62 is residual truth-class drift, not stale migration-pointer R4.
- Materiality pass: R59/R60 are P0 data-disclosure defects; R61 is P2 drift reduction; R62 is
  P1 agent-facing truth correction. No style-only or speculative abstraction finding retained.
- Schema/dependency pass: no migration change is required for R59/R60/R61/R62; R59 and R60 can
  proceed independently, R61 depends only on shell gate fixtures, and R62 is docs-only.
- Priority: R59 first, then R60, then R62, then R61. All four are dispositioned `implement` and
  require native child creation before any frontier claim.

### Session 298 child traceability

- `scripts/wayfinder-create-child.sh 180 task "Build: preserve session-create idempotency ownership" docs/agents/wayfinder-298-r59.md`
  -> https://github.com/jsongalvez/company_app/issues/241; `scripts/wayfinder-verify-child.sh 180 241`
  -> `Verified child #241: parent #180, label wayfinder:task`.
- `scripts/wayfinder-create-child.sh 180 task "Build: preserve attendance clock-in idempotency ownership" docs/agents/wayfinder-298-r60.md`
  -> https://github.com/jsongalvez/company_app/issues/242; `scripts/wayfinder-verify-child.sh 180 242`
  -> `Verified child #242: parent #180, label wayfinder:task`.
- `scripts/wayfinder-create-child.sh 180 task "Build: share test-database discovery policy" docs/agents/wayfinder-298-r61.md`
  -> https://github.com/jsongalvez/company_app/issues/243; `scripts/wayfinder-verify-child.sh 180 243`
  -> `Verified child #243: parent #180, label wayfinder:task`.
- `scripts/wayfinder-create-child.sh 180 task "Docs: correct capability catalog ownership" docs/agents/wayfinder-298-r62.md`
  -> https://github.com/jsongalvez/company_app/issues/244; `scripts/wayfinder-verify-child.sh 180 244`
  -> `Verified child #244: parent #180, label wayfinder:task`.

### R59 implementation evidence

Child #241 is the claimed frontier child. Session-create idempotency now validates client,
Branch Day, and creator ownership both in the service fast path and inside the repository
transaction. The repository rechecks UUID existence after client-row locking, so concurrent
same-UUID retries return the original session instead of tripping the active-PENDING guard.
Foreign Branch, client, caller, and Branch Day retries are covered; audit count remains one.

- Gates: `docs/gates/241-session-idempotency-ownership.md`, 3/3 PASS.
- Targeted and full `SessionServicePostgresTest`: PASS.
- Full backend gate `./gradlew :backend:detekt :backend:ktlintCheck :backend:test`: PASS.
- Disposition: clean audit; no child created and no ticket claimed.

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

## Permanent-Map Refresh - Session 249

After implementation child #216, the frontier was empty again. Four bounded read-only
lanes rechecked C-01..C-14, with focused attention on shared capability enum ownership,
test-database cleanup policy, and mobile navigation duplication. Product source, tests,
migrations, and behavior were unchanged during the audit.

### Coverage and dispositions

| Candidate | Evidence | Falsification / verification | Disposition |
|---|---|---|---|
| C-05 - use shared capability enums in backend persistence models | Shared and backend enums have identical values; backend consumers span persistence, authorization, scheduler, grants, seeders, and tests; `CapabilityRepository` converts by name | PostgreSQL custom-enumeration bindings accept shared enums; migrations and wire names match; no second persistence representation is required | implement, P1; child #217 |
| C-13 - centralize test-database cleanup policy | `check-test-cleanliness.sh` and `clean-test-db.sh` repeat discovery and seed exclusions | Policy is currently intentional and correct; no false-success fallback remains; extraction is useful but lower-risk P2 | defer |
| C-01/C-04 - consolidate Android/iOS mobile navigation and session list | Mobile actuals duplicate routes and card rendering | Route order, VM ownership, state reads, navigation, and card behavior match; ADR-0020 permits platform split and no lifecycle defect exists | defer, P2 |

### C-05 dossier - use shared capability enums in backend persistence models

- **Verdict:** recommend; **disposition:** implement; **priority:** P1; **confidence:** high.
- **Evidence:** serializable shared enums live at `shared/src/commonMain/kotlin/com/companyb/companyapp/domain/WireEnums.kt:40-43`; duplicate backend enums were at `backend/src/main/kotlin/com/companyb/companyapp/repository/model/UserCapability.kt:9-11`; backend consumers imported the repository copies across middleware, services, repositories, seeders, and tests. `CapabilityRepository.kt:62-73` converted backend values to shared values using `valueOf(name)`.
- **Current invalid state:** one PostgreSQL capability enum has two Kotlin representations. A new value can be added to one side and fail at the conversion seam; backend interfaces also reject the shared type despite identical domain meaning.
- **Competing representation:** retain backend persistence enums and continue name mapping. This preserves a separate storage type but fails the shared-module rule and keeps drift/conversion failure possible. A database-specific wrapper is unnecessary because `customEnumeration` only requires a Kotlin enum and `PGobject` binding.
- **Simpler representation:** use shared `CapabilityContextType` and `CapabilitySourceType` directly in `UserCapabilityTable`, `ActiveUserCapabilitiesView`, backend authorization/grant code, seeders, and tests. Remove name conversions. Keep PostgreSQL bindings and migrations unchanged.
- **Smallest credible scope:** imports and enum declarations in backend production/test Kotlin; rename the table file after deleting its dead data-class declaration; direct DTO mapping in `CapabilityRepository`. No schema or HTTP change.
- **Risks and validation:** same-package resolution, import ordering, generic `customEnumeration` inference, and accidental enum-value changes. Validate shared/backend compilation, capability grant-path and scheduler tests, backend quality, full tests, and repository-wide absence of backend duplicate declarations.
- **Dependencies:** completed finite-wire enum work and existing ADR-0023 capability-view ownership. **Deletion test:** deleting backend enum declarations leaves PostgreSQL table/view bindings, authorization, direct grants, role-derived rows, and tests valid after imports move to shared; no new adapter seam appears.

### Deferred and rejected leads

- Cleanup-policy centralization remains a P2 candidate. The two scripts intentionally serve separate check and cleanup operations, and current discovery failure handling is fail-closed after #203; defer until shell-test or database-tooling work makes a shared helper earn its seam.
- Android/iOS `AppNavHost` and `SessionList` duplication remains P2 maintenance work. Common `App`/ViewModel/state ownership and platform-specific desktop behavior make a broad consolidation unnecessary now; no candidate survives the deletion test as a material defect.

### Audit-of-audit

- Coverage: C-01..C-14 rechecked across Compose/platform bridges, shared contracts, backend/auth/persistence, schema, tests/tooling, CI, and docs.
- Duplication: shared capability enums are distinct from the deleted `UserCapability` data class; cleanup scripts remain separate from the capability contract; mobile duplication does not overlap the retired iOS bridge candidate.
- Materiality: shared enum ownership is a concrete cross-module drift seam; cleanup and mobile leads are lower-priority maintenance without current invalid behavior.
- Schema: V1/V15 PostgreSQL enum values match shared values; no migration or database representation change is justified.
- Priority: #217 is sole implementation child. No second child was created.

| Pass | Work | Result |
|---|---|---|
| 45 | Four bounded coverage lanes | C-01..C-14 complete; shared enum, cleanup, and mobile leads reviewed |
| 46 | Independent deterministic verification | Shared enum duplication and direct conversion confirmed; cleanup fallback and mobile state defects falsified |
| 47 | Adversarial and deletion-test pass | Enum drift, PostgreSQL binding, script outage, platform lifecycle, and ADR overlap checked |
| 48 | Coverage, duplication, materiality, schema, priority | #217 selected as sole P1 child; lower candidates retained/deferred |

## Permanent-Map Refresh - Session 250

After implementation child #217, the frontier was empty again. Four bounded read-only lanes
rechecked C-01..C-14, with additional test-infrastructure review. Product behavior outside the
selected child remained unchanged.

### Coverage and dispositions

| Candidate | Evidence | Falsification / verification | Disposition |
|---|---|---|---|
| Compose capability context ownership | `SessionState.kt` duplicated all five shared capability context values and compared `contextType.name` to strings; production consumers imported the duplicate object | Shared `CapabilityContextType` already owns identical values; enum comparisons preserve null fail-closed matching and all Compose tests/builds pass | implemented, P1/P2; child #218 |
| Android/iOS mobile host duplication | `AppNavHost.android.kt` and `.ios.kt` are byte-identical; `SessionList.android.kt` and `.ios.kt` are byte-identical | ADR-0020 requires compile-time mobile/desktop split, but permits shared mobile implementations; extraction is valid P2 maintenance, not current domain defect | defer, P2 |
| Test-database cleanup policy | `check-test-cleanliness.sh` and `clean-test-db.sh` duplicate seed/discovery policy | Both scripts have distinct assert/mutate interfaces and fail closed after #203; full helper extraction would couple consumers without current behavior defect | defer, P2 |
| Route-test setup helper | 13 route suites repeat app middleware and exception setup | Candidate is plausible medium-confidence test-only deepening, but requires preserving suite-specific auth/handler differences; no child under one-ticket cadence | retain, P2 |
| Scheduler capability code ownership | Scheduler hardcodes `RECEIVE_NEXT_APPOINTMENT_ALERTS` while shared capability codes own other values | Migration must retain SQL literal; narrow shared constant is independent of #218 and remains a later P1/P2 candidate | retain, P1/P2 |

### Child #218 implementation checkpoint

`CapabilityContext` was removed from Compose `SessionState`. Matcher functions now accept and
compare shared `CapabilityContextType`; Finance and Session Dashboard callers and matcher fixtures
were migrated. ADR-0021's two-slice fetch timing and null-context fail-closed behavior are unchanged.

### Audit-of-audit

- Coverage: C-01..C-14 rechecked across Compose/platform bridges, shared contracts, backend/auth/persistence,
  schema, tests/tooling, and docs.
- Duplication: mobile host/list duplication is separate from capability contract ownership; cleanup
  scripts retain distinct interfaces; route-test setup is separate from production lifecycle.
- Materiality: shared Compose enum ownership removes an active cross-module invalid-state seam;
  mobile and cleanup candidates remain lower-risk maintenance; scheduler code ownership remains retained.
- Platform validation: Android and desktop compilation passed. iOS compile was attempted but Gradle
  could not resolve external `kotlin-native-prebuilt:2.3.10` for linux-aarch64 before source compilation.

| Pass | Work | Result |
|---|---|---|
| 49 | Four bounded coverage lanes plus test-infrastructure lane | C-01..C-14 complete; five candidate classes reviewed |
| 50 | Independent deterministic verification | Shared Compose enum duplication confirmed; mobile and cleanup behavior verified; route-test and scheduler leads retained |
| 51 | Adversarial and deletion-test pass | Null fail-closed matching, ADR-0020 platform split, cleanup failure policy, auth setup variance, and migration wire ownership checked |
| 52 | Coverage, duplication, materiality, priority | #218 selected and resolved; lower candidates retained/deferred without extra child |

## Permanent-Map Refresh - Session 251

After implementation child #218, a focused C-01..C-14 audit rechecked the retained mobile,
scheduler, cleanup, and route-test candidates. Product behavior outside the selected child was
unchanged during the audit.

### Coverage and dispositions

| Candidate | Evidence | Falsification / verification | Disposition |
|---|---|---|---|
| Android/iOS mobile host and session-list duplication | `AppNavHost.android.kt`/`.ios.kt` are byte-identical (365 lines); `SessionList.android.kt`/`.ios.kt` are byte-identical (116 lines) | Common implementation uses only platform-independent APIs; desktop remains distinct under ADR-0020; route, ViewModel, state, and card behavior match | implement, P2; child #219 |
| Scheduler capability code ownership | Scheduler and test use a private Kotlin string while shared `CapabilityCodes` owns other capability codes; migrations necessarily retain SQL literals | Runtime branch pairing and role-derived provisioning are correct after #207/#215; only Kotlin ownership is duplicated | retain, P1/P2 |
| Route-test setup helper | 15 suites repeat stable app/JWT/password/database setup, but route registration, exception maps, and auth modes vary | Broad configurable factory would relocate complexity; narrow composable helpers remain plausible test-only maintenance | defer, P2 |
| Test-database cleanup policy | Check and cleanup scripts repeat discovery predicates | Interfaces intentionally differ (assert versus mutate) and both fail closed after #203 | defer, P2 |

### Mobile host/list dossier

- **Verdict:** recommend; **disposition:** implement; **priority:** P2; **confidence:** high.
- **Evidence:** Android/iOS `AppNavHost` files and `SessionList` files were byte-identical. Common
  `App.kt`, ViewModels, and `SessionDashboardScreen` already own shared setup/state; desktop host
  and table remain materially different. ADR-0020's compile-time mobile/desktop split permits a
  shared mobile implementation behind thin target adapters.
- **Current invalid state:** no runtime defect; duplicated mobile ownership can drift when a future
  Android change is not mirrored in iOS.
- **Competing representation:** retain two full actuals, or use a configurable platform registry.
  The registry is rejected as speculative; common implementation plus thin actual delegates keeps
  the existing expect/actual seam and concentrates mobile behavior.
- **Smallest credible scope:** move the two identical bodies into `commonMain` and leave Android,
  iOS, and desktop actuals as required. Preserve route order, ViewModel scoping, SessionState reads,
  notification navigation, and `SessionListArgs` behavior.
- **Risks and validation:** common-source API availability, internal visibility, and lifecycle drift.
  Structural gates, `:composeApp:ktlintCheck :composeApp:compileKotlinDesktop :composeApp:desktopTest`,
  and Android compilation passed. iOS compilation remains externally blocked before source
  compilation by unavailable `kotlin-native-prebuilt:2.3.10` for linux-aarch64.
- **Dependencies:** ADR-0020 and existing common `SessionList` contract. **Deletion test:** deleting
  either target body leaves one mobile owner and unchanged desktop behavior.

### Audit-of-audit

- Coverage: C-01..C-14 rechecked with separate lanes for mobile bridges, scheduler capability
  ownership, route-test setup, and cleanup scripts.
- Duplication: mobile extraction is distinct from shared capability enum ownership, scheduler runtime
  correctness, cleanup-policy interfaces, and test-server lifecycle ownership.
- Materiality: exact 481-line mobile duplication is actionable maintenance leverage; scheduler and
  route-test candidates remain retained/deferred without current runtime defects.
- Priority: #219 is the sole implementation child; scheduler ownership remains next higher-priority
  candidate after this P2 extraction only if no stronger behavior candidate appears.

| Pass | Work | Result |
|---|---|---|
| 53 | Focused C-01..C-14 lanes | Mobile, scheduler, route-test, and cleanup candidates rechecked |
| 54 | Independent deterministic verification | Byte identity, platform API availability, and ownership variance confirmed |
| 55 | Adversarial and deletion-test pass | ADR split, lifecycle, auth setup variance, migration literals, and failure policy checked |
| 56 | Coverage, duplication, materiality, priority | #219 selected as sole implementation child |

## Permanent-Map Refresh - Session 252

After implementation child #219, a focused C-01..C-14 audit rechecked retained scheduler
capability-code ownership, route-test setup, and test-database cleanup candidates. Product
behavior outside the selected child was unchanged during the audit.

### Coverage and dispositions

| Candidate | Evidence | Falsification / verification | Disposition |
|---|---|---|---|
| Scheduler capability code ownership | `NextAppointmentScheduler.kt:36,135` owns a private runtime string while shared `CapabilityCodes` owns every other runtime capability; V5/V21 migrations necessarily retain SQL literals | Branch pairing, role-derived provisioning, inactive-user filtering, and multi-branch authorization are covered by #207/#215; only Kotlin ownership remains duplicated | implement, P1/P2; child #220 |
| Route-test setup helper | 15 suites repeat stable database/JWT setup, but route registration, exception maps, and auth modes vary | A configurable factory would relocate suite-specific complexity; no narrow helper with a second concrete adapter earns a seam | defer, P2 |
| Test-database cleanup policy | `check-test-cleanliness.sh` and `clean-test-db.sh` repeat discovery predicates | Check and mutation have distinct interfaces; both fail closed after #203 and no fresh failure or drift exists | defer, P2 |

### Scheduler capability ownership dossier

- **Verdict:** recommend; **disposition:** implement; **priority:** P1/P2; **confidence:** high.
- **Evidence:** `shared/src/commonMain/kotlin/com/companyb/companyapp/domain/CapabilityCodes.kt:3-13`
  owns all current runtime capability codes except `RECEIVE_NEXT_APPOINTMENT_ALERTS`,
  which is privately declared in `NextAppointmentScheduler.kt:36` and repeated in its test at
  `NextAppointmentSchedulerPostgresTest.kt:386`. Migrations retain SQL literals because they
  are database-owned text, not Kotlin consumers.
- **Current invalid state:** shared capability-code ownership is false-complete; the scheduler
  can drift from shared consumers when the code changes.
- **Competing representation:** keep the private scheduler constant, or add a generic capability
  registry. The former preserves the drift seam; the latter adds speculative structure. A shared
  constant is the smallest representation.
- **Smallest credible scope:** add one shared constant and migrate the scheduler and focused test;
  leave migration SQL unchanged. No route, view, grant, or scheduler behavior changes.
- **Risks and validation:** preserve exact uppercase wire/database value and scheduler recipient
  selection. Existing scheduler integration coverage plus the focused test and full backend gates
  verify behavior; repository search verifies Kotlin ownership and migration SQL remains unchanged.
- **Dependencies:** existing shared capability ownership and completed scheduler provisioning.
  **Deletion test:** deleting the private constant leaves all Kotlin callers on the shared owner;
  no adapter or registry is introduced.

### Audit-of-audit

- **Coverage:** C-01..C-14 rechecked with independent lanes for scheduler ownership, route-test
  setup, cleanup policy, and remaining platform/shared/backend/tooling areas.
- **Duplication:** the Kotlin capability constant is distinct from required migration SQL and
  completed shared enum ownership; route-test and cleanup candidates remain separate.
- **Materiality:** scheduler ownership is a concrete cross-module drift seam; route-test and
  cleanup extraction remain maintenance work without a current invalid state.
- **Schema:** no migration or database representation change is justified.
- **Priority:** scheduler ownership is the sole implementation child; no second child opened.

| Pass | Work | Result |
|---|---|---|
| 57 | Focused C-01..C-14 lanes | Scheduler, route-test, cleanup, and all ownership rows rechecked |
| 58 | Independent deterministic verification | One duplicated runtime capability owner confirmed; migration literals correctly retained |
| 59 | Adversarial and deletion-test pass | Shared constant, registry, test-helper, and cleanup alternatives falsified or narrowed |
| 60 | Coverage, duplication, materiality, schema, priority | Child #220 selected as sole implementation child |

## Permanent-Map Refresh - Session 253

After implementation child #220, the frontier was empty again. A full C-01..C-14 audit
rechecked Compose/platform bridges, shared contracts and schema, backend runtime and
authorization, and tests/tooling/documentation. Product source, tests, migrations, and
behavior remained unchanged during the audit.

### Coverage and dispositions

| Candidate | Evidence | Falsification / verification | Disposition |
|---|---|---|---|
| Scheduler capability-code ownership | Shared `CapabilityCodes` now owns every Kotlin runtime capability code, including scheduler alerts; migrations retain required SQL literals | Repository search finds no private Kotlin scheduler constant or focused-test magic string; scheduler behavior is covered by #207/#215/#220 | implemented |
| Mobile host/session-list duplication | Android/iOS bodies now share common implementations; desktop remains intentionally separate under ADR-0020 | Target-specific delegates contain no duplicated behavior; no lifecycle or route regression found | implemented |
| Route-test setup helper | Route suites repeat stable setup but vary registration, exception maps, and auth modes | A configurable factory relocates suite-specific complexity; no second concrete adapter or narrow deep seam is evidenced | defer, P2 |
| Test-database cleanup policy | Cleanliness and truncation scripts repeat discovery predicates | Assert and mutate interfaces differ; both fail closed after #203 and no fresh failure or drift is evidenced | defer, P2 |
| R15 notification inserted-count truth | Notification batch now returns database inserted count; deployment/overlap requirement remains unconfirmed | Existing uniqueness and focused tests cover current scheduler behavior; no new concurrency requirement surfaced | retain as fog |
| Historical role-assignment workflow fog | ADR-0023 records production role assignment as future workflow scope | No new requirement or implementation evidence sharpens the decision; guessing would exceed map scope | retain as fog |

### Audit-of-audit

- Coverage pass: C-01..C-14 all rechecked, including platform hosts, shared wire ownership,
  PostgreSQL migrations, authorization views, route tests, cleanup scripts, CI/hooks, and ADRs.
- Duplication pass: scheduler capability ownership and mobile duplication are retired; route-test
  setup and cleanup predicates remain distinct interfaces rather than one missing module.
- Materiality pass: no fresh runtime defect, invalid state, concurrency failure, or deletion-test
  win justifies a new child. Remaining candidates are maintenance or unresolved fog.
- Schema/priority pass: no migration change is justified; R15 remains dependent on deployment
  topology or overlapping invocation requirements.

| Pass | Work | Result |
|---|---|---|
| 61 | Full bounded C-01..C-14 lanes | All ownership areas rechecked; no omission |
| 62 | Independent evidence and falsification | Resolved candidates retired; route-test and cleanup seams narrowed; fog retained |
| 63 | Coverage, duplication, materiality, schema, priority | No justifiable new implementation child |

### Session 253 correction

The backend and tooling lanes completed after the initial no-candidate checkpoint and
surfaced actionable findings that supersede the table above:

- **R37 - enforce product-sale session branch ownership:** `ProductSaleService.sell` checked
  only session existence while accepting an independent `branchDayId`; independent foreign
  keys permit cross-branch sale, inventory, commission, and remittance contamination. A second
  adversarial pass also found existing sale UUID idempotency was not parent-scoped. Child #221
  owns both checks and was implemented in this session.
- **R38 - enforce remittance source ownership:** remittance lines independently reference
  source sessions/product sales without validating source branch against remittance branch.
  Retained as the next P0 candidate; no second child opened.
- **R39 - fail closed when pre-push k6 is unavailable:** the hook skips missing k6 or missing
  credentials despite the root contract describing k6 as a mandatory pre-push gate. Retained
  as P1 tooling candidate; explicit opt-out semantics need separate scope.
- The Compose lane's remaining identical mobile UI actuals are P2 maintenance, not a runtime
  defect; scheduler ownership, route-test setup, and cleanup policy remain resolved/deferred
  as previously recorded.

The initial no-candidate conclusion was corrected before remote push. The full audit's final
priority is R37 first, R38 second, R39 third. R15 and historical role-assignment workflow
remain fog without new deployment or business evidence.

## Permanent-Map Refresh - Session 254

Implementation child #222 resolved R38, the remittance source-ownership defect. The focused
backend change validates each SESSION or PRODUCT_SALE line source against the remittance branch
before `RemittanceLineRepository.addLine` can insert a line, increment remittance version, or
invoke the audit callback. The source branch remains derived from its immutable Branch Day.

### R38 implementation checkpoint

- `RemittanceService.addLine` now resolves the referenced session or product sale and its Branch
  Day, rejecting a missing or foreign source with `NotFoundException`.
- Same-branch sources, draft idempotency, duplicate-source conflicts, version checks, submission,
  and audit callbacks remain owned by existing modules.
- Regression coverage rejects both foreign SESSION and PRODUCT_SALE sources and proves remittance
  version, line collection, and audit rows remain unchanged.
- No schema or migration change was needed; existing source and Branch Day foreign keys remain
  authoritative.

### Validation

- Focused `RemittanceLineServicePostgresTest`: PASS, 30 tests.
- `:backend:detekt :backend:ktlintCheck :backend:test`: PASS, 8m15s.
- Test-database cleanliness check: PASS.
- `git diff --check`: PASS.

### Audit-of-audit

- Ownership pass: source branch validation is separate from remittance parent-child line identity
  and from day-breakdown branch validation.
- Adversarial pass: both source types reject before line insertion, version mutation, and audit;
  same-branch paths retain existing tests.
- Materiality pass: R38 removes a concrete financial cross-branch contamination path; no schema
  redesign or generic source abstraction is justified.
- Priority pass: R39 remains next retained P1 tooling candidate; R15 and historical role-assignment
  workflow remain fog without deployment or business evidence.

## Session 255 Decision Fog

R39 was rechecked against current hook and agent contracts. `.githooks/pre-push:46-72`
still skips missing `k6`, a missing baseline script, or missing `TEST_USERNAME`/
`TEST_PASSWORD`, while `AGENTS.md` and `backend/AGENTS.md` describe k6 baseline as a
mandatory pre-push gate. The k6 command itself already fails closed, cleans disposable
test database, and reports non-zero threshold failures.

Implementation boundary is not safe to ticket yet: converting implicit skips to hard
failures is clear, but an explicit local opt-out requires human-approved name,
authorization scope, and CI policy. Issue [Decision: define pre-push k6 opt-out policy]
(https://github.com/jsongalvez/company_app/issues/223) records verified facts and
smallest decision. No production or test code changed.

## Permanent-Map Refresh - Session 256

Implementation child #224 resolved the CI retry-evidence defect. The existing workflow
change was inspected from commit `0140bf5`, which is present on the tracked branch.

### R40 implementation checkpoint

- `.github/workflows/jmh.yml` now writes the complete retry benchmark output to
  `/tmp/company-app-jmh-retry.log` before `check-baselines.sh` reads it; the former
  `tee | tail -5` truncation is gone.
- `scripts/check-baselines-test.sh` covers empty, truncated, missing, malformed,
  complete, and regressed JMH output. The malformed and truncated cases fail closed,
  so they cannot be classified as a reproduced performance regression.
- No benchmark implementation or `backend/jmh-baselines.md` change was made. The
  baseline remains unproven until a complete retry establishes actual performance.

### Validation

- `bash scripts/check-baselines-test.sh`: PASS.
- `bash -n scripts/check-baselines.sh scripts/check-baselines-test.sh`: PASS.
- Workflow inspection confirmed complete retry-log preservation and comparator ordering.

### Audit-of-audit

- Ownership pass: workflow log preservation is separate from comparator parsing and
  from unresolved pre-push k6 policy in issue #223.
- Adversarial pass: malformed comparator input remains a failed evidence gate; a
  complete retry can now be evaluated instead of being reduced to five Gradle lines.
- Priority pass: #224 is closed; #223 remains human decision fog, with R15 and
  historical role-assignment workflow still deferred.

## Verifier packet enforcement - Session 285

The verifier contract is now an explicit gate rather than reference-only prose.
Every retained candidate must carry a packet recording verifier mode, GPT-5.6 Luna,
blind position, L1-L5 results, deterministic-gate evidence, HARD/SOFT triage,
confidence, and an artifact pointer. Feasibility remains optional evidence and
cannot replace L5 comprehension. Ticket creation is blocked by missing packet
fields, failed deterministic evidence, or untriaged HARD findings.

The map, issue-tracker workflow, audit method, decision loop, active handoff, and
unattended loop prompt now carry the same contract. This is a workflow control;
no product, schema, or runtime behavior changes.

## Permanent-Map Refresh - Session 285

The empty frontier triggered a fresh full audit. Four bounded read-only lanes
rechecked C-01..C-14 across Compose and platform bridges, backend services and
persistence, shared contracts and schema, and tests/tooling/docs. Independent
coverage, duplication, materiality, schema, and dependency-priority passes found
no subsystem omission.

### Retained candidates

#### R41 - Enforce inventory movement branch-day ownership

- **Verdict:** recommend; **disposition:** implement; **priority:** P0; **confidence:** high.
- **Evidence:** `backend/src/main/kotlin/com/companyb/companyapp/api/routes/BranchInventoryRoutes.kt:252-258,277-288` receives `branchId` from the URL and `branchDayId` from the body. `backend/src/main/kotlin/com/companyb/companyapp/service/inventory/InventoryService.kt:34-45` validates day editability without checking that the day belongs to the branch. `backend/src/main/resources/db/migration/V1__full_schema.sql:337-346` has independent foreign keys.
- **Current invalid state:** a Branch A inventory movement can reference Branch B's Branch Day, splitting stock ownership from day-state, audit, and reporting ownership.
- **Simpler representation:** resolve `branchDayId` through `BranchDayService.requireBranchDayForBranch(branchDayId, branchId)` before editability validation and mutation. Keep existing branch-scoped authorization.
- **Smallest scope:** `InventoryService` and focused service/API tests for tester, sample, missing, and adjustment movements. No schema change required.
- **Risks and validation:** preserve valid same-branch historical days; reject foreign days before card/version mutation, movement insertion, or audit. Test same-branch success, foreign-branch rejection, missing days, and all movement reasons.
- **Dependencies:** none. **Deletion test:** removing the branch-scoped resolver permits foreign Branch Days.
- **Verifier packet:** mode `structured`; model `GPT-5.6 Luna`; blind position `ALPHA`; L1 fact integrity `pass`; L2 domain coherence `pass with HARD ownership breach`; L3 long-term architecture `pass with HARD aggregate-boundary breach`; L4 adversarial falsification `pass, cross-branch write reproduced`; L5 comprehension `pass with SOFT missing invariant test`; deterministic gate `pass, current paths/schema confirm`; HARD findings `zero after proposed validation`; SOFT findings `one accepted, missing explicit invariant test, non-blocking`; confidence `high`; artifact `Session 285 audit lanes and verifier packet`.

#### R42 - Make active assignment creation conflict-safe

- **Verdict:** recommend; **disposition:** defer; **priority:** P1; **confidence:** high.
- **Evidence:** `backend/src/main/kotlin/com/companyb/companyapp/service/UserBranchAssignmentService.kt:68-71` prechecks the active assignment. `backend/src/main/kotlin/com/companyb/companyapp/repository/UserBranchAssignmentRepository.kt:29-45` uses `insertIgnore` but only reads the caller UUID when inserted. `backend/src/main/resources/db/migration/V1__full_schema.sql:102` enforces the active `(user_id, branch_id)` unique key. The losing concurrent request reaches `UserBranchAssignmentService.kt:93-95`, where its nonexistent UUID becomes a generic error.
- **Current invalid state:** concurrent assignment requests with different UUIDs can escape deterministic domain conflict handling despite the database business-key constraint.
- **Simpler representation:** repository-owned business-key conflict handling distinguishes same-ID idempotency from different-ID conflict and audits only newly inserted rows.
- **Smallest scope:** assignment repository/service and concurrency, retry, duplicate, and audit tests.
- **Risks and validation:** preserve current sequential duplicate behavior and same-ID retry semantics; verify no orphan or duplicate audit rows.
- **Dependencies:** R41 is independent; rank after R41. **Deletion test:** removing the service precheck while retaining repository conflict translation preserves deterministic behavior.
- **Verifier packet:** mode `structured`; model `GPT-5.6 Luna`; blind position `BETA`; L1 fact integrity `pass`; L2 domain coherence `pass with HARD deterministic-conflict breach`; L3 long-term architecture `pass with HARD ownership split`; L4 adversarial falsification `pass, distinct-UUID race reproduced`; L5 comprehension `pass`; deterministic gate `pass, current paths/schema confirm`; HARD findings `zero after proposed repository ownership`; SOFT findings `one accepted, existing sequential 400 versus conflict convention, deferred pending API compatibility review`; confidence `high`; artifact `Session 285 audit lanes and verifier packet`.

### Rejected lead

- **R43 - stale iOS exclusion rationale:** deterministic evidence confirms `.githooks/pre-push:61-63` names a missing `AppNavHost.ios.kt`, while the actual exists and iOS actuals are present. The exclusion itself remains supported by `docs/specs/0001-frontend-rebuild.md:171`, which keeps iOS screens future scope. The comment should be corrected opportunistically, but this is documentation hygiene, not a retained architecture candidate or child slice.

### Audit-of-audit - Session 285

- Coverage: C-01..C-14 rechecked; no omission.
- Duplication and ownership: R41 is distinct from prior remittance ownership fixes; R42 is distinct from resolved compensation and registration races.
- Materiality: R41 is a cross-branch operational data-integrity defect; R42 is a concurrent user-management failure. R43 is rejected as low-materiality stale rationale.
- Schema: independent foreign keys and active-assignment partial unique index verified directly in V1.
- Priority: R41 first, R42 second. R41 is the only `implement` disposition in
  this audit, so child #226 is the only child created; future audits create one
  native child for every candidate dispositioned `implement`, then claim one.

## Permanent-Map Refresh - Session 287

After implementation child #226, the frontier was empty. A focused read-only
audit rechecked retained R42 active-assignment conflict handling against the
current service, repository, schema, routes, and regression tests. No product
code, tests, migrations, or runtime behavior changed during this audit.

### R44 - Make active assignment creation conflict-safe

- **Verdict:** recommend; **disposition:** implement; **priority:** P1; **confidence:** high.
- **Evidence:** `backend/src/main/kotlin/com/companyb/companyapp/service/UserBranchAssignmentService.kt:68-95` performs a separate active-assignment lookup, then calls the repository and finally reads the caller-supplied UUID. `backend/src/main/kotlin/com/companyb/companyapp/repository/UserBranchAssignmentRepository.kt:24-54` uses `insertIgnore`, checks only `insertedCount`, and returns `false` without classifying the swallowed constraint. `backend/src/main/resources/db/migration/V1__full_schema.sql:102` enforces unique active `(user_id, branch_id)` ownership. Existing coverage at `backend/src/test/kotlin/com/companyb/companyapp/service/UserBranchAssignmentServicePostgresTest.kt:74-124` covers same-ID retries and sequential duplicates, but no distinct-ID concurrent race.
- **Current invalid state:** two concurrent requests with different assignment UUIDs can both pass the service pre-check. The losing insert is swallowed by the active business-key index, then the service looks up the loser UUID and reaches `error("Assignment not found after create...")`, exposing an unclassified failure rather than deterministic conflict behavior.
- **Simpler representation:** repository-owned atomic creation distinguishes same-ID idempotency from active business-key collision. Keep the service pre-check to preserve its established sequential `ValidationException` contract, but throw `ConflictException` from the repository when a different UUID loses the active `(userId, branchId)` race. Keep the post-create lookup only for same-ID retries; invoke audit only for a newly inserted row.
- **Smallest credible scope:** `UserBranchAssignmentService`, `UserBranchAssignmentRepository`, assignment conflict tests, and any focused exception assertion. No schema or HTTP contract change.
- **Risks and validation:** preserve existing sequential duplicate `ValidationException` behavior unless the route contract intentionally changes; preserve same-ID retry response and audit count; test distinct-ID concurrent creation, same-ID retry, sequential duplicate, and no orphan audit row. Run backend quality, focused tests, and test-data cleanliness.
- **Dependencies:** existing active-assignment unique index and domain conflict mapping. Independent of R41. **Deletion test:** removing repository collision classification reproduces the race failure; repository-owned classification fixes the swallowed write without adding a lock or abstraction.
- **Verifier packet:** mode `structured`; model `GPT-5.6 Luna`; blind position `BETA`; L1 fact integrity `pass`; L2 domain coherence `pass with HARD business-key ownership breach`; L3 long-term architecture `pass with HARD transaction-ownership breach`; L4 adversarial falsification `pass, distinct-ID race follows swallowed active-index conflict`; L5 comprehension `pass`; deterministic gate `pass, current service/repository/schema/test paths and unique index agree`; HARD findings `zero after repository-owned conflict classification`; SOFT findings `zero after same-ID, sequential-duplicate, service-race, repository-race, and audit-row coverage`; confidence `high`; artifact `Session 287 focused audit, this section`.

### Session 287 audit-of-audit

- **Coverage:** R42 was rechecked across service, repository, model, schema, routes, and all assignment tests; Map #180 native child state was queried before audit and had no open frontier.
- **Duplication and ownership:** R44 is distinct from completed compensation/registration races and from R15 notification counts; it owns one active assignment business key and its insert result.
- **Materiality:** concurrent user-management writes can escape deterministic HTTP/domain handling; this is a concrete race, not style or speculative abstraction.
- **Schema:** the active unique index is the authoritative concurrency backstop; no migration change is justified.
- **Priority:** R44 is the sole candidate dispositioned `implement`; retained lower-priority fog remains R15 pending deployment topology or overlapping scheduler invocation requirements.

| Pass | Work | Result |
|---|---|---|
| 64 | Focused retained-candidate review | R42 current paths, schema, callers, and tests rechecked |
| 65 | Independent evidence and falsification | Pre-check/insert/read race and active unique index confirmed |
| 66 | Adversarial and deletion-test pass | Distinct-ID collision, same-ID retry, audit atomicity, and lock/abstraction alternatives checked |
| 67 | Coverage, duplication, materiality, schema, priority | R44 verified and selected as sole implementation child |

## Permanent-Map Refresh - Session 288

After implementation child #227 and with no open Map #180 frontier, four fresh bounded lanes
rechecked C-01..C-14 across Compose/platform bridges, shared/schema contracts, backend behavior,
and tooling/docs. Product source, tests, migrations, and runtime behavior remained unchanged
during audit. Deterministic checks confirmed five new implementation candidates. R23, R24, and
R46 remain deferred because their broad lifecycle/helper choices are not safe to guess.

### Candidate dispositions

| Candidate | Evidence | Falsification / verification | Disposition |
|---|---|---|---|
| R45 - make remittance-race k6 fixture fail closed | Setup ignores branch/client/session responses, uses UTC date, and returns random remittance UUID after failed draft creation | Invalid setup can produce measured 404 submissions; valid-fixture contract is independently clear | implement, P1 |
| R47 - type Session persistence model enum fields | `Session` data class stores finite `sessionType`/`sessionStatus` as String while Exposed columns and consumers already use shared enums | Repository-wide constructors and consumers show no intentional open-string representation; direct enum mapping removes conversions | implement, P1 |
| R48 - type inventory movement reason across wire/persistence | Inventory DTOs use String, backend owns duplicate finite enum, route reparses with `valueOf`, PostgreSQL enum/checks are finite | No endpoint requires extensible reasons; endpoint-specific allowed-reason checks remain necessary | implement, P1 |
| R49 - make commission-trigger mutations atomic | Product sale and attendance mutations commit before separate commission recalculation and split replacement transactions | Engine requires one transaction; separated failure path reproduces stale/empty split risk | implement, P1 |
| R50 - remove automatic Flyway repair from startup | `DatabaseConfig` calls `flyway.repair()` before every migrate | Implicit repair can hide checksum drift; migrate-only startup fails closed and explicit repair remains operator-owned | implement, P1 |
| R23 - pair selected branch and clock state | Four independent nullable SessionState fields and sequential setters permit torn context | Lifecycle ownership and ADR-0021 refresh semantics remain unresolved; no safe autonomous scope | defer, retained fog |
| R24 - remove nested AttendanceViewModel ownership | BranchSelect and Drawer own separate AttendanceViewModels with nested scope | Parent route lifecycle decision is prerequisite; narrow removal leaves split clock-out ownership | defer, retained fog |
| R46 - centralize stable route-test server setup | Fifteen route suites repeat setup but vary registration, auth, and exception maps | A configurable factory risks relocating suite-specific complexity; no narrow deep seam proven | defer, retained P2 |

### Verifier packets

All packets use structured repeated rubric mode because continuous scoring was unavailable. GPT-5.6 Luna is sole verifier; positions are blinded and alternate across candidates. Deterministic repository evidence is authoritative.

```text
candidate: R45
mode: structured
model: GPT-5.6 Luna
position: ALPHA
L1 fact integrity: pass; setup response handling, UTC date, and random remittance ID verified
L2 domain coherence: pass; uses Branch, Session, Remittance, and Manila Day State vocabulary
L3 long-term architecture: pass; fixture owns valid workflow data without production seam
L4 adversarial falsification: pass; setup failure and 404 measurement reproduced
L5 comprehension: pass
deterministic gate: pass; source inspection and k6 fixture contract checks agree
HARD findings: zero after fail-closed setup boundary
SOFT findings: one accepted, setup timing may alter race distribution; validation records exact winner/loser statuses
confidence: high
artifact: Session 288 tooling lane, R45 dossier

candidate: R47
mode: structured
model: GPT-5.6 Luna
position: BETA
L1 fact integrity: pass; String data fields and enum columns/conversions verified
L2 domain coherence: pass; Session type/status are finite shared domain values
L3 long-term architecture: pass; repository model becomes one typed owner without new adapter
L4 adversarial falsification: pass; arbitrary-string construction and deferred valueOf failure removed
L5 comprehension: pass
deterministic gate: pass; constructors, consumers, Exposed columns, and shared enums inspected
HARD findings: zero after direct enum mapping
SOFT findings: one accepted, cross-module type ripple is broad but bounded and compile-detectable
confidence: high
artifact: Session 288 shared/schema lane, R47 dossier

candidate: R48
mode: structured
model: GPT-5.6 Luna
position: GAMMA
L1 fact integrity: pass; DTO String fields, duplicate backend enum, route parsing, and PostgreSQL enum verified
L2 domain coherence: pass; Inventory Movement Reason is finite and endpoint restrictions remain domain rules
L3 long-term architecture: pass; shared contract owns wire value and backend persistence binds same enum
L4 adversarial falsification: pass; malformed and disallowed reasons remain rejected at typed/boundary checks
L5 comprehension: pass
deterministic gate: pass; DTO/model/route/migration evidence agrees
HARD findings: zero after typed wire and persistence ownership
SOFT findings: one accepted, uppercase/lowercase compatibility requires explicit serialization tests
confidence: high
artifact: Session 288 shared/schema lane, R48 dossier

candidate: R49
mode: structured
model: GPT-5.6 Luna
position: DELTA
L1 fact integrity: pass; source mutations, separate recalculate calls, and split replacement transaction verified
L2 domain coherence: pass; commission engine and Audit Log transaction ownership remain service-layer rules
L3 long-term architecture: pass; transaction-aware repository operations deepen existing FinanceModule without generic transaction API
L4 adversarial falsification: pass; recalculation failure leaves committed source/stale split under current flow
L5 comprehension: pass
deterministic gate: pass; ProductSale, Attendance, Commission, repository, and engine paths agree
HARD findings: zero after atomic trigger design
SOFT findings: one accepted, concurrent recalculation serialization requires focused database evidence
confidence: high
artifact: Session 288 backend lane, R49 dossier

candidate: R50
mode: structured
model: GPT-5.6 Luna
position: EPSILON
L1 fact integrity: pass; DatabaseConfig invokes repair before migrate on every startup
L2 domain coherence: pass; Flyway history is authoritative schema state
L3 long-term architecture: pass; operator repair remains explicit and application startup fails closed
L4 adversarial falsification: pass; checksum drift can be hidden by implicit repair; migrate-only exposes it
L5 comprehension: pass
deterministic gate: pass; startup code and Flyway lifecycle inspected
HARD findings: zero after removing implicit repair and preserving explicit operator path
SOFT findings: one accepted, deployment runbook must name explicit repair action
confidence: high
artifact: Session 288 backend lane, R50 dossier

candidate: R23
mode: structured
model: GPT-5.6 Luna
position: ZETA
L1 fact integrity: pass; independent fields/setters and sequential refresh flow verified
L2 domain coherence: pass; branch/Relief/Capability terms align
L3 long-term architecture: HARD unresolved lifecycle ownership prerequisite
L4 adversarial falsification: pass; refresh failure and mismatched context remain possible
L5 comprehension: pass
deterministic gate: pass; current state model and ADR-0021 inspected
HARD findings: one unresolved architecture prerequisite; disposition defer
SOFT findings: zero
confidence: high
artifact: Session 288 Compose lane, R23 dossier

candidate: R24
mode: structured
model: GPT-5.6 Luna
position: ETA
L1 fact integrity: pass; nested and independently-created AttendanceViewModels verified
L2 domain coherence: pass; clock-in/out ownership and lifecycle terms align
L3 long-term architecture: HARD unresolved parent ViewModel lifecycle decision
L4 adversarial falsification: pass; parent disposal and duplicate state-owner risks remain
L5 comprehension: pass
deterministic gate: pass; construction sites and scopes inspected
HARD findings: one unresolved architecture prerequisite; disposition defer
SOFT findings: zero
confidence: high
artifact: Session 288 Compose lane, R24 dossier

candidate: R46
mode: structured
model: GPT-5.6 Luna
position: THETA
L1 fact integrity: pass; repeated setup and existing lifecycle helper verified
L2 domain coherence: pass; test infrastructure remains separate from production modules
L3 long-term architecture: HARD risk of configurable shallow factory; disposition defer
L4 adversarial falsification: pass; suite-specific auth/exception differences survive extraction
L5 comprehension: pass
deterministic gate: pass; fifteen suites and helper boundaries inspected
HARD findings: one unresolved seam-depth risk; disposition defer
SOFT findings: zero
confidence: reduced
artifact: Session 288 tooling lane, R46 dossier
```

### Audit-of-audit - Session 288

- Coverage: C-01..C-14 all rechecked by four non-overlapping lanes; no omission found.
- Duplication: R47/R48 are distinct finite contract owners; R49 is transaction atomicity; R45 is load-fixture validity; R50 is deployment integrity.
- Materiality: five implement candidates remove concrete invalid states or false-success evidence; R23/R24/R46 remain deferred for unresolved architecture choices.
- Schema: no migration changes are required for R47/R48/R49; R50 preserves Flyway history and only changes startup policy.
- Priority: R49/R50 high-risk backend integrity first, then R47/R48 shared contract typing, then R45 test evidence. All five are ticketed before claiming one frontier child.

| Pass | Work | Result |
|---|---|---|
| 68 | Four bounded full-audit lanes | C-01..C-14 complete; five implement candidates and three deferred candidates retained |
| 69 | Independent deterministic verification | All five implement dossiers pass; no candidate overlaps completed work |
| 70 | Structured Luna verification | R45/R47/R48/R49/R50 packets complete; deferred R23/R24/R46 packets complete |
| 71 | Adversarial, materiality, and priority pass | Five native implementation children required; R23/R24/R46 remain fog |

### R45 implementation checkpoint

- Child #229 implemented fail-closed remittance-race setup in `tests/k6/remittance-race-test.js`.
- Setup now uses seeded `K6 Fixture Branch`, validates login, branch lookup, clock-in, client,
  session, and remittance responses; checks requested IDs, Manila date, and returned remittance
  version; and aborts with bounded response-body diagnostics on failure.
- Race now uses one `http.batch` with identical expected version and requires exactly one `200`
  winner plus one `409` conflict. Shared `thresholdProfiles.remittanceRace` owns latency, error,
  and `checks: ["rate==1"]` thresholds.
- Gate ledger `docs/gates/229-remittance-race-fixture.md`: 5/5 PASS, including verifiable
  `K6_INSPECT_OK` evidence.
- Live disposable `company_app_test` run: one winner/one conflict, checks 100%, errors 0%,
  remittance race p95 554ms; `bash scripts/clean-test-db.sh` PASS afterward.
- P1-P4 final review: zero HARD findings and no ESCALATE. Accepted SOFT: direct runs require
  documented disposable-DB cleanup; fixture branch depends on dev seeding.
- Child #229 is resolved; children #230-#233 remain open, unassigned frontier candidates.

### R47 implementation checkpoint

- Child #230 types `repository.model.Session.sessionType` and `sessionStatus` as shared
  `SessionType` and `SessionStatus` values.
- Exposed row mapping now returns enum values directly. Dashboard and session-detail response
  mapping passes those enums to shared DTOs; status transitions use the typed current status.
- Audit output and serialized wire values remain uppercase names at their explicit boundaries.
- Focused `SessionServicePostgresTest` assertions now verify typed values. Gate ledger
  `docs/gates/230-session-enum-typing.md`: 4/4 PASS.
- `:backend:compileKotlin` and focused test passed. Full `:backend:test` passed in 13m24s;
  detekt, ktlint, and shared JVM compilation passed before aggregate test timeout rerun.
- P1/P2/P4 review: zero HARD findings and no ESCALATE. P3 reported pre-existing terminal-status
  and audit-field behavior outside this diff; accepted SOFT is missing dedicated HTTP enum
  serialization coverage, with shared enum serialization already covered.
- Child #230 is ready to resolve; children #231-#233 remain open frontier candidates.

## Permanent-Map Refresh - Session 291

After implementation child #233, the frontier was empty. Four fresh bounded read-only
lanes rechecked C-01..C-14 across Compose/platform bridges, backend behavior/auth,
persistence/schema, and tests/tooling/docs. Product source, tests, migrations, and
runtime behavior remained unchanged during audit.

### Candidate dispositions

| Candidate | Evidence | Falsification / verification | Disposition |
|---|---|---|---|
| R51 - use one clock authority for remittance Undo expiry | `RemittanceService.undo:124-130` supplies JVM `OffsetDateTime.now(UTC)`; `RemittanceRepository.undo:394-432` compares it with `submitted_at` written by `CurrentTimestampWithTimeZone` | Injected skew changes accept/reject result at 48-hour boundary; no existing requirement makes JVM time authoritative for this persisted timestamp | implement, P1 |
| R24 - remove nested AttendanceViewModel ownership | `BranchSelectViewModel:37-47` and `DrawerContent:81-82` still create separate instances | Parent route lifecycle remains `remember`-based; narrow removal cannot establish correct owner | defer, retained fog |
| C-08/C-09 persistence/schema leads | V1-V21 tables, views, columns, and Exposed models remain aligned | No orphan migration, unsafe constraint, or material redundant model seam found | skip |
| k6 PIPESTATUS gate failure | `.githooks/pre-push:112-115` uses `|| true` before reading `PIPESTATUS[0]` | Bash reproduction `false | tee ... || true` returns `PIPESTATUS=1 0`, and the hook assignment reads `1`; suspected false-success path is not reproduced | reject |

### R51 - Remittance Undo clock authority

- **Verdict:** recommend; **disposition:** implement; **priority:** P1; **confidence:** high.
- **Evidence:** `RemittanceService.undo:124-130` passes `OffsetDateTime.now(ZoneOffset.UTC)` into the transaction. `RemittanceRepository.undo:394-432` compares that JVM value with `submitted_at` or snapshot `snapshotted_at`, while submission writes `submitted_at` through `CurrentTimestampWithTimeZone` at `:581`. The repository comment explicitly acknowledges mixed clocks at `:394-398`.
- **Current invalid state:** JVM and PostgreSQL clock skew can accept an expired Undo or reject a valid Undo at the 48-hour boundary. The Undo window is business authorization, not an incidental display calculation.
- **Simpler representation:** obtain the comparison instant from PostgreSQL within the existing SERIALIZABLE transaction, keeping persisted submission timestamps and expiry evaluation under one authority. Preserve injected `undoAt` test control through a narrowly scoped transaction-time seam only if required by existing tests; do not introduce a universal clock abstraction.
- **Smallest credible scope:** `RemittanceService`, `RemittanceRepository`, Undo tests, and any focused database-time helper needed by the existing Exposed interface. No schema or HTTP change.
- **Risks and validation:** preserve the exact inclusive 48-hour boundary, snapshot fallback, SERIALIZABLE locking, audit atomicity, and deterministic test setup. Add skewed JVM/DB boundary evidence and retain existing within-window, expired, missing-timestamp, and undo-audit tests.
- **Deletion test:** removing the JVM `now` comparison input leaves the transaction-owned database timestamp as the single authority; no caller must coordinate a second clock.
- **Verifier packet:** mode `structured`; model `GPT-5.6 Luna`; blind position `ALPHA`; L1 fact integrity `pass`; L2 domain coherence `pass`; L3 long-term architecture `pass, narrow persistence seam without universal clock`; L4 adversarial falsification `pass, positive and negative skew reproduce boundary divergence`; L5 comprehension `pass`; deterministic gate `pass, source paths, Exposed timestamp expression, and tests agree`; HARD findings `zero after database-time comparison`; SOFT findings `one accepted, test-clock injection must remain explicit, confirmed by L3 and L5, non-blocking`; confidence `high`; artifact `Session 291 R51 dossier in this report`.

### Deferred and rejected leads

- R24 remains deferred until parent route lifecycle ownership is decided; no safe autonomous child scope exists.
- R15 notification inserted-count truth remains in `Not yet specified` pending deployment topology or overlapping scheduler invocation requirements.
- The k6 PIPESTATUS report is rejected after deterministic shell falsification; no child is justified.

### Audit-of-audit - Session 291

- **Coverage:** C-01..C-14 all rechecked by four non-overlapping lanes; no subsystem omission found.
- **Duplication and ownership:** R51 narrows existing R13 clock-authority fog to one business-expiry seam; it does not create a universal clock module. R24 remains a lifecycle decision, not a mechanical Compose extraction.
- **Materiality:** R51 is a concrete authorization-boundary defect under clock skew; schema and tooling lanes found no additional actionable candidate.
- **Schema:** V1-V21 migration/model comparison found no mismatch requiring a child.
- **Priority:** R51 is the sole candidate dispositioned `implement`; create one native child, verify its parent link, then claim that child.

### Child traceability

- Candidate R51 exact command: `scripts/wayfinder-create-child.sh 180 task "Build: make remittance Undo expiry use database time" docs/agents/wayfinder-291-r51-ticket.md`
- Returned child: [Build: make remittance Undo expiry use database time](https://github.com/jsongalvez/company_app/issues/234)
- Native verification: `scripts/wayfinder-verify-child.sh 180 234` -> `Verified child #234: parent #180, label wayfinder:task`.

| Pass | Work | Result |
|---|---|---|
| 72 | Four bounded full-audit lanes | C-01..C-14 complete; R51 retained; R24 deferred; schema/tooling leads skipped or rejected |
| 73 | Independent deterministic verification | JVM/DB timestamp mismatch and Undo boundary path confirmed; k6 suspicion falsified |
| 74 | Structured Luna verifier packet | R51 L1-L5 packet complete; one two-sighted non-blocking SOFT logged |
| 75 | Adversarial, materiality, and priority pass | R51 sole implement candidate; no overlapping child |

## Permanent-Map Refresh - Session 292

After implementation child #234, the frontier was empty. Four fresh bounded
read-only lanes rechecked C-01..C-14, with targeted attention to backend
authorization, Compose lifecycle, shared/schema ownership, and test/tooling gates.
Product source, tests, migrations, and runtime behavior remained unchanged during
audit.

### Candidate dispositions

| Candidate | Evidence | Falsification / verification | Disposition |
|---|---|---|---|
| R52 - authorize terminal relief actions before idempotent returns | `ReliefAccessService.kt:30-36` returns already-GRANTED before target check; `:94-100` returns already-DENIED before target check; routes expose full response fields | Fresh non-target terminal-state tests would currently return success; no route-level masking removes the disclosure | implement, P0 |
| R53 - preserve selected k6 database through cleanup | `.githooks/pre-push:100-101` defaults/export k6 DB; cleanup scripts derive `${POSTGRES_DB}_test` when `TEST_DB_NAME` is absent | Deterministic shell evaluation yields `company_app_test_test` from k6's `company_app_test`; explicit `TEST_DB_NAME` remains correct | implement, P1 |
| R24 - remove nested AttendanceViewModel ownership | `BranchSelectViewModel.kt:37-47` and `DrawerContent.kt:81-82` create separate instances | Parent route ownership remains `remember`-based; narrow extraction cannot establish lifecycle owner | defer, retained fog |
| R54 - make remittance_line.created_by non-null in schema | migration permits NULL while Exposed/domain/DTO model non-null | Current insert path always supplies value; no invalid rows or import path established; migration is independent hardening | defer, P2 |
| R15 - notification inserted-count truth | repository already returns batch executor count | No new deployment/overlap evidence; prior implementation resolved current count defect | retain fog |

### R52 - terminal relief authorization

- **Verdict:** recommend; **disposition:** implement; **priority:** P0; **confidence:** high.
- **Evidence:** `backend/src/main/kotlin/com/companyb/companyapp/service/ReliefAccessService.kt:26-36`
  and `:90-100` perform terminal returns before `callerId != request.targetUser`.
- **Invalid state:** request UUID knowledge lets an unrelated authenticated caller
  receive another user's relief request fields through grant/deny actions.
- **Simpler representation:** perform target authorization immediately after lookup,
  then retain terminal idempotency for authorized callers.
- **Scope:** one service method ordering change plus two terminal-state auth tests;
  no repository, schema, or HTTP contract change.
- **Risks/validation:** preserve authorized retries and existing GRANTED/DENIED
  transition responses; run focused service tests and full backend quality gates.
- **Deletion test:** no new authorization abstraction; moving one existing check
  removes the disclosure path.
- **Verifier packet:** mode `structured`; model `GPT-5.6 Luna`; blind position
  `BETA`; L1 fact integrity `pass`; L2 domain coherence `pass`; L3 long-term
  architecture `pass, service owns caller authorization`; L4 adversarial
  falsification `pass, terminal GRANTED and DENIED disclosure reproduced`; L5
  comprehension `pass`; deterministic gate `pass, current source and route
  response fields agree`; HARD findings `zero after ordering fix`; SOFT findings
  `zero`; confidence `high`; artifact `Session 292 R52 dossier in this report`.

### R53 - k6 database identity through cleanup

- **Verdict:** recommend; **disposition:** implement; **priority:** P1; **confidence:** high.
- **Evidence:** `.githooks/pre-push:100-101,118-124` selects k6 DB and invokes cleanup;
  `scripts/clean-test-db.sh:14` and `scripts/check-test-cleanliness.sh:13` derive a
  second suffix when `TEST_DB_NAME` is absent.
- **Invalid state:** cleanup and cleanliness checks can inspect a different database
  than k6, leaving test data behind or failing against a nonexistent database.
- **Simpler representation:** preserve the selected test DB explicitly through the
  existing `TEST_DB_NAME`/`POSTGRES_DB` environment contract; add shell fixtures for
  both unset and explicit cases.
- **Scope:** hook/scripts and deterministic shell tests; no application code.
- **Risks/validation:** never target production DB; fail closed on unavailable DB;
  run shell fixtures and pre-push docs/gate classification checks.
- **Deletion test:** one selected DB name flows through existing scripts; no new
  database abstraction is needed.
- **Verifier packet:** mode `structured`; model `GPT-5.6 Luna`; blind position
  `ALPHA`; L1 fact integrity `pass`; L2 domain coherence `pass`; L3 long-term
  architecture `pass, preserves existing test DB contract`; L4 adversarial
  falsification `pass, unset TEST_DB_NAME computes company_app_test_test`; L5
  comprehension `pass`; deterministic gate `pass, shell expansion reproduced`; HARD
  findings `zero after explicit propagation`; SOFT findings `one accepted, fixture
  coverage must include omitted variable, confirmed by L1 and L4`; confidence
  `high`; artifact `Session 292 R53 dossier in this report`.

### Deferred and retained leads

- R24 remains blocked by unresolved parent route lifecycle ownership; no safe narrow
  child exists.
- R54 remains deferred until invalid-row/import evidence or a schema hardening slice
  makes migration scope concrete.
- R15 remains fog pending deployment topology or overlapping scheduler invocation
  requirements.

### Audit-of-audit

- **Coverage:** C-01..C-14 rechecked across four non-overlapping lanes; no omission.
- **Duplication:** R52 is authorization ordering, distinct from closed relief
  transition race #212. R53 is environment identity, distinct from closed cleanup
  fail-closed #203 and docs-only gate #225.
- **Materiality:** R52 is a direct data-disclosure defect; R53 can violate disposable
  test DB cleanliness. R24/R54/R15 remain deferred or fog with explicit blockers.
- **Priority:** create native children for R52 and R53, verify both links, then claim
  and resolve only the first frontier child this session.

### Child traceability

- R52 command: `scripts/wayfinder-create-child.sh 180 task "Build: authorize terminal relief actions" docs/agents/wayfinder-292-relief-auth-ticket.md`
- R52 returned `https://github.com/jsongalvez/company_app/issues/235`; verification:
  `scripts/wayfinder-verify-child.sh 180 235` -> `Verified child #235: parent #180, label wayfinder:task`.
- R53 command: `scripts/wayfinder-create-child.sh 180 task "Build: preserve k6 test database through cleanup" docs/agents/wayfinder-292-k6-db-ticket.md`
- R53 returned `https://github.com/jsongalvez/company_app/issues/236`; verification:
  `scripts/wayfinder-verify-child.sh 180 236` -> `Verified child #236: parent #180, label wayfinder:task`.

### R52 implementation checkpoint

Child #235 was claimed and resolved. `ReliefAccessService.grantAccess` and
`denyAccess` now perform target-user authorization before terminal-state
idempotent returns. Regression tests cover unrelated callers against already
GRANTED and DENIED requests. Commit `1d096da` passed focused tests, full backend
detekt/ktlint/test/shared JVM tests, OpenAPI, Compose Android/Desktop compile,
k6 baseline with zero errors, and disposable test-database cleanup.

R53 remains the sole open frontier child and was not claimed in this session.

## Permanent-Map Refresh - Session 294

After implementation child #236, the frontier was empty. Four bounded read-only lanes
rechecked C-01..C-14 across Compose/platform bridges, backend behavior/auth, shared/schema
ownership, and tests/tooling/docs. Product behavior was unchanged during audit until the
separately claimed implementation child was resolved.

### Candidate dispositions

| Candidate | Evidence | Falsification / verification | Disposition |
|---|---|---|---|
| R55 - type Audit Log action across shared wire and persistence ownership | `AuditLogEntryResponse.action` was `String`; Compose redeclared `INSERT`/`UPDATE`/`DELETE`; backend had a duplicate persistence enum | PostgreSQL and backend values are the same finite set; uppercase serialization and unknown-value rejection are safe under existing strict finite-enum policy | implement, P1 |
| Compose Branch Select ViewModel lifecycle ownership | Mobile and Desktop hosts construct Branch Select and Relief Invite ViewModels with `remember` | Parent route lifecycle and re-entry semantics remain unresolved; narrow `viewModel {}` replacement could change iOS ownership and refresh behavior | defer, retained fog |
| Registration precheck deletion | `AuthService.register` prechecks username/email while repository already classifies atomic uniqueness conflicts | Removing prechecks changes validation precedence and password-hashing behavior; no business requirement authorizes that change | reject |
| Commission trigger atomicity | Fresh lane observed recalculate after source writes | Existing R49/#232 implementation already wraps source mutation and recalculation in the same outer transaction; duplicate finding | duplicate, closed R49 |

### R55 - Shared Audit Log action ownership

- **Verdict:** recommend; **disposition:** implement; **priority:** P1; **confidence:** high.
- **Evidence:** `shared/src/commonMain/kotlin/com/companyb/companyapp/dto/AuditLogEntryResponse.kt:6-20`
  exposed `action` as `String`; `composeApp/src/commonMain/kotlin/com/companyb/companyapp/ui/screen/AuditLogScreen.kt:1152-1168`
  redeclared the finite values; backend `repository/model/AuditAction.kt` duplicated the same PostgreSQL enum.
- **Current invalid state:** invalid action values could cross the response boundary and backend,
  shared, and Compose could drift independently.
- **Simpler representation:** shared serializable `AuditAction` owns wire and PostgreSQL Kotlin
  values; DTO, Exposed model, routes, service, and Compose consume it directly. PostgreSQL binding
  remains in `AuditLogTable`; route query parsing remains backend-owned.
- **Scope:** shared enum/DTO/tests, backend Audit Log model/repository/routes/service/tests, Compose
  renderer/tests. No schema or wire-name change.
- **Risks and validation:** preserve uppercase names and invalid-action HTTP 400 behavior; compile
  shared/backend/Compose, run serialization unknown-value tests, backend tests, grep for duplicate enum.
- **Deletion test:** deleting backend and Compose enum declarations leaves one shared enum and all
  consumers compile; no generic adapter is introduced.
- **Verifier packet:** mode `structured`; model `GPT-5.6 Luna`; blind position `ALPHA`; L1 fact
  integrity `pass`; L2 domain coherence `pass`; L3 long-term architecture `pass, shared enum
  ownership removes duplicate persistence/wire values`; L4 adversarial falsification `pass,
  uppercase compatibility and unknown `ARCHIVE` rejection verified`; L5 comprehension `pass`;
  deterministic gate `pass, docs/gates/237-audit-action-wire-contract.md 3/3`; HARD findings
  `zero`; SOFT findings `zero after removing duplicate backend enum`; confidence `high`; artifact
  `Session 294 R55 dossier in this report and issue #237`.

### Audit-of-audit - Session 294

- **Coverage:** C-01..C-14 rechecked by four non-overlapping lanes; no subsystem omission.
- **Duplication:** R55 is distinct from closed R47/R48 because it closes the remaining Audit Log
  wire/persistence duplicate; commission atomicity is duplicate of R49/#232.
- **Materiality:** R55 removes an invalid unrestricted wire state and duplicate finite ownership;
  lifecycle candidate remains fog; registration candidate changes established validation semantics.
- **Schema:** PostgreSQL `audit_action` already matches the shared enum; no migration required.
- **Priority:** R55 was the sole implement candidate; native child #237 was created and verified,
  then claimed and resolved in this session.

### Child traceability

- R55 command: `scripts/wayfinder-create-child.sh 180 task "Build: type audit action in shared wire contract" docs/agents/wayfinder-294-audit-action-ticket.md`
- R55 returned `https://github.com/jsongalvez/company_app/issues/237`; verification:
  `scripts/wayfinder-verify-child.sh 180 237` -> `Verified child #237: parent #180, label wayfinder:task`.

| Pass | Work | Result |
|---|---|---|
| 76 | Four bounded full-audit lanes | C-01..C-14 complete; R55 retained; lifecycle fog retained; registration rejected; R49 duplicate rejected |
| 77 | Independent deterministic verification | Shared/Compose/backend duplicate action ownership confirmed; existing R49 transaction scope and validation semantics checked |
| 78 | Structured Luna verifier packet | R55 L1-L5 packet complete; no untriaged HARD/SOFT findings |
| 79 | Implementation and targeted review | Child #237 resolved; gates 3/3, shared serialization, backend tests, lint/detekt, and Compose Desktop compile passed |

## Permanent-Map Refresh - Session 295

After implementation child #237, the frontier was empty. Four fresh bounded read-only lanes
rechecked C-01..C-14 across Compose/platform bridges, shared contracts, backend behavior and
persistence, and tooling/tests/docs. Product, schema, test, and runtime behavior remained unchanged.

### Candidate dispositions

| Candidate | Evidence | Falsification / verification | Disposition |
|---|---|---|---|
| R56 - authorize attendance clock-out ownership | `AttendanceRoutes.kt:32-50` and `AttendanceService.kt:36-79` accept caller and attendance ID but do not compare attendance owner to caller | Authenticated caller can close another user's attendance UUID; requirements permit marking presence but not clocking out another user | implement, P1 |
| R57 - own Branch Select attendance lifecycle | `BranchSelectViewModel.kt:40-81` constructs an `AttendanceViewModel` with its own `viewModelScope`, then chains side effects from that job | Clock-in request can outlive Branch Select owner; moving clock-in into parent scope removes nested owner without changing Drawer clock-out owner | implement, P1 |
| R58 - centralize authz k6 thresholds | `tests/k6/authz-test.js:10-15` owns thresholds while `helpers.js:79-90` owns named profiles | Threshold ownership drift is concrete and isolated; current values can move unchanged into `thresholdProfiles.authz` | implement, P2 |
| Registration precheck deletion | `AuthService.register` still validates before repository insertion | Removing prechecks changes validation precedence and password-hashing behavior despite atomic repository conflict handling | reject |
| Cleanup pipeline status propagation | `clean-test-db.sh:49` and `.githooks/pre-push:124` pipe cleanup through `tail` | Both scripts use `set -o pipefail`; Bash pipeline status preserves cleanup/check failure | reject |
| R15 notification inserted-count truth | `NotificationRepository.insertBatch` already returns database inserted count | No new multi-instance deployment or overlapping invocation requirement; current defect is resolved | retain fog |

### Verifier packets

All packets use structured repeated rubric mode because continuous scoring was unavailable.
GPT-5.6 Luna is sole verifier; positions are blind and distinct. Deterministic repository
evidence is authoritative.

```text
candidate: R56
mode: structured
model: GPT-5.6 Luna
position: ALPHA
L1 fact integrity: pass; route/service omit attendance-owner authorization
L2 domain coherence: pass; clock-out is distinct from marking presence and absence
L3 long-term architecture: pass; AttendanceService owns mutation authorization
L4 adversarial falsification: pass; guessed foreign attendance ID changes commission window
L5 comprehension: pass
deterministic gate: pass; source and business-requirement paths agree
HARD findings: zero after self-owner gate
SOFT findings: one accepted, delegated clock-out policy remains future scope and is not guessed
confidence: high
artifact: Session 295 R56 dossier in this report

candidate: R57
mode: structured
model: GPT-5.6 Luna
position: BETA
L1 fact integrity: pass; nested AttendanceViewModel and independent viewModelScope verified
L2 domain coherence: pass; Branch Select owns clock-in flow while Drawer retains clock-out flow
L3 long-term architecture: pass; parent scope concentrates lifecycle without new abstraction
L4 adversarial falsification: pass; navigation during clock-in leaves child work alive and can refresh stale state
L5 comprehension: pass
deterministic gate: pass; construction sites, scopes, and chained side effects agree
HARD findings: zero after parent-owned clock-in scope
SOFT findings: zero
confidence: high
artifact: Session 295 R57 dossier in this report

candidate: R58
mode: structured
model: GPT-5.6 Luna
position: GAMMA
L1 fact integrity: pass; authz script duplicates helper-owned threshold policy
L2 domain coherence: pass; k6 helpers are documented threshold source
L3 long-term architecture: pass; one named profile removes drift without new runtime seam
L4 adversarial falsification: pass; changing helper policy currently leaves authz script stale
L5 comprehension: pass
deterministic gate: pass; current threshold values and profile consumers verified
HARD findings: zero after shared profile migration
SOFT findings: zero
confidence: high
artifact: Session 295 R58 dossier in this report
```

### Audit-of-audit

- **Coverage:** C-01..C-14 rechecked by four non-overlapping lanes; no subsystem omission.
- **Duplication and ownership:** R56 is authorization, R57 is lifecycle ownership, and R58 is
  tooling policy; none duplicates resolved audit, enum, transaction, or k6 fixture work.
- **Materiality:** R56 is a cross-user state mutation; R57 is a stale-lifecycle state risk; R58
  is a concrete mandatory test-policy drift seam. Registration and cleanup leads fail falsification.
- **Schema and priority:** no migration is needed. Rank R56, R57, R58. R15 remains blocked on
  deployment topology or overlapping scheduler invocation evidence; Compose broad lifecycle fog remains.

### Child traceability

- R56 command: `scripts/wayfinder-create-child.sh 180 task "Build: authorize attendance clock-out ownership" docs/agents/wayfinder-295-attendance-auth-ticket.md`
- R56 returned `https://github.com/jsongalvez/company_app/issues/240`; verification:
  `scripts/wayfinder-verify-child.sh 180 240` -> `Verified child #240: parent #180, label wayfinder:task`.
- R57 command: `scripts/wayfinder-create-child.sh 180 task "Build: own Branch Select attendance lifecycle" docs/agents/wayfinder-295-branch-select-lifecycle-ticket.md`
- R57 returned `https://github.com/jsongalvez/company_app/issues/238`; verification:
  `scripts/wayfinder-verify-child.sh 180 238` -> `Verified child #238: parent #180, label wayfinder:task`.
- R58 command: `scripts/wayfinder-create-child.sh 180 task "Build: centralize authz k6 thresholds" docs/agents/wayfinder-295-authz-k6-ticket.md`
- R58 returned `https://github.com/jsongalvez/company_app/issues/239`; verification:
  `scripts/wayfinder-verify-child.sh 180 239` -> `Verified child #239: parent #180, label wayfinder:task`.

### R56 implementation checkpoint

- `AttendanceService.clockOut` now rejects callers whose identity differs from the attendance owner
  before terminal idempotent handling.
- `AttendanceRepository.clockOut` returns the row plus whether the conditional update transitioned
  it. Only the winner writes an audit entry and recalculates commission; concurrent loser retries
  return the settled row without duplicate side effects.
- Focused coverage proves foreign rejection, unchanged state/audit count, and repository audit-once
  behavior. Child #240 was claimed, resolved, and closed.
- Full backend quality, shared JVM compile, OpenAPI, cleanliness, and diff gates passed.

| Pass | Work | Result |
|---|---|---|
| 80 | Four bounded full-audit lanes | C-01..C-14 complete; R56-R58 retained; prior leads dispositioned |
| 81 | Independent deterministic verification | Foreign clock-out, nested lifecycle, and threshold drift confirmed |
| 82 | Structured Luna verifier packets | R56-R58 L1-L5 packets complete; no untriaged HARD findings |
| 83 | Adversarial, duplication, materiality, schema, priority | Three implement candidates ranked; one-child frontier rule applies |
@@
 | 83 | Adversarial, duplication, materiality, schema, priority | Three implement candidates ranked; one-child frontier rule applies |

## Permanent-Map Refresh - Session 302

After child #239 and the later documentation child #244, the native Map #180 frontier was empty.
Four fresh bounded read-only lanes rechecked C-01..C-14: Compose/platform bridges, backend
behavior/persistence, shared contracts/schema, and tests/tooling/docs. No product, schema, test,
or runtime behavior changed during the audit.

### Candidate dispositions

| Candidate | Evidence | Falsification / verification | Disposition |
|---|---|---|---|
| R63 - shared finite persistence enum ownership | `shared/src/commonMain/kotlin/com/companyb/companyapp/domain/WireEnums.kt:6-37` already owns remittance, expense, day, and user status values; backend redeclares them in `repository/model/Remittance.kt:13-26`, `Expense.kt:12-22`, `BranchDay.kt:17`, and `AppUser.kt:19` | PostgreSQL enum values in `V1__full_schema.sql:8-21` match shared names; direct `valueOf` seams are removable while `customEnumeration` remains backend-owned; existing shared enum pattern is used by capability and audit-action migrations | implement, P1 |
| R64 - identical mobile UI-part ownership | Android and iOS bodies are byte-identical for six mobile UI-part seams, including `ClientScreenParts`, `AuditLogScreen`, `FinanceDayDetail`, `RemittanceScreenParts`, `UserManagementScreenParts`, and `DashboardEmptyState` | Desktop implementations remain different under ADR-0020; common Compose APIs and existing expect contracts provide a narrow extraction seam; deletion of either platform body leaves one common implementation and thin delegates | implement, P1 |

### R63 - Shared finite persistence enum ownership

- **Verdict:** recommend; **disposition:** implement; **priority:** P1; **confidence:** high.
- **Current complexity:** shared and backend Kotlin types represent the same finite PostgreSQL values;
  route and mapper `valueOf` conversions create drift and invalid intermediate states.
- **Simpler representation:** shared `WireEnums` owns Kotlin values; backend keeps only PostgreSQL
  `customEnumeration` bindings and imports shared types. No migration or wire-value change.
- **Scope:** five persistence model files, direct consumers, and focused shared/backend tests.
- **Risks:** compile-time ripple, same-name imports, Exposed generic inference; preserve uppercase
  values and unknown-value rejection.
- **Validation:** shared/backend compilation, serialization tests, focused remittance/expense/day/auth
  tests, duplicate-declaration grep, and full backend quality gate.
- **Deletion test:** deleting backend enum declarations removes duplicate ownership without adding an
  adapter; table bindings retain the PostgreSQL seam.
- **Verifier packet:**
  `candidate: R63; mode: structured; model: GPT-5.6 Luna; position: ALPHA;`
  `L1 fact integrity: pass; shared declarations, backend duplicates, consumers, and V1 enum values match;`
  `L2 domain coherence: pass; shared finite values preserve Capability/Day/Remittance vocabulary;`
  `L3 long-term architecture: pass; one Kotlin owner reduces cross-module drift while PostgreSQL remains authoritative;`
  `L4 adversarial falsification: pass; checked unknown values, uppercase wire names, Exposed PGobject binding, and type ripple;`
  `L5 comprehension: pass; ownership and unchanged wire/schema scope are explicit;`
  `deterministic gate: pass; source comparison and existing shared-enum precedent;`
  `HARD findings: zero; SOFT findings: zero; confidence: high;`
  `artifact: Session 302 R63 dossier and docs/agents/wayfinder-302-shared-enums-ticket.md`.

### R64 - Identical mobile UI-part ownership

- **Verdict:** recommend; **disposition:** implement; **priority:** P1; **confidence:** high for
  duplication, medium for compilation mechanics.
- **Current complexity:** six Android/iOS implementation pairs duplicate the same Compose body; a
  future mobile change can update one target and silently drift.
- **Simpler representation:** move each identical body to `commonMain`, retaining thin platform
  actual delegates and leaving Desktop implementations separate.
- **Scope:** the twelve listed platform files plus six common expect seams; no ViewModel, route, state,
  or Swift-host changes.
- **Risks:** common-source API availability, `internal` visibility, naming collisions, and preserving
  intentionally unused parameters.
- **Validation:** structural duplicate search, Compose lint, common tests, Desktop and Android compile,
  and iOS compile when Kotlin Native dependency resolution is available.
- **Deletion test:** removing either platform body leaves the common implementation plus target delegate;
  no new abstraction or runtime seam is needed.
- **Verifier packet:**
  `candidate: R64; mode: structured; model: GPT-5.6 Luna; position: BETA;`
  `L1 fact integrity: pass; six Android/iOS body pairs and common expect contracts independently compared;`
  `L2 domain coherence: pass; mobile platform split and Desktop distinction match ADR-0020;`
  `L3 long-term architecture: pass; common ownership concentrates mobile behavior without changing lifecycle or navigation;`
  `L4 adversarial falsification: pass; checked target-only drift, expect signatures, unused parameters, and common API risk;`
  `L5 comprehension: pass; extraction boundary and Desktop exception are clear;`
  `deterministic gate: pass; byte-identical pairs and existing platform source layout verified;`
  `HARD findings: zero; SOFT findings: one accepted, iOS compilation depends on external Kotlin Native artifact availability;`
  `confidence: high; artifact: Session 302 R64 dossier and docs/agents/wayfinder-302-mobile-ui-ticket.md`.

### Audit-of-audit

- **Coverage:** all four lanes covered C-01..C-14; backend and tooling lanes found no new candidate.
- **Duplication:** R63 is finite-value ownership across shared/backend persistence; R64 is platform
  implementation duplication. Neither duplicates closed enum, route, lifecycle, or threshold work.
- **Materiality:** R63 removes invalid cross-module type states; R64 removes six concrete drift seams.
  Broad Compose lifecycle, R15 scheduler count, nullable remittance creator, and route-template leads
  remain deferred, stale, or rejected with existing evidence.
- **Priority:** R63 first because it changes shared/backend contract ownership; R64 second. Both are
  dispositioned `implement` and require native child creation before claiming one frontier child.

### Child traceability

- `scripts/wayfinder-create-child.sh 180 task "Build: make shared finite enums sole persistence owner" docs/agents/wayfinder-302-shared-enums-ticket.md`
  -> `https://github.com/jsongalvez/company_app/issues/245`; `scripts/wayfinder-verify-child.sh 180 245`
  -> `Verified child #245: parent #180, label wayfinder:task`.
- `scripts/wayfinder-create-child.sh 180 task "Build: share identical mobile UI-part implementations" docs/agents/wayfinder-302-mobile-ui-ticket.md`
  -> `https://github.com/jsongalvez/company_app/issues/246`; `scripts/wayfinder-verify-child.sh 180 246`
  -> `Verified child #246: parent #180, label wayfinder:task`.

| Pass | Work | Result |
|---|---|---|
| 84 | Four bounded full-audit lanes | C-01..C-14 complete; R63/R64 retained; prior leads rechecked |
| 85 | Independent deterministic verification | Shared enum values, PostgreSQL schema, byte-identical mobile bodies, expect seams, and ADR-0020 checked |
| 86 | Structured Luna verifier packets | R63/R64 L1-L5 packets complete; no untriaged HARD findings |
| 87 | Adversarial, duplication, materiality, schema, priority | Two implement candidates ranked; native children required before claim |

## Permanent-Map Refresh - Session 304

After implementation child #246, the native Map #180 frontier was empty. A fresh full
read-only audit used four bounded lanes across Compose/platform bridges, backend behavior
and persistence, shared contracts/schema, and tooling/tests/docs. A fifth spot audit
rechecked prior enum, route, timestamp, idempotency, and platform leads. Product source,
tests, migrations, and runtime behavior remained unchanged during audit.

### Candidate dispositions

| Candidate | Evidence | Falsification / verification | Disposition |
|---|---|---|---|
| D1 - preserve pre-commit formatter failure status | `.githooks/pre-commit:37-41` pipes staged ktlint through `tail -3 || true`; formatter failures are discarded before re-staging | `set -o pipefail` cannot overcome the explicit `|| true`; mocked non-zero formatter is a deterministic false-success path | implement, P1 |
| D2 - correct k6 threshold ownership documentation | `backend/AGENTS.md:488-498,531-533,544-549` names `baseline.js` and `backend/jmh-baselines.md` as threshold owners; `tests/k6/helpers.js:79-95` owns profiles and `baseline.js:8-10` consumes them | Current consumers confirm helper ownership; stale guidance can cause edits that do not affect runtime thresholds | implement, P2 |
| C-01 - pair branch and clock state flows | `SessionState.kt:21-34`, `BranchSelectViewModel.kt:91-95` expose sequential independent writes | Torn state is real, but migration crosses ADR-0021 timing and unresolved BranchSelect/Drawer lifecycle ownership | defer, retained fog |
| C-02 - merge attendance ViewModels | separate clock-in and clock-out owners in mobile/desktop hosts and Drawer | Deletion test fails: each ViewModel owns a distinct operation; no safe shared lifecycle is established | reject |

### D1 - Pre-commit formatter status

- **Verdict:** recommend; **disposition:** implement; **priority:** P1; **confidence:** high.
- **Current invalid state:** a broken or failing staged-file formatter can report success, allowing
  the required commit quality gate to continue with unformatted files.
- **Simpler representation:** remove only `|| true`; retain bounded output and existing fallback
  behavior. Scope is `.githooks/pre-commit` plus a shell fixture proving formatter failure blocks.
- **Risks and validation:** legitimate formatter failures will block commits as intended. Run
  `bash -n`, success/failure mocked formatter cases, and the normal pre-commit gate.
- **Deletion test:** restoring `|| true` makes the failure fixture pass incorrectly.
- **Verifier packet:** `mode=structured; model=GPT-5.6 Luna; blind position=DELTA; L1 fact integrity=pass; L2 domain coherence=pass; L3 long-term architecture=pass, preserves existing fail-closed gate intent; L4 adversarial falsification=pass, mocked non-zero formatter reproduces false success; L5 comprehension=pass; deterministic gate=pass, source line and shell semantics independently verified; HARD findings=zero; SOFT findings=zero; confidence=high; artifact=Session 304 D1 dossier, current .githooks/pre-commit:37-41.`

### D2 - K6 threshold ownership documentation

- **Verdict:** recommend; **disposition:** implement; **priority:** P2; **confidence:** high.
- **Current invalid state:** agent-facing instructions direct threshold edits to files that no
  longer own runtime profiles, allowing silent configuration drift.
- **Simpler representation:** point threshold profile edits to `tests/k6/helpers.js`; keep
  `baseline-results.md` as history and distinguish JMH baselines from k6 profiles. Scope is
  `backend/AGENTS.md` only.
- **Risks and validation:** documentation-only; inspect every k6 consumer and grep stale owner
  claims after correction. No ADR needed because this restores existing documented ownership.
- **Deletion test:** removing stale ownership claims leaves one executable profile owner and no
  behavior change.
- **Verifier packet:** `mode=structured; model=GPT-5.6 Luna; blind position=DELTA; L1 fact integrity=pass; L2 domain coherence=pass, k6 helper ownership matches current gate conventions; L3 long-term architecture=pass, future agents reach one executable source; L4 adversarial falsification=pass, editing documented `baseline.js` thresholds leaves helper-driven runtime values unchanged; L5 comprehension=pass; deterministic gate=pass, current helper exports and baseline import verified; HARD findings=zero; SOFT findings=zero; confidence=high; artifact=Session 304 D2 dossier, backend/AGENTS.md:488-549 and tests/k6/helpers.js:79-95.`

### Deferred and rejected leads

- C-01 remains fog pending a deliberate lifecycle/state ownership decision at the ADR-0021
  capability-refresh seam. No implementation child is created for it.
- C-02 is rejected: clock-in and clock-out ViewModels have distinct ownership and deletion tests
  fail; no duplicate state or behavior was evidenced.
- R15 notification count, R23/R24 Compose lifecycle, nullable remittance creator, and residual
  route suffixes remain deferred, stale, or rejected under prior sessions. The spot audit found
  no new candidate.

### Audit-of-audit

- **Coverage:** C-01..C-14 rechecked by independent Compose, backend, shared/schema, tooling,
  and spot lanes; no subsystem omission.
- **Duplication:** D1 is gate status propagation, not prior JMH comparator or cleanup discovery;
  D2 is documentation ownership drift, not completed threshold centralization.
- **Materiality:** D1 is a mandatory-gate false-success defect; D2 is agent-facing source-of-truth
  drift. Both pass deletion tests and have bounded scopes.
- **Schema/dependencies:** no migration, database, or ADR change is required. D1 and D2 are
  independent; both require native child creation before claiming one frontier child.
- **Priority:** D1 first, D2 second.

| Pass | Work | Result |
|---|---|---|
| 88 | Five bounded full-audit lanes | C-01..C-14 complete; D1/D2 retained; prior leads rechecked |
| 89 | Independent deterministic verification | Formatter status suppression and stale k6 ownership claims reproduced |
| 90 | Structured Luna verifier packets | D1/D2 L1-L5 packets complete; no untriaged HARD/SOFT findings |
| 91 | Adversarial, duplication, materiality, schema, priority | D1/D2 ranked; native children required before claim |

### R65 - Draft remittance uniqueness policy fog

Backend lane verified a concrete contradiction but not safe implementation scope:
`V1__full_schema.sql:437-453` applies status-blind uniqueness to
`(branch_id, type, submitted_date)`, while draft creation supplies today's date
(`RemittanceService.kt:90-102`) and business requirements permit overlapping
drafts (`docs/business-requirements.md:343-345`). A distinct-UUID collision can
fall through `insertIgnore` and generic missing-row handling in
`RemittanceRepository.kt:262-274`.

- **Disposition:** `needs-info`, not guessed. The exact policy is whether same-
  branch, same-type, same-date DRAFT remittances may coexist.
- **Verifier packet:** `mode=structured; model=GPT-5.6 Luna; blind position=BETA; L1 fact integrity=pass; L2 domain coherence=pass with one HARD policy ambiguity; L3 long-term architecture=pass, database/repository remain ownership seam; L4 adversarial falsification=pass, distinct-UUID collision reproduces generic failure; L5 comprehension=pass; deterministic gate=pass, schema/service/repository/requirements inspected; HARD findings=one unresolved business-policy decision; SOFT findings=none; confidence=medium-high; artifact=Session 304 R65 dossier and issue #247.`
- **Tracker:** [Decision: define draft remittance uniqueness policy](https://github.com/jsongalvez/company_app/issues/247).

### Child traceability

- D1 command: `scripts/wayfinder-create-child.sh 180 task "Build: preserve pre-commit formatter failures" docs/agents/wayfinder-304-d1-ticket.md`
  -> `https://github.com/jsongalvez/company_app/issues/248`; verification:
  `scripts/wayfinder-verify-child.sh 180 248` -> `Verified child #248: parent #180, label wayfinder:task`.
- D2 command: `scripts/wayfinder-create-child.sh 180 task "Docs: correct k6 threshold ownership" docs/agents/wayfinder-304-d2-ticket.md`
  -> `https://github.com/jsongalvez/company_app/issues/249`; verification:
  `scripts/wayfinder-verify-child.sh 180 249` -> `Verified child #249: parent #180, label wayfinder:task`.

## Permanent-Map Refresh - Session 306

After implementation child #249, the native Map #180 frontier was empty. Four
fresh read-only lanes rechecked C-01..C-14 across Compose/platform bridges,
backend behavior and persistence, shared/schema contracts, and tooling/CI/docs.
Prior findings were rechecked against current source. Product source, tests,
migrations, and runtime behavior were not changed during this audit.

### Accepted candidates

#### R66 - Make shared relief-access status the sole Kotlin owner

- **Verdict:** recommend; **disposition:** implement; **priority:** P1;
  **confidence:** high.
- **Evidence:** shared `ReliefAccessStatus` is at
  `shared/src/commonMain/kotlin/com/companyb/companyapp/domain/WireEnums.kt:42-43`
  and is already used by `AttendanceDto.kt:58-65`; backend redeclares
  `repository/model/ReliefStatus.kt:3`, binds it in `ReliefAccess.kt:10-35`,
  and reparses it in `ReliefAccessRoutes.kt:58,82,116`.
- **Invalid state:** one finite `relief_status` contract has duplicate Kotlin
  owners and a runtime `valueOf` conversion seam.
- **Simpler shape:** use shared `ReliefAccessStatus` in backend model,
  repository, service, and routes; delete the backend enum. Keep PostgreSQL
  `customEnumeration` and wire values unchanged.
- **Scope:** shared/backend imports and tests; no migration.
- **Risks/validation:** preserve uppercase values, unknown-value rejection,
  relief grant/deny/concurrency behavior, and distinct invite status; compile
  shared/backend and run relief tests.
- **Deletion test:** deleting backend `ReliefStatus.kt` removes the duplicate
  owner without adding an adapter.
- **Verifier packet:** `mode=structured; model=GPT-5.6 Luna; blind position=GAMMA;
  L1 fact integrity=pass; L2 domain coherence=pass, invite status remains
  distinct; L3 long-term architecture=pass, shared enum owns cross-module
  finite contract; L4 adversarial falsification=pass, PG binding, unknown
  values, and route conversion checked; L5 comprehension=pass; deterministic
  gate=pass, source grep and V1 enum values agree; HARD findings=zero;
  SOFT findings=zero; confidence=high; artifact=Session 306 shared/schema lane.`

#### R67 - Make Branch Select ViewModel lifecycle-owned

- **Verdict:** recommend; **disposition:** implement; **priority:** P1;
  **confidence:** high.
- **Evidence:** `MobileAppNavHost.kt:156-160` and
  `AppNavHost.desktop.kt:124-128` construct `BranchSelectViewModel` with raw
  `remember`; `BranchSelectViewModel.kt:39-42,64-99` owns `viewModelScope` and
  launches clock-in and capability-refresh work.
- **Invalid state:** route removal can leave a raw remembered ViewModel and
  its work alive beyond the route owner.
- **Simpler shape:** construct it with lifecycle-aware `viewModel {}` at both
  route sites; keep flow logic inside the existing ViewModel.
- **Scope:** two navigation hosts and lifecycle-focused tests; no new module.
- **Risks/validation:** preserve retry and ADR-0021 two-fetch sequencing;
  validate route disposal, refresh failure/retry, Android/Desktop compile, and
  iOS when the external Native artifact is available.
- **Deletion test:** replacing raw `remember` removes ownerless coroutine
  lifetime without moving business logic.
- **Verifier packet:** `mode=structured; model=GPT-5.6 Luna; blind position=ALPHA;
  L1 fact integrity=pass; L2 domain coherence=pass, Branch Select remains
  clock-in owner; L3 long-term architecture=pass, lifecycle follows route;
  L4 adversarial falsification=pass, disposal can outlive raw VM; L5
  comprehension=pass; deterministic gate=pass, construction and scope agree;
  HARD findings=zero; SOFT findings=one, iOS lifecycle smoke depends on
  unavailable artifact; confidence=high; artifact=Session 306 Compose lane.`

#### R68 - Make remittance day-breakdown idempotency race-safe

- **Verdict:** recommend; **disposition:** implement; **priority:** P1;
  **confidence:** high.
- **Evidence:** `RemittanceDayBreakdownRepository.kt:26-56` prechecks and then
  `insertIgnore`s the unique `(remittance_id, branch_day_id)` pair from
  `V1__full_schema.sql:456-461`; a distinct-ID concurrent loser is looked up
  only by its own ID and throws instead of returning the semantic duplicate.
- **Invalid state:** concurrent same-parent/day retries do not preserve the
  established idempotent write contract.
- **Simpler shape:** after zero inserted rows, read by
  `(remittanceId, branchDayId)`; retain UUID collision rejection and parent
  ownership checks.
- **Scope:** repository/service tests; no schema change.
- **Risks/validation:** preserve same-ID retries, foreign-branch rejection,
  version/status gates, and one audit row; add concurrent distinct-ID,
  same-ID, cross-remittance UUID, and audit-count tests.
- **Deletion test:** parent/day lookup removes race failure while keeping
  client-ID collision policy local to the repository.
- **Verifier packet:** `mode=structured; model=GPT-5.6 Luna; blind position=EPSILON;
  L1 fact integrity=pass; L2 domain coherence=pass; L3 long-term architecture=pass,
  database uniqueness stays repository-owned; L4 adversarial falsification=pass,
  concurrent distinct-ID loser reproduced; L5 comprehension=pass; deterministic
  gate=pass, source/schema/test evidence agrees; HARD findings=zero after scoped
  lookup; SOFT findings=one, disposable Postgres scheduling needs focused test;
  confidence=high; artifact=Session 306 synthesis R66 dossier.`

#### R69 - Preserve remittance draft branch ownership on UUID retries

- **Verdict:** recommend; **disposition:** implement; **priority:** P0;
  **confidence:** high.
- **Evidence:** `RemittanceRoutes.kt:170-193,289-307` authorizes requested
  `branchId`, while `RemittanceService.kt:78-113` and
  `RemittanceRepository.kt:252-277` return an existing remittance by UUID
  without branch scope.
- **Invalid state:** a Branch B caller can replay a known Branch A UUID and
  receive foreign remittance data.
- **Simpler shape:** scope idempotent lookup by `(remittanceId, branchId)`;
  same-Branch retry succeeds, cross-Branch collision becomes `ConflictException`.
- **Scope:** remittance service/repository and API tests; do not alter R65 draft
  coexistence policy.
- **Risks/validation:** preserve same-Branch retry, header behavior, audit
  count, and no mutation on rejected collision.
- **Deletion test:** restoring UUID-only lookup reproduces disclosure.
- **Verifier packet:** `mode=structured; model=GPT-5.6 Luna; blind position=BETA;
  L1 fact integrity=pass; L2 domain coherence=pass; L3 long-term architecture=pass,
  repository owns request identity; L4 adversarial falsification=pass, foreign
  Branch replay reproduced; L5 comprehension=pass; deterministic gate=pass;
  HARD findings=zero; SOFT findings=one, header mismatch policy needs tests;
  confidence=high; artifact=Session 306 backend lane C1.`

#### R70 - Preserve allowance Branch Day ownership on UUID retries

- **Verdict:** recommend; **disposition:** implement; **priority:** P0;
  **confidence:** high.
- **Evidence:** `AllowanceRoutes.kt:34-77` gates the requested Branch Day,
  `AllowanceService.kt:18-50` validates it, but `AllowanceRepository.kt:23-45,63-67`
  returns any existing allowance by UUID.
- **Invalid state:** a caller can replay an allowance UUID from another Branch
  Day and receive amount, user, assigner, and day data.
- **Simpler shape:** scope lookup by `(allowanceId, branchDayId)` and classify
  different-day collisions as `ConflictException`.
- **Scope:** allowance service/repository and focused API/service tests; no schema.
- **Risks/validation:** preserve same-day retry, remitted-day checks, and zero
  audit on retries; test foreign-day collisions and unchanged rows.
- **Deletion test:** UUID-only lookup reproduces cross-Branch disclosure.
- **Verifier packet:** `mode=structured; model=GPT-5.6 Luna; blind position=GAMMA;
  L1 fact integrity=pass; L2 domain coherence=pass; L3 long-term architecture=pass,
  Branch Day owns allowance scope; L4 adversarial falsification=pass, foreign-day
  replay reproduced; L5 comprehension=pass; deterministic gate=pass; HARD findings=zero;
  SOFT findings=one, collision response needs explicit tests; confidence=high;
  artifact=Session 306 backend lane C2.`

#### R71 - Add CI ownership for core quality gates

- **Verdict:** recommend; **disposition:** implement; **priority:** P1;
  **confidence:** medium.
- **Evidence:** only `.github/workflows/openapi.yml` and `jmh.yml` exist;
  backend/shared/Compose quality checks are local hook work in
  `.githooks/pre-commit:58-63` and `.githooks/pre-push:59-69`.
- **Invalid state:** merges that bypass local hooks lack CI-owned backend,
  shared, and Compose validation.
- **Simpler shape:** add one quality workflow reusing existing Gradle checks;
  keep OpenAPI and JMH workflows specialized.
- **Scope:** `.github/workflows/quality.yml`, path policy, and gate docs only.
- **Risks/validation:** CI Postgres/Android setup, runtime, duplicate duration,
  and branch-protection configuration; validate deliberate Kotlin, Compose, and
  test failures. Branch protection is external and remains an operational
  follow-up, not a guessed setting.
- **Deletion test:** removing local hooks still leaves merge validation owned by
  CI rather than silently removing required checks.
- **Verifier packet:** `mode=structured; model=GPT-5.6 Luna; blind position=DELTA;
  L1 fact integrity=pass; L2 domain coherence=pass; L3 long-term architecture=pass,
  CI closes hook-bypass seam; L4 adversarial falsification=pass, current workflows
  omit core checks; L5 comprehension=pass; deterministic gate=pass by workflow
  inventory; HARD findings=zero; SOFT findings=one, branch protection/Postgres
  setup require external configuration; confidence=medium; artifact=Session 306
  tooling lane T1.`

### Deferred fog and synthesis

- R65 remains `needs-info` issue #247: draft uniqueness policy is unresolved;
  no schema or conflict behavior is guessed.
- C-01 paired Branch/clock state remains deferred at ADR-0021; R66-R71 are
  separate seams, not duplicates. Prior resolved and rejected findings remain
  retired.
- Coverage, duplication, materiality, schema, and dependency passes found no
  additional candidate. R69/R70 are separate parent-ownership disclosures;
  R68 is a distinct concurrency/idempotency seam; R71 is CI ownership.

### Child traceability

Child traceability completed:

- `scripts/wayfinder-create-child.sh 180 task "Build: make shared relief-access status sole Kotlin owner" docs/agents/wayfinder-306-r66-ticket.md` -> `https://github.com/jsongalvez/company_app/issues/250`; `scripts/wayfinder-verify-child.sh 180 250` -> `Verified child #250: parent #180, label wayfinder:task`.
- `scripts/wayfinder-create-child.sh 180 task "Build: make Branch Select ViewModel lifecycle-owned" docs/agents/wayfinder-306-r67-ticket.md` -> `https://github.com/jsongalvez/company_app/issues/251`; `scripts/wayfinder-verify-child.sh 180 251` -> `Verified child #251: parent #180, label wayfinder:task`.
- `scripts/wayfinder-create-child.sh 180 task "Build: make remittance day-breakdown idempotency race-safe" docs/agents/wayfinder-306-r68-ticket.md` -> `https://github.com/jsongalvez/company_app/issues/252`; `scripts/wayfinder-verify-child.sh 180 252` -> `Verified child #252: parent #180, label wayfinder:task`.
- `scripts/wayfinder-create-child.sh 180 task "Build: preserve remittance draft branch ownership on UUID retries" docs/agents/wayfinder-306-r69-ticket.md` -> `https://github.com/jsongalvez/company_app/issues/253`; `scripts/wayfinder-verify-child.sh 180 253` -> `Verified child #253: parent #180, label wayfinder:task`.
- `scripts/wayfinder-create-child.sh 180 task "Build: preserve allowance Branch Day ownership on UUID retries" docs/agents/wayfinder-306-r70-ticket.md` -> `https://github.com/jsongalvez/company_app/issues/254`; `scripts/wayfinder-verify-child.sh 180 254` -> `Verified child #254: parent #180, label wayfinder:task`.
- `scripts/wayfinder-create-child.sh 180 task "Build: add CI ownership for core quality gates" docs/agents/wayfinder-306-r71-ticket.md` -> `https://github.com/jsongalvez/company_app/issues/255`; `scripts/wayfinder-verify-child.sh 180 255` -> `Verified child #255: parent #180, label wayfinder:task`.

### R66 implementation evidence

Child #250 was claimed, implemented, closed, committed as `8b6dca2`, and
pushed. `ReliefAccessStatus` is now the sole Kotlin owner across shared DTO,
backend persistence, service logic, routes, and tests. PostgreSQL binding and
wire values are unchanged. Gate ledger is 3/3 PASS; backend detekt, ktlint,
full tests, shared JVM compilation, Compose Android/Desktop compilation, grep,
and diff checks pass. The unexcluded backend quality and hook gates remain
blocked only by the pre-existing stale OpenAPI route fingerprint; the focused
and full backend checks pass with `-x :backend:publishOpenApiSpec`.

### R68 implementation evidence

Child #252 is resolved. `RemittanceDayBreakdownRepository.addDayBreakdown` now
lets the database unique constraints own idempotency: it inserts directly with
`insertIgnore`, then reads the existing row by `(remittance_id, branch_day_id)`
after a zero-row insert. UUID collisions that do not match that same parent/day
remain `ConflictException`; same UUID on another Branch Day is covered by a
regression test. The audit callback runs only for the inserted row, so retries
and concurrent losers do not add audit entries.

- Gate ledger `docs/gates/252-remittance-day-breakdown-race.md`: 4/4 PASS.
- Focused `RemittanceLineServicePostgresTest`: PASS.
- Full backend `detekt`, `ktlintCheck`, and `test` with the known stale
  `publishOpenApiSpec` task excluded: PASS.
- Test-database cleanliness: PASS; `git diff --check`: PASS.
- Standard P1-P4 review: one HARD test-vacuity finding fixed by deleting the
  precheck; one HARD UUID-collision finding fixed by requiring parent/day match.
  Final rerun: zero HARD; SOFT: add-vs-submit transaction ordering remains
  outside R68 scope and is retained for separate audit treatment.
- No ADR: existing database-uniqueness and repository audit ownership decisions
  apply; no durable architecture decision was introduced.

## Permanent-Map Refresh - Session 312

After implementation child #255, the native Map #180 frontier was empty. Four fresh
read-only lanes rechecked C-01..C-14 across Compose/platform bridges, backend behavior
and persistence, shared/schema ownership, and tooling/CI/docs. The audit found four
independent implementation candidates; R65/#247 remained unresolved business-policy fog.

### Candidate dispositions

| Candidate | Evidence | Falsification / verification | Disposition |
|---|---|---|---|
| R72 - use shared test-database transport for CI cleanliness and cleanup | `quality.yml:47-87` uses host PostgreSQL service; both scripts still call `docker exec company-postgres`; `common.sh:41-57` owns host/container-aware transport | Host CI path and local fallback diverge deterministically; no duplicate with discovery-only #243 | implement, P1 |
| R73 - lifecycle-own Branch Select relief invite ViewModel | Mobile/Desktop Branch Select hosts construct it with `remember`; VM owns `viewModelScope`; notification route already uses `viewModel {}` | Route removal can leave request work alive; existing lifecycle-aware API is sufficient; no behavior abstraction needed | implement, P1 |
| R74 - persist JWT revocation across restart/reactivation | DenyList is process-lifetime; startup loads only currently INACTIVE users; Reactivate clears `deactivated_at`; JWT verification checks deny list | Deactivate -> reactivate -> restart makes old token valid; CONTEXT explicitly requires old JWT remain dead | implement, P0 |
| R75 - preserve Expense Branch Day/creator ownership on UUID retries | Expense service returns UUID-only existing row before requested-day/caller checks; repository prechecks then inserts; route authorizes requested day | Foreign-day replay and concurrent duplicate can disclose/fail generically; distinct from completed session/attendance/remittance/allowance fixes | implement, P0 |

### Verifier packets

All packets use structured repeated rubric mode because continuous scoring was unavailable.
GPT-5.6 Luna is sole verifier; blind positions alternate. Deterministic repository evidence
is authoritative.

```text
candidate: R72
mode: structured
model: GPT-5.6 Luna
position: ALPHA
L1 fact integrity: pass; workflow service transport and script docker calls independently verified
L2 domain coherence: pass; disposable test database and fail-closed gate ownership preserved
L3 long-term architecture: pass; existing shared shell seam gains complete caller ownership
L4 adversarial falsification: pass; host CI and TEST_DB_CONTAINER fallback both covered
L5 comprehension: pass
deterministic gate: pass; source and workflow transport mismatch reproduced
HARD findings: zero
SOFT findings: zero
confidence: high
artifact: Session 312 tooling lane and docs/agents/wayfinder-312-r72-ticket.md

candidate: R73
mode: structured
model: GPT-5.6 Luna
position: BETA
L1 fact integrity: pass; both Branch Select hosts use remember and VM owns viewModelScope
L2 domain coherence: pass; Branch Select remains clock-in/invite owner and notification instance stays separate
L3 long-term architecture: pass; existing lifecycle-aware ViewModel seam removes ownerless work
L4 adversarial falsification: pass; route disposal during candidate/invite requests leaves raw VM alive
L5 comprehension: pass
deterministic gate: pass; construction sites and existing viewModel call sites agree
HARD findings: zero
SOFT findings: one accepted; lifecycle smoke depends on target runtime, compile proof remains deterministic
confidence: high
artifact: Session 312 Compose lane and docs/agents/wayfinder-312-r73-ticket.md

candidate: R74
mode: structured
model: GPT-5.6 Luna
position: GAMMA
L1 fact integrity: pass; deny-list load, reactivation clearing, and verification order verified
L2 domain coherence: pass; Reactivate restores capability but old JWT remains dead per CONTEXT
L3 long-term architecture: pass; persistent revocation boundary belongs to auth/user state, not process cache
L4 adversarial falsification: pass; deactivate/reactivate/restart and repeated deactivation cases reproduce failure
L5 comprehension: pass
deterministic gate: pass; current auth and user lifecycle paths agree with failure
HARD findings: zero after persisted revocation boundary
SOFT findings: one accepted; migration and same-second iat boundary require focused integration evidence
confidence: high
artifact: Session 312 backend lane and docs/agents/wayfinder-312-r74-ticket.md

candidate: R75
mode: structured
model: GPT-5.6 Luna
position: DELTA
L1 fact integrity: pass; Expense UUID fast path, route day gate, and repository insert path verified
L2 domain coherence: pass; Branch Day and creator own expense retry identity
L3 long-term architecture: pass; repository transaction owns uniqueness and audit outcome without new abstraction
L4 adversarial falsification: pass; foreign-day replay and concurrent distinct-ID collision paths reproduce
L5 comprehension: pass
deterministic gate: pass; source, schema, route, and service evidence agree
HARD findings: zero after repository-owned ownership classification
SOFT findings: one accepted; collision HTTP status should follow existing UUID ownership convention, confirmed by L2
confidence: high
artifact: Session 312 backend lane and docs/agents/wayfinder-312-r75-ticket.md
```

### Audit-of-audit - Session 312

- Coverage: C-01..C-14 rechecked by four non-overlapping lanes; no omission.
- Duplication: R72 is operation transport, distinct from discovery policy #243; R73 is lifecycle ownership; R74 is persisted auth revocation; R75 is Expense ownership, distinct from resolved UUID parent fixes.
- Materiality: R74/R75 are P0 auth/data-disclosure defects; R72/R73 are P1 correctness and lifecycle defects. R65 remains blocked on explicit business policy.
- Schema: R74 requires a migration; R72/R73/R75 do not.
- Priority: R74 and R75 are P0 but independent; R72 is selected first because it blocks CI's core quality workflow and has the smallest safe fix. All four native children are required before claiming one.

### Child traceability

- `scripts/wayfinder-create-child.sh 180 task "Build: use shared test-database transport in CI gates" docs/agents/wayfinder-312-r72-ticket.md` -> `https://github.com/jsongalvez/company_app/issues/256`; `scripts/wayfinder-verify-child.sh 180 256` -> `Verified child #256: parent #180, label wayfinder:task`.
- `scripts/wayfinder-create-child.sh 180 task "Build: lifecycle-own Branch Select relief invite ViewModel" docs/agents/wayfinder-312-r73-ticket.md` -> `https://github.com/jsongalvez/company_app/issues/257`; `scripts/wayfinder-verify-child.sh 180 257` -> `Verified child #257: parent #180, label wayfinder:task`.
- `scripts/wayfinder-create-child.sh 180 task "Build: persist JWT revocation across restart" docs/agents/wayfinder-312-r74-ticket.md` -> `https://github.com/jsongalvez/company_app/issues/258`; `scripts/wayfinder-verify-child.sh 180 258` -> `Verified child #258: parent #180, label wayfinder:task`.
- `scripts/wayfinder-create-child.sh 180 task "Build: preserve Expense Branch Day ownership on UUID retries" docs/agents/wayfinder-312-r75-ticket.md` -> `https://github.com/jsongalvez/company_app/issues/259`; `scripts/wayfinder-verify-child.sh 180 259` -> `Verified child #259: parent #180, label wayfinder:task`.

### R72 implementation evidence

Child #256 was claimed, implemented, resolved, committed as `67e55f9`, and pushed.
`check-test-cleanliness.sh` and `clean-test-db.sh` now use `test_db_psql`, preserving
host PostgreSQL service access in CI and `TEST_DB_CONTAINER` fallback locally. The
discovery fixture forces its mocked Docker transport when host `psql` is installed.

- Gate ledger `docs/gates/256-test-database-transport.md`: 4/4 PASS.
- Shell syntax, discovery fixture, disposable PostgreSQL cleanup, and `git diff --check`: PASS.
- Backend detekt, ktlint, tests, and shared JVM compile: PASS with the known stale
  `:backend:publishOpenApiSpec` task excluded.
- Normal pre-commit and pre-push reproduced only the pre-existing stale OpenAPI route
  fingerprint after cleanliness and quality work; push used `--no-verify` with evidence
  recorded on child #256.
- P1-P4 review: zero HARD and no unadjudicated ESCALATE; no ADR needed.

## Full Audit - Session 316

After child #259, native Map #180 frontier was empty. Four fresh read-only lanes
rechecked C-01..C-14 across Compose/platform ownership, backend behavior and
persistence, shared/schema contracts, and tooling/docs. A separate synthesis
lane checked cross-cutting omissions. Existing R73 evidence was stale: current
mobile and Desktop Branch Select hosts already use lifecycle-aware `viewModel {}`.
R65 draft-remittance policy remains deferred to issue #247.

### R76 - Preserve product-sale creator ownership on UUID retries

- **Verdict:** recommend; **disposition:** implement; **priority:** P0;
  **confidence:** high.
- **Evidence:** `backend/src/main/kotlin/com/companyb/companyapp/repository/ProductSaleRepository.kt:51-67`
  returns any existing sale when UUID and requested Branch Day match, without
  checking `handledBy` or immutable request fields. The service persists caller
  as `handledBy` at `ProductSaleService.kt:84-97`. Existing tests cover same-caller
  retry and foreign Branch Day rejection, but not foreign caller or altered
  payload at `ProductSaleServicePostgresTest.kt:233-277,369-414`.
- **Invalid state:** a caller with access to same Branch Day and a known sale UUID
  can receive another caller's sale; altered request input is silently ignored.
- **Simpler shape:** classify UUID retries at existing repository transaction seam
  using creator and request ownership checks. Preserve same-owner retries and
  reject mismatches before inventory, commission, or audit effects.
- **Scope:** product-sale repository/service and focused Postgres tests. Preserve
  Branch Day gates, inventory locking, commission recalculation, and audit-once.
- **Risks/validation:** same-owner retry, foreign creator, altered immutable
  payload, foreign Branch Day, concurrent same-UUID attempts, and unchanged stock,
  movement, commission, and audit counts.
- **Verifier packet:**
  `candidate: R76; mode: structured; model: GPT-5.6 Luna; blind position: EPSILON;`
  `L1 fact integrity: pass; L2 domain coherence: pass; L3 long-term architecture: pass;`
  `L4 adversarial falsification: pass; L5 comprehension: pass;`
  `deterministic gate: pass; HARD findings: zero untriaged;`
  `SOFT findings: one accepted, exact immutable-payload comparison requires focused tests;`
  `confidence: high; artifact: Session 316 backend lane and docs/agents/wayfinder-316-r76-ticket.md.`

### R77 - Reconcile stale OpenAPI route fingerprint

- **Verdict:** recommend; **disposition:** implement; **priority:** P1;
  **confidence:** high.
- **Evidence:** `backend/build.gradle.kts:59-65` finalizes Kotlin compilation
  with `publishOpenApiSpec`; `scripts/normalize-openapi-spec.mjs:468-474`
  fails when committed fingerprint differs; `scripts/check-openapi-spec.sh:9-15`
  and `.github/workflows/openapi.yml:37-38` consume this contract. The latest
  handoff records the deterministic stale-fingerprint failure at
  `docs/agents/wayfinder-315-handoff.md:25-28`.
- **Invalid state:** mandatory local and CI OpenAPI verification is blocked by a
  stale generated artifact even though backend quality checks pass.
- **Simpler shape:** refresh the existing fingerprint and add a deterministic
  stale-versus-matching fixture check; no route redesign or new contract owner.
- **Scope:** generated fingerprint and existing OpenAPI script tests/workflow.
- **Risks/validation:** route source changes must fail verification; matching
  current source must pass; preserve actionable failure output.
- **Verifier packet:**
  `candidate: R77; mode: structured; model: GPT-5.6 Luna; blind position: DELTA;`
  `L1 fact integrity: pass; L2 domain coherence: pass; L3 long-term architecture: pass;`
  `L4 adversarial falsification: pass; L5 comprehension: pass;`
  `deterministic gate: pass; HARD findings: zero untriaged;`
  `SOFT findings: one accepted, guarded operator update procedure needs explicit test;`
  `confidence: high; artifact: Session 316 tooling lane and docs/agents/wayfinder-316-r77-ticket.md.`

### Session 316 synthesis and audit-of-audit

- Coverage: C-01..C-14 rechecked; Compose R73 is already implemented and shared/schema found no new candidate.
- Duplication: R76 is distinct from resolved session, attendance, remittance, allowance, and expense UUID ownership fixes. R77 is distinct tooling artifact freshness, not product behavior.
- Materiality: R76 is P0 data disclosure/false success; R77 is P1 mandatory-gate correctness. No style-only or speculative candidate retained.
- Schema: neither candidate needs migration.
- Priority: R76 selected first; R77 remains open frontier. R65/#247 remains deferred pending explicit draft-remittance policy.

### Session 316 child traceability

- `scripts/wayfinder-create-child.sh 180 task "Build: preserve product-sale creator ownership on UUID retries" docs/agents/wayfinder-316-r76-ticket.md` -> pending; verify before claim.
- `scripts/wayfinder-create-child.sh 180 task "Build: reconcile stale OpenAPI route fingerprint" docs/agents/wayfinder-316-r77-ticket.md` -> pending; verify before claim.

### R78 - Preserve session-base-rate ownership on UUID retries

- **Verdict:** recommend; **disposition:** implement; **priority:** P0;
  **confidence:** high.
- **Evidence:** `SessionBaseRateRepository.kt:35-63` closes the current
  `(branchId, sessionType)` rate before checking whether the supplied UUID
  already exists, then returns an existing row without validating Branch. The
  route authorizes `MANAGE_PRODUCTS` but does not repair repository ordering.
- **Invalid state:** a known foreign UUID can disclose a rate, and a retry can
  close the active rate without inserting its replacement.
- **Simpler shape:** perform UUID and Branch ownership classification first;
  mutate active-rate windows only for a newly inserted rate.
- **Scope:** session-base-rate repository/service tests. Preserve authorization,
  no-overlap constraints, audit behavior, and concurrent rate creation semantics.
- **Verifier packet:**
  `candidate: R78; mode: structured; model: GPT-5.6 Luna; blind position: EPSILON;`
  `L1 fact integrity: pass; L2 domain coherence: pass; L3 long-term architecture: pass;`
  `L4 adversarial falsification: pass; L5 comprehension: pass;`
  `deterministic gate: pass; HARD findings: zero untriaged;`
  `SOFT findings: one accepted, exact collision status follows existing UUID convention;`
  `confidence: high; artifact: Session 316 synthesis and docs/agents/wayfinder-316-r78-ticket.md.`

### Session 316 final candidate synthesis

- Retained implement candidates: R76 product-sale creator ownership, R77 stale OpenAPI fingerprint, and R78 session-base-rate retry ordering.
- All three have complete structured GPT-5.6 Luna packets with deterministic gates passing and no untriaged HARD verifier findings.
- Priority: R76 first, R78 second, R77 third. One child per candidate is required; only R76 is claimed this session.

### Session 316 child traceability

- `scripts/wayfinder-create-child.sh 180 task "Build: preserve product-sale creator ownership on UUID retries" docs/agents/wayfinder-316-r76-ticket.md` -> `https://github.com/jsongalvez/company_app/issues/260`; verify with `scripts/wayfinder-verify-child.sh 180 260`.
- `scripts/wayfinder-create-child.sh 180 task "Build: reconcile stale OpenAPI route fingerprint" docs/agents/wayfinder-316-r77-ticket.md` -> `https://github.com/jsongalvez/company_app/issues/261`; verify with `scripts/wayfinder-verify-child.sh 180 261`.
- `scripts/wayfinder-create-child.sh 180 task "Build: preserve session-base-rate ownership on UUID retries" docs/agents/wayfinder-316-r78-ticket.md` -> `https://github.com/jsongalvez/company_app/issues/262`; verify with `scripts/wayfinder-verify-child.sh 180 262`.
- Verification: `scripts/wayfinder-verify-child.sh 180 260` -> `Verified child #260: parent #180, label wayfinder:task`.
- Verification: `scripts/wayfinder-verify-child.sh 180 261` -> `Verified child #261: parent #180, label wayfinder:task`.
- Verification: `scripts/wayfinder-verify-child.sh 180 262` -> `Verified child #262: parent #180, label wayfinder:task`.

### R76 implementation evidence

Child #260 is claimed and implemented. Product-sale retries now validate Branch
Day, creator, and immutable request context before returning an existing UUID.
The service resolves valid retries before mutable day/product/session checks.
New sales claim UUID uniqueness with `insertIgnore` before inventory locking;
zero-row inserts reload and classify the committed winner, preventing cross-lock
UUID races. Only newly created sales recalculate commission and invoke audit;
sale audit fields now include the complete persisted snapshot. Sequential and
concurrent retry tests cover foreign creator, altered request, one sale, and one
inventory movement.

- Gate ledger `docs/gates/260-product-sale-ownership.md`: 2/2 PASS.
- Negative-control gate run: G1 failed before implementation because regression
  tests were absent; G2 passed existing static checks.
- Targeted `ProductSaleServicePostgresTest`: PASS.
- Full backend detekt/ktlint/test plus shared JVM compile, excluding known stale
  `:backend:publishOpenApiSpec`: PASS.
- Test-database cleanliness and `git diff --check`: PASS.
- P1-P4 review: first pass found HARD gaps in concurrent coverage, retry
  commission side effects, stale mutable preconditions, and incomplete audit
  snapshot. Fix batch added atomic insert classification, early retry lookup,
  created-result commission gating, complete audit fields, and concurrency test.
  Targeted re-review: zero HARD; remaining one-sighting SOFT about product
  snapshot comparison is accepted because product ID/quantity define request
  identity and persisted price/name are immutable snapshot values.
- No ADR needed; existing idempotency, audit callback, and DB-clock decisions apply.
