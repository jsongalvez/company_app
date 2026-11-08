package com.companyb.companyapp.notification

import com.companyb.companyapp.authorization.Role
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.identity.RoleTable
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.notification.NotificationCreateParams
import com.companyb.companyapp.notification.NotificationRepository
import com.companyb.companyapp.notification.NotificationTable
import com.companyb.companyapp.session.SessionBaseRateRepository
import com.companyb.companyapp.session.SessionService
import com.companyb.companyapp.session.SessionVoidTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import com.companyb.companyapp.workforce.UserBranchAssignmentCreateParams
import com.companyb.companyapp.workforce.UserBranchAssignmentRepository
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NextAppointmentSchedulerPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val coordinatorId = TestFixtures.uuid()
    private val nonCoordinatorId = TestFixtures.uuid()
    private val unassignedCoordinatorId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()

    private val manilaZone: ZoneId = ZoneId.of("Asia/Manila")
    private val sourceId = TestFixtures.uuid()

    private lateinit var branchDayId: UUID
    private val rateId = TestFixtures.uuid()
    private val secondSessionRateId = TestFixtures.uuid()
    private val subsequentRateId = TestFixtures.uuid()

    private val fixedClock: Clock
        get() {
            val now = TestFixtures.today.atTime(7, 0).toInstant(ZoneOffset.UTC)
            return Clock.fixed(now, manilaZone)
        }

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "scheduler-caller")
        IdentityFixtures.insertTestUser(coordinatorId, "scheduler-coordinator")
        IdentityFixtures.insertTestUser(nonCoordinatorId, "scheduler-practitioner")
        IdentityFixtures.insertTestUser(unassignedCoordinatorId, "scheduler-unassigned-coordinator")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Test Scheduler Branch ${branchId.toString().take(8)}")
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Other Branch ${otherBranchId.toString().take(8)}")
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        SessionClientFixtures.insertTestClient(clientId)
        IdentityFixtures.member(callerId, branchId)
        SessionClientFixtures.insertTestBaseRate(rateId, branchId, callerId)
        SessionClientFixtures.insertTestBaseRate(
            secondSessionRateId,
            branchId,
            callerId,
            SessionType.SECOND_SESSION,
        )
        SessionClientFixtures.insertTestBaseRate(
            subsequentRateId,
            branchId,
            callerId,
            SessionType.SUBSEQUENT,
        )
    }

    @Test
    fun `scheduler creates notifications for coordinator with assignment`() {
        IdentityFixtures.hold(coordinatorId, Role.COORDINATOR)
        insertUserBranchAssignment(coordinatorId, branchId)

        val sessionId = createCompletedSessionWithAppointment(twoDaysFromNow())

        val count = NextAppointmentScheduler.run(fixedClock)
        assertEquals(1, count)

        val notifications =
            transaction {
                NotificationTable
                    .selectAll()
                    .where { NotificationTable.sessionId eq sessionId }
                    .map { it[NotificationTable.userId] }
            }
        assertTrue(notifications.contains(coordinatorId))
    }

    @Test
    fun `scheduler derives notification capability for assigned coordinator role`() {
        IdentityFixtures.hold(coordinatorId, Role.COORDINATOR)
        insertUserBranchAssignment(coordinatorId, branchId)

        val sessionId = createCompletedSessionWithAppointment(twoDaysFromNow())

        val count = NextAppointmentScheduler.run(fixedClock)
        assertEquals(1, count)

        val notifications =
            transaction {
                NotificationTable
                    .selectAll()
                    .where { NotificationTable.sessionId eq sessionId }
                    .map { it[NotificationTable.userId] }
            }
        assertEquals(listOf(coordinatorId), notifications)
    }

    @Test
    fun `scheduler skips if all notifications already exist`() {
        IdentityFixtures.hold(coordinatorId, Role.COORDINATOR)
        insertUserBranchAssignment(coordinatorId, branchId)

        val sessionId = createCompletedSessionWithAppointment(twoDaysFromNow())

        NextAppointmentScheduler.run(fixedClock)
        val secondRun = NextAppointmentScheduler.run(fixedClock)
        assertEquals(0, secondRun)

        val count =
            transaction {
                NotificationTable
                    .selectAll()
                    .where { NotificationTable.sessionId eq sessionId }
                    .count()
            }
        assertEquals(1, count.toInt())
    }

    @Test
    fun `notification batch count matches rows inserted when input repeats a pair`() {
        val sessionId = createCompletedSessionWithAppointment(twoDaysFromNow())
        val targetDate = twoDaysFromNow()
        val params =
            NotificationCreateParams(
                sessionId = sessionId,
                userId = coordinatorId,
                branchId = branchId,
                message = "Upcoming appointment",
                eventType = NextAppointmentScheduler.APPOINTMENT_REMINDER,
                sourceId = sessionId,
                targetDate = targetDate,
                dedupKey = "APPT:$sessionId:$targetDate",
            )

        val count = NotificationRepository.insertBatch(listOf(params, params))

        assertEquals(1, count)
        assertEquals(
            1,
            transaction {
                NotificationTable
                    .selectAll()
                    .where { NotificationTable.sessionId eq sessionId }
                    .count()
                    .toInt()
            },
        )
    }

    @Test
    fun `scheduler does not notify non-coordinator users`() {
        insertUserBranchAssignment(nonCoordinatorId, branchId)

        createCompletedSessionWithAppointment(twoDaysFromNow())

        val count = NextAppointmentScheduler.run(fixedClock)
        assertEquals(0, count)
    }

    @Test
    fun `scheduler does not notify coordinator without active assignment`() {
        IdentityFixtures.hold(coordinatorId, Role.COORDINATOR)

        createCompletedSessionWithAppointment(twoDaysFromNow())

        val count = NextAppointmentScheduler.run(fixedClock)
        assertEquals(0, count)
    }

    @Test
    fun `scheduler does not notify coordinator assigned to different branch`() {
        IdentityFixtures.hold(coordinatorId, Role.COORDINATOR)
        insertUserBranchAssignment(coordinatorId, otherBranchId)

        createCompletedSessionWithAppointment(twoDaysFromNow())

        val count = NextAppointmentScheduler.run(fixedClock)
        assertEquals(0, count)
    }

    @Test
    fun `a coordinator gets alerts at every branch they are assigned to`() {
        IdentityFixtures.hold(coordinatorId, Role.COORDINATOR)
        insertUserBranchAssignment(coordinatorId, branchId)
        insertUserBranchAssignment(coordinatorId, otherBranchId)

        val coordinatorsByBranch =
            NextAppointmentRepository.findActiveCoordinatorsForBranches(listOf(branchId, otherBranchId))

        assertEquals(listOf(coordinatorId), coordinatorsByBranch[branchId])
        assertEquals(listOf(coordinatorId), coordinatorsByBranch[otherBranchId])
    }

    @Test
    fun `scheduler does not notify manager without coordinator capability`() {
        IdentityFixtures.hold(coordinatorId, Role.COORDINATOR)
        insertUserBranchAssignment(coordinatorId, branchId)
        val managerId = unassignedCoordinatorId
        insertUserBranchAssignment(managerId, branchId)

        createCompletedSessionWithAppointment(twoDaysFromNow())

        val count = NextAppointmentScheduler.run(fixedClock)
        assertEquals(1, count)
    }

    @Test
    fun `scheduler does not include voided sessions`() {
        IdentityFixtures.hold(coordinatorId, Role.COORDINATOR)
        insertUserBranchAssignment(coordinatorId, branchId)

        val sessionId = createCompletedSessionWithAppointment(twoDaysFromNow())
        voidSession(sessionId)

        val count = NextAppointmentScheduler.run(fixedClock)
        assertEquals(0, count)
    }

    @Test
    fun `scheduler only includes COMPLETED sessions`() {
        IdentityFixtures.hold(coordinatorId, Role.COORDINATOR)
        insertUserBranchAssignment(coordinatorId, branchId)

        val completedSessionId = createCompletedSessionWithAppointment(twoDaysFromNow())
        createSession(twoDaysFromNow())

        val count = NextAppointmentScheduler.run(fixedClock)
        assertEquals(1, count)

        val notifications =
            transaction {
                NotificationTable.selectAll().map { it[NotificationTable.sessionId] }
            }
        assertTrue(notifications.contains(completedSessionId))
    }

    @Test
    fun `scheduler only matches exact target date`() {
        IdentityFixtures.hold(coordinatorId, Role.COORDINATOR)
        insertUserBranchAssignment(coordinatorId, branchId)

        createCompletedSessionWithAppointment(twoDaysFromNow())
        createCompletedSessionWithAppointment(twoDaysFromNow().plusDays(1))

        val count = NextAppointmentScheduler.run(fixedClock)
        assertEquals(1, count)
    }

    @Test
    fun `targetDate returns now plus 2 days`() {
        val now = LocalDate.of(2026, 7, 14)
        val target = NextAppointmentScheduler.targetDate(now)
        assertEquals(LocalDate.of(2026, 7, 16), target)
    }

    @Test
    fun `nextRunDelayMs returns positive delay`() {
        val now = java.time.ZonedDateTime.of(2026, 7, 14, 6, 0, 0, 0, manilaZone)
        val delay = NextAppointmentScheduler.nextRunDelayMs(now)

        assertEquals(
            java.time.Duration
                .ofHours(1)
                .toMillis(),
            delay,
        )
    }

    @Test
    fun `nextRunDelayMs wraps to next day when past 7 AM`() {
        val now = java.time.ZonedDateTime.of(2026, 7, 14, 8, 0, 0, 0, manilaZone)
        val delay = NextAppointmentScheduler.nextRunDelayMs(now)
        assertEquals(
            java.time.Duration
                .ofHours(23)
                .toMillis(),
            delay,
        )
    }

    private fun twoDaysFromNow(): LocalDate = TestFixtures.today.plusDays(2)

    private fun createCompletedSessionWithAppointment(appointmentDate: LocalDate): UUID {
        val sessionId = TestFixtures.uuid()
        SessionService.create(
            callerId = callerId,
            id = sessionId,
            clientId = clientId,
            branchId = branchId,
            isWalkIn = false,
            requestedPractitionerId = null,
            finalPrice = BigDecimal("2500.00"),
            remarks = null,
            otherConcerns = null,
            nextAppointmentDate = appointmentDate,
            requireClockIn = false,
        )
        SessionService.updateStatus(callerId, sessionId, SessionStatus.COMPLETED, 1)
        return sessionId
    }

    private fun createSession(appointmentDate: LocalDate): UUID {
        val sessionId = TestFixtures.uuid()
        SessionService.create(
            callerId = callerId,
            id = sessionId,
            clientId = clientId,
            branchId = branchId,
            isWalkIn = false,
            requestedPractitionerId = null,
            finalPrice = BigDecimal("2500.00"),
            remarks = null,
            otherConcerns = null,
            nextAppointmentDate = appointmentDate,
            requireClockIn = false,
        )
        return sessionId
    }

    private fun voidSession(sessionId: UUID) {
        transaction {
            SessionVoidTable.insertIgnore {
                it[SessionVoidTable.id] =
                    TestFixtures.uuid()
                it[SessionVoidTable.sessionId] =
                    sessionId
                it[SessionVoidTable.voidedBy] =
                    callerId
                it[SessionVoidTable.voidReason] =
                    "test void"
            }
        }
    }

    private fun insertUserBranchAssignment(
        userId: UUID,
        branchId: UUID,
    ) {
        transaction {
            UserBranchAssignmentRepository.createInTransaction(
                UserBranchAssignmentCreateParams(
                    id = TestFixtures.uuid(),
                    userId = userId,
                    branchId = branchId,
                    slot = 1,
                    assignedBy = callerId,
                ),
            )
        }
    }
}
