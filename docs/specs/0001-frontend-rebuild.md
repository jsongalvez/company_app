# Spec: CompanyApp Frontend Rebuild + Backend Fixes

## Problem Statement

The CompanyApp backend is complete (56/65 PRD stories done), but the composeApp frontend was built without a UX plan — a few proof-of-concept screens (Login, Home, ClientSearch, SessionCreate) hacked together with a `Screen` sealed class for navigation, no design system, no capability-gated UI, and no test infrastructure for ViewModels. The remaining PRD frontend stories (US-042–050) are thin descriptions with no UX definition. Continuing to build on this foundation will produce an inconsistent, unmaintainable UI.

Additionally, three backend gaps must be closed before the frontend can be properly built: a `GET /api/me/capabilities` endpoint for UI capability gating, a V3 migration that should be merged into V1, and the VIEWER role that has no real purpose.

## Solution

Erase all existing frontend screens and rebuild from scratch with a clear UX plan grounded in the domain model. Apply the Linear design language. Use NavHost for routing. Gate all UI elements by capabilities fetched from a new `GET /api/me/capabilities` backend endpoint. Test ViewModels with Ktor `MockEngine`. Rename VIEWER to ONBOARDING (zero-permission new-account role).

## User Stories

### Backend fixes

1. As a developer, I want the V3 `client_middle_name_trgm` migration merged into V1 and the V3 file deleted, so that the schema lives in a single migration file.

2. As a frontend developer, I want a `GET /api/me` endpoint that returns the authenticated user's id, username, and status, so that the frontend can display the current user's identity.

3. As a frontend developer, I want a `GET /api/me/capabilities` endpoint that returns the authenticated user's current capabilities (code, contextType, contextId, sourceType), so that the UI can show or hide features based on real permissions.

4. As an admin, I want the VIEWER role renamed to ONBOARDING with zero capabilities, so that newly registered accounts have no access until assigned to a branch by someone with `MANAGE_USERS`.

### Frontend infrastructure

5. As a developer, I want NavHost-based routing replacing the `Screen` sealed class, so that the app can support 3+ levels of screen depth with a proper back-stack.

6. As a developer, I want Ktor `MockEngine` support in `ApiClient` so that ViewModels can be unit-tested without a running backend.

7. As a developer, I want the Linear DESIGN.md as the project's design system so that all screens share a consistent visual language.

### Authentication and branch selection

8. As a user, I want to log in with my username and password, so that I can access the app.

9. As a user, I want to see a list of my assigned branches and clock into one, so that I can begin working at a branch for the day.

10. As a relief user clocking into a non-home branch, I want to request edit access from a checked-in user at that branch, so that I can perform my duties.

### Dashboard

11. As a user, I want to see today's sessions as a live-updating list immediately after clock-in, so that I can track what is happening at my branch.

12. As a user, I want summary cards showing today's gross income and my commission split, so that I can see the day's financials at a glance.

13. As a user, I want to see all session fields in the dashboard columns: client name, session type, status, walk-in flag, base price, final price, practitioners, booked time, next appointment date, remarks, and concerns.

14. As a user, I want voided sessions displayed with a grey background and strikethrough and excluded from summary totals, so that I can distinguish them from active sessions.

### Session detail and editing

15. As a user, I want to tap a session row to open the session detail screen, so that I can view or edit its full information.

16. As a coordinator on desktop, I want to click a session cell to edit its value inline — dropdowns for enum fields (type, status) and number input for prices — so that I can update records without navigating away.

17. As a coordinator, I want to void a session with a required reason, so that erroneous sessions are excluded from financial calculations.

18. As a coordinator, I want to un-void a session that was voided in error, so that it is restored to active status.

19. As a coordinator editing a session on a REMITTED day, I want a warning and a required reason field, so that I understand the edit will be flagged for audit review.

### Navigation drawer

20. As a user with `EDIT_BRANCH_DATA`, I want a Clients item in the navigation drawer, so that I can search for and manage client records.

21. As a user with `EDIT_BRANCH_DATA`, I want an Inventory item in the navigation drawer, so that I can view stock levels and record sales.

