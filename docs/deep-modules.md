# Backend module router

Answers one question: **where do I start, and what seam do I cross?** It is
navigation metadata, not an implementation manual. Use it with the discovery
protocol in [`docs/agents/context-discovery.md`](agents/context-discovery.md):
resolve the owning module → open its anchors → search for edges → expand only on
evidence. Callers, importers, and tests are derived by code search on demand — never
listed here.

**Card fields:** `Public seam` lists only symbols another semantic module is intended
to cross — internal helpers, test hooks, and sibling-consumed repository functions
belong under `Search` instead. `Anchors` are candidate entrypoints: open only the one
matching the requested behavior.

```
routes (thin: parse → call one service command; capability before-filters)
  └─ service commands (one command = one transaction + its audit row, ADR-0024)
       ├─ Session · Attendance · Inventory · Product Sales · Finance · Relief
       │  · Dashboard · Users · Clients · Export …
       │      ├─► CapabilityService   (narrowest seam — one boolean)
       │      └─► BranchDayService    (deepest module — every operational/
       │                               financial write gates through it)
       └─ repositories (`*InTransaction` store ops; mutators open no transaction)
```

**Where depth lives:** Branch Day hides day-state semantics behind a handful of
functions; Finance hides the commission engine and the serializable remittance
workflow; Capability is narrow by design; Audit looks trivial but hides JSONB diffing,
flagging, and read scoping. The remaining top-level services (users, clients,
products/categories) are shallow CRUD-style
commands whose interface matches their implementation — a direct
service → repository → test path needs no deep-module ceremony. Repositories are
intentionally shallow.

---

## Branch Day

