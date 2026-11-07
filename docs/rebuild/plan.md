# Rebuild plan

Status: Phase 1. Today uses layout B (Day sheet). Sizes stay as drawn: body 15 px, rows 16 px, tap targets 44 px.
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

- Tokens in one file: colour (light and dark), space (4, 8, 12, 16, 24, 32), type (13, 15, 17, 20, 28), line, focus. Square corners and no motion stay.
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
| Design | No raw colour or size outside the token file | lint (`tools/quality`) |
| Design | Contrast of every token pair used for text, light and dark (4.5:1 text, 3:1 large and UI) | script |
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

## Order of work (after the pick)

Each unit ends with a failing-then-passing test, `tools/push.sh`, GitHub runs checked by head SHA, `app-shipcheck`, and before/after phone shots.

1. Guards first: design lint (`web/tests/unit/design-lint.test.ts`, raw-value counts that only shrink) and the phone check (`web/tests/phone.e2e.ts`, every role on every menu page, with old pages on `web/tests/phone-legacy.json`).
2. Tokens, the contrast check, and the component kit.
3. Today in layout B, with the new top bar and menu, on the kit and today's data code. Today leaves the legacy list.
4. Store and outbox v2 with the bridge, and the day document endpoint. Today moves onto them; its browser tests are the safety net.
5. Policy table, interpreters, generated tests; V13 facts migration; delete the view and capability code.
6. Then pages by use: New session and Session, Clients, Expenses & pay, Remittances, Stock and Products, Relief, Alerts, Profile and My shifts, Branches and Users, Reports and Change history, Problem reports.
7. Delete the bridge and the old data layers; squash migrations; reset prod after a fresh dump.

Today comes before the store and the policy so the new look reaches the phone first, and so the store is shaped by a real screen's needs. Today's data wiring changes once more in unit 4; its view does not.

Done when: every page is on the kit with an empty lint allow list; role names live only in the policy table; the old data-layer files are gone; every page passes the phone check for every persona; the full browser suite is green on GitHub; prod runs it with `/health` UP.
