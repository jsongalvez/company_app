package com.companyb.companyapp.service

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayAssignmentTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.GrantReliefAccessTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.attendance.AttendanceRepository
import com.companyb.companyapp.service.attendance.AttendanceService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class AttendanceServicePostgresTest : BasePostgresTest() {
    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val otherBranchId = UUID.randomUUID()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(userId, "user")
        DatabaseTestHelper.insertTestUser(otherUserId, "other-user")
        trackOwned(AppUserTable, AppUserTable.id, userId)
        trackOwned(AppUserTable, AppUserTable.id, otherUserId)
        DatabaseTestHelper.insertTestBranch(branchId, "Test Branch")
        DatabaseTestHelper.insertTestBranch(otherBranchId, "Other Branch")
        trackOwned(BranchTable, BranchTable.id, branchId)
        trackOwned(BranchTable, BranchTable.id, otherBranchId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, userId)
        trackOwned(AttendanceTable, AttendanceTable.userId, userId)
        trackOwned(BranchDayAssignmentTable, BranchDayAssignmentTable.userId, userId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.userId, userId)
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.assignedBy, userId)
        trackOwned(GrantReliefAccessTable, GrantReliefAccessTable.requestedBy, userId)
    }

    @Test
    fun `clockIn creates attendance and branch day assignment and writes audit`() {
        val attendanceId = UUID.randomUUID()

        val (result, duration) =
            measureTimedValue {
                AttendanceService.clockIn(attendanceId, branchId, userId)
            }
        assertTrue(duration < 5.seconds, "clockIn regressed: took $duration")

        assertTrue(result.created)
        assertEquals(attendanceId, result.id)
        assertEquals(userId, result.userId)
        assertNotNull(result.clockIn)
        assertNull(result.clockOut)
        assertEquals(1L, auditEntryCount(attendanceId))
        assertNotNull(branchDayAssignmentExists(attendanceId))
    }

    @Test
    fun `clockIn trigger rolls back attendance assignment and audit when transaction fails`() {
        val attendanceId = UUID.randomUUID()

        assertFailsWith<IllegalStateException> {
            transaction {
                AttendanceService.clockIn(attendanceId, branchId, userId)
                error("injected commission trigger failure")
            }
        }

        assertEquals(
            0,
            transaction { AttendanceTable.selectAll().where { AttendanceTable.id eq attendanceId }.count() },
        )
        assertEquals(0, auditEntryCount(attendanceId))
        assertEquals(
            0,
            transaction {
                BranchDayAssignmentTable
                    .selectAll()
                    .where { BranchDayAssignmentTable.userId eq userId }
                    .count()
            },
        )
    }

    @Test
    fun `clockIn with same id returns existing attendance`() {
        val attendanceId = UUID.randomUUID()
        val first = AttendanceService.clockIn(attendanceId, branchId, userId)

        val second = AttendanceService.clockIn(attendanceId, branchId, userId)

        assertEquals(first.id, second.id)
        assertTrue(second.created.not())
    }

    @Test
    fun `clockIn rejects same id from another caller without duplicate side effects`() {
        val attendanceId = UUID.randomUUID()
        AttendanceService.clockIn(attendanceId, branchId, userId)

        assertFailsWith<ConflictException> {
            AttendanceService.clockIn(attendanceId, branchId, otherUserId)
        }

        assertEquals(1L, auditEntryCount(attendanceId))
        assertEquals(1L, branchDayAssignmentCount(userId))
    }

    @Test
    fun `clockIn rejects same id for another branch without duplicate side effects`() {
        val attendanceId = UUID.randomUUID()
        AttendanceService.clockIn(attendanceId, branchId, userId)

        assertFailsWith<ConflictException> {
            AttendanceService.clockIn(attendanceId, otherBranchId, userId)
        }

        assertEquals(1L, auditEntryCount(attendanceId))
        assertEquals(1L, branchDayAssignmentCount(userId))
    }

    @Test
    fun `clockIn throws Conflict when user already has active clock-in`() {
        val firstId = UUID.randomUUID()
        AttendanceService.clockIn(firstId, branchId, userId)

        val secondId = UUID.randomUUID()
        assertFailsWith<ConflictException> {
            AttendanceService.clockIn(secondId, branchId, userId)
        }
    }

    @Test
    fun `clockIn sets isRelief true when no branch assignment exists`() {
        val attendanceId = UUID.randomUUID()

        val result = AttendanceService.clockIn(attendanceId, branchId, userId)

        assertTrue(result.isRelief)
    }

    @Test
    fun `clockIn sets isRelief false when branch assignment exists`() {
        DatabaseTestHelper.grantManageUsers(userId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, userId)
        val assignmentId = UUID.randomUUID()
        UserBranchAssignmentService.create(userId, assignmentId, branchId, userId, 1)

        val attendanceId = UUID.randomUUID()
        val result = AttendanceService.clockIn(attendanceId, branchId, userId)

        assertTrue(result.isRelief.not())
    }

    @Test
    fun `clockOut sets clockOut and writes audit`() {
        val attendanceId = UUID.randomUUID()
        AttendanceService.clockIn(attendanceId, branchId, userId)

        val result = AttendanceService.clockOut(attendanceId, userId)

        assertTrue(result.created.not())
        assertNotNull(result.clockOut)
        assertEquals(2L, auditEntryCount(attendanceId))
        assertNotNull(auditNewClockOut(attendanceId))
    }

    @Test
    fun `clockOut on already clocked out record returns existing`() {
        val attendanceId = UUID.randomUUID()
        AttendanceService.clockIn(attendanceId, branchId, userId)
        val first = AttendanceService.clockOut(attendanceId, userId)

        val second = AttendanceService.clockOut(attendanceId, userId)

        assertTrue(first.created.not())
        assertTrue(second.created.not())
        assertEquals(first.clockOut, second.clockOut)
        assertEquals(2L, auditEntryCount(attendanceId))
    }

    @Test
    fun `repository clockOut only audits first transition`() {
        val attendanceId = UUID.randomUUID()
        AttendanceService.clockIn(attendanceId, branchId, userId)
        var auditCalls = 0

        AttendanceRepository.clockOut(attendanceId) { _, _ -> auditCalls++ }
        AttendanceRepository.clockOut(attendanceId) { _, _ -> auditCalls++ }

        assertEquals(1, auditCalls)
    }

    @Test
    fun `clockOut rejects another user's attendance`() {
        val attendanceId = UUID.randomUUID()
        AttendanceService.clockIn(attendanceId, branchId, userId)

        assertFailsWith<ForbiddenException> {
            AttendanceService.clockOut(attendanceId, otherUserId)
        }

        assertNull(
            transaction {
                AttendanceTable
                    .selectAll()
                    .where { AttendanceTable.id eq attendanceId }
                    .single()[AttendanceTable.clockOut]
            },
        )
        assertEquals(1L, auditEntryCount(attendanceId))
    }

    @Test
    fun `clockOut on non-existent attendance throws NotFound`() {
        val unknownId = UUID.randomUUID()

        assertFailsWith<NotFoundException> {
            AttendanceService.clockOut(unknownId, userId)
        }
    }

    private fun branchDayAssignmentExists(attendanceId: UUID): UUID? =
        transaction {
            val branchDayId =
                AttendanceTable
                    .selectAll()
                    .where { AttendanceTable.id eq attendanceId }
                    .single()[AttendanceTable.branchDayId]

            BranchDayAssignmentTable
                .selectAll()
                .where {
                    (BranchDayAssignmentTable.branchDayId eq branchDayId) and
                        (BranchDayAssignmentTable.userId eq userId)
                }.singleOrNull()
                ?.let { it[BranchDayAssignmentTable.id] }
        }

    private fun auditEntryCount(attendanceId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq "attendance") and
                        (AuditLogTable.recordId eq attendanceId)
                }.count()
        }

    private fun auditNewClockOut(attendanceId: UUID): String =
        transaction {
            val row =
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq "attendance") and
                            (AuditLogTable.recordId eq attendanceId)
                    }.orderBy(AuditLogTable.changedAt to SortOrder.DESC)
                    .limit(1)
                    .single()
            DatabaseTestHelper.extractJsonField(row[AuditLogTable.newValue] ?: "{}", "clockOut")
        }

    private fun branchDayAssignmentCount(userId: UUID): Long =
        transaction {
            BranchDayAssignmentTable
                .selectAll()
                .where { BranchDayAssignmentTable.userId eq userId }
                .count()
        }
}
