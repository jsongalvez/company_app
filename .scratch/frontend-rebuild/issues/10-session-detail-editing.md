# 10 — Session detail + inline editing + void/unvoid

**What to build:** Clicking a session row opens the detail view. Desktop: master-detail side panel sliding in from the right. Mobile: full-screen navigation. All session fields are viewable; coordinators can edit cells inline (desktop) or via the detail form. Void and unvoid with reason dialogs. Day-state warnings for PAST/REMITTED edits.

**Blocked by:** 09 — Session dashboard (detail is accessed from the dashboard table/cards)

**Status:** ready-for-agent

- [ ] `SessionDetailScreen.kt`: full session display — client info, type, status, walk-in, base/final price, practitioners list, concerns list, remarks, booked_at, next_appointment
- [ ] Desktop layout: `Row` — dashboard table (weight 0.6) + detail panel (weight 0.4) with `AnimatedVisibility` slide-in. Empty state when no session selected
- [ ] Mobile layout: full-screen navigation from dashboard card tap
- [ ] Inline editing (desktop): click enum cell → dropdown (`ExposedDropdownMenuBox` for session_type, session_status). Click price cell → inline `OutlinedTextField` with number input. PATCH endpoint called on blur/enter
- [ ] Void button → dialog with required reason text field → `POST /api/sessions/{id}/void`
- [ ] Unvoid button → dialog with required reason → `POST /api/sessions/{id}/unvoid`
- [ ] Add/remove practitioners: list with add button, remove icon per practitioner
- [ ] Day-state warnings: banner at top for PAST days ("Editing a past day — changes are audited"), stricter banner for REMITTED days ("Editing a remitted day — reason required, changes will be flagged")
- [ ] ViewModel tests: session detail load, inline edit PATCH, void/unvoid, day-state flagging
- [ ] `:composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid :composeApp:desktopTest` passes
