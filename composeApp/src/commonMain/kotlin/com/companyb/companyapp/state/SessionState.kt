package com.companyb.companyapp.state

import com.companyb.companyapp.dto.MeResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

object SessionState {
    private val _currentUser = MutableStateFlow<MeResponse?>(null)
    val currentUser: StateFlow<MeResponse?> = _currentUser.asStateFlow()

    private val _capabilities = MutableStateFlow<Set<String>>(emptySet())
    val capabilities: StateFlow<Set<String>> = _capabilities.asStateFlow()

    private val _selectedBranchId = MutableStateFlow<String?>(null)
    val selectedBranchId: StateFlow<String?> = _selectedBranchId.asStateFlow()

    private val _selectedBranchName = MutableStateFlow<String?>(null)
    val selectedBranchName: StateFlow<String?> = _selectedBranchName.asStateFlow()

    // #94 Q3c(ii) — mid-session 401 surfaces "session expired" once on the Login screen
    // (launch-validation 401 stays silent). App.kt sets it; LoginScreen consumes + clears.
    private val _expiredNotice = MutableStateFlow(false)
    val expiredNotice: StateFlow<Boolean> = _expiredNotice.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> =
        currentUser
            .map { it != null }
            .stateIn(GlobalScope, SharingStarted.Eagerly, false)

    fun setUser(user: MeResponse) {
        _currentUser.value = user
    }

    fun setCapabilities(caps: Set<String>) {
        _capabilities.value = caps
    }

    fun setSelectedBranch(
        id: String,
        name: String,
    ) {
        _selectedBranchId.value = id
        _selectedBranchName.value = name
    }

    fun setExpiredNotice(value: Boolean) {
        _expiredNotice.value = value
    }

    fun clear() {
        _currentUser.value = null
        _capabilities.value = emptySet()
        _selectedBranchId.value = null
        _selectedBranchName.value = null
    }
}

/**
 * ADR-0021 two-slice filter — pre-BranchSelect slice. Before [SessionState.selectedBranchId]
 * is set, only GLOBAL-context capabilities are usefully resolvable; branch-scoped rows are
 * present in the response but cannot be matched to a branch (the code-only `Set<String>`
 * surface can't carry context — the #99 F7 divergence is its own fog decision).
 */
fun globalCapabilities(rows: List<UserCapabilityResponse>): Set<String> =
    rows
        .filter { it.contextType == "GLOBAL" }
        .map { it.capabilityCode }
        .toSet()

/**
 * ADR-0021 two-slice filter — post-clock-in slice. Resolves the branch-scoped rows for the
 * selected branch (contextType BRANCH, contextId == branchId) alongside the global slice.
 * BRANCH_DAY/MEDICAL_MISSION/PROVINCIAL_TOUR rows are excluded by design — the code-only
 * `Set<String>` cannot represent them (the #99 F7 full context model covers that).
 */
fun capabilitiesForBranch(
    rows: List<UserCapabilityResponse>,
    branchId: String,
): Set<String> =
    rows
        .filter { it.contextType == "GLOBAL" || (it.contextType == "BRANCH" && it.contextId == branchId) }
        .map { it.capabilityCode }
        .toSet()