22. As a user with `ASSIGN_COMPENSATION`, I want a Finance item in the navigation drawer, so that I can view daily P&L, assign compensation, and log expenses.

23. As a coordinator with `SUBMIT_REMITTANCE`, I want a Remittances item in the navigation drawer, so that I can create drafts and submit remittances.

24. As a user, I want a Notifications item with a red badge showing unread count, so that I can see relief requests and appointment alerts.

25. As a user, I want an Audit Log item, so that I can review the change history for any record.

26. As a user with `VIEW_BRANCH_DATA`, I want a Reports item, so that I can export daily, monthly, and all-time summaries.

27. As a user with `MANAGE_USERS`, I want a User Management item, so that I can deactivate users and manage slot ordering.

28. As a user, I want the navigation drawer to only show items I have the capabilities to use, so that I am not shown features I cannot access.

### Responsive layout

29. As a desktop user, I want the dashboard displayed as a dense table with a master-detail side panel opening when I click a session row, so that I can edit a session while still seeing the full day's list.

30. As a mobile user, I want the dashboard displayed as a scrollable card list where each card shows key session fields and is tappable to the detail screen, so that the app is usable on a phone screen.

## Implementation Decisions

### Linear design language

The project adopts the Linear DESIGN.md as its visual system: dark canvas (`#010102`), four-step surface ladder (`#0f1011` through `#191a1b`) for card/panel hierarchy, lavender-blue (`#5e6ad2`) as the single chromatic accent, Inter font family as the SF Pro Display substitute, Compact 8px button radius, 12px card radius, and hairline borders (`#23252a`) instead of shadows.

The DESIGN.md file is dropped into the project root. All new composables reference its token conventions.

### NavHost routing

Replace the `Screen` sealed class in `HomeScreen.kt` with a Compose Multiplatform `NavHost`. Define a route sealed class (or enum) with typed argument support. Each screen is a `@Composable` function registered as a `composable()` route. The `NavController` is passed via composition local or constructor injection to screens that need to navigate.

### Capability gating

On login success, the frontend calls `GET /api/me` and `GET /api/me/capabilities` and stores the result in a `SessionState` object (StateFlow, available app-wide). The drawer composable reads this state and filters items. Each screen's route guard checks the required capability before rendering; unauthorized navigation shows a "Not authorized" state or redirects.

### Session dashboard

The dashboard fetches today's sessions for the selected branch via the existing session endpoints (or a new `GET /api/branches/{branchId}/sessions?date=today` if needed). Data refreshes on a 30-second polling interval. Summary cards show gross income (sum of final_price for COMPLETED, non-voided sessions) and the current user's commission split (from `GET /api/commission-split?branchDayId=...`).

Desktop: a scrollable table with clickable cells. Enum fields (session_type, session_status) render as dropdowns on click. Price fields render as editable number inputs. Inline edits call the appropriate PATCH endpoint. The table has a sticky header row.

Mobile: a `LazyColumn` of cards. Each card shows client name, type badge, status badge (color-coded: green COMPLETED, amber PENDING, red NO_SHOW/CANCELLED), final price. Tapping a card navigates to the session detail screen.

Voided sessions: rendered with a grey background tint, strikethrough on client name, and a "VOIDED" badge. Excluded from summary card calculations.

### Master-detail (desktop)

The desktop layout uses a `Row` with the dashboard table on the left (flex weight 0.6) and a sliding detail panel on the right (flex weight 0.4). When no session is selected, the right panel shows an empty state. When a session row is clicked, the detail panel slides in with full session information, practitioner list, concerns, and action buttons (mark complete, void/unvoid). The `AnimatedVisibility` or `AnimatedContent` composable provides the slide transition.

### ONBOARDING role

The V2 seed migration replaces `VIEWER` with `ONBOARDING`. All `role_capability` rows for the ONBOARDING role are removed (zero capabilities). Any code references to `VIEWER` in detekt configs, test helpers, or documentation are updated.

### V3 migration merge

