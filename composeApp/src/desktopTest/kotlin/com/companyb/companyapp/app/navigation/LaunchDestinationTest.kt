package com.companyb.companyapp.app.navigation

import com.companyb.companyapp.app.ClockContext
import com.companyb.companyapp.contracts.branch.BranchClockInStatus
import com.companyb.companyapp.contracts.identity.MeResponse
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.workforce.branch.showContinueFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #669 — launch/login entry-point decisions: a restored clock opens Sessions directly,
 * a validated session without a shift opens branch selection, and only an
 * already-clocked-here row offers resume (never a second clock-in).
 */
class LaunchDestinationTest {
    private val user =
        MeResponse(
            id = "user-1",
            username = "practitioner",
            displayName = "Practitioner",
            status = UserStatus.ACTIVE,
            createdAt = "2026-01-01T00:00:00Z",
        )
    private val clock =
        ClockContext(
            branchId = "branch-1",
            branchName = "Branch A",
            attendanceId = "attendance-1",
            branchDayId = "day-1",
            isRelief = false,
        )

    @Test
    fun `restored clock opens Sessions directly`() {
        assertEquals(Route.Dashboard(), startDestinationFor(user, clock))
    }

    @Test
    fun `validated session without a shift opens branch selection`() {
        assertEquals(Route.BranchSelect, startDestinationFor(user, null))
    }

    @Test
    fun `no validated user opens Login`() {
        assertEquals(Route.Login, startDestinationFor(null, null))
    }

    @Test
    fun `clock without identity fails closed to Login`() {
        assertEquals(Route.Login, startDestinationFor(null, clock))
    }

    @Test
    fun `already-clocked-here row offers resume`() {
        assertTrue(showContinueFor(BranchClockInStatus.CLOCKED_IN_HERE))
    }

    @Test
    fun `elsewhere row offers no resume action`() {
        assertFalse(showContinueFor(BranchClockInStatus.CLOCKED_IN_ELSEWHERE))
    }

    @Test
    fun `not-clocked-in row keeps Clock In instead`() {
        assertFalse(showContinueFor(BranchClockInStatus.NOT_CLOCKED_IN))
    }
}
