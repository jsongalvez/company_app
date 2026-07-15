# 06 — NavHost routing + App.kt rewrite

**What to build:** Replace the `Screen` sealed class in `HomeScreen.kt` with Compose Multiplatform `NavHost`. Define typed routes for all screens. Rewrite `App.kt` to use NavHost with placeholder screens. The existing `Screen` sealed class and all current screen files are deleted.

**Blocked by:** 04 — Linear DESIGN.md (NavHost screens must be wrapped in LinearTheme)

**Status:** ready-for-agent

- [ ] Add `navigation-compose` dependency to `composeApp/build.gradle.kts` (compatible version for KMP)
- [ ] Define `Route` sealed class with all screen routes and typed arguments: Login, BranchSelector, Dashboard, SessionDetail(sessionId), ClientSearch, Inventory, ProductSale, DailyFinance, Remittances, Notifications, AuditLog, Reports, UserManagement
- [ ] Rewrite `App.kt`: `NavHost` with `startDestination = Route.Login`, each route renders a placeholder composable (just a `Text` with the route name on `LinearTheme` background)
- [ ] Delete existing screen files: `LoginScreen.kt`, `HomeScreen.kt`, `ClientSearchScreen.kt`, `SessionCreateScreen.kt`
- [ ] `:composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid` passes
