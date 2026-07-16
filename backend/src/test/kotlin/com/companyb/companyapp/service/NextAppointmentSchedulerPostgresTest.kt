package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.NotificationTable
import com.companyb.companyapp.repository.model.SessionStatus
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.BranchDayService
import com.companyb.companyapp.test.DatabaseTestHelper
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NextAppointmentSchedulerPostgresTest {
    private val callerId = UUID.randomUUID()
    private val coordinatorId = UUID.randomUUID()
    private val nonCoordinatorId = UUID.randomUUID()
    private val unassignedCoordinatorId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val otherBranchId = UUID.randomUUID()
    private val clientId = UUID.randomUUID()

    private val manilaZone: ZoneId = ZoneId.of("Asia/Manila")
    private val sourceId = UUID.randomUUID()

    private lateinit var branchDayId: UUID

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows()
        insertUser(callerId, "scheduler-caller")
        insertUser(coordinatorId, "scheduler-coordinator")
        insertUser(nonCoordinatorId, "scheduler-practitioner")
        insertUser(unassignedCoordinatorId, "scheduler-unassigned-coordinator")
        insertBranch(branchId, "Test Scheduler Branch ${branchId.toString().take(8)}")
        insertBranch(otherBranchId, "Other Branch ${otherBranchId.toString().take(8)}")
        branchDayId = createBranchDay(branchId)
        insertClient(clientId)
        grantEditBranchData(callerId)
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows()
        }
    }

    @Test
    fun `scheduler creates notifications for coordinator with assignment`() {
        grantReceiveNextAppointmentAlerts(coordinatorId, branchId)
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
    fun `scheduler skips if all notifications already exist`() {
        grantReceiveNextAppointmentAlerts(coordinatorId, branchId)
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
    fun `scheduler does not notify non-coordinator users`() {
        insertUserBranchAssignment(nonCoordinatorId, branchId)

        createCompletedSessionWithAppointment(twoDaysFromNow())

        val count = NextAppointmentScheduler.run(fixedClock)
        assertEquals(0, count)
    }

    @Test
    fun `scheduler does not notify coordinator without active assignment`() {
        grantReceiveNextAppointmentAlerts(coordinatorId, branchId)

        createCompletedSessionWithAppointment(twoDaysFromNow())

        val count = NextAppointmentScheduler.run(fixedClock)
        assertEquals(0, count)
    }

    @Test
    fun `scheduler does not notify coordinator assigned to different branch`() {
        grantReceiveNextAppointmentAlerts(coordinatorId, branchId)
        insertUserBranchAssignment(coordinatorId, otherBranchId)

        createCompletedSessionWithAppointment(twoDaysFromNow())

        val count = NextAppointmentScheduler.run(fixedClock)
        assertEquals(0, count)
    }

    @Test
    fun `scheduler does not notify manager without coordinator capability`() {
        grantReceiveNextAppointmentAlerts(coordinatorId, branchId)
        insertUserBranchAssignment(coordinatorId, branchId)
        val managerId = unassignedCoordinatorId
        insertUserBranchAssignment(managerId, branchId)

        createCompletedSessionWithAppointment(twoDaysFromNow())

        val count = NextAppointmentScheduler.run(fixedClock)
        assertEquals(1, count)
    }

    @Test
    fun `scheduler does not include voided sessions`() {
        grantReceiveNextAppointmentAlerts(coordinatorId, branchId)
        insertUserBranchAssignment(coordinatorId, branchId)

        val sessionId = createCompletedSessionWithAppointment(twoDaysFromNow())
        voidSession(sessionId)

        val count = NextAppointmentScheduler.run(fixedClock)
        assertEquals(0, count)
    }

    @Test
    fun `scheduler only includes COMPLETED sessions`() {
        grantReceiveNextAppointmentAlerts(coordinatorId, branchId)
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
        grantReceiveNextAppointmentAlerts(coordinatorId, branchId)
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

    private val fixedClock: Clock
        get() {
            val now = LocalDate.now(manilaZone).atTime(7, 0).toInstant(ZoneOffset.UTC)
            return Clock.fixed(now, manilaZone)
        }

    private fun twoDaysFromNow(): LocalDate = LocalDate.now(manilaZone).plusDays(2)

    @Suppress("ThrowsCount")
    private fun createCompletedSessionWithAppointment(appointmentDate: LocalDate): UUID {
        val sessionId = UUID.randomUUID()
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
            bookedAt = null,
            nextAppointmentDate = appointmentDate,
        )
        SessionService.updateStatus(callerId, sessionId, SessionStatus.COMPLETED, 1)
        return sessionId
    }

    private fun createSession(appointmentDate: LocalDate): UUID {
        val sessionId = UUID.randomUUID()
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
            bookedAt = null,
            nextAppointmentDate = appointmentDate,
        )
        return sessionId
    }

    private fun voidSession(sessionId: UUID) {
        transaction {
            com.companyb.companyapp.repository.model.SessionVoidTable.insertIgnore {
                it[com.companyb.companyapp.repository.model.SessionVoidTable.id] =
                    UUID.randomUUID()
                it[com.companyb.companyapp.repository.model.SessionVoidTable.sessionId] =
                    sessionId
                it[com.companyb.companyapp.repository.model.SessionVoidTable.voidedBy] =
                    callerId
                it[com.companyb.companyapp.repository.model.SessionVoidTable.voidReason] =
                    "test void"
            }
        }
    }

    private fun insertUser(
        userId: UUID,
        username: String,
    ) {
        DatabaseTestHelper.insertUser(
            id = userId,
            username = "$username-$userId",
            passwordHash = "test-password-hash",
            email = "${userId.toString().take(8)}@t.st",
            displayName = "Test User $username",
        )
    }

    private fun insertBranch(
        id: UUID,
        name: String,
    ) {
        transaction {
            BranchTable.insert {
                it[BranchTable.id] = id
                it[BranchTable.name] = name
                it[BranchTable.branchType] = BranchType.CLINIC
            }
        }
    }

    private fun insertClient(clientId: UUID) {
        transaction {
            ClientTable.insert {
                it[ClientTable.id] = clientId
                it[ClientTable.firstName] = "Test"
                it[ClientTable.lastName] = "Client"
                it[ClientTable.gender] = "M"
                it[ClientTable.age] = 30
            }
        }
    }

    private fun createBranchDay(branchId: UUID): UUID =
        transaction {
            val today = LocalDate.now(BranchDayService.manilaZone)
            BranchDayTable.insertIgnore {
                it[BranchDayTable.branchId] = branchId
                it[BranchDayTable.date] = today
            }
            BranchDayTable
                .selectAll()
                .where {
                    (BranchDayTable.branchId eq branchId) and
                        (BranchDayTable.date eq today)
                }.single()[BranchDayTable.id]
        }

    private fun grantReceiveNextAppointmentAlerts(
        userId: UUID,
        branchId: UUID,
    ) {
        DatabaseTestHelper.grantCapability(
            userId = userId,
            capabilityCode = "RECEIVE_NEXT_APPOINTMENT_ALERTS",
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
    }

    private fun insertUserBranchAssignment(
        userId: UUID,
        branchId: UUID,
    ) {
        UserBranchAssignmentRepository.create(
            id = UUID.randomUUID(),
            userId = userId,
            branchId = branchId,
            slot = 1,
            assignedBy = callerId,
        )
    }

    private fun grantEditBranchData(userId: UUID) {
        DatabaseTestHelper.grantEditBranchData(userId, sourceId)
    }

    private fun deleteTestRows() {
        val allTestUsers =
            listOf(callerId, coordinatorId, nonCoordinatorId, unassignedCoordinatorId)
        transaction {
            NotificationTable.deleteAll()
            com.companyb.companyapp.repository.model.SessionVoidTable
                .deleteAll()
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId inList allTestUsers }
            UserBranchAssignmentTable.deleteWhere {
                UserBranchAssignmentTable.userId inList allTestUsers
            }
            com.companyb.companyapp.repository.model.AuditLogTable.deleteWhere {
                com.companyb.companyapp.repository.model.AuditLogTable.changedBy inList allTestUsers
            }
            SessionTable.deleteWhere { SessionTable.clientId eq clientId }
            BranchDayTable.deleteWhere {
                (BranchDayTable.branchId eq branchId) or
                    (BranchDayTable.branchId eq otherBranchId)
            }
            BranchTable.deleteWhere {
                (BranchTable.id eq branchId) or
                    (BranchTable.id eq otherBranchId)
            }
            ClientTable.deleteWhere { ClientTable.id eq clientId }
            AppUserTable.deleteWhere { AppUserTable.id inList allTestUsers }
        }
    }
}