The contents of `V3__client_middle_name_trgm.sql` (middle_name column + trigram index on client) are merged into `V1__full_schema.sql` at the appropriate position. The V3 file is deleted. `DatabaseConfig.runMigrations()` no longer needs `flyway.repair()` for V3 (it was already calling `repair()` for V1 edits, which covers this).

### Backend endpoints

`GET /api/me` — returns `{ id, username, status, createdAt }`. No capability gate (the caller is always themselves). Queries `app_user` by the JWT subject.

`GET /api/me/capabilities` — returns `[{ capabilityCode, contextType, contextId, sourceType }]`. No capability gate. Queries `active_user_capabilities` view by the JWT subject. Used by the frontend to gate UI elements.

### MockEngine testing

`ApiClient` gains an optional `engine: HttpClientEngine? = null` constructor parameter. When null, it uses the platform default engine (current behavior). When provided (in tests), it uses the MockEngine. ViewModel tests instantiate `ApiClient` with a `MockEngine` configured to return fake JSON responses for each endpoint the ViewModel calls. Tests verify the ViewModel's StateFlow transitions (Idle → Loading → Success/Error).

### Existing code disposition

All existing screen files (`LoginScreen.kt`, `HomeScreen.kt`, `ClientSearchScreen.kt`, `SessionCreateScreen.kt`) are deleted and rewritten. The 19 ViewModels are kept and modified where needed (new endpoints, capability-aware methods). `App.kt` is rewritten with NavHost. `ApiClient.kt` gains the MockEngine parameter. The `UiState` sealed class is kept as-is.

## Testing Decisions

A good test verifies external behavior — that a ViewModel transitions through the correct UiState sequence when the API returns specific responses — without asserting on implementation details like internal MutableStateFlow field names.

### Backend tests

Use the existing `*PostgresTest.kt` pattern (real Postgres via `DatabaseTestHelper.ensureDatabase()`). Each new endpoint gets a dedicated test class with tests covering: success path, unauthenticated (no JWT), and edge cases (user with zero capabilities, inactive user).

### Frontend ViewModel tests

Create a `MockApiClient` helper that wraps Ktor `MockEngine` with a configurable response handler. Each ViewModel test file instantiates the ViewModel with a MockApiClient, triggers a method, and asserts the StateFlow value sequence. Tests live under `composeApp/src/commonTest/`.

Pattern (prior art from `UiStateTest.kt` and `ComposeAppCommonTest.kt`):
```kotlin
class AuthViewModelTest {
    @Test
    fun loginSuccessEmitsSuccessState() = runTest { ... }
}
```

### Frontend UI tests

No automated Compose UI tests in this phase. The build gate is `./gradlew :composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid`. Manual verification on desktop and Android emulator.

### Integration tests

Run the existing k6 suite (`tests/k6/full-suite.js`) after backend changes to verify no regressions. Run `./gradlew :backend:test` as the quality gate for all backend work.

## Out of Scope

- iOS screens and iOS Keychain token store (future US-047, US-048)
- Desktop Windows-specific target configuration (future US-049, partially done)
- Responsive adaptations beyond table/card-list toggle (future US-050)
- Dark/light mode toggle — Linear dark is the only theme
- SSE/WebSocket live updates — polling at 30s interval is sufficient for MVP
- Frontend end-to-end tests
- Offline mode — the app is strictly online
- VIEWER → ONBOARDING data migration (no existing VIEWER users in any DB)

## Further Notes

- The old `scripts/ralph/prd.json` and `scripts/ralph/progress.txt` frontend stories (US-042–050) are superseded by this spec.
- The Linear DESIGN.md file is available at `https://raw.githubusercontent.com/VoltAgent/awesome-design-md/main/design-md/linear.app/DESIGN.md`. Drop it into the project root.
- The `GET /api/me/capabilities` response must include `contextType` and `contextId` so the frontend can determine branch-scoped vs global capabilities for the currently selected branch.
- Branch selector must show the clock-in status per branch: "Clocked in here", "Clocked in elsewhere", "Not clocked in", with relief duty clearly labeled.
