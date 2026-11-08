# Rebuild plan

Status: Phase 1, units 1-3 done (guards; tokens and contrast; the kit with Today in layout B), and unit 4's day-one checks are in. Unit 4 started again on Nov 8 as [Revisions](#revisions) 6: the role rules are a table in `authorization/Policy.kt`, and grants come from it. Today uses layout B (Day sheet). Sizes stay as drawn: body 15 px, rows 16 px, tap targets 44 px. Where to start: [Handoff](#handoff).
The repo is public: this file quotes no one.

## Goal

Make the app easier to use on a clinic phone and cheap to change when the roles change. The look stays calm and plain:
rectangles, warm greys, dark text, no motion. Every part of the rebuild is proven by checks that stay in the repo.

## Lessons, and the decision each one drives

Evidence says checked (ran or read it), inferred, or guessed.

| # | What happened | Evidence | Decision |
|---|---|---|---|
| 1 | 24 commits touched the access rules in two weeks (rule changes and refactors of the same code). They touched a median of 17 files each, because the rules live in a 187-line SQL view with role names in its WHERE clauses, the Kotlin `Act`/`Reach` enum, the web mirror `grants.ts`, the pinned `route-access.txt`, and role names in 14 backend files (34 places). | git log since 10-25 (checked); `V12__mission_staff.sql`; grep (checked) | One policy table, as data. The database stores facts only (roles, memberships, relief days, helpers, shifts). Server and web read the same table. Tests are generated from it. |
| 2 | Screens showed stale data after a change; each fix patched one page or one cache key. The web grew six data layers: query cache, key builders, a write-to-key table, the outbox, a Today store, an HTML snapshot. | `docs/patterns.md` row 2 (4 reports, 4 fixes, checked); `web/src/lib` (checked) | One local store of documents. Every write goes through one outbox and the server answers with the new document. Screens only read the store. |
| 3 | Taps felt slow at 200-300 ms. Optimistic writes were added one feature at a time (clock, save, mark). | chat Oct 29 (checked); `clock-snappy`, `optimistic-save` tests | Optimistic by construction: a write changes the local document first; the answer replaces it. |
| 4 | A refresh that blanks or redraws the screen breaks the calm. Fixed with an HTML snapshot and keep-last-screen rules. | chat Oct 29 (checked); `reload-calm`, `clock-calm` tests | A view never goes back to empty once it has data. "Updating..." shows only past 300 ms. The blank-frame film stays a test. |
| 5 | Offline saves were lost or doubled (5 reports, 9 fixes) until each save carried an id and a TLA+ model checked the queue. | `docs/patterns.md` row 5 (checked); `tools/tla/outbox` | Keep ids and the model. Every write, online or not, goes through the same queue. |
| 6 | The phone layout broke 12 times: bars wrapped, items clipped, inputs zoomed. The web has 129 hand-set pixel sizes and 33 style blocks (Shell alone 255 lines). | `docs/patterns.md` row 1 (checked); grep (checked) | Tokens only. A lint refuses raw colours and sizes. A phone check runs every page at 392x718: no sideways scroll, bars stay one row, targets at least 44 px, inputs at least 16 px. |
| 7 | Filters, tabs and Back were lost on reload (5 reports). | `docs/patterns.md` row 3 (checked) | All view state lives in the address. A router test reloads each page and compares. |
| 8 | A date picker was the wrong way to find old records (5 reports). | `docs/patterns.md` row 4 (checked) | Lists show newest first with "Show older". Range pickers only in reports. |
| 9 | Screens showed controls the server refused, and made calls the server refused. | `docs/patterns.md` Done row (PR #947, checked) | The web interprets the same policy table; a shared vector file tests both interpreters. |
| 10 | Rules the security audit found missing (pay only for that day's staff, clock-in needs a working role) were added one service at a time. | chat Oct 31 (checked, summary) | Invariants over the policy table, run as property tests: no write for Accountant, pay only for that day's staff, no self role change, Superuser only by Superuser. |
| 11 | Speed: rounds matter, not call counts; phone connections died after 135-180 s idle; the pool size cut p95 from 438 to 145 ms; search and recent clients slowed with data until indexed. | perf-playbook evidence (checked, from memory of the runs) | Fast reads by data shape, no server caches. A statement budget per route test and the DB load test stay. |
| 12 | Browser tests flaked on timing (remittance race, concerns, user-branch). | chat Nov 5-6 (checked, summaries) | Tests wait on app states (a row, a version), never on time. |
| 13 | A new practitioner with no branch was confused and got refused calls. | feedback Nov 6 (checked) | Every role and state has a designed empty state, including "no access yet" and "no branch yet". |
| 14 | Each fix brought its own styling, and the UI drifted. | inferred from 6 | Components own styling. Pages compose components and lay them out with tokens. |

## Architecture

### Keep

The stacks (Kotlin, Javalin, Exposed, Postgres, Flyway; Svelte 5, Vite). The infrastructure: `tools/push.sh` gates, CI, deploy gate, `verify.sh`, fuzz, tracing (`traced`, `trace_span`), telemetry recorder, service worker and its update rules, the outbox TLA+ model, the word list, the comment rules. The domain rules in the services (visit types, prices, commission split, remittance snapshots, day states) and their tests. They were debugged over weeks; rewriting them buys nothing the user asked for.

### 1. Access: one policy table

- Facts in the database: `app_user.roles`, `membership(user, branch, from, to)`, `relief_day(user, branch_day)`, `helper(user, branch, role_there)`, `shift(user, branch_day, in, out)`.
- Standing at a place, computed per request in one query: `MEMBER`, `HELPER(role)`, `RELIEF`, `NONE`, plus the day's state (`OPEN`, `PAST`, `REMITTED`) and whether the person is on duty.
- `Policy.kt`: a list of rows `Action -> [Rule(roles, standings, dayStates, onDuty, reason)]`. Plain data, no lambdas, so it renders to JSON for the web, to a Markdown matrix for review, and to test expectations.
- `Access.allows(facts, action, place)` interprets the rows (about 30 lines). The web's `can()` interprets the same JSON (about 30 lines). A vector file generated by the server is run by both.
- Rank rules (who may give which role) become a column of the table: `assignable[role]`.
- Routes keep declaring their gate, now as an `Action`. The route test runs every route as every persona at every standing and day state and expects exactly what the policy says. Changing a role is a row edit, a regenerated matrix to review, and green tests.
- Deleted: the `active_user_capabilities` view, `capability`, `role_capability`, `user_capability`, `CapabilityRepository`, `Reach`, `grants.ts`, `access.gen.ts`, role names outside the table (a test fails on any).

Default chosen: the table lives in code, reviewed and tested, not editable in the app. A change ships in minutes. An in-app role editor is possible later because the table is data.

### 2. Client: one store, one outbox, views

- `store`: documents by typed key (`day:<branch>:<date>`, `client:<id>`, `finance:<branch>:<month>`, ...), each with the server's version. Kept in IndexedDB per user, so the app opens on the last screen with no signal.
- `outbox`: every write, online or offline, as an op with its own id. Ops apply to the local document at once (optimistic) through one pure function per op kind.
- A view is the server document with the pending ops applied. A refused op stays visible, marked, with Retry and Throw away.
- Reads revalidate in the background; the screen never empties; "Updating..." past 300 ms.
- During the move, a small bridge marks the old cache keys stale when a new-store write lands, so unmoved pages stay fresh. It is deleted with the last old page.

### 3. Server API

- Day document: `GET /api/days/{branch}/{date}` returns everything Today shows, with a version and the caller's standing there.
- Every write that changes a day answers with that day's new document (already true for save session and clock-in). The client never guesses what went stale.
- Other areas keep resource routes, reshaped page by page as the new screens need them; each write answers with the documents it changed.
- Statement budget per route: a test fails when a route runs more statements than its budget (N+1 guard).

### 4. Schema

- Access tables become facts (see 1) in a V13 migration.
- Before launch, once nothing else changes the schema, squash V1-V13 into one readable baseline. Prod gets a fresh dump first and is reset (zero real data). The migration-equivalence rule in `AGENTS.md` changes in the same commit.

### 5. Design system

- Tokens in one file, `web/src/ui/tokens.css`: colour (light and dark), space (4, 8, 12, 16, 24, 32), type (13, 15, 16, 17, 20, 30), line, focus. Square corners and no motion stay.
- Phone base text 15 px (today 13 px). Touch targets 44 px. Inputs 16 px so Android doesn't zoom.
- About ten components in `web/src/ui/`: Page, TopBar, Button (primary, plain, quiet, danger), Row, Section, Stat, Notice, Sheet (bottom sheet on phones), Field, Segmented, Badge.
- One accent per screen. Links read as text. Status words carry meaning with a small coloured mark, never colour alone.

## Proof plan

Each check stays in the repo and runs in CI. Baselines come from the current app before any change.

| Part | Check | Kind |
|---|---|---|
| Policy | Invariant property tests over the table (no Accountant writes, pay only for that day's staff, no self role change, Superuser only by Superuser, clock-in needs a working role) | unit test |
| Policy | Route x persona x standing x day-state test, expectations generated from the table | integration test |
| Policy | Shared vectors: the Kotlin and TS interpreters agree on every case | unit test (both sides) |
| Policy | The Markdown matrix is up to date | CI diff check |
| Policy | No role name outside `Policy.kt` and the migration that creates roles | grep test |
| Store | Each op's local apply matches the server's answer on fixtures | unit test |
| Store | Replay is idempotent; the TLA+ model passes with the new queue | unit test, TLC |
| Store | Optimistic, offline, refused, clash, calm: the carried browser tests | browser test |
| API | Request rounds per flow (sign-in 1, open 1, save 1, clock 1) | browser test (`calls.e2e`) |
| API | Statement budget per route | integration test |
| Design | No raw colour or size outside the token file; every `var()` names a property that is set | lint (`web/tests/unit/design-lint.test.ts`) |
| Design | Contrast of the token pairs used for text, light and dark, and of each phone page's rendered text (4.5:1 text, 3:1 large text and the focus ring) | unit test (`contrast.test.ts`), browser test (`phone.e2e.ts`) |
| Design | Every page x persona at 392x718: no sideways scroll, one-row bars, 44 px targets, 16 px inputs | browser test |
| Design | Phone screenshot set of every page, kept as a CI artifact | script |
| Design | Word list on screen | `copy.test.ts` (kept) |
| Perf | First frame from saved state, blank frames 0 | film (kept) |
| Perf | Tap to row on screen under 100 ms with the network held | browser test |
| Schema | Builds from empty; DB load test at 1x-1000x on the hot routes | script (kept) |

## Today options (the one taste call)

Prototype: `.audit/rebuild/today-proto/index.html` (throwaway, made-up data). Shots: `.audit/rebuild/today-proto/shots/`.

| Option | Shape | First tappable row on a 392x718 screen (measured) |
|---|---|---|
| A Queue | Duty line, three numbers, then Waiting, Booked, Done on one scroll; New session and Product sale docked at the thumb | 289 px |
| B Day sheet | Income first in large type, sales and commission, duty card, then Here now and a time-ordered log; three docked buttons | 466 px |
| C Find first | A search box first, then Here now / Booked / Done as tabs; totals in a docked footer | 289 px |

All three move Clock in/out from the top bar onto Today, move theme and full screen into the menu, and drop the Refresh button and the five filter tabs.
Picked: B. The sizes stay as drawn; every button, row buttons included, gets the 44 px tap height.

Built (unit 3), where the drawing met real data and behaviour:

- The top bar keeps the client search row and, off Today, Home: both exist today and the drawing left them out. Open question for the user.
- "On duty since 8:02 AM" and the log's done times need times the API does not send yet; unit 4's day document adds them. Until then the log is ordered by arrival.
- The log holds sessions only; clock-ins and sales join it with the day document.
- Booked visits keeps its name: the list holds upcoming visits on any day, not only today's.
- The sales cell keeps "Product commission": the number is commission on product sales only.
- Rows offer Done (waiting) and Undo (logged). No-show and Cancel live on the session page, as do illnesses.
- The Refresh button is gone; R refreshes on a keyboard, and opening Today reads it again.
- Who's clocked in folds under the duty card; its per-person buttons stay out of the card, so the card's own Clock in/out is the only one there.

## Order of work (after the pick)

Each unit ends with a failing-then-passing test, `tools/push.sh`, GitHub runs checked by head SHA, `app-shipcheck`, and before/after phone shots.

1. Guards first: design lint (`web/tests/unit/design-lint.test.ts`, raw-value counts that only shrink) and the phone check (`web/tests/phone.e2e.ts`, every role on every menu page, with old pages on `web/tests/phone-legacy.json`).
2. Tokens and the contrast checks.
3. The component kit, built with its first page: Today in layout B, with the new top bar and menu, on today's data code. Today leaves the legacy list. A kit no page uses yet has nothing for the phone check to test.
4. Policy table: day-one checks first; then fixtures as facts, V13 `relief_day`, the table in `Access` and the web's rights in one change; generated tests; delete the view and capability code. (Was unit 5; see Revisions 1 and 5.)
5. The data layer as revised (Revisions 2): read and change sets from the database replace `writes.ts`; the today-screen document gains times and past days. Today's browser tests are the safety net.
6. Then pages by use: New session and Session, Clients, Expenses & pay, Remittances, Stock and Products, Relief, Alerts, Profile and My shifts, Branches and Users, Reports and Change history, Problem reports.
7. Delete the bridge and the old data layers; squash migrations; reset prod after a fresh dump.

Today comes before the store and the policy so the new look reaches the phone first, and so the store is shaped by a real screen's needs. Today's data wiring changes once more in unit 4; its view does not.

Done when: every page is on the kit with an empty lint allow list; role names live only in the policy table; the old data-layer files are gone; every page passes the phone check for every persona; the full browser suite is green on GitHub; prod runs it with `/health` UP.

## Revisions

Nov 7, after unit 3. Before each unit, four questions are put to this plan: reduce the problem to what it cannot do without and rebuild from there; what would a principal architect rewrite before starting; which single test breaks the design on day one (written into the repo before the unit is built); which higher-level abstraction makes the problem disappear.

1. **The policy table comes before the data layer.** Roles changing is the stated need, and the table deletes the most code: the capability view and tables, `Reach`, `grants.ts`, the gates each page and route keeps. Every read checks access, so the data-layer work below has fewer tables to reason about once those are gone.
2. **The data layer keeps the cache and the outbox and drops the hand-kept invalidation.** Both work. `calls.e2e.ts` pins exact rereads ("saving an expense re-reads only the expenses and the day's totals"), and the TLA+ model checks the queue, so a store and outbox v2 would rewrite working parts. The part that breaks is `web/src/lib/writes.ts`: 30 hand-kept rows of what each save makes stale. A forgotten row is a stale screen (lesson 2). Instead, each answer names the tables its request read (`X-Reads`) and changed (`X-Changed`). The cache marks stale every entry whose reads meet a change and rereads the entries on screen. `writes.ts`, its `wrote()` calls and the planned bridge are deleted.
   - Premise, checked on Postgres 18.6 with the verify copy: `pg_stat_xact_user_tables` reports a transaction's scans and row changes. A count over the `daily_sales_summary` view showed the base table it scanned. A second transaction on the same connection still showed the first one's counts, because they last until the backend flushes them. So the tracker diffs a snapshot from a transaction's first statement against one taken before commit, and fails closed: with no snapshot, the answer reads every table.
   - Some tables are touched by requests for their own bookkeeping, such as sign-in tokens, telemetry, trace spans and the audit log. Each one goes on a list with its reason, or every answer would make every entry stale. Which tables belong there is inferred, not yet checked.
   - Rejected: one global data version. It is simpler, but after any save it would reread every screen and break the pinned rereads.
3. **Tests that break each design on day one.** Write them first, as a ratchet where the code is not there yet:
   - Policy: a route or screen deciding access without the table. Checks: every registered route declares an `Action` the table names (a test over Javalin's route list, with an explicit public list). Role names outside `Policy.kt` and the migration that creates roles may only go down, to zero at the end of the unit. The ratchet must define its matches exactly: role names also appear as plain words and column names.
   - Data: (a) a read that changes a tracked table, so each answer would make its own entry stale and loop. Check: call every GET route as each persona on seed data and expect an empty `X-Changed`. (b) A write that changes what a screen shows, but whose change set misses that screen's reads. Check: for each scripted write, read every GET route before and after on seed data, and expect every changed answer to have reads that meet the write's changes.
4. **The day document already exists.** `GET /api/branches/{id}/today-screen` is today's day document, shared by Today's three loaders. In the data unit it gains clock-in and done times and a date for past days; there is no new endpoint.

5. **Nov 7, unit 4 start. The tests must speak in facts before the table goes in.** A draft of the table and its wiring (`.audit/rebuild/policy-wip.patch`, local to the VPS checkout) put `Access.allows` on the table. `AccessTableTest` then refused relief and helpers everywhere, and `RouteAccessTest` and the statement budget moved (checked). The cause, checked: 118 fixture calls in 56 test files build access from grant rows (`IdentityFixtures.grantCapability` with source `SYSTEM`, and the `AuthorizationGrants` writers with no `medical_mission_delegate` row behind them). The facts model has no such rows. The shapes are: edit at a branch 34, view at a branch 17, edit on a day 15, view everywhere 14, others 18. So unit 4 does three things in one change:
   - A V13 `relief_day` fact takes over from relief grant rows.
   - Fixtures set facts (member, relief day, helper, role) instead of grant rows, by codemod. Each view-only-at-a-branch case is judged by hand, since a member can also edit.
   - The table is wired into `Access`, and the web's rights come from the same facts. A server on the table beside a web on the old view reads twice and can disagree. The budget rose by one statement on sign-in, start, clock-in and clock-out, inferred to come from that double read.
   - The table's shape, from the draft: `Act` rows of `Rule.Everywhere(roles)`, `Rule.Here(roles, standings)` and `Rule.Somewhere(roles, standings)`. Standings are MEMBER, HELPER and RELIEF. At a place, a person's roles count, or a helper's picked role. Staff without an assignment is data (`STAFF_WITHOUT_ASSIGNMENT`: Superuser at every branch type, Owner and Manager at tours and missions). One statement reads a person's facts, kept per request.
   - Decided: the web gets rights the server computed, not a second interpreter. The server sends three maps: allowed acts everywhere, at each branch the person stands at, and on each relief day. These answer every place. A day with no relief has its branch's rights, and a branch with no standing has the everywhere rights. With one interpreter, the shared vector file and the TS interpreter are not needed.
   - The day-one checks are in. Role names outside `Policy.kt` (50 in 13 files, `tools/quality/role-names.py`, run by `tools/push.sh` and `quality.yml`) and routes whose gate hides its action (127, `RouteGateTest`) may only go down. Both failed at their targets on the old tree, then passed with today's counts as the baseline.

6. **Nov 8, unit 4 again: the role rules move first, the grant rows stay.** An earlier run of Revisions 5 was reverted. It read facts with SQL text, raised the statement budgets with no reason given, and took Accountant out of tests. This time behaviour is pinned before anything moves, and the rows tests write stay valid:
   - `AccessMatrixTest` pins `Access.allows` for every act, 18 people and 6 places, each person's grants as `/api/me/capabilities` lists them, and `/api/roles` (`backend/src/test/resources/access-matrix.txt`, rewritten with `WRITE_ACCESS_MATRIX=1`).
   - `Policy.kt` holds the roles as an enum. Each role has what it grants everywhere, what it grants at a branch it works at, the branch types it works at unassigned, and whether it works (Onboarding does not). A working person is a member where they work. An edit row in `user_capability` carries a view, and a relief day or a helper seat carries a member's rights. This replaces legs (a2)-(f) of the `active_user_capabilities` view and the `role_capability` seed. Planting a wrong row fails the matrix (checked).
   - One typed Exposed statement reads a person's facts: roles, the branches their roles reach unassigned, open assignments and `user_capability` rows in force. The statement count per route stays where it was. Relief holders and alert receivers are typed queries in `CapabilityRepository`. Nothing reads the view or `role_capability` any more, and V13 drops them.
   - `tools/quality/check-raw-sql.sh` fails on SQL text in server code.
   - Still Revisions 5: relief and helpers as facts (V13 `relief_day`), test fixtures as facts, and the web's rights from the server. The grant rows stay the facts for relief and helpers until then.

## Handoff

Nov 8, unit 4 as Revisions 6. For a fresh agent; read `AGENTS.md`, then this file.

- Pushed and live, green on GitHub: `625c0a88` (matrix test and raw-SQL check), `972cf6f0` (the policy table), `5afc5cc0` (V13 drops `active_user_capabilities` and `role_capability`; prod dump `company_app-2026-11-08_093244-pre-v13-policy.dump`). Behaviour unchanged: the matrix file only gained its `/api/roles` section.
- Next step: Revisions 5's facts, one at a time with the matrix as the net. First, V14 `relief_day` replaces relief's `user_capability` rows (`GrantStore.grantReliefCapabilityInTransaction`, `reliefHoldersInTransaction`). Then helpers, then test fixtures, then the web's rights from the server.
- Commands: `./gradlew :backend:test --tests '*AccessMatrixTest'` (about 30 s); `WRITE_ACCESS_MATRIX=1` to rewrite and read the diff; `bash tools/quality/check-raw-sql.sh`; `python3 tools/quality/role-names.py`; `tools/push.sh origin master`; `gh run list --commit SHA`; `app-shipcheck SHA`. Read failures from `backend/build/test-results/test/TEST-*.xml`.
- Gotchas: Exposed left-joined columns are typed non-null; read them with `row.getOrNull(col)`. A new cross-owner table read needs a line in `ProjectionGrantRegistry` (`SemanticOwnershipArchitectureTest`). `user_capability` has a `valid_range` check, so a fixture for ended relief writes the row, then moves `valid_from` and `valid_to` back. Self-test scripts that name repo-looking paths trip `check-references.sh`; build them from variables. `prod-snapshot.sh` can report a `trace_span` count mismatch because tracing writes during the dump; run it again.
- Dead ends: wiring the table into `Access` while fixtures still write grant rows (Revisions 5); a union of every fact in one SQL string.
