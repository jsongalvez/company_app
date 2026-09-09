package com.companyb.companyapp.app.navigation

/**
 * #671 — per-section working context: switching sections and returning preserves the
 * branch/date, selected object, and list anchor for that section.
 *
 * Keyed by (userId, branchId): a branch switch is a fresh key, so old-branch rows can
 * never present as new-branch data. Screens own their toolbar/list/detail state; this
 * store only retains the small anchor each section needs to restore it (selected id +
 * scroll anchor id). No disk persistence of clinical drafts — memory only.
 *
 * Cleared on logout/access loss alongside `AppSessionState.clear()` (see `App.kt` and the
 * drawer clock-out path). A singleton object keeps the single-copy rule: both NavHost
 * actuals and every section share one working set per user+branch.
 *
 * Why not `NavController` saved-state: back-stack entries (and their saved state) are
 * destroyed on pop, but the ticket requires anchors to survive section switches — a
 * session-scoped store is the minimal structure that outlives the entries. It is not
 * an all-purpose cache: section-keyed anchors only, memory-only (no disk persistence
 * of clinical drafts), keyed so a branch switch rekeys, cleared with the session.
 *
 * #672 — sections with toolbar tabs persist the selected tab here (Sessions owns
 * All/Pending/Completed): the drawer collapses to the Dashboard root on
 * section-switch, so a ViewModel/screen-held filter would reset on every return.
 * The tab rides the same user+branch key and null-leg rules as the anchors.
 *
 * #727 — Inventory owns a lightweight working context on the same seam (search query
 * + Low stock filter + identity scroll anchor): the drawer collapses to the Dashboard
 * root on section-switch, so entry-scoped query/filter/list state alone would reset
 * on every Inventory → Finance → Inventory roundtrip. Query/low-stock ride the same
 * user+branch key and null-leg rules as the anchors; the anchor reuses
 * scrollAnchorId as the visible card id (identity, not index) so filtering or data
 * changes degrade to a safe fallback instead of a stuck viewport.
 */
object NavigationContextStore {
    /** What a section needs to restore its working position on return. */
    data class SectionContext(
        val selectedId: String? = null,
        val scrollAnchorId: String? = null,
        val tab: String? = null,
        // #727 — Inventory working context only (query + Low stock filter). Null =
        // never visited; empty query / false filter are meaningful retained values.
        val query: String? = null,
        val lowStockOnly: Boolean? = null,
    )

    private val contexts: MutableMap<String, SectionContext> = mutableMapOf()

    private fun key(
        userId: String?,
        branchId: String?,
        section: Route,
    ): String = "${userId ?: "?"}|${branchId ?: "?"}|${sectionKey(section)}"

    /**
     * Stable per-section key: detail routes share their parent's slot, so selecting a
     * session then pushing its detail does not fork the Sessions context.
     *
     * Exhaustive over [Route] (#468 — no `::class` runtime narrowing): pushed routes map
     * to their parent slot, auth/branch entry points keep stable keys.
     */
    fun sectionKey(route: Route): String =
        when (ShellLayoutPolicy.parentFor(route)) {
            is Route.Dashboard -> "sessions"
            is Route.SessionDetail -> "sessions"
            is Route.SessionCreate -> "sessions"
            is Route.Clients -> "clients"
            is Route.ClientDetail -> "clients"
            is Route.Inventory -> "inventory"
            is Route.BaseRates -> "rates"
            is Route.ProductCatalog -> "catalog"
            is Route.Finance -> "finance"
            is Route.RemittanceList -> "remittance"
            is Route.RemittanceDetail -> "remittance"
            is Route.Notifications -> "notifications"
            is Route.AuditLog -> "audit"
            is Route.AuditLogHistory -> "audit"
            is Route.UserManagement -> "team"
            is Route.MedicalMissionDelegates -> "delegates"
            is Route.Profile -> "profile"
            is Route.BranchSelect -> "branch-select"
            is Route.Login -> "login"
            is Route.AcceptInvite -> "invite"
            is Route.ForgotPassword -> "password"
        }

    /** Last retained context for this user+branch+section, or null when never visited. */
    fun retained(
        userId: String?,
        branchId: String?,
        section: Route,
    ): SectionContext? {
        // Fail-closed on the transient clock-out window (clock null, route still
        // Dashboard): null legs never read or write orphan entries.
        if (userId == null || branchId == null) return null
        return contexts[key(userId, branchId, section)]
    }

    /** Retains this section's working position; null legs leave the stored value intact. */
    fun retain(
        userId: String?,
        branchId: String?,
        section: Route,
        selectedId: String?,
        scrollAnchorId: String? = null,
        tab: String? = null,
        // #727 — null = leave intact; non-null (including "" / false) overwrites so an
        // explicit clear is itself retained and never resurrects an older value.
        query: String? = null,
        lowStockOnly: Boolean? = null,
    ) {
        if (userId == null || branchId == null) return
        val k = key(userId, branchId, section)
        val previous = contexts[k] ?: SectionContext()
        contexts[k] =
            previous.copy(
                selectedId = selectedId ?: previous.selectedId,
                scrollAnchorId = scrollAnchorId ?: previous.scrollAnchorId,
                tab = tab ?: previous.tab,
                query = query ?: previous.query,
                lowStockOnly = lowStockOnly ?: previous.lowStockOnly,
            )
    }

    /** Drops one section's context (e.g. the object was deleted upstream). */
    fun forget(
        userId: String?,
        branchId: String?,
        section: Route,
    ) {
        contexts.remove(key(userId, branchId, section))
    }

    /** Logout/access-loss/clock-out: protected working state leaves with the session. */
    fun clear() {
        contexts.clear()
    }
}
