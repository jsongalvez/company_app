package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.navigation.Route
import com.companyb.companyapp.state.CapabilityContext
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.state.hasCapabilityAnyContext
import com.companyb.companyapp.state.hasCapabilityAtContextType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DrawerItem(
    val route: Route,
    val label: String,
    val capabilityCode: String?,
    val visible: Boolean,
    // #158 — when set, the item is also visible when the caller holds this code at
    // BRANCH_DAY context (the relief day-grant shape: a relief delegate reaches the
    // Finance day-scoped surface without any BRANCH/VIEW grant).
    val dayGrantCode: String? = null,
)

data class DrawerUiState(
    val drawerItems: List<DrawerItem>,
)

class DrawerViewModel : ViewModel() {
    private val allItems: List<DrawerItem> =
        listOf(
            DrawerItem(Route.Clients, "Clients", CapabilityCodes.EDIT_BRANCH_DATA, visible = false),
            DrawerItem(Route.Inventory, "Inventory", CapabilityCodes.EDIT_BRANCH_DATA, visible = false),
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
            ),
            DrawerItem(Route.RemittanceList, "Remittance", CapabilityCodes.SUBMIT_REMITTANCE, visible = false),
            DrawerItem(Route.Notifications, "Notifications", null, visible = true),
            DrawerItem(Route.AuditLog, "Audit Log", null, visible = true),
            DrawerItem(Route.UserManagement, "User Management", CapabilityCodes.MANAGE_USERS, visible = false),
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
            SessionState.capabilities.collect { caps ->
                _uiState.value =
                    DrawerUiState(
                        drawerItems =
                            allItems.map { item ->
                                item.copy(
                                    // #156 — any-context membership per drawer item (#92 Q3
                                    // "some branch": the drawer shows the item if the user holds
                                    // the code at any context). #158 — the day-grant OR.
                                    visible =
                                        item.capabilityCode == null ||
                                            caps.hasCapabilityAnyContext(
                                                item.capabilityCode,
                                            ) ||
                                            (
                                                item.dayGrantCode != null &&
                                                    caps.hasCapabilityAtContextType(
                                                        item.dayGrantCode,
                                                        CapabilityContext.BRANCH_DAY,
                                                    )
                                            ),
                                )
                            },
                    )
            }
        }
    }
}
