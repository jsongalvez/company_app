package com.companyb.companyapp.identity

import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.session.dashboard.TodayScreenService
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.workforce.AttendanceService
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The start bundle gives what the reads it replaces give, with the same rights. */
class StartServiceTest : BasePostgresTest() {
    private val boss = TestFixtures.uuid()
    private val onDuty = TestFixtures.uuid()
    private val offDuty = TestFixtures.uuid()
    private val stranger = TestFixtures.uuid()
    private val branchA = TestFixtures.uuid()
    private val branchB = TestFixtures.uuid()

    override fun initTestData() {
        listOf(boss to "boss", onDuty to "on-duty", offDuty to "off-duty", stranger to "stranger")
            .forEach { (id, name) -> IdentityFixtures.insertTestUser(id, name) }
        BranchWorkforceFixtures.insertTestBranch(branchA, "Branch A")
        BranchWorkforceFixtures.insertTestBranch(branchB, "Branch B")
        listOf(onDuty to branchA, offDuty to branchA).forEachIndexed { i, (user, branch) ->
            BranchWorkforceFixtures.insertTestAssignment(
                userId = user,
                branchId = branch,
                slot = (i + 1).toShort(),
                assignedBy = boss,
            )
        }
        // Staff work at their branch: the day is read for them (a global grant does not count here).
        listOf(onDuty, offDuty).forEach {
            IdentityFixtures.grantCapability(
                it,
                CapabilityCodes.EDIT_BRANCH_DATA,
                CapabilityContextType.BRANCH,
                branchA,
                boss,
            )
        }
        AttendanceService.clockIn(TestFixtures.uuid(), branchA, onDuty)
    }

    private fun start(
        user: UUID,
        asked: UUID? = null,
        withToday: Boolean = true,
    ) = StartService.get(user, asked, withToday)

    @Test
    fun `carries the same people, rights and branches as the separate reads`() {
        val s = start(onDuty)
        assertEquals(MeService.getMe(onDuty), s.me)
        assertEquals(MeService.getCapabilities(onDuty), s.capabilities)
        assertEquals(MeService.getBranches(onDuty), s.branches)
        assertEquals(MeService.getActiveAttendance(onDuty).shift, s.shift)
    }

    @Test
    fun `opens on the branch the person is clocked in at when none is asked for`() {
        assertEquals(branchA.toString(), start(onDuty).branchId)
    }

    @Test
    fun `keeps the branch asked for when the person may use it`() {
        assertEquals(branchA.toString(), start(offDuty, asked = branchA).branchId)
    }

    @Test
    fun `ignores a branch the person has no part in`() {
        assertEquals(branchA.toString(), start(offDuty, asked = branchB).branchId)
    }

    @Test
    fun `a person with no branch gets no branch and no day`() {
        val s = start(stranger, asked = branchA)
        assertNull(s.branchId)
        assertNull(s.today)
        assertNull(s.todayScreen)
    }

    @Test
    fun `the day and Today's lists are the ones the separate reads give`() {
        val s = start(onDuty)
        assertNotNull(s.today)
        val screen = assertNotNull(s.todayScreen)
        assertNotNull(screen.dashboard)
        assertEquals(TodayScreenService.get(onDuty, branchA).roster?.size, screen.roster?.size)
    }

    @Test
    fun `a person who is not clocked in gets the day but Today's lists say why not`() {
        val s = start(offDuty)
        assertNotNull(s.today)
        val screen = assertNotNull(s.todayScreen)
        assertNull(screen.dashboard)
        assertEquals(TodayScreenService.NOT_CLOCKED_IN, screen.dashboardRefusal)
    }

    @Test
    fun `leaves Today's lists out unless the phone opens on Today`() {
        assertNull(start(onDuty, withToday = false).todayScreen)
    }
}
