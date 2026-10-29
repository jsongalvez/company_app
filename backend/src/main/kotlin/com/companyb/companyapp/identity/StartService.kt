package com.companyb.companyapp.identity

import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.branch.BranchRoutes
import com.companyb.companyapp.branchday.BranchDayRoutes
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.branch.BranchClockInStatus
import com.companyb.companyapp.contracts.identity.StartResponse
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.notification.NotificationService
import com.companyb.companyapp.session.dashboard.TodayScreenRoutes
import com.companyb.companyapp.session.dashboard.TodayScreenService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * What the app needs to open, read once. Each part comes from the service behind the read it replaces
 * (`/api/me`, capabilities, branches, accessible, today, active-attendance, unread-count, today-screen),
 * so the rights are the same; a part the caller may not see is null.
 */
private val logger = KotlinLogging.logger {}

object StartService {
    fun get(
        callerId: UUID,
        asked: UUID?,
        withToday: Boolean,
    ): StartResponse =
        transaction {
            val branches = MeService.getBranches(callerId)
            val accessible = BranchRoutes.accessibleBranches(callerId)
            val known = branches.map { it.branchId } + accessible.map { it.id }
            val branchId =
                (
                    asked?.takeIf { it.toString() in known }?.toString()
                        ?: branches.firstOrNull { it.clockInStatus == BranchClockInStatus.CLOCKED_IN_HERE }?.branchId
                        ?: branches.firstOrNull()?.branchId
                        ?: accessible.firstOrNull()?.id
                )?.let(UUID::fromString)
            val today = branchId?.let { dayAt(callerId, it) }
            StartResponse(
                me = MeService.getMe(callerId),
                capabilities = MeService.getCapabilities(callerId),
                branches = branches,
                accessible = accessible,
                branchId = branchId?.toString(),
                today = today,
                shift = MeService.getActiveAttendance(callerId).shift,
                unreadCount = NotificationService.countUnread(callerId),
                todayScreen =
                    if (withToday &&
                        branchId != null
                    ) {
                        TodayScreenRoutes.response(TodayScreenService.get(callerId, branchId))
                    } else {
                        null
                    },
            )
        }

    /** The branch's day for people who work there (or hold a relief grant for it); null for those who only oversee. */
    private fun dayAt(
        callerId: UUID,
        branchId: UUID,
    ) = try {
        Access.require(callerId, Act.WORK_DAY, Place.today(branchId))
        BranchDayRoutes.todayResponse(branchId)
    } catch (e: ForbiddenException) {
        logger.debug { "[START] no day shown at $branchId for $callerId: ${e.message}" }
        null
    }
}
