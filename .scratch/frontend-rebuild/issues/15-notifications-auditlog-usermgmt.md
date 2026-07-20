# 15 — Notifications + audit log + user management screens

**What to build:** Three simpler read/action screens. Notifications shows the user's unread notifications with mark-read. Audit log shows change history with filtering and acknowledge. User management (capability-gated) shows user list with deactivate and slot ordering.

**Blocked by:** 11 — Navigation drawer

**Status:** ready-for-agent

- [ ] `NotificationsScreen.kt`: list of unread notifications from `GET /api/notifications`. Each item: message, timestamp. Swipe or button to mark read → `PATCH /api/notifications/{id}/read`. Empty state: "No notifications"
- [ ] `AuditLogScreen.kt`: filter by table name + record ID. Results list: timestamp, changed_by, action (INSERT/UPDATE/DELETE), old/new values (truncated). Flagged entries: highlighted with warning icon. Acknowledge button → `PATCH /api/audit-log/{id}/acknowledge`
- [ ] `UserManagementScreen.kt`: user list with roles, status, branch assignments. Deactivate button with confirmation → `PATCH /api/users/{id}/deactivate`. Slot ordering: drag-to-reorder or swap button per branch (future — display only for now)
- [ ] All screens use existing ViewModels: `NotificationViewModel`, `AuditLogViewModel`, `UserViewModel`
- [ ] ViewModel tests: notification list/mark-read, audit log query/acknowledge, user deactivate
- [ ] `:composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid :composeApp:desktopTest` passes
