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

    // #156 — the #92 locked context model: full rows (contextType/contextId preserved;
    // BRANCH_DAY/MEDICAL_MISSION/PROVINCIAL_TOUR rows are no longer dropped). Both ADR-0021
    // fetches (login/launch + clock-in) store the full list; resolution happens at call
    // sites via hasCapability / hasCapabilityAnyContext below.
    private val _capabilities = MutableStateFlow<List<UserCapabilityResponse>>(emptyList())
    val capabilities: StateFlow<List<UserCapabilityResponse>> = _capabilities.asStateFlow()

    private val _selectedBranchId = MutableStateFlow<String?>(null)
    val selectedBranchId: StateFlow<String?> = _selectedBranchId.asStateFlow()

    private val _selectedBranchName = MutableStateFlow<String?>(null)
    val selectedBranchName: StateFlow<String?> = _selectedBranchName.asStateFlow()

    // #147 — clock-state slots written at clock-in (ClockInResponse.id + branchDayId):
    // ClockOutRequest carries only the attendance id, so the drawer's clock-out action
    // sources it here. Cleared by clearClockState (clock-out) and clear (logout/401).
    private val _attendanceId = MutableStateFlow<String?>(null)
    val attendanceId: StateFlow<String?> = _attendanceId.asStateFlow()

    private val _branchDayId = MutableStateFlow<String?>(null)
    val branchDayId: StateFlow<String?> = _branchDayId.asStateFlow()

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

    fun setCapabilities(caps: List<UserCapabilityResponse>) {
        _capabilities.value = caps
    }

    fun setSelectedBranch(
        id: String,
        name: String,
    ) {
        _selectedBranchId.value = id
        _selectedBranchName.value = name
    }

    fun setClockState(
        attendanceId: String,
        branchDayId: String,
    ) {
        _attendanceId.value = attendanceId
        _branchDayId.value = branchDayId
    }

    /**
     * #147 (Q3) — clock-out state transition: the user stays logged in, the branch selection
     * is dropped, and capabilities reset to EMPTY (not re-fetched: no capability consumers
     * exist pre-clock-in; the next clock-in refreshes wholesale via ADR-0021 — clear-to-empty
     * is the fail-closed direction if a future surface wrongly renders caps-gated UI early).
     */
    fun clearClockState() {
        _attendanceId.value = null
        _branchDayId.value = null
        _selectedBranchId.value = null
        _selectedBranchName.value = null
        _capabilities.value = emptyList()
    }

    fun setExpiredNotice(value: Boolean) {
        _expiredNotice.value = value
    }

    fun clear() {
        _currentUser.value = null
        _capabilities.value = emptyList()
        _selectedBranchId.value = null
        _selectedBranchName.value = null
        _attendanceId.value = null
        _branchDayId.value = null
    }
}

/**
 * #156 — capability context types as returned by `GET /api/me/capabilities`
 * (the backend `capability_context_type` enum; shared with the backend only as
 * raw strings in `UserCapabilityResponse`).
 */
object CapabilityContext {
    const val GLOBAL = "GLOBAL"
    const val BRANCH = "BRANCH"
    const val BRANCH_DAY = "BRANCH_DAY"
    const val MEDICAL_MISSION = "MEDICAL_MISSION"
    const val PROVINCIAL_TOUR = "PROVINCIAL_TOUR"
}

/**
 * #156 — the #92 locked per-element check: true iff [code] is held at exactly
 * [contextType]/[contextId]. The caller resolves the scope — BRANCH rows against
 * the selected branch (dashboard `canEdit` against [SessionState.selectedBranchId];
 * Finance per-element gates against the Finance surface's VIEWED branch, the branch
 * the backend gates via the day row), BRANCH_DAY rows against the day row's
 * branchDayId (future day-gates). A null [contextId] never matches: fail-closed
 * pre-clock-in and pre-day-selection.
 */
fun List<UserCapabilityResponse>.hasCapability(
    code: String,
    contextType: String,
    contextId: String?,
): Boolean =
    contextId != null &&
        any { it.capabilityCode == code && it.contextType == contextType && it.contextId == contextId }

/**
 * #156 — the #92 Q3 "some branch" route-gate semantics: true iff [code] is held at
 * any context (any contextType/contextId). Backend precedent: `CapabilityRepository.hasCapabilityAnyContext`.
 */
fun List<UserCapabilityResponse>.hasCapabilityAnyContext(code: String): Boolean = any { it.capabilityCode == code }

/**
 * #158 — true iff [code] is held at [contextType] with any contextId. The relief
 * shape: the caller holds a day grant (`EDIT_BRANCH_DATA` at `BRANCH_DAY`) — the
 * day-grant consumers route through [hasDayGrant].
 */
fun List<UserCapabilityResponse>.hasCapabilityAtContextType(
    code: String,
    contextType: String,
): Boolean = any { it.capabilityCode == code && it.contextType == contextType }

/**
 * #158 — the relief day-grant shape shared by the drawer, both NavHost gates and the
 * Finance screen: the caller holds [code] at BRANCH_DAY context (any day).
 */
fun List<UserCapabilityResponse>.hasDayGrant(code: String): Boolean =
    hasCapabilityAtContextType(code, CapabilityContext.BRANCH_DAY)
