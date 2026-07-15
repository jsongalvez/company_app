# 07 — Login flow with capabilities fetch

**What to build:** Rewrite the login screen with Linear aesthetic. After successful login, call `GET /api/me` and `GET /api/me/capabilities` and store the results in a `SessionState` object accessible app-wide. This is the critical-path ticket — all downstream screens depend on knowing who the user is and what they can do.

**Blocked by:** 03 — /api/me + /api/me/capabilities, 05 — MockEngine, 06 — NavHost routing

**Status:** ready-for-agent

- [ ] `LoginScreen.kt`: dark canvas background, centered card with app title, username/password fields (Linear-style inputs: surface-1 bg, 8px rounded, Ink text), lavender primary button ("Sign in"), error text in ink-muted, register link
- [ ] `AuthViewModel`: after login success, chain `fetchMe()` → `fetchCapabilities()` calls. Expose `meState` and `capabilitiesState` StateFlows
- [ ] `SessionState` singleton/object in `composeApp/src/commonMain/` holding `currentUser: StateFlow<MeResponse?>`, `capabilities: StateFlow<List<UserCapabilityResponse>>`, and `isLoggedIn: StateFlow<Boolean>`. Populated by AuthViewModel on login, cleared on logout
- [ ] On login success: `SessionState` populated → `NavController` navigates to `BranchSelector`
- [ ] On app start: if `TokenStore` has a token, attempt silent re-auth (fetch /api/me — if 401, clear token and show login)
- [ ] ViewModel tests: login success/failure, me fetch success/failure, capabilities fetch empty/non-empty
- [ ] `:composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid :composeApp:desktopTest` passes