**Owns:** operational-day identity and state (`OPEN`/`PAST`/`REMITTED`), the 04:00 Asia/Manila rollover, lazy status evaluation (an OPEN day before today is PAST), editability/readability gates, remittance-driven day transitions.
**Anchors:** `branchday/BranchDayService.kt`, `branchday/BranchDayRepository.kt` (internal).
**Public seam:** `currentOperationalDate` · `resolveOrCreate` / `getToday` / `findToday` / `findByBranchAndDate` (resolve-or-create vs find-only discipline matters) · `findById` / `findByIdInTransaction` (find-only reads) · `lockDayInTransaction` / `findLockedDayInTransaction` (in-transaction lock seam, #536) · `requireBranchDayExists` / `requireBranchDayForBranch` · `checkBranchDayEditable` / `checkBranchDayEditableInTransaction` / `checkBranchDayReadable` · `evaluateStatus` · `markDaysRemittedInTransaction` / `releaseDaysFromRemittanceInTransaction` · `expirationUtc` · `manilaZone`.
**Depends on:** Capability (`EDIT_PAST_DAY`). Consumed by Session, Attendance, Inventory, Product Sales, Finance, Relief, Remittance, summaries.
**Expansion triggers:** changing rollover time/timezone or day-state semantics; touching `branch_day` schema/locking; a seam change here justifies enumerating consumers at expansion time via `rg 'checkBranchDayEditable|resolveOrCreate|findToday'` — not preloading them.
**Tests/authority:** pure functions (`evaluateStatus`, `assertEditableState`) are DB-free unit-tested; backend `AGENTS.md` "HTTP errors & day state"; `docs/architecture.md`.
**Search:** `checkBranchDayEditable`, `findToday`, `branch_day`.

## Capability (Authorization)

**Owns:** the grant model (GLOBAL/BRANCH/BRANCH_DAY contexts, validity windows, sources/priorities), the central check over `active_user_capabilities`, route-level enforcement helpers, branch read-window scoping.
**Anchors:** `authorization/CapabilityService.kt`, `authorization/CapabilityFilter.kt`, `authorization/CapabilityRepository.kt` (internal).
**Public seam:** `hasCapability` / `requireCapability` (+ `ForBranchDay` / `AnyContext` variants) · `GLOBAL_CONTEXT_ID` (nil UUID) · `CapabilityFilter.require*` family · `BranchReadScope.windowBranchIds` · grant seams on `AuthorizationGrants` (`grantReliefCapabilityInTransaction` / `deleteReliefGrantBySourceIdInTransaction` / `grantDelegateCapabilityInTransaction` / `closeDelegateCapabilityInTransaction` — workforce commands write grants only through these, supplying target user/branch/day and source identity while the fixed capability/source/context/priority stay authorization-owned (#606); the table and `GrantStore` stay internal to the owner). GLOBAL grants never satisfy day-scoped gates (#131 strictness); inventory is not relief-eligible (#157).
**Depends on:** nothing upstream semantically; consumed by nearly everything.
**Expansion triggers:** new context type or source type (schema + view + filter changes); window/priority semantics (`GrantPriorities`; `active_user_capabilities` view — baseline in V1, branch-derived leg widened by V21, OWNER GLOBAL read added by V25); changing which gate a route uses.
**Tests/authority:** ADR-0007 (route-level gates), ADR-0023; backend `AGENTS.md` "Authorization".
**Search:** `requireBranchOrBranchDayCapability`, `hasCapabilityForBranchDay`.

## Auth & Credentials

**Owns:** login, JWT issue/verify, durable revocation (persisted `jwt_revoked_at` boundaries checked in the authorization read), password hashing with timing-shield dummy hash, single-use credential tokens (invite redemption #350, password reset #353), per-IP rate limiting.
**Anchors:** `identity/JwtService.kt`, `identity/UserRepository.kt` (authorization + boundary), `identity/AuthService.kt`.
**Public seam:** `JwtService.generateToken` / `verifyToken` · `UserRepository.authorize` / `advanceRevocationBoundaryInTransaction` · `Password.create` / `verify` · `CredentialTokens.generate` / `hash` · `AuthService.login` / `logout` / `acceptInvite` / `requestPasswordReset` / `resetPassword`.
**Depends on:** Users (authorization lookup, revocation boundaries), Audit (token consumption rows).
**Expansion triggers:** JWT claims/expiry; `credential_token` schema; second-precision `iat` ambiguity rules (#492: same-second issuance stays denied).
**Tests/authority:** `PersistentRevocationPostgresTest`; token lifecycle comments in `AuthService`/`CredentialTokens`.
**Search:** `mintResetCode` (internal test seam for the reset flow), `RateLimiter.isAllowed`, `consumeIfLive`.

## Users & Roles

**Owns:** user lifecycle (idempotent deactivate/reactivate + persisted JWT revocation boundary), invite minting with re-invite recovery, full-replace role membership (SUPERUSER guarded both directions), user listing with assignments + roles.
**Anchors:** `identity/UserService.kt`, `identity/UserRepository.kt`, `identity/RoleRepository.kt`.
**Public seam:** `UserService` commands (`deactivate`, `reactivate`, `replaceRoles`, `mintInvite`, `listUsers`, `getRoles`).
**Depends on:** Auth (tokens, persisted revocation, hashing), Audit. Role→capability derivation lives in SQL (view union: V16-era GLOBAL leg, V21 branch-scoped leg, V25 OWNER GLOBAL read), not this module.
**Expansion triggers:** role-derived capability semantics (ADR-0023 + its #417/#431 amendments); deactivation/revocation interplay with the persisted boundary.
**Search:** `SUPERUSER_GUARD_MESSAGE`, `deactivateInTransaction`, `user_role`.

## Session

**Owns:** session aggregate — type ladder (`computeSessionType`: REGULAR→SECOND→SUBSEQUENT, mission/provincial variants), base-rate snapshot at create, optimistic-versioned updates, void/unvoid records, practitioner management (slot snapshots + parent version bumps), concerns + promotion, base-rate rotation, create preview.
**Anchors:** `session/SessionService.kt` (+ internal `SessionRepository` in same package) · `session/SessionRoutes.kt` · `session/SessionBaseRateService.kt`.
**Public seam:** `SessionService` commands (`create` / `updateStatus` / `updateFinalPrice` / `voidSession` / `unvoidSession`) · `computeSessionType` (pure) · `previewSession` · practitioner ops via `SessionPractitionerService` · concern ops via `SessionConcernService` (catalog `ConcernService.listAll` stays distinct) · rate ops via `SessionBaseRateService` · `SessionReads.findById` / `findByIdInTransaction` / `hasActivePendingSessionInTransaction` (commerce/remittance/dashboard/authz/client reads).
**Depends on:** Branch Day (gates + find-only gated-day handoff #157), Client (row lock + existence reads via the `ClientReads` seam; one-PENDING guard stays session-owned), Workforce membership/slot facts via the `WorkforceReads` seam (ACTIVE member check #366, unended slot lookup).
**Expansion triggers:** version-bump mechanics (`incrementSessionVersion` count-0 rule); walk-in status CHECK constraint; `idx_client_one_pending_session` backstop; `active_session_voids` view consumers (commission, remittance pickers, scheduler, dashboard).
**Tests/authority:** `docs/engines.md`; backend `AGENTS.md` "Sessions".
**Search:** `computeSessionType`, `SessionReads` (cross-owner boundary; `SessionRepository` direct imports from other owners stay banned), `VersionMismatchException`, `session_void`.

## Workforce (Attendance · Assignments)

**Owns:** clock-in/out, `branch_day_assignment` upsert, relief determination at clock-in, clocked-in-at reads feeding commission/dashboard eligibility; branch assignments, active membership directory and the lock+check duty-cutoff seam.
**Anchors:** `workforce/AttendanceService.kt` (+ internal `AttendanceRepository` in same package) · `workforce/UserBranchAssignmentService.kt` (+ internal `UserBranchAssignmentRepository`, `BranchMemberRepository`) · `workforce/ShiftGuard.kt` (duty-cutoff seam).
**Public seam:** `clockIn` / `clockOut` · `mark` / `rosterToday` · `findUsersClockedInAt` · `hasActiveClockIn` · `findUsersByBranchDayId` · assignment `create` / `remove` / `updateSlot` / `swapSlots` / `listActiveMembers` · `WorkforceReads.hasActiveMember` / `findAssignmentSlotInTransaction` / `findAttendanceWindowsInTransaction` (+ `AttendanceWindow`) · `ShiftGuard.ensureNoActiveClockIn` / `ensureRetractionAllowed` (relief cutoff).
**Depends on:** Branch Day (today resolution), Commission (recalc joins the clock-in/out transaction).
**Expansion triggers:** `idx_one_active_clock_in` uniqueness; relief-flag semantics consumed by commission eligibility and dashboards.
**Search:** `isRelief`, `findUsersClockedInAt`, `clockOutInTransaction`.

## Inventory

**Owns:** per-branch stock cards with optimistic versioning, movement ledger with sign/notes rules per movement type, low-stock alerts (per-product reorder points).
**Anchors:** `commerce/InventoryService.kt`, `commerce/MovementType.kt`, `commerce/BranchInventoryRepository.kt` (internal).
**Public seam:** `recordMovement` / `ensureCard` / `getStock` / `getLowStockAlerts` / `getMovementHistory` · `MovementType` sealed class.
**Depends on:** Branch Day gate (via `StockValidator`, inside the command tx), Products, Branch existence. Not relief-eligible — branch-scoped only (#157 scoping trap documented in routes).
**Expansion triggers:** card version conflicts; movement reason enum/schema; FOR UPDATE materialization pattern (terminal-op rule).
**Search:** `requireCardForUpdateInTransaction` (consumed by Product Sales' sell transaction), `currentStock`, `insertIgnore`.

## Product Sales

**Owns:** `sell()` — atomic sale insert + locked stock decrement + sale movement + audit + commission recalc in one transaction; price/commission frozen at sale time; non-voided-sale reads.
**Anchors:** `commerce/ProductSaleService.kt`, `commerce/ProductSaleRepository.kt` (internal).
**Public seam:** `sell()` · `CommerceReads.findSaleById(InTransaction)` / `findNonVoidedSalesByBranchDayInTransaction` (commission/remittance reads).
**Depends on:** Branch Day gate, Inventory card lock, Products, Session (same-day linkage validation), Commission.
**Expansion triggers:** idempotent-retry ownership classification (day mismatch = 404 vs field mismatch = 409); stock guard ordering.
**Search:** `insertSaleInTransaction`, `commissionAmountAtTime`.

## Finance (Day entries · Commission · Remittance)

**Owns:** day-entry mutation policy — expenses (soft-delete/restore + reason), compensations (paying/work branch authority + uniqueness), allowances (excluded from P&L) — each with idempotent commands, day locks and audit atomicity · the commission engine (eligible set = clocked-in-at-sale ∓ manual inclusions; split at scale 4; replace-per-day splits; `recalculateInTransaction` store-side entry for enclosing commands) · the remittance workflow (DRAFT→SUBMITTED under SERIALIZABLE isolation, immutable SESSION financial snapshot, 48h undo window on the DB clock, day transitions via the Branch Day boundary, overlap-exclusion mapping).
**Anchors:** `finance/ExpenseService.kt` (+ internal `ExpenseRepository`, record + internal `ExpenseTable` in same package) · `finance/CompensationService.kt` (+ internal `CompensationRepository`) · `finance/AllowanceService.kt` (+ internal `AllowanceRepository`) · `finance/FinanceReads.kt` · `remittance/RemittanceService.kt` (pure rules in `remittance/RemittancePolicy.kt`).
**Public seam:** finance `FinanceReads.findExpenseById(InTransaction)` / `findCompensationById(InTransaction)` (authz + compensation-gate reads; service-to-service, no allowlist) · remittance `submit` / `undoAt` / `createDraft` / `updateHeader` / `addLine` / `removeLine` / `addDayBreakdown` / `getDrift` / list+picker reads · commission `recalculate(InTransaction)` / `splitCommission` / `liveCommissions` / `createManualInclusion` / `getByBranchDayId`.
**Depends on:** Branch Day (locks, mark/release days), Session + Product Sales (line-source validation, gross sums), Workforce attendance windows via the `WorkforceReads` seam (commission eligibility); finance day-entry rows feed remittance sums via direct table reads inside the remittance internal store (deliberate; #546 owns the finance move, no new seam), capability/compensation gates via the `FinanceReads` seam.
**Expansion triggers:** V13 trigger carve-out for snapshot deletion; `no_remittance_overlap` exclusion constraint; BigDecimal no-rounding rule (remittance sums) vs scale-4 splits; drift read semantics.
**Tests/authority:** `docs/engines.md` (exact pseudocode); `RemittancePolicy` is DB-free unit-tested; k6 `remittance-race-test.js` covers the serializable race.
**Search:** `recalculateInTransaction`, `assertWithinUndoWindow`, `remittance_financial_snapshot`.

## Relief

**Owns:** two paths to day-scoped edit access — broadcast Relief Request (PENDING→GRANTED/DENIED/CANCELLED; one live request per requester/day via partial index) and branch-initiated Relief Invite (PENDING→ACCEPTED/DECLINED/RETRACTED; accept writes the grant immediately) — plus outcome notifications, expiry job (04:05 Manila) and reminder job (07:00), medical-mission delegates. Grant rows themselves are authorization-owned (`authorization/GrantStore` behind `AuthorizationGrants`); workforce commands pair status flips with the seam in one transaction.
**Anchors:** `workforce/relief/ReliefAccessService.kt`, `workforce/relief/ReliefInviteService.kt`, `workforce/relief/ReliefAccessRepository.kt` (internal).
**Public seam:** `ReliefAccessService` commands (request/grant/deny/cancel/list) · `ReliefInviteService` commands (create/accept/decline/retract/search) · `MedicalMissionDelegateService.assignDelegate` / `revokeDelegate` · `ReliefNotifications.*` (command-transaction broadcasts).
**Depends on:** Branch Day (day-open gate, `expirationUtc`), Workforce membership/cutoff (`BranchMemberRepository`, `UserBranchAssignmentRepository`, `ShiftGuard`), Capability view, Notifications.
**Expansion triggers:** partial unique indexes `idx_one_live_relief_request` / `idx_one_pending_accepted_invite`; job re-run safety via UNIQUE `(dedup_key, user_id)` (#508); accepted-invite revocation follow-up (#363 pending).
**Tests/authority:** relief-cluster rules live in code comments (#159/#352/#357/#358); backend `AGENTS.md` day-scoped gate section.
**Search:** `AuthorizationGrants`, `hasPendingOrAcceptedInvite`, `ReliefNotifications.`

## Audit

**Owns:** append-only `audit_log` rows (JSONB old/new diffing, flagged+reason vocabulary, acknowledgment excluding the editor), scoped reads (branch window + branchless-table policy + NULL-branch fallback), the audited-table registry.
**Anchors:** `audit/AuditLog.kt` (public append seam), `audit/AuditLogStore.kt` (internal table/query internals), `audit/AuditLogService.kt`, `audit/AuditLogReadScope.kt`.
**Public seam:** `AuditLog.recordInsert` / `recordUpdate` / `recordDelete` / `record` + `redactClientNamesInTransaction` — must run inside the caller's command transaction · browse/find/acknowledge reads via `AuditLogService` · `AuditLogTableRegistry.tables` (register every newly audited table there or it is invisible to the UI).
**Depends on:** Capability (read windows via `BranchReadScope`).
**Expansion triggers:** read-scope policy changes (`BRANCHLESS_POLICY` map, global-view fallback); cursor format; flag acknowledgment rules.
**Tests/authority:** ADR-0014 (audit field mapping), ADR-0019 (before-state capture); backend `AGENTS.md` "Audit logging".
**Search:** `AuditLogTableRegistry`, `auditFields`, `recordUpdate(`.

## Dashboard & Notifications

**Owns:** universal post-clock-in dashboard read (enrichment aggregation + live commission replication mirroring the engine), notification-as-authorization session detail (#152), the notification store (occurrence-keyed uniqueness via `(dedup_key, user_id)`), appointment reminder sweep, scheduler lifecycle.
**Anchors:** `session/dashboard/DashboardService.kt` (+ internal `DashboardRepository`, records + `mapDashboardSession` in same package) · `session/dashboard/DashboardRoutes.kt` · `notification/NotificationService.kt` (+ internal `NotificationRepository`, record + internal `NotificationTable` in same package) · `notification/NextAppointmentScheduler.kt` (+ internal `NextAppointmentRepository`) · `notification/NotificationRoutes.kt` · `app/SchedulerLifecycle.kt` (#551 composition-supplied jobs).
**Public seam:** `DashboardService.getToday` / `getSessionDetail` · `NotificationService.listUnread` / `browseHistory` / `countUnread` / `markRead` / `markAllRead` · `NotificationReads.existsForSessionAndUser` / `findUsersBySource` + `NotificationAppender.append` (relief/session-detail reads + broadcasts; service-to-service, no allowlist).
**Depends on:** Attendance (dashboard gate), Commission (live eligibility replication), Sessions (detail + reminders), Branch Day (scheduler operational dates).
**Expansion triggers:** occurrence-identity dedup keys (appointment session+target date, relief event+source, revocation `:direct` audience split) under UNIQUE `(dedup_key, user_id)` (#508); ownership-in-WHERE read-state rule (#141).
**Tests/authority:** backend `AGENTS.md` "Sessions" (dashboard + detail gate exceptions).
**Search:** `mapDashboardSession`, `existsForSessionAndUser`, `insertBatch`.

## Client

**Owns:** client CRUD, trigram+ILIKE search, anonymize (soft-delete under row lock with a PENDING-session guard). Shallow module — direct service/repository/test path.
**Anchors:** `client/ClientService.kt` (+ internal `ClientRepository` in same package) · `client/Client.kt` (record + internal table) · `client/ClientRoutes.kt`.
**Public seam:** `create` / `search` / `findById` / `update` / `anonymize` · `ClientReads.acquireLockInTransaction` / `findById` (session lock/read seam).
**Depends on:** Sessions (pending-session guard via `acquireClientLock` + `hasActivePendingSessionInTransaction`).
**Expansion triggers:** anonymization column nulling set; search ranking (`similarity()` threshold).
**Search:** `anonymizeInTransaction`, `similarity(`.

## Reporting

**Owns:** summary projections, cursors, report assembly and CSV/PDF rendering — daily/monthly/all-time/branch-type reads over `daily_sales_summary` / `monthly_remittance_summary` views; results never stored. Monthly row mapping is single-sourced (`toMonthlyRemittanceSummary`).
**Anchors:** `reporting/DailySalesSummaryService.kt` (+ internal `DailySalesSummaryRepository`, record + internal `DailySalesSummaryView` in same package) · `reporting/MonthlyRemittanceSummaryService.kt` (+ internal `MonthlyRemittanceSummaryRepository`, shared monthly mapper) · `reporting/ExportService.kt` (+ internal `ExportRepository`, `BranchTypeMonthlySummary`) · `reporting/CsvExporter.kt` · `reporting/PdfExporter.kt` · `reporting/ExportContract.kt` · `reporting/DailySalesSummaryRoutes.kt` · `reporting/MonthlyRemittanceSummaryRoutes.kt` · `reporting/ExportRoutes.kt`.
**Public seam:** `getDailySummary` / `browseDailySummaries` · `getMonthlySummary` · `exportDaily` / `exportRange` / `exportMonthly` / `exportAllTime` / `exportByBranchType` → `ExportResult(bytes, contentType, fileName)`.
**Depends on:** Branch Day (existence + day reads via `BranchService`/`BranchDayService`), Branch existence, Capability read windows; export branch-type summaries join `branch` rows inside the internal store (deliberate remaining edge).
**Expansion triggers:** route-gate shape — the wildcard before-filter `/api/branches/{branchId}/export/*` is load-bearing (#114 lesson, fourth occurrence); new summary source; cursor format (`date|branchDayId` opaque).
**Search:** `ExportFormat`, `sendFileResponse`, `encodeDailySummaryCursor`.

---

## Platform cross-cutting

Not a semantic module — read only when the ticket touches it directly:
`Main.kt` (explicit composition root: Javalin wiring, `/api/*` JWT filter, exception→status mapping, scheduler job wiring — #551),
`exception/ServiceExceptions.kt`, `api/routes/RoutesUtil.kt` (parsing/keyset limits),
`database/DatabaseConfig.kt` + `DatabaseHealth.kt`, `app/AppConfig.kt` (#551 startup composition),
`http/KotlinxSerializationMapper.kt` + `http/openapi/*` (canonical contract, projector, export — #551),
`observability/*` (incident packets/delivery, metrics, feedback/metrics adapters, slow-query reads — #551),
`logging/*` converters (tracing stays distinguishable from observability — #551),
`repository/model/*` Exposed tables/views (schema work only —
`V1__full_schema.sql` (squashed baseline, #370/#461/#548) and `V2` seeds in
`backend/src/main/resources/db/migration/` are the current-schema authority).

## Adding a module

New feature cluster → add a card here with the fields above (Owns / Anchors / Public
seam / Depends on / Expansion triggers / Tests-authority-search). Keep anchors ≤5;
derive consumers and tests by search, not enumeration. Register audited tables in
`AuditLogTableRegistry`; wire the route object in `Main.initializeJavalin`.
