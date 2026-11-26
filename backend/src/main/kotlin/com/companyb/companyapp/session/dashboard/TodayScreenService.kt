package com.companyb.companyapp.session.dashboard

import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.branchday.SiteHub
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.session.BookingResponse
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.observability.traced
import com.companyb.companyapp.session.SessionRepository
import com.companyb.companyapp.session.VisitBookingService
import com.companyb.companyapp.workforce.AttendancePresence
import com.companyb.companyapp.workforce.AttendanceRosterEntry
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Each part is null when the caller may not see it; [dashboardRefusal] says why the dashboard is. */
data class TodayScreenData(
    val dashboard: DashboardData?,
    val dashboardRefusal: String?,
    val bookings: List<BookingResponse>?,
    val roster: List<AttendanceRosterEntry>?,
    val earlierWaitingDays: List<LocalDate>?,
    val siteHub: SiteHub.Ticket? = null,
    val date: LocalDate,
)

/**
 * The Today screen in one transaction, each fact read once. The parts keep the rights the
 * three reads they replaced had: the dashboard needs a clock-in at this branch today,
 * bookings need the right to view the branch, the roster needs a home-branch membership.
 * A past day's document goes to whoever may see that day, as its session list does, and
 * holds only that day's parts: its sessions and its roster.
 */
object TodayScreenService {
    const val NOT_CLOCKED_IN = "You are not clocked in at this branch"

    /**
     * The document for [date], today when null. [date] is never after today (the route refuses it).
     * @throws NotFoundException if the branch does not exist.
     * @throws com.companyb.companyapp.exception.ForbiddenException for a past day the caller may not see.
     */
    fun get(
        callerId: UUID,
        branchId: UUID,
        date: LocalDate? = null,
    ): TodayScreenData =
        traced("TodayScreenService.get") {
            transaction {
                BranchService.findById(branchId)
                val today = BranchDayService.currentOperationalDate()
                if (date != null && date != today) return@transaction pastDay(callerId, branchId, date)
                val day = BranchDayService.findByBranchAndDate(branchId, today)
                val canView =
                    Access.allows(
                        callerId,
                        Act.SEE_DAY,
                        day?.let { Place.Day(it.id, branchId) } ?: Place.Branch(branchId),
                    )
                val presence = AttendancePresence.read(branchId, day?.id)
                val clockedIn = day != null && presence.isClockedIn(callerId)
                TodayScreenData(
                    dashboard = if (clockedIn) DashboardService.readToday(callerId, branchId, day.id) else null,
                    dashboardRefusal = if (clockedIn) null else NOT_CLOCKED_IN,
                    bookings = if (canView) VisitBookingService.upcoming(branchId) else null,
                    roster = if (presence.isMember(callerId)) presence.entries() else null,
                    earlierWaitingDays =
                        if (canView) {
                            SessionRepository.findEarlierWaitingDays(branchId, today)
                        } else {
                            null
                        },
                    // Whoever may work the day, roster row or not: at a tour that is every Owner and Manager.
                    siteHub =
                        if (Access.allows(
                                callerId,
                                Act.WORK_DAY,
                                day?.let { Place.Day(it.id, branchId) } ?: Place.Branch(branchId),
                            )
                        ) {
                            SiteHub.ticket(branchId, today)
                        } else {
                            null
                        },
                    date = today,
                )
            }
        }

    private fun pastDay(
        callerId: UUID,
        branchId: UUID,
        date: LocalDate,
    ): TodayScreenData {
        val day = BranchDayService.findByBranchAndDate(branchId, date)
        Access.require(callerId, Act.SEE_DAY, day?.let { Place.Day(it.id, branchId) } ?: Place.Branch(branchId))
        val presence = AttendancePresence.read(branchId, day?.id)
        return TodayScreenData(
            dashboard = day?.let { DashboardService.readToday(callerId, branchId, it.id) } ?: NO_SESSIONS,
            dashboardRefusal = null,
            bookings = null,
            roster = if (presence.isMember(callerId)) presence.entries() else null,
            earlierWaitingDays = null,
            date = date,
        )
    }

    /** A past day with no day row had no sessions. */
    private val NO_SESSIONS =
        DashboardData(
            emptyList(),
            emptyMap(),
            emptySet(),
            emptyList(),
            emptyList(),
            CommissionSummary(BigDecimal.ZERO, 0),
        )
}
