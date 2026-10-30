package com.companyb.companyapp.session.dashboard

import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.session.BookingResponse
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.session.SessionRepository
import com.companyb.companyapp.session.VisitBookingService
import com.companyb.companyapp.workforce.AttendancePresence
import com.companyb.companyapp.workforce.AttendanceRosterEntry
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/** Each part is null when the caller may not see it; [dashboardRefusal] says why the dashboard is. */
data class TodayScreenData(
    val dashboard: DashboardData?,
    val dashboardRefusal: String?,
    val bookings: List<BookingResponse>?,
    val roster: List<AttendanceRosterEntry>?,
    val earlierWaitingDays: List<java.time.LocalDate>?,
)

/**
 * The Today screen in one transaction, each fact read once. The parts keep the rights the
 * three reads they replaced had: the dashboard needs a clock-in at this branch today,
 * bookings need the right to view the branch, the roster needs a home-branch membership.
 */
object TodayScreenService {
    const val NOT_CLOCKED_IN = "You are not clocked in at this branch"

    /** @throws NotFoundException if the branch does not exist. */
    fun get(
        callerId: UUID,
        branchId: UUID,
    ): TodayScreenData =
        transaction {
            BranchService.findById(branchId)
            val day = BranchDayService.findToday(branchId)
            val canView =
                Access.allows(callerId, Act.SEE_DAY, day?.let { Place.Day(it.id, branchId) } ?: Place.Branch(branchId))
            val presence = AttendancePresence.read(branchId, day?.id)
            val clockedIn = day != null && presence.isClockedIn(callerId)
            TodayScreenData(
                dashboard = if (clockedIn) DashboardService.readToday(callerId, branchId, day.id) else null,
                dashboardRefusal = if (clockedIn) null else NOT_CLOCKED_IN,
                bookings = if (canView) VisitBookingService.upcoming(branchId) else null,
                roster = if (presence.isMember(callerId)) presence.entries() else null,
                earlierWaitingDays =
                    if (canView) {
                        SessionRepository.findEarlierWaitingDays(branchId, BranchDayService.currentOperationalDate())
                    } else {
                        null
                    },
            )
        }
}
