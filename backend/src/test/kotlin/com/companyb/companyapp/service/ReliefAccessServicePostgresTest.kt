package com.companyb.companyapp.service
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.ReliefAccessStatus
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.exception.ConflictException
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
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * #357 broadcast model: a request names no target user and is branch-day-scoped (today or
 * a future date); any ACTIVE outsider may raise it, any active branch member grants,
 * denies, or cancels it; all retraction locks once the requester clocks in. The #354
 * supersede machinery is retired — multiple relief workers per branch-day are allowed.
 */
class ReliefAccessServicePostgresTest : BasePostgresTest() {
    private val reliefUserId = TestFixtures.uuid()
    private val memberId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val branchName = "ReliefTest-${branchId.toString().take(8)}"
    private val branchDayId = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(reliefUserId, "relief")
        trackOwned(AppUserTable, AppUserTable.id, reliefUserId)
        DatabaseTestHelper.insertTestUser(memberId, "member")
        trackOwned(AppUserTable, AppUserTable.id, memberId)
        DatabaseTestHelper.insertTestBranch(branchId, branchName)
        trackOwned(BranchTable, BranchTable.id, branchId)
        insertBranchDay(branchDayId, branchId)
        trackOwned(BranchDayTable, BranchDayTable.id, branchDayId)
        // The member's home assignment — grant/deny/cancel authority (#357).
        DatabaseTestHelper.insertTestAssignment(
            userId = memberId,
            branchId = branchId,
            slot = 1,
            assignedBy = memberId,
        )
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.userId, memberId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, reliefUserId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, reliefUserId)
        // Member-authored grants/denies/cancels write audit rows too (#357 membership authority).
        trackOwned(AuditLogTable, AuditLogTable.changedBy, memberId)
        trackOwned(GrantReliefAccessTable, GrantReliefAccessTable.branchDayId, branchDayId)
    }

    // ──────────────────────────────────────────────
    // Request lifecycle (#357: branch + date picker)
    // ──────────────────────────────────────────────

    @Test
    fun `successful request persists with PENDING status and writes audit`() {
        val requestId = TestFixtures.uuid()

        val result =
            ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        assertEquals(requestId, result.id)
        assertEquals(ReliefAccessStatus.PENDING, result.requestStatus)
        assertEquals(reliefUserId, result.requestedBy)
        assertTrue(requestExists(requestId))
        assertEquals(1L, auditReliefEntryCount(requestId))
    }

    @Test
    fun `future-dated request resolves its own branch day and stays PENDING`() {
        val futureDate = TestFixtures.today.plusDays(FUTURE_DAYS)
        val futureRequestId = TestFixtures.uuid()

        val result = ReliefAccessService.requestReliefAccess(futureRequestId, branchId, futureDate, reliefUserId)
        trackRequest(futureRequestId)

        assertEquals(ReliefAccessStatus.PENDING, result.requestStatus)
        val day = BranchDayService.requireBranchDayExists(result.branchDayId)
        assertEquals(futureDate, day.date)
    }

    @Test
    fun `null date defaults to the current operational day`() {
        val requestId = TestFixtures.uuid()

        val result = ReliefAccessService.requestReliefAccess(requestId, branchId, null, reliefUserId)

        val day = BranchDayService.requireBranchDayExists(result.branchDayId)
        assertEquals(TestFixtures.today, day.date)
    }

    @Test
    fun `past-dated request fails with 400`() {
        val requestId = TestFixtures.uuid()

        assertFailsWith<ValidationException> {
            ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today.minusDays(1), reliefUserId)
        }
        assertFalse(requestExists(requestId))
    }

    @Test
    fun `request by an assigned member of the branch fails with 400`() {
        val assignedMember = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(assignedMember, "home-member")
        trackOwned(AppUserTable, AppUserTable.id, assignedMember)
        DatabaseTestHelper.insertTestAssignment(
            userId = assignedMember,
            branchId = branchId,
            slot = 2,
            assignedBy = memberId,
        )
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.userId, assignedMember)

        assertFailsWith<ValidationException> {
            ReliefAccessService.requestReliefAccess(TestFixtures.uuid(), branchId, null, assignedMember)
        }
    }

    @Test
    fun `request fails with 403 when requester is deactivated`() {
        DatabaseTestHelper.insertTestUser(inactiveRequester, "inactive")
        trackOwned(AppUserTable, AppUserTable.id, inactiveRequester)
        transaction {
            AppUserTable.update({ AppUserTable.id eq inactiveRequester }) {
                it[AppUserTable.status] = UserStatus.INACTIVE
            }
        }

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.requestReliefAccess(TestFixtures.uuid(), branchId, null, inactiveRequester)
        }
    }

    @Test
    fun `request on non-existent branch fails with 404`() {
        assertFailsWith<NotFoundException> {
            ReliefAccessService.requestReliefAccess(TestFixtures.uuid(), TestFixtures.uuid(), null, reliefUserId)
        }
    }

    @Test
    fun `duplicate live request id is an idempotent replay`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        val duplicate =
            ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        assertEquals(requestId, duplicate.id)
        assertEquals(ReliefAccessStatus.PENDING, duplicate.requestStatus)
    }

    // ──────────────────────────────────────────────
    // Flood control (#352 Q4: one live ask per branch day)
    // ──────────────────────────────────────────────

    @Test
    fun `second live request for the same branch day fails with 409`() {
        ReliefAccessService.requestReliefAccess(TestFixtures.uuid(), branchId, TestFixtures.today, reliefUserId)

        assertFailsWith<ConflictException> {
            ReliefAccessService.requestReliefAccess(TestFixtures.uuid(), branchId, TestFixtures.today, reliefUserId)
        }
    }

    @Test
    fun `re-asking after denial is allowed (different day identity per flood rule)`() {
        val firstId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(firstId, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.denyAccess(firstId, memberId)

        val secondId = TestFixtures.uuid()
        val reAsk =
            ReliefAccessService.requestReliefAccess(
                secondId,
                branchId,
                TestFixtures.today.plusDays(1),
                reliefUserId,
            )
        trackRequest(secondId)

        assertEquals(ReliefAccessStatus.PENDING, reAsk.requestStatus)
        assertTrue(
            ReliefAccessRepository.findByRequestedByAndBranchDayId(
                reliefUserId,
                reAsk.branchDayId,
                ReliefAccessStatus.PENDING,
            ) !=
                null,
        )
    }

    @Test
    fun `re-asking after cancellation is allowed on a different day`() {
        val firstId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(firstId, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.cancelRequest(firstId, reliefUserId)

        val secondId = TestFixtures.uuid()
        val reAsk =
            ReliefAccessService.requestReliefAccess(secondId, branchId, TestFixtures.today.plusDays(1), reliefUserId)
        trackRequest(secondId)

        assertEquals(ReliefAccessStatus.PENDING, reAsk.requestStatus)
    }

    // ──────────────────────────────────────────────
    // Grant / deny (membership authority, no targeting)
    // ──────────────────────────────────────────────

    @Test
    fun `grant by a branch member sets GRANTED, writes capability and audit`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        val result = ReliefAccessService.grantAccess(requestId, memberId)

        assertEquals(requestId, result.id)
        assertEquals(ReliefAccessStatus.GRANTED, result.requestStatus)
        assertEquals(memberId, result.grantedBy)
        assertNotNull(result.grantedAt)
        assertTrue(
            CapabilityService.hasCapability(
                userId = reliefUserId,
                capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
                contextType = CapabilityContextType.BRANCH_DAY,
                contextId = result.branchDayId,
            ),
        )
        assertEquals(2L, auditReliefEntryCount(requestId))
    }

    @Test
    fun `future-dated grant yields edit access scoped to that day only`() {
        val futureDate = TestFixtures.today.plusDays(FUTURE_DAYS)
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, futureDate, reliefUserId)
        trackRequest(requestId)
        val futureDayId = ReliefAccessRepository.findById(requestId)!!.branchDayId

        ReliefAccessService.grantAccess(requestId, memberId)

        assertTrue(
            CapabilityService.hasCapability(
                userId = reliefUserId,
                capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
                contextType = CapabilityContextType.BRANCH_DAY,
                contextId = futureDayId,
            ),
        )
        // A different day gains nothing.
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
    fun `grant by a non-member fails with 403`() {
        val outsider = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(outsider, "outsider")
        trackOwned(AppUserTable, AppUserTable.id, outsider)
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.grantAccess(requestId, outsider)
        }
    }

    @Test
    fun `grant replay on already granted request is idempotent`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.grantAccess(requestId, memberId)

        val duplicate = ReliefAccessService.grantAccess(requestId, memberId)

        assertEquals(requestId, duplicate.id)
        assertEquals(ReliefAccessStatus.GRANTED, duplicate.requestStatus)
        assertEquals(memberId, duplicate.grantedBy)
    }

    @Test
    fun `two requests from different requesters can both be granted (no supersede)`() {
        val secondRelief = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(secondRelief, "relief-2")
        trackOwned(AppUserTable, AppUserTable.id, secondRelief)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, secondRelief)
        val request1 = TestFixtures.uuid()
        val request2 = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(request1, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.requestReliefAccess(request2, branchId, TestFixtures.today, secondRelief)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, secondRelief)

        val grant1 = ReliefAccessService.grantAccess(request1, memberId)
        val grant2 = ReliefAccessService.grantAccess(request2, memberId)

        assertEquals(ReliefAccessStatus.GRANTED, grant1.requestStatus)
        assertEquals(ReliefAccessStatus.GRANTED, grant2.requestStatus)
        assertTrue(
            CapabilityService.hasCapability(
                userId = reliefUserId,
                capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
                contextType = CapabilityContextType.BRANCH_DAY,
                contextId = grant1.branchDayId,
            ),
        )
        assertTrue(
            CapabilityService.hasCapability(
                userId = secondRelief,
                capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
                contextType = CapabilityContextType.BRANCH_DAY,
                contextId = grant2.branchDayId,
            ),
        )
    }

    @Test
    fun `deny by a branch member sets DENIED and writes audit`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        val result = ReliefAccessService.denyAccess(requestId, memberId)

        assertEquals(requestId, result.id)
        assertEquals(ReliefAccessStatus.DENIED, result.requestStatus)
        assertEquals(2L, auditReliefEntryCount(requestId))
    }

    @Test
    fun `deny replay is idempotent and deny after grant fails with 400`() {
        val deniedId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(deniedId, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.denyAccess(deniedId, memberId)
        assertEquals(ReliefAccessStatus.DENIED, ReliefAccessService.denyAccess(deniedId, memberId).requestStatus)

        val grantedId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(grantedId, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.grantAccess(grantedId, memberId)
        assertFailsWith<ValidationException> {
            ReliefAccessService.denyAccess(grantedId, memberId)
        }
    }

    @Test
    fun `deny by a non-member fails with 403`() {
        val outsider = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(outsider, "other")
        trackOwned(AppUserTable, AppUserTable.id, outsider)
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.denyAccess(requestId, outsider)
        }
    }

    @Test
    fun `grant and deny on non-existent requests fail with 404`() {
        assertFailsWith<NotFoundException> { ReliefAccessService.grantAccess(TestFixtures.uuid(), memberId) }
        assertFailsWith<NotFoundException> { ReliefAccessService.denyAccess(TestFixtures.uuid(), memberId) }
    }

    @Test
    fun `grant on REMITTED day with reason succeeds and flags audit entry`() {
        transaction {
            BranchDayTable.update({ BranchDayTable.id eq branchDayId }) {
                it[BranchDayTable.status] = DayStatus.REMITTED
            }
        }
        val requestId = TestFixtures.uuid()
        // Distinct-name local: inside insert{}, a bare name colliding with a table column
        // resolves to the COLUMN via the implicit receiver (the #356 lesson).
        val seededDayForRemit = branchDayId
        transaction {
            GrantReliefAccessTable.insert {
                it[GrantReliefAccessTable.id] = requestId
                it[GrantReliefAccessTable.branchDayId] = seededDayForRemit
                it[GrantReliefAccessTable.requestedBy] = reliefUserId
                it[GrantReliefAccessTable.requestStatus] = ReliefAccessStatus.PENDING
            }
        }
        trackOwned(GrantReliefAccessTable, GrantReliefAccessTable.id, requestId)
        DatabaseTestHelper.grantEditPastDay(memberId, branchId, TestFixtures.uuid())
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, memberId)

        val result = ReliefAccessService.grantAccess(requestId, memberId, "Coordinator correction")

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
    fun `grant fails with 403 on REMITTED day without EDIT_PAST_DAY`() {
        val remittedDayId = TestFixtures.uuid()
        insertBranchDay(remittedDayId, branchId, DayStatus.REMITTED, TestFixtures.today.minusDays(1))
        trackOwned(BranchDayTable, BranchDayTable.id, remittedDayId)
        val requestId = TestFixtures.uuid()
        transaction {
            GrantReliefAccessTable.insert {
                it[GrantReliefAccessTable.id] = requestId
                it[GrantReliefAccessTable.branchDayId] = remittedDayId
                it[GrantReliefAccessTable.requestedBy] = reliefUserId
                it[GrantReliefAccessTable.requestStatus] = ReliefAccessStatus.PENDING
            }
        }
        trackOwned(GrantReliefAccessTable, GrantReliefAccessTable.id, requestId)

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.grantAccess(requestId, memberId)
        }
    }

    // ──────────────────────────────────────────────
    // Cancel / withdraw lock matrix (#357 owner rules)
    // ──────────────────────────────────────────────

    @Test
    fun `requester withdraws own pending ask`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        val result = ReliefAccessService.cancelRequest(requestId, reliefUserId)

        assertEquals(ReliefAccessStatus.CANCELLED, result.requestStatus)
        assertEquals(2L, auditReliefEntryCount(requestId))
    }

    @Test
    fun `any branch member cancels someone else's pending ask`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        val result = ReliefAccessService.cancelRequest(requestId, memberId)

        assertEquals(ReliefAccessStatus.CANCELLED, result.requestStatus)
    }

    @Test
    fun `cancel by a non-member non-requester fails with 403`() {
        val outsider = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(outsider, "cancel-outsider")
        trackOwned(AppUserTable, AppUserTable.id, outsider)
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.cancelRequest(requestId, outsider)
        }
    }

    @Test
    fun `withdraw locks once the requester clocks in as relief`() {
        val attendanceId = TestFixtures.uuid()
        insertAttendance(attendanceId, reliefUserId, branchDayId)
        trackOwned(AttendanceTable, AttendanceTable.branchDayId, branchDayId)
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        assertFailsWith<ValidationException> {
            ReliefAccessService.cancelRequest(requestId, reliefUserId)
        }
        assertFailsWith<ValidationException> {
            ReliefAccessService.cancelRequest(requestId, memberId)
        }
        assertEquals(ReliefAccessStatus.PENDING, ReliefAccessRepository.findById(requestId)?.requestStatus)
    }

    @Test
    fun `cancel on decided request fails with 409`() {
        val grantedId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(grantedId, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.grantAccess(grantedId, memberId)

        assertFailsWith<ConflictException> {
            ReliefAccessService.cancelRequest(grantedId, reliefUserId)
        }
    }

    @Test
    fun `cancel on non-existent request fails with 404`() {
        assertFailsWith<NotFoundException> {
            ReliefAccessService.cancelRequest(TestFixtures.uuid(), memberId)
        }
    }

    // ──────────────────────────────────────────────
    // Discovery reads
    // ──────────────────────────────────────────────

    @Test
    fun `members see every request on the day, outsiders only their own`() {
        val otherOutsider = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(otherOutsider, "relief-3")
        trackOwned(AppUserTable, AppUserTable.id, otherOutsider)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, otherOutsider)
        val mine = TestFixtures.uuid()
        val theirs = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(mine, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.requestReliefAccess(theirs, branchId, TestFixtures.today, otherOutsider)

        val memberView = ReliefAccessService.listForCaller(memberId, branchDayId)
        val outsiderView = ReliefAccessService.listForCaller(reliefUserId, branchDayId)

        assertEquals(setOf(mine, theirs), memberView.map { it.id }.toSet())
        assertEquals(listOf(mine), outsiderView.map { it.id })
    }

    @Test
    fun `mine list carries branch context across days`() {
        val todayId = TestFixtures.uuid()
        val futureId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(todayId, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.requestReliefAccess(futureId, branchId, TestFixtures.today.plusDays(1), reliefUserId)
        trackRequest(futureId)

        val mine = ReliefAccessRepository.findMine(reliefUserId)

        assertEquals(setOf(todayId, futureId), mine.map { it.access.id }.toSet())
        assertTrue(mine.all { it.branchName == branchName && it.date >= TestFixtures.today })
    }

    private val inactiveRequester = TestFixtures.uuid()

    /** Track a service-created request row and its (possibly fresh) branch day for teardown. */
    private fun trackRequest(requestId: UUID) {
        val dayId = ReliefAccessRepository.findById(requestId)?.branchDayId ?: return
        trackOwned(GrantReliefAccessTable, GrantReliefAccessTable.id, requestId)
        trackOwned(BranchDayTable, BranchDayTable.id, dayId)
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

    private fun insertAttendance(
        id: UUID,
        userId: UUID,
        dayId: UUID,
    ) {
        transaction {
            AttendanceTable.insert {
                it[AttendanceTable.id] = id
                it[AttendanceTable.branchDayId] = dayId
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

    private companion object {
        const val FUTURE_DAYS = 2L
    }
}
