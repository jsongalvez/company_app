# 08 — Branch selector + clock-in

**What to build:** After login, display a list of the user's assigned branches. Each branch card shows the branch name, type badge (CLINIC/PROVINCIAL_TOUR/MEDICAL_MISSION), and a Clock In / Clock Out button. Relief duty (non-home branch) is clearly labeled. Clocking in navigates to the dashboard.

**Blocked by:** 06 — NavHost routing, 07 — Login + capabilities

**Status:** ready-for-agent

- [ ] `BranchSelectorScreen.kt`: list of branches from `GET /api/branches` (existing endpoint), filtered to user's assigned branches
- [ ] Branch card states: "Clocked in here" (green accent border), "Clocked in elsewhere" (disabled button + info text), "Not clocked in" (normal button)
- [ ] Relief duty indicator: non-home branches show "Relief — view-only until granted" badge
- [ ] Clock-in calls existing `POST /api/attendance/clock-in` with client-generated UUID idempotency key
- [ ] On clock-in success: navigate to `Dashboard` route
- [ ] ViewModel tests: branch list load, clock-in success (200), clock-in conflict (409 — already clocked in), clock-in error
- [ ] `:composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid :composeApp:desktopTest` passes
