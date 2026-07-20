# 09 — Session dashboard — table + summary cards

**What to build:** The main screen. After clock-in, the user sees today's session dashboard for the selected branch. Summary cards show gross income and commission split. The session list is a dense table on desktop, a card list on mobile. Voided sessions are greyed out. Data refreshes every 30 seconds.

**Blocked by:** 06 — NavHost routing, 07 — Login + capabilities

**Status:** ready-for-agent

- [ ] `DashboardScreen.kt`: summary cards row (gross income today, commission split for current user) + session list
- [ ] Summary cards: `Surface` cards with key-value layout. Gross income = sum of final_price for COMPLETED non-voided sessions. Commission split = from `GET /api/commission-split` filtered to current user
- [ ] Desktop table: scrollable with sticky header. Columns: client name, session type, status (color-coded), walk-in badge, base price, final price, practitioners, booked at, next appointment, remarks, concerns. Click row → session detail
- [ ] Mobile cards: `LazyColumn` of `Card` composables. Each card: client name + type badge + status badge (green/amber/red) + final price. Tap → session detail screen
- [ ] Voided sessions: grey surface-2 background, strikethrough on client name, "VOIDED" badge, excluded from income sum
- [ ] 30-second polling via `LaunchedEffect` + `delay(30_000)` loop calling `GET /api/sessions?branchDayId=...`
- [ ] Responsive: `BoxWithConstraints` or window size class to switch between table and card layout
- [ ] ViewModel tests: session list load, summary calculation, polling, voided exclusion
- [ ] `:composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid :composeApp:desktopTest` passes
