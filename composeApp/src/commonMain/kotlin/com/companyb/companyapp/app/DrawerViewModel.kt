package com.companyb.companyapp.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.app.GLOBAL_CAPABILITY_CONTEXT_ID
import com.companyb.companyapp.app.hasCapability
import com.companyb.companyapp.app.hasCapabilityAnyContext
import com.companyb.companyapp.app.hasDayGrant
import com.companyb.companyapp.app.navigation.Route
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * #671 — task-grouped navigation model. The drawer renders Work, then Finance, then a
 * collapsed-by-default Administration group; footer destinations ([section] == null)
 * render in the shell footer instead of any group.
 */
enum class DrawerSection(
    val heading: String,
) {
    WORK("Work"),
    FINANCE("Finance"),
    ADMINISTRATION("Administration"),
}

data class DrawerItem(
    val route: Route,
    val label: String,
    val capabilityCode: String?,
    val visible: Boolean,
    // #158 — when set, the item is also visible when the caller holds this code at
    // BRANCH_DAY context (the relief day-grant shape: a relief delegate reaches the
    // Finance day-scoped surface without any BRANCH/VIEW grant).
    val dayGrantCode: String? = null,
    val globalCapabilityOnly: Boolean = false,
    val section: DrawerSection? = null,
)

data class DrawerUiState(
    val drawerItems: List<DrawerItem>,
)

class DrawerViewModel : ViewModel() {
    private val allItems: List<DrawerItem> =
        listOf(
            // #389 — the clocked-in home is a drawer item like any other section: without it,
            // top-level screens (no back affordance) strand desktop users away from Dashboard.
            // #671 — labeled Sessions (the existing Dashboard route keeps its identifier).
            DrawerItem(Route.Dashboard(), "Sessions", null, visible = true, section = DrawerSection.WORK),
            DrawerItem(
                Route.Clients,
                "Clients",
                CapabilityCodes.EDIT_BRANCH_DATA,
                visible = false,
                section = DrawerSection.WORK,
            ),
            DrawerItem(
                Route.Inventory,
                "Inventory",
                CapabilityCodes.EDIT_BRANCH_DATA,
                visible = false,
                section = DrawerSection.WORK,
            ),
            // #418 — rate admin: any-context MANAGE_PRODUCTS shows the item (drawer convention);
            // the screen itself re-checks exact scope against the clocked-in branch.
            DrawerItem(
                Route.BaseRates,
                "Base Rates",
                CapabilityCodes.MANAGE_PRODUCTS,
                visible = false,
                section = DrawerSection.ADMINISTRATION,
            ),
            // #441 — shared catalog admin: exact GLOBAL MANAGE_CATALOG shows the item (the
            // Mission Delegates globalCapabilityOnly shape); backend gates authoritative.
            DrawerItem(
                Route.ProductCatalog,
                "Product Catalog",
                CapabilityCodes.MANAGE_CATALOG,
                visible = false,
                globalCapabilityOnly = true,
                section = DrawerSection.ADMINISTRATION,
            ),
            // #105 D1 — Finance and Reports merge into one item, gate widened to the widest
            // read capability (VIEW_BRANCH_DATA): Accountant (GLOBAL view) sees the item.
            // #158 — day-grant holders (BRANCH_DAY EDIT_BRANCH_DATA) also see it: the Finance
            // day-scoped read entry is their relief surface.
            DrawerItem(
                Route.Finance,
                "Finance & Reports",
                CapabilityCodes.VIEW_BRANCH_DATA,
                visible = false,
                dayGrantCode = CapabilityCodes.EDIT_BRANCH_DATA,
                section = DrawerSection.FINANCE,
            ),
            DrawerItem(
                Route.RemittanceList,
                "Remittance",
                CapabilityCodes.SUBMIT_REMITTANCE,
                visible = false,
                section = DrawerSection.FINANCE,
            ),
            DrawerItem(Route.Notifications, "Notifications", null, visible = true, section = DrawerSection.WORK),
            DrawerItem(Route.AuditLog, "Audit Log", null, visible = true, section = DrawerSection.ADMINISTRATION),
            DrawerItem(
                Route.UserManagement,
                "Team & branches",
                CapabilityCodes.MANAGE_USERS,
                visible = false,
                section = DrawerSection.ADMINISTRATION,
            ),
            DrawerItem(
                Route.MedicalMissionDelegates,
                "Mission delegates",
                CapabilityCodes.ASSIGN_DELEGATE,
                visible = false,
                globalCapabilityOnly = true,
                section = DrawerSection.ADMINISTRATION,
            ),
            // #381 — own profile: reachable by every authenticated user, no capability gate.
            // #671 — footer destination: the shell footer renders the signed-in display
            // name as the Profile entry instead of a flat list row.
            DrawerItem(Route.Profile, "Profile", null, visible = true),
        )

    private val _uiState: MutableStateFlow<DrawerUiState> =
        MutableStateFlow(
            DrawerUiState(
                drawerItems = allItems.map { it.copy(visible = it.capabilityCode == null) },
            ),
        )
    val uiState: StateFlow<DrawerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // #498 — single snapshot source; visibility derives from its capability rows.
            AppSessionState.snapshot.collect { snap ->
                val caps = snap.capabilities
                _uiState.value =
                    DrawerUiState(
                        drawerItems = allItems.map { item -> item.copy(visible = item.isVisible(caps)) },
                    )
            }
        }
    }

    private fun DrawerItem.isVisible(caps: List<UserCapabilityResponse>): Boolean {
        val capabilityVisible =
            capabilityCode != null &&
                if (globalCapabilityOnly) {
                    caps.hasCapability(
                        capabilityCode,
                        CapabilityContextType.GLOBAL,
                        GLOBAL_CAPABILITY_CONTEXT_ID,
                    )
                } else {
                    caps.hasCapabilityAnyContext(capabilityCode)
                }
        return capabilityCode == null || capabilityVisible || (dayGrantCode != null && caps.hasDayGrant(dayGrantCode))
    }
}
