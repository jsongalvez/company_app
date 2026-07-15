# 14 — Finance + remittances + reports screens

**What to build:** Three screens in one ticket — they share ViewModels and are moderate complexity (mostly display + form actions). Finance shows daily P&L with compensation and expenses. Remittances manages drafts and submission. Reports provides export buttons.

**Blocked by:** 11 — Navigation drawer

**Status:** ready-for-agent

- [ ] `DailyFinanceScreen.kt`: daily sales summary (from `GET /api/branches/{branchId}/daily-summary`), compensation list with assign/edit forms, expense list with add/edit forms, allowance list
- [ ] `RemittanceScreen.kt`: list of remittance drafts for branch. Create draft: date range picker, method selector. Draft detail: session/product lines with checkboxes, day breakdown, version display. Submit: version confirmation dialog → `POST /api/remittances/{id}/submit`
- [ ] `ReportsScreen.kt`: export buttons — Daily CSV, Daily PDF, Monthly CSV, Monthly PDF, All-time CSV, All-time PDF, Provincial, Medical Mission. Year/month pickers for monthly. Calls existing export endpoints
- [ ] All screens use existing ViewModels: `CompensationViewModel`, `ExpenseViewModel`, `AllowanceViewModel`, `RemittanceViewModel`, `ReportViewModel`
- [ ] ViewModel tests: daily summary load, compensation assign/edit, expense CRUD, remittance draft create/submit, export download
- [ ] `:composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid :composeApp:desktopTest` passes
