# 11 — Navigation drawer with capability gating

**What to build:** A hamburger drawer accessible from the dashboard. Items are shown or hidden based on the user's capabilities (from `SessionState`). A red badge on the hamburger icon and on the Notifications item shows unread count.

**Blocked by:** 07 — Login + capabilities (needs SessionState with capabilities)

**Status:** ready-for-agent

- [ ] `AppDrawer.kt` composable: `ModalNavigationDrawer` + `DrawerState`. Hamburger icon in dashboard TopAppBar triggers `drawerState.open()`
- [ ] Drawer header: app name, current username, selected branch name
- [ ] Drawer items (capability-gated):
  - Clients — `EDIT_BRANCH_DATA`
  - Inventory — `EDIT_BRANCH_DATA`
  - Finance — `ASSIGN_COMPENSATION`
  - Remittances — `SUBMIT_REMITTANCE`
  - Notifications — always visible (personal) + red badge with unread count
  - Audit Log — always visible
  - Reports — `VIEW_BRANCH_DATA`
  - User Management — `MANAGE_USERS`
- [ ] Red notification badge: count from `GET /api/notifications` (existing endpoint, unread count). Displayed as red pill on hamburger icon and next to Notifications item. Polled every 60 seconds
- [ ] Drawer item click → `NavController.navigate(route)` + close drawer
- [ ] Drawer integrated into `DashboardScreen` via `ModalNavigationDrawer`
- [ ] ViewModel tests: drawer item visibility per capability set, badge count
- [ ] `:composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid :composeApp:desktopTest` passes
