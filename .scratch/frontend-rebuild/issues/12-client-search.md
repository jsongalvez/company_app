# 12 — Client search screen

**What to build:** A client search screen accessible from the navigation drawer. Debounced search with client name/phone matching. Results as tappable cards that navigate to a client detail (future) or session create flow. Reuses existing `ClientViewModel` search logic.

**Blocked by:** 11 — Navigation drawer (accessed via drawer item)

**Status:** ready-for-agent

- [ ] `ClientSearchScreen.kt`: TopAppBar with back arrow, search input (Linear-style), result list
- [ ] Debounced search: 300ms delay on input change, calls existing `GET /api/clients?q=...`
- [ ] Result cards: client full name, gender, age, phone. Tap → for now, show a dialog or toast (client detail screen is future scope). Later: tap → navigate to session create with this client
- [ ] Empty state: "No clients found" with query displayed
- [ ] Loading: inline spinner (18dp, strokeWidth=2)
- [ ] Reuses `ClientViewModel` with URL-encoded query (Ktor `parameter()` fix from US-059)
- [ ] ViewModel tests: search success, empty results, search error
- [ ] `:composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid :composeApp:desktopTest` passes
