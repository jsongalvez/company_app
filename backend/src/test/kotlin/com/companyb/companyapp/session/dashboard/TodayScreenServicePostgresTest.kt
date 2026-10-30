package com.companyb.companyapp.session.dashboard

import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.database.JdbcTiming
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.logging.RequestQueryTally
import com.companyb.companyapp.session.BookingInput
import com.companyb.companyapp.session.VisitBookingService
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import com.companyb.companyapp.workforce.AttendanceService
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Today screen is one read: the same answers as the three it replaced, in few queries. */
class TodayScreenServicePostgresTest : BasePostgresTest() {
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()
    private val onDuty = TestFixtures.uuid()
    private val member = TestFixtures.uuid()
    private val elsewhere = TestFixtures.uuid()
    private val globalViewer = TestFixtures.uuid()
    private val outsider = TestFixtures.uuid()
    private val sessionIds = List(3) { TestFixtures.uuid() }

    override fun initTestData() {
        listOf(onDuty, member, elsewhere, globalViewer, outsider).forEachIndexed { i, id ->
            IdentityFixtures.insertTestUser(id, "today-$i")
        }
        BranchWorkforceFixtures.insertTestBranch(branchId, "Today Branch $branchId")
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Other Branch $otherBranchId")
        BranchWorkforceFixtures.insertTestAssignment(
            userId = onDuty,
            branchId = branchId,
            slot = 1,
            assignedBy = onDuty,
        )
        BranchWorkforceFixtures.insertTestAssignment(
            userId = member,
            branchId = branchId,
            slot = 2,
            assignedBy = onDuty,
        )
        IdentityFixtures.grantCapability(
            userId = onDuty,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = TestFixtures.uuid(),
        )
        IdentityFixtures.grantCapability(
            userId = globalViewer,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = TestFixtures.uuid(),
        )
        AttendanceService.clockIn(TestFixtures.uuid(), branchId, onDuty)
        AttendanceService.clockIn(TestFixtures.uuid(), otherBranchId, elsewhere)
        val day = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        sessionIds.forEach {
            SessionClientFixtures.insertTestSession(
                it,
                SessionClientFixtures.insertTestClient(),
                day,
            )
        }
        VisitBookingService.create(
            onDuty,
            TestFixtures.uuid(),
            branchId,
            SessionClientFixtures.insertTestClient(),
            BookingInput(BranchDayService.currentOperationalDate().plusDays(1), null, null),
        )
    }

    @Test
    fun `an on-duty member gets all three parts in a handful of queries`() {
        val queries = countQueries { TodayScreenService.get(onDuty, branchId) }
        val screen = TodayScreenService.get(onDuty, branchId)

        assertEquals(
            sessionIds.toSet(),
            screen.dashboard!!
                .sessions
                .map { it.id }
                .toSet(),
        )
        assertEquals(1, screen.bookings!!.size)
        assertEquals(listOf(true, false), screen.roster!!.map { it.present })
        assertNull(screen.dashboardRefusal)
        assertTrue(queries <= QUERY_BUDGET, "Today screen ran $queries queries, budget $QUERY_BUDGET")
    }

    @Test
    fun `a member who has not clocked in is told so but still sees the roster and bookings`() {
        val screen = TodayScreenService.get(member, branchId)

        assertNull(screen.dashboard)
        assertEquals("You are not clocked in at this branch", screen.dashboardRefusal)
        assertNull(screen.bookings)
        assertEquals(2, screen.roster!!.size)
    }

    @Test
    fun `someone clocked in at another branch gets no dashboard here`() {
        val screen = TodayScreenService.get(elsewhere, branchId)

        assertNull(screen.dashboard)
        assertNotNull(screen.dashboardRefusal)
        assertNull(screen.roster)
        assertNull(screen.bookings)
    }

    @Test
    fun `an all-branches viewer sees bookings but is not on the roster`() {
        val screen = TodayScreenService.get(globalViewer, branchId)

        assertNull(screen.dashboard)
        assertEquals(1, screen.bookings!!.size)
        assertNull(screen.roster)
    }

    // Feedback d6cc83cf: a session left Waiting on an earlier day no longer blocks today, so Today
    // names the open days that still hold one, for the branch to mark. No clock-in needed to see it.
    @Test
    fun `Today names earlier open days that still hold a Waiting session`() {
        val today = BranchDayService.currentOperationalDate()

        fun sessionOn(
            dayId: UUID,
            status: SessionStatus = SessionStatus.PENDING,
        ) = SessionClientFixtures.insertTestSession(
            TestFixtures.uuid(),
            SessionClientFixtures.insertTestClient(),
            dayId,
            sessionStatus = status,
        )
        sessionOn(BranchWorkforceFixtures.createBranchDayForDate(branchId, today.minusDays(2)))
        sessionOn(BranchWorkforceFixtures.createBranchDayForDate(branchId, today.minusDays(1)), SessionStatus.COMPLETED)
        sessionOn(BranchWorkforceFixtures.createRemittedBranchDay(branchId, today.minusDays(3)))
        sessionOn(BranchWorkforceFixtures.createBranchDayForDate(otherBranchId, today.minusDays(4)))

        assertEquals(listOf(today.minusDays(2)), TodayScreenService.get(globalViewer, branchId).earlierWaitingDays)
        assertNull(TodayScreenService.get(outsider, branchId).earlierWaitingDays)
    }

    @Test
    fun `someone with no part in the branch sees nothing`() {
        val screen = TodayScreenService.get(outsider, branchId)

        assertNull(screen.dashboard)
        assertNull(screen.bookings)
        assertNull(screen.roster)
    }

    @Test
    fun `an unknown branch is not found`() {
        assertFailsWith<NotFoundException> { TodayScreenService.get(onDuty, TestFixtures.uuid()) }
    }

    private fun countQueries(block: () -> Unit): Int {
        val source = JdbcTiming.wrap(TestDatabaseLifecycle.requireTestDataSource())
        Database.connect(source)
        try {
            RequestQueryTally.startRequest()
            block()
            return RequestQueryTally.snapshot().queries
        } finally {
            RequestQueryTally.endRequest()
            Database.connect(TestDatabaseLifecycle.requireTestDataSource())
        }
    }

    private companion object {
        // 13: one read names the earlier days still holding a Waiting session (feedback d6cc83cf).
        const val QUERY_BUDGET = 13
    }
}
