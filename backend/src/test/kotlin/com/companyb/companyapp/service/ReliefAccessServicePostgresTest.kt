package com.companyb.companyapp.service
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.ReliefAccessStatus
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.ReliefAccessRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayAssignmentTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.GrantReliefAccessTable
import com.companyb.companyapp.repository.model.ReliefAccess
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.ReliefGrantOutcome
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReliefAccessServicePostgresTest : BasePostgresTest() {
    private companion object {
        const val CONCURRENT_OPERATIONS = 2
        const val CONCURRENT_TIMEOUT_SECONDS = 10L
    }

    private val reliefUserId = TestFixtures.uuid()
    private val targetUserId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val branchName = "ReliefTest-${branchId.toString().take(8)}"
    private val branchDayId = TestFixtures.uuid()
    private val attendanceId = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(reliefUserId, "relief")
        trackOwned(AppUserTable, AppUserTable.id, reliefUserId)
        DatabaseTestHelper.insertTestUser(targetUserId, "target")
        trackOwned(AppUserTable, AppUserTable.id, targetUserId)
        DatabaseTestHelper.insertTestBranch(branchId, branchName)
        trackOwned(BranchTable, BranchTable.id, branchId)
        insertBranchDay(branchDayId, branchId)
        trackOwned(BranchDayTable, BranchDayTable.id, branchDayId)
        insertBranchDayAssignment(reliefUserId, branchDayId, isRelief = true)
        trackOwned(BranchDayAssignmentTable, BranchDayAssignmentTable.branchDayId, branchDayId)
        insertAttendance(attendanceId, targetUserId, branchDayId)
        trackOwned(AttendanceTable, AttendanceTable.branchDayId, branchDayId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, reliefUserId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, targetUserId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, reliefUserId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, targetUserId)
        trackOwned(GrantReliefAccessTable, GrantReliefAccessTable.branchDayId, branchDayId)
    }

    @Test
    fun `successful relief request persists with PENDING status and writes audit`() {
        val requestId = TestFixtures.uuid()

        val result = ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)

        assertEquals(requestId, result.id)
        assertEquals(ReliefAccessStatus.PENDING, result.requestStatus)
        assertEquals(reliefUserId, result.requestedBy)
        assertEquals(targetUserId, result.targetUser)
        assertTrue(requestExists(requestId))
        assertEquals(1L, auditReliefEntryCount(requestId))
    }

    @Test
    fun `duplicate request id returns existing row (idempotent)`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)

        val duplicate = ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)

        assertEquals(requestId, duplicate.id)
        assertEquals(ReliefAccessStatus.PENDING, duplicate.requestStatus)
    }

    @Test
    fun `fails with 400 when target user has no active clock-in`() {
        val noClockInTarget = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(noClockInTarget, "no-clock")
        trackOwned(AppUserTable, AppUserTable.id, noClockInTarget)
        val requestId = TestFixtures.uuid()

        assertFailsWith<ValidationException> {
            ReliefAccessService.requestReliefAccess(requestId, branchDayId, noClockInTarget, reliefUserId)
        }
    }

    @Test
    fun `fails with 403 when requester is not a relief user`() {
        val nonReliefUser = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(nonReliefUser, "non-relief")
        trackOwned(AppUserTable, AppUserTable.id, nonReliefUser)
        insertBranchDayAssignment(nonReliefUser, branchDayId, isRelief = false)
        trackOwned(BranchDayAssignmentTable, BranchDayAssignmentTable.branchDayId, branchDayId)
        val requestId = TestFixtures.uuid()

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, nonReliefUser)
        }
    }

    @Test
    fun `successful grant sets status to GRANTED and creates user_capability and writes audit`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)

        val result =
            when (val outcome = ReliefAccessService.grantAccess(requestId, targetUserId)) {
                is ReliefGrantOutcome.Granted -> outcome.reliefAccess
                is ReliefGrantOutcome.Superseded -> error("first grant must win, got superseded")
            }

        assertEquals(requestId, result.id)
        assertEquals(ReliefAccessStatus.GRANTED, result.requestStatus)
        assertEquals(targetUserId, result.grantedBy)
        assertNotNull(result.grantedAt)
        assertTrue(
            CapabilityService.hasCapability(
                userId = reliefUserId,
                capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
                contextType = CapabilityContextType.BRANCH_DAY,
                contextId = branchDayId,
            ),
        )
        assertEquals(2L, auditReliefEntryCount(requestId))
    }

    @Test
    fun `grant on already granted request returns existing (idempotent)`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)
        ReliefAccessService.grantAccess(requestId, targetUserId)

        val duplicate =
            when (val outcome = ReliefAccessService.grantAccess(requestId, targetUserId)) {
                is ReliefGrantOutcome.Granted -> outcome.reliefAccess
                is ReliefGrantOutcome.Superseded -> error("replay of the granted request must stay Granted")
            }

        assertEquals(requestId, duplicate.id)
        assertEquals(ReliefAccessStatus.GRANTED, duplicate.requestStatus)
        assertEquals(targetUserId, duplicate.grantedBy)
    }

    @Test
    fun `grant from non-target user fails with 403`() {
        val otherUser = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(otherUser, "other")
        trackOwned(AppUserTable, AppUserTable.id, otherUser)
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.grantAccess(requestId, otherUser)
        }
    }

    @Test
    fun `grant on already granted request rejects non-target user`() {
        val otherUser = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(otherUser, "other-grant-terminal")
        trackOwned(AppUserTable, AppUserTable.id, otherUser)
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)
        ReliefAccessService.grantAccess(requestId, targetUserId)

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.grantAccess(requestId, otherUser)
        }
    }

    @Test
    fun `grant on non-existent request fails with 404`() {
        assertFailsWith<NotFoundException> {
            ReliefAccessService.grantAccess(TestFixtures.uuid(), targetUserId)
        }
    }

    @Test
    fun `losing duplicate grant returns explicit superseded outcome carrying the winner`() {
        val requestId1 = TestFixtures.uuid()
        val requestId2 = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId1, branchDayId, targetUserId, reliefUserId)
        ReliefAccessService.requestReliefAccess(requestId2, branchDayId, targetUserId, reliefUserId)
        ReliefAccessService.grantAccess(requestId1, targetUserId)

        val superseded =
            when (val outcome = ReliefAccessService.grantAccess(requestId2, targetUserId)) {
                is ReliefGrantOutcome.Granted -> error("second grant must lose: $outcome")
                is ReliefGrantOutcome.Superseded -> outcome.reliefAccess
            }

        // The losing caller sees the row that actually won — never a success-shaped echo
        // of its own request (#354 edge 3).
        assertEquals(requestId1, superseded.id)
        assertEquals(ReliefAccessStatus.GRANTED, superseded.requestStatus)
    }

    @Test
    fun `successful deny sets status to DENIED and writes audit`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)

        val result = ReliefAccessService.denyAccess(requestId, targetUserId)

        assertEquals(requestId, result.id)
        assertEquals(ReliefAccessStatus.DENIED, result.requestStatus)
        assertEquals(2L, auditReliefEntryCount(requestId))
    }

    @Test
    fun `deny on already denied request returns existing (idempotent)`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)
        ReliefAccessService.denyAccess(requestId, targetUserId)

        val duplicate = ReliefAccessService.denyAccess(requestId, targetUserId)

        assertEquals(requestId, duplicate.id)
        assertEquals(ReliefAccessStatus.DENIED, duplicate.requestStatus)
    }

    @Test
    fun `grant after deny returns superseded outcome with the denied row and no capability`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)
        ReliefAccessService.denyAccess(requestId, targetUserId)

        val superseded =
            when (val outcome = ReliefAccessService.grantAccess(requestId, targetUserId)) {
                is ReliefGrantOutcome.Granted -> error("grant after deny must not read as granted: $outcome")
                is ReliefGrantOutcome.Superseded -> outcome.reliefAccess
            }

        assertEquals(ReliefAccessStatus.DENIED, superseded.requestStatus)
        assertFalse(
            CapabilityService.hasCapability(
                userId = reliefUserId,
                capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
                contextType = CapabilityContextType.BRANCH_DAY,
                contextId = branchDayId,
            ),
        )
    }

    @Test
    fun `deny from non-target user fails with 403`() {
        val otherUser = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(otherUser, "other")
        trackOwned(AppUserTable, AppUserTable.id, otherUser)
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.denyAccess(requestId, otherUser)
        }
    }

    @Test
    fun `deny on already denied request rejects non-target user`() {
        val otherUser = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(otherUser, "other-deny-terminal")
        trackOwned(AppUserTable, AppUserTable.id, otherUser)
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)
        ReliefAccessService.denyAccess(requestId, targetUserId)

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.denyAccess(requestId, otherUser)
        }
    }

    @Test
    fun `deny on already granted request fails with 400`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)
        ReliefAccessService.grantAccess(requestId, targetUserId)

        assertFailsWith<ValidationException> {
            ReliefAccessService.denyAccess(requestId, targetUserId)
        }
    }

    @Test
    fun `deny on non-existent request fails with 404`() {
        assertFailsWith<NotFoundException> {
            ReliefAccessService.denyAccess(TestFixtures.uuid(), targetUserId)
        }
    }

    @Test
    fun `concurrent grant and deny never leave denied request with capability`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)
        val executor = Executors.newFixedThreadPool(CONCURRENT_OPERATIONS)

        try {
            listOf(
                executor.submit<Result<ReliefGrantOutcome>> {
                    runCatching { ReliefAccessService.grantAccess(requestId, targetUserId) }
                },
                executor.submit<Result<ReliefAccess>> {
                    runCatching { ReliefAccessService.denyAccess(requestId, targetUserId) }
                },
            ).forEach { it.get(CONCURRENT_TIMEOUT_SECONDS, TimeUnit.SECONDS) }

            val finalRequest = ReliefAccessRepository.findById(requestId)
            assertNotNull(finalRequest)
            val hasCapability =
                CapabilityService.hasCapability(
                    userId = reliefUserId,
                    capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
                    contextType = CapabilityContextType.BRANCH_DAY,
                    contextId = branchDayId,
                )

            assertFalse(finalRequest.requestStatus == ReliefAccessStatus.DENIED && hasCapability)
            if (finalRequest.requestStatus == ReliefAccessStatus.GRANTED) {
                assertTrue(hasCapability)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `request fails with 403 on REMITTED day`() {
        val remittedBranchDayId = TestFixtures.uuid()
        val remittedAttendanceId = TestFixtures.uuid()
        val yesterday = TestFixtures.today.minusDays(1)
        insertBranchDay(remittedBranchDayId, branchId, DayStatus.REMITTED, yesterday)
        trackOwned(BranchDayTable, BranchDayTable.id, remittedBranchDayId)
        insertBranchDayAssignment(reliefUserId, remittedBranchDayId, isRelief = true)
        trackOwned(BranchDayAssignmentTable, BranchDayAssignmentTable.branchDayId, remittedBranchDayId)
        insertAttendance(remittedAttendanceId, targetUserId, remittedBranchDayId)
        trackOwned(AttendanceTable, AttendanceTable.branchDayId, remittedBranchDayId)
        val requestId = TestFixtures.uuid()

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.requestReliefAccess(requestId, remittedBranchDayId, targetUserId, reliefUserId)
        }
    }

    @Test
    fun `grant on REMITTED day with reason succeeds and flags audit entry`() {
        transaction {
            BranchDayTable.update({ BranchDayTable.id eq branchDayId }) {
                it[BranchDayTable.status] = DayStatus.REMITTED
            }
        }
        val requestId = TestFixtures.uuid()
        val remittedDayId = branchDayId
        transaction {
            GrantReliefAccessTable.insert {
                it[GrantReliefAccessTable.id] = requestId
                it[GrantReliefAccessTable.branchDayId] = remittedDayId
                it[GrantReliefAccessTable.requestedBy] = reliefUserId
                it[GrantReliefAccessTable.targetUser] = targetUserId
                it[GrantReliefAccessTable.requestStatus] = ReliefAccessStatus.PENDING
            }
        }
        trackOwned(GrantReliefAccessTable, GrantReliefAccessTable.id, requestId)
        DatabaseTestHelper.grantEditPastDay(targetUserId, branchId, TestFixtures.uuid())
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, targetUserId)

        val result =
            when (val outcome = ReliefAccessService.grantAccess(requestId, targetUserId, "Coordinator correction")) {
                is ReliefGrantOutcome.Granted -> outcome.reliefAccess
                is ReliefGrantOutcome.Superseded -> error("flagged-day grant must win: $outcome")
            }

        assertEquals(ReliefAccessStatus.GRANTED, result.requestStatus)
        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq GrantReliefAccessTable.tableName) and
                            (AuditLogTable.recordId eq requestId)
                    }.single()
            }
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Coordinator correction", audit[AuditLogTable.reason])
    }

    @Test
    fun `grant fails with 403 on REMITTED day`() {
        val yesterday = TestFixtures.today.minusDays(1)
        val remittedBranchDayId = TestFixtures.uuid()
        insertBranchDay(remittedBranchDayId, branchId, DayStatus.REMITTED, yesterday)
        trackOwned(BranchDayTable, BranchDayTable.id, remittedBranchDayId)
        insertBranchDayAssignment(reliefUserId, remittedBranchDayId, isRelief = true)
        trackOwned(BranchDayAssignmentTable, BranchDayAssignmentTable.branchDayId, remittedBranchDayId)
        val requestId = TestFixtures.uuid()
        transaction {
            GrantReliefAccessTable.insert {
                it[GrantReliefAccessTable.id] = requestId
                it[GrantReliefAccessTable.branchDayId] = remittedBranchDayId
                it[GrantReliefAccessTable.requestedBy] = reliefUserId
                it[GrantReliefAccessTable.targetUser] = targetUserId
                it[GrantReliefAccessTable.requestStatus] = ReliefAccessStatus.PENDING
            }
        }
        trackOwned(GrantReliefAccessTable, GrantReliefAccessTable.id, requestId)

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.grantAccess(requestId, targetUserId)
        }
    }

    @Test
    fun `deny fails with 403 on REMITTED day`() {
        val yesterday = TestFixtures.today.minusDays(1)
        val remittedBranchDayId = TestFixtures.uuid()
        insertBranchDay(remittedBranchDayId, branchId, DayStatus.REMITTED, yesterday)
        trackOwned(BranchDayTable, BranchDayTable.id, remittedBranchDayId)
        insertBranchDayAssignment(reliefUserId, remittedBranchDayId, isRelief = true)
        trackOwned(BranchDayAssignmentTable, BranchDayAssignmentTable.branchDayId, remittedBranchDayId)
        val requestId = TestFixtures.uuid()
        transaction {
            GrantReliefAccessTable.insert {
                it[GrantReliefAccessTable.id] = requestId
                it[GrantReliefAccessTable.branchDayId] = remittedBranchDayId
                it[GrantReliefAccessTable.requestedBy] = reliefUserId
                it[GrantReliefAccessTable.targetUser] = targetUserId
                it[GrantReliefAccessTable.requestStatus] = ReliefAccessStatus.PENDING
            }
        }
        trackOwned(GrantReliefAccessTable, GrantReliefAccessTable.id, requestId)

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.denyAccess(requestId, targetUserId)
        }
    }

    // ──────────────────────────────────────────────
    // #354 correctness edges
    // ──────────────────────────────────────────────

    @Test
    fun `request fails with 400 when the target is the caller (#354 edge 1)`() {
        val requestId = TestFixtures.uuid()

        assertFailsWith<ValidationException> {
            ReliefAccessService.requestReliefAccess(requestId, branchDayId, reliefUserId, reliefUserId)
        }
        assertFalse(requestExists(requestId))
    }

    @Test
    fun `grant fails with 400 when target lost presence after request (#354 edge 2)`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)
        transaction {
            AttendanceTable.update({ AttendanceTable.id eq attendanceId }) {
                it[AttendanceTable.clockOut] = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }

        assertFailsWith<ValidationException> {
            ReliefAccessService.grantAccess(requestId, targetUserId)
        }
        assertEquals(ReliefAccessStatus.PENDING, ReliefAccessRepository.findById(requestId)?.requestStatus)
        assertFalse(
            CapabilityService.hasCapability(
                userId = reliefUserId,
                capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
                contextType = CapabilityContextType.BRANCH_DAY,
                contextId = branchDayId,
            ),
        )
    }

    @Test
    fun `concurrent grants leave one winner and one explicit superseded outcome (#354 edge 3)`() {
        val secondTarget = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(secondTarget, "target-2")
        trackOwned(AppUserTable, AppUserTable.id, secondTarget)
        insertAttendance(TestFixtures.uuid(), secondTarget, branchDayId)
        trackOwned(AttendanceTable, AttendanceTable.branchDayId, branchDayId)
        val request1 = TestFixtures.uuid()
        val request2 = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(request1, branchDayId, targetUserId, reliefUserId)
        ReliefAccessService.requestReliefAccess(request2, branchDayId, secondTarget, reliefUserId)

        val executor = Executors.newFixedThreadPool(CONCURRENT_OPERATIONS)
        try {
            val outcomes =
                listOf(
                    executor.submit<Result<ReliefGrantOutcome>> {
                        runCatching { ReliefAccessService.grantAccess(request1, targetUserId) }
                    },
                    executor.submit<Result<ReliefGrantOutcome>> {
                        runCatching { ReliefAccessService.grantAccess(request2, secondTarget) }
                    },
                ).map { it.get(CONCURRENT_TIMEOUT_SECONDS, TimeUnit.SECONDS) }

            outcomes.forEach { result ->
                assertTrue(result.isSuccess, "neither leg may error: ${result.exceptionOrNull()}")
            }
            val decided = outcomes.mapNotNull { it.getOrNull() }
            val granted = decided.filterIsInstance<ReliefGrantOutcome.Granted>()
            val superseded = decided.filterIsInstance<ReliefGrantOutcome.Superseded>()
            assertEquals(1, granted.size)
            assertEquals(1, superseded.size)

            val winner = granted.single().reliefAccess
            val loserRow = superseded.single().reliefAccess
            assertEquals(winner.id, loserRow.id, "the superseded outcome must carry the winning row")
            assertTrue(winner.id == request1 || winner.id == request2)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `request fails with 400 when target user is deactivated (#354 edge 4)`() {
        val inactiveTarget = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(inactiveTarget, "inactive")
        trackOwned(AppUserTable, AppUserTable.id, inactiveTarget)
        transaction {
            AppUserTable.update({ AppUserTable.id eq inactiveTarget }) {
                it[AppUserTable.status] = UserStatus.INACTIVE
            }
        }
        insertAttendance(TestFixtures.uuid(), inactiveTarget, branchDayId)
        trackOwned(AttendanceTable, AttendanceTable.branchDayId, branchDayId)
        val requestId = TestFixtures.uuid()

        assertFailsWith<ValidationException> {
            ReliefAccessService.requestReliefAccess(requestId, branchDayId, inactiveTarget, reliefUserId)
        }
        assertFalse(requestExists(requestId))
    }

    private fun insertBranchDay(
        id: UUID,
        branchId: UUID,
        status: DayStatus = DayStatus.OPEN,
        date: LocalDate = TestFixtures.today,
    ) {
        transaction {
            BranchDayTable.insert {
                it[BranchDayTable.id] = id
                it[BranchDayTable.branchId] = branchId
                it[BranchDayTable.date] = date
                it[BranchDayTable.status] = status
            }
        }
    }

    private fun insertBranchDayAssignment(
        userId: UUID,
        branchDayId: UUID,
        isRelief: Boolean,
    ) {
        transaction {
            BranchDayAssignmentTable.insert {
                it[BranchDayAssignmentTable.id] = TestFixtures.uuid()
                it[BranchDayAssignmentTable.branchDayId] = branchDayId
                it[BranchDayAssignmentTable.userId] = userId
                it[BranchDayAssignmentTable.isRelief] = isRelief
            }
        }
    }

    private fun insertAttendance(
        id: UUID,
        userId: UUID,
        branchDayId: UUID,
    ) {
        transaction {
            AttendanceTable.insert {
                it[AttendanceTable.id] = id
                it[AttendanceTable.branchDayId] = branchDayId
                it[AttendanceTable.userId] = userId
                it[AttendanceTable.markedBy] = userId
                it[AttendanceTable.clockIn] = TestFixtures.now
            }
        }
    }

    private fun requestExists(requestId: UUID): Boolean =
        transaction {
            GrantReliefAccessTable
                .selectAll()
                .where { GrantReliefAccessTable.id eq requestId }
                .empty()
                .not()
        }

    private fun auditReliefEntryCount(requestId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq GrantReliefAccessTable.tableName) and
                        (AuditLogTable.recordId eq requestId)
                }.count()
        }
}
