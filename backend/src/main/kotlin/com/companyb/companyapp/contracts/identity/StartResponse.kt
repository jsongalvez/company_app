package com.companyb.companyapp.contracts.identity

import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.contracts.branch.BranchResponse
import com.companyb.companyapp.contracts.branch.MeBranchResponse
import com.companyb.companyapp.contracts.branchday.BranchDayTodayResponse
import com.companyb.companyapp.contracts.session.TodayScreenResponse
import com.companyb.companyapp.contracts.workforce.ActiveShiftResponse
import kotlinx.serialization.Serializable

/**
 * Everything the phone needs to open the app, in one answer: who you are, your rights and branches,
 * the branch it opens on, that branch's day, your open shift, the unread alert count and, when the
 * phone asked for it, the Today screen's lists. Each part is what the single read it replaces gives,
 * with the same rights: [today] and [todayScreen] are null where those reads would refuse.
 */
@Serializable
data class StartResponse(
    val me: MeResponse,
    val capabilities: List<UserCapabilityResponse>,
    val branches: List<MeBranchResponse>,
    val accessible: List<BranchResponse>,
    /** The branch to open on: the one asked for if usable, else where the person is clocked in, else their first. */
    val branchId: String?,
    val today: BranchDayTodayResponse?,
    val shift: ActiveShiftResponse?,
    val unreadCount: Int,
    val todayScreen: TodayScreenResponse?,
)
