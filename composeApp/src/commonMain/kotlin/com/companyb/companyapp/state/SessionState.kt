package com.companyb.companyapp.state

import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.dto.ClockInResponse
import com.companyb.companyapp.dto.MeResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** #498 — one clock-in context: branch + attendance + day + relief flag publish together. */
data class ClockContext(
    val branchId: String,
    val branchName: String,
    val attendanceId: String,
    val branchDayId: String,
    val isRelief: Boolean,
)

/** #498 — the single atomic session snapshot: user + full capability rows + nullable clock. */
data class SessionSnapshot(
    val user: MeResponse? = null,
    val capabilities: List<UserCapabilityResponse> = emptyList(),
    val clock: ClockContext? = null,
)

object SessionState {
    private val _snapshot = MutableStateFlow(SessionSnapshot())
    val snapshot: StateFlow<SessionSnapshot> = _snapshot.asStateFlow()

    // #94 Q3c(ii) — mid-session 401 surfaces "session expired" once on the Login screen
    // (launch-validation 401 stays silent). App.kt sets it; LoginScreen consumes + clears.
    private val _expiredNotice = MutableStateFlow(false)
    val expiredNotice: StateFlow<Boolean> = _expiredNotice.asStateFlow()

    /** Publishes bootstrap user + initial capabilities as one value; clears any stale clock. */
    fun setBootstrapState(
        user: MeResponse,
        caps: List<UserCapabilityResponse>,
    ) {
        _snapshot.value = SessionSnapshot(user = user, capabilities = caps, clock = null)
    }

    /**
     * #498 — clock-in transition: publishes the complete clock context at once, preserving
     * the authenticated user and the pre-refresh capabilities (the ADR-0021 refresh lands
     * separately via [setCapabilities]; capabilities may legitimately await refresh).
     */
    fun setClockedIn(
        branchId: String,
        branchName: String,
        clockIn: ClockInResponse,
    ) {
        val current = _snapshot.value
        _snapshot.value =
            current.copy(
                clock =
                    ClockContext(
                        branchId = branchId,
                        branchName = branchName,
                        attendanceId = clockIn.id,
                        branchDayId = clockIn.branchDayId,
                        isRelief = clockIn.isRelief,
                    ),
            )
    }

    /** Capability refresh for the current context; preserves user + clock. */
    fun setCapabilities(caps: List<UserCapabilityResponse>) {
        _snapshot.value = _snapshot.value.copy(capabilities = caps)
    }

    /**
     * #147 (Q3) — clock-out: user stays logged in, clock + capabilities clear together
     * (fail-closed; next clock-in refreshes wholesale via ADR-0021).
     */
    fun clearClockState() {
        _snapshot.value = _snapshot.value.copy(clock = null, capabilities = emptyList())
    }

    fun setExpiredNotice(value: Boolean) {
        _expiredNotice.value = value
    }

    fun clear() {
        _snapshot.value = SessionSnapshot()
    }
}

/** Backend CapabilityService.GLOBAL_CONTEXT_ID (UUID 0) as a string —
 * the contextId of GLOBAL-scoped capability rows. */
const val GLOBAL_CAPABILITY_CONTEXT_ID = "00000000-0000-0000-0000-000000000000"

/**
 * #156 — the #92 locked per-element check: true iff [code] is held at exactly
 * [contextType]/[contextId]. The caller resolves the scope — BRANCH rows against
 * the clocked-in branch; BRANCH_DAY rows against the day row's
 * branchDayId (future day-gates). A null [contextId] never matches: fail-closed
 * pre-clock-in and pre-day-selection.
 */
fun List<UserCapabilityResponse>.hasCapability(
    code: String,
    contextType: CapabilityContextType,
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
    contextType: CapabilityContextType,
): Boolean = any { it.capabilityCode == code && it.contextType == contextType }

/**
 * #158 — the relief day-grant shape shared by the drawer, both NavHost gates and the
 * Finance screen: the caller holds [code] at BRANCH_DAY context (any day).
 */
fun List<UserCapabilityResponse>.hasDayGrant(code: String): Boolean =
    hasCapabilityAtContextType(code, CapabilityContextType.BRANCH_DAY)

/**
 * #158/P5 — the day-scoped gate shape shared by the Finance VM and DayEditor (the
 * backend's `requireBranchOrBranchDayCapability` mirror): [code] at [branchId] (BRANCH)
 * OR at [dayId] (BRANCH_DAY — the relief grant for that day). A null [dayId] fails the
 * day leg closed. The backend has the third implementation of the same OR
 * (`CapabilityRepository.hasCapabilityForBranchDay`) — this matcher is the frontend's
 * single copy.
 */
fun List<UserCapabilityResponse>.hasBranchOrDayCapability(
    code: String,
    branchId: String?,
    dayId: String?,
): Boolean =
    hasCapability(code, CapabilityContextType.BRANCH, branchId) ||
        (dayId != null && hasCapability(code, CapabilityContextType.BRANCH_DAY, dayId))
