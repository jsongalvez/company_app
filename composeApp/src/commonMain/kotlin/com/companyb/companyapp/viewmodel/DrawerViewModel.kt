package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.navigation.Route
import com.companyb.companyapp.state.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DrawerItem(
    val route: Route,
    val label: String,
    val capabilityCode: String?,
    val visible: Boolean,
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
            DrawerItem(Route.Finance, "Finance & Reports", CapabilityCodes.VIEW_BRANCH_DATA, visible = false),
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
                                    visible = item.capabilityCode == null || item.capabilityCode in caps,
                                )
                            },
                    )
            }
        }
    }
}
