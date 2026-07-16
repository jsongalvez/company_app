package com.companyb.companyapp.service

import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayAssignmentTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.DayStatus
import com.companyb.companyapp.repository.model.GrantReliefAccessTable
import com.companyb.companyapp.repository.model.ReliefStatus
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReliefAccessServicePostgresTest : BasePostgresTest() {
    private val reliefUserId = UUID.randomUUID()
    private val targetUserId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val branchName = "ReliefTest-${branchId.toString().take(8)}"
    private val branchDayId = UUID.randomUUID()
    private val attendanceId = UUID.randomUUID()

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
    fun `successful relief request persists with PENDING status`() {
        val requestId = UUID.randomUUID()

        val result = ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)

        assertEquals(requestId, result.id)
        assertEquals(ReliefStatus.PENDING, result.requestStatus)
        assertEquals(reliefUserId, result.requestedBy)
        assertEquals(targetUserId, result.targetUser)
        assertTrue(requestExists(requestId))
    }

    @Test
    fun `duplicate request id returns existing row (idempotent)`() {
        val requestId = UUID.randomUUID()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)

        val duplicate = ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)

        assertEquals(requestId, duplicate.id)
        assertEquals(ReliefStatus.PENDING, duplicate.requestStatus)
    }

    @Test
    fun `fails with 400 when target user has no active clock-in`() {
        val noClockInTarget = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(noClockInTarget, "no-clock")
        trackOwned(AppUserTable, AppUserTable.id, noClockInTarget)
        val requestId = UUID.randomUUID()

        assertFailsWith<BadRequestResponse> {
            ReliefAccessService.requestReliefAccess(requestId, branchDayId, noClockInTarget, reliefUserId)
        }
    }

    @Test
    fun `fails with 403 when requester is not a relief user`() {
        val nonReliefUser = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(nonReliefUser, "non-relief")
        trackOwned(AppUserTable, AppUserTable.id, nonReliefUser)
        insertBranchDayAssignment(nonReliefUser, branchDayId, isRelief = false)
        trackOwned(BranchDayAssignmentTable, BranchDayAssignmentTable.branchDayId, branchDayId)
        val requestId = UUID.randomUUID()

        assertFailsWith<ForbiddenResponse> {
            ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, nonReliefUser)
        }
    }

    @Test
    fun `successful grant sets status to GRANTED and creates user_capability`() {
        val requestId = UUID.randomUUID()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)

        val result = ReliefAccessService.grantAccess(requestId, targetUserId)

        assertEquals(requestId, result.id)
        assertEquals(ReliefStatus.GRANTED, result.requestStatus)
        assertEquals(targetUserId, result.grantedBy)
        assertNotNull(result.grantedAt)
        assertTrue(capabilityExistsForReliefUser(requestId, reliefUserId, branchDayId))
    }

    @Test
    fun `grant on already granted request returns existing (idempotent)`() {
        val requestId = UUID.randomUUID()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)
        ReliefAccessService.grantAccess(requestId, targetUserId)

        val duplicate = ReliefAccessService.grantAccess(requestId, targetUserId)

        assertEquals(requestId, duplicate.id)
        assertEquals(ReliefStatus.GRANTED, duplicate.requestStatus)
        assertEquals(targetUserId, duplicate.grantedBy)
    }

    @Test
    fun `grant from non-target user fails with 403`() {
        val otherUser = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherUser, "other")
        trackOwned(AppUserTable, AppUserTable.id, otherUser)
        val requestId = UUID.randomUUID()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)

        assertFailsWith<ForbiddenResponse> {
            ReliefAccessService.grantAccess(requestId, otherUser)
        }
    }

    @Test
    fun `grant on non-existent request fails with 404`() {
        assertFailsWith<NotFoundResponse> {
            ReliefAccessService.grantAccess(UUID.randomUUID(), targetUserId)
        }
    }

    @Test
    fun `duplicate grant for same requestedBy and branchDayId returns existing grant`() {
        val requestId1 = UUID.randomUUID()
        val requestId2 = UUID.randomUUID()
        ReliefAccessService.requestReliefAccess(requestId1, branchDayId, targetUserId, reliefUserId)
        ReliefAccessService.requestReliefAccess(requestId2, branchDayId, targetUserId, reliefUserId)
        ReliefAccessService.grantAccess(requestId1, targetUserId)

        val result = ReliefAccessService.grantAccess(requestId2, targetUserId)

        assertEquals(requestId1, result.id)
        assertEquals(ReliefStatus.GRANTED, result.requestStatus)
    }

    @Test
    fun `successful deny sets status to DENIED`() {
        val requestId = UUID.randomUUID()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)

        val result = ReliefAccessService.denyAccess(requestId, targetUserId)

        assertEquals(requestId, result.id)
        assertEquals(ReliefStatus.DENIED, result.requestStatus)
    }

    @Test
    fun `deny on already denied request returns existing (idempotent)`() {
        val requestId = UUID.randomUUID()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)
        ReliefAccessService.denyAccess(requestId, targetUserId)

        val duplicate = ReliefAccessService.denyAccess(requestId, targetUserId)

        assertEquals(requestId, duplicate.id)
        assertEquals(ReliefStatus.DENIED, duplicate.requestStatus)
    }

    @Test
    fun `deny from non-target user fails with 403`() {
        val otherUser = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherUser, "other")
        trackOwned(AppUserTable, AppUserTable.id, otherUser)
        val requestId = UUID.randomUUID()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)

        assertFailsWith<ForbiddenResponse> {
            ReliefAccessService.denyAccess(requestId, otherUser)
        }
    }

    @Test
    fun `deny on already granted request fails with 400`() {
        val requestId = UUID.randomUUID()
        ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, reliefUserId)
        ReliefAccessService.grantAccess(requestId, targetUserId)

        assertFailsWith<BadRequestResponse> {
            ReliefAccessService.denyAccess(requestId, targetUserId)
        }
    }

    @Test
    fun `deny on non-existent request fails with 404`() {
        assertFailsWith<NotFoundResponse> {
            ReliefAccessService.denyAccess(UUID.randomUUID(), targetUserId)
        }
    }

    private fun capabilityExistsForReliefUser(
        sourceId: UUID,
        userId: UUID,
        contextId: UUID,
    ): Boolean =
        transaction {
            UserCapabilityTable
                .selectAll()
                .where {
                    (UserCapabilityTable.sourceId eq sourceId) and
                        (UserCapabilityTable.userId eq userId) and
                        (UserCapabilityTable.contextId eq contextId)
                }.empty()
                .not()
        }

    private fun insertBranchDay(
        id: UUID,
        branchId: UUID,
    ) {
        transaction {
            BranchDayTable.insert {
                it[BranchDayTable.id] = id
                it[BranchDayTable.branchId] = branchId
                it[BranchDayTable.date] = LocalDate.now()
                it[BranchDayTable.status] = DayStatus.OPEN
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
                it[BranchDayAssignmentTable.id] = UUID.randomUUID()
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
                it[AttendanceTable.clockIn] = OffsetDateTime.now()
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
}
