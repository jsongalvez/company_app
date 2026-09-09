package com.companyb.companyapp.workforce
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.commission.CommissionService
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.workforce.AttendanceService
import com.companyb.companyapp.workforce.AttendanceTable
import com.companyb.companyapp.workforce.BranchDayAssignmentTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #404 — member-marked attendance: a home member marks another home member present or
 * absent at their shared branch today. Covers the gate shape (membership both sides),
 * effect parity with self clock-in/out, marker attribution in the audit trail, and the
 * membership-gated roster read.
 */
class AttendanceMarkPostgresTest : BasePostgresTest() {
    private val markerId = TestFixtures.uuid()
    private val targetId = TestFixtures.uuid()
    private val outsiderId = TestFixtures.uuid()
    private val thirdMemberId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(markerId, "marker")
        IdentityFixtures.insertTestUser(targetId, "target")
        IdentityFixtures.insertTestUser(outsiderId, "outsider")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Mark Branch")
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Other Branch")

        // Marker and target are home members at branchId; the outsider holds no assignment.
        BranchWorkforceFixtures.insertTestAssignment(
            userId = markerId,
            branchId = branchId,
            slot = 1,
            assignedBy = markerId,
        )
        BranchWorkforceFixtures.insertTestAssignment(
            userId = targetId,
            branchId = branchId,
            slot = 2,
            assignedBy = markerId,
        )
    }

    @Test
    fun `marking present creates attendance attributed to the marker`() {
        val attendanceId = TestFixtures.uuid()

        val result =
            AttendanceService.mark(
                callerId = markerId,
                branchId = branchId,
                targetUserId = targetId,
                present = true,
                attendanceId = attendanceId,
            )

        assertTrue(result.created)
        val row = assertNotNull(result.attendance)
        assertEquals(targetId, row.userId)
        assertEquals(markerId, row.markedBy)
        assertNull(row.clockOut)

        val audit = latestAudit(attendanceId)
        assertEquals(AuditAction.INSERT.name, audit.action)
        assertEquals(markerId.toString(), audit.newMarkedBy, "the audit attributes the marker")
    }

    @Test
    fun `marked member gains the same access and eligibility as a self clock-in`() {
        val today = BranchDayService.resolveOrCreate(branchId, BranchDayService.currentOperationalDate())

        val result =
            AttendanceService.mark(
                callerId = markerId,
                branchId = branchId,
                targetUserId = targetId,
                present = true,
                attendanceId = TestFixtures.uuid(),
            )
        val resultClockIn = assertNotNull(result.attendance).clockIn

        assertTrue(
            AttendanceService.hasActiveClockIn(targetId, today.id),
            "a marked-present member is clocked in exactly like a self starter",
        )
        assertTrue(
            AttendanceService.findUsersClockedInAt(today.id, resultClockIn).contains(targetId),
            "the marked member sits inside their own clock window",
        )
        assertNotNull(branchDayAssignmentFor(targetId, today.id))
    }

    @Test
    fun `commission failure inside the mark command rolls the attendance back`() {
        val attendanceId = TestFixtures.uuid()
        CommissionService.failAfterReplacementForTests = true
        try {
            assertFailsWith<IllegalStateException> {
                AttendanceService.mark(
                    callerId = markerId,
                    branchId = branchId,
                    targetUserId = targetId,
                    present = true,
                    attendanceId = attendanceId,
                )
            }
        } finally {
            CommissionService.failAfterReplacementForTests = false
        }

        assertEquals(0L, attendanceCount(attendanceId), "attendance rolled back with the failed recalculation")
        assertEquals(0L, auditCount(attendanceId))
        assertEquals(0L, branchDayAssignmentCount(targetId))
    }

    @Test
    fun `mark present rejects when the target already has an active clock-in`() {
        AttendanceService.clockIn(TestFixtures.uuid(), branchId, targetId)

        assertFailsWith<ConflictException> {
            AttendanceService.mark(
                callerId = markerId,
                branchId = branchId,
                targetUserId = targetId,
                present = true,
                attendanceId = TestFixtures.uuid(),
            )
        }
    }

    @Test
    fun `same-id replay returns the existing row without duplicate side effects`() {
        val attendanceId = TestFixtures.uuid()
        val first =
            AttendanceService.mark(
                callerId = markerId,
                branchId = branchId,
                targetUserId = targetId,
                present = true,
                attendanceId = attendanceId,
            )

        val second =
            AttendanceService.mark(
                callerId = markerId,
                branchId = branchId,
                targetUserId = targetId,
                present = true,
                attendanceId = attendanceId,
            )

        assertTrue(first.created)
        assertFalse(second.created)
        assertEquals(1L, auditCount(attendanceId))
        assertEquals(1L, branchDayAssignmentCount(targetId))
    }

    @Test
    fun `same-id replay for another target conflicts`() {
        val attendanceId = TestFixtures.uuid()
        AttendanceService.mark(
            callerId = markerId,
            branchId = branchId,
            targetUserId = targetId,
            present = true,
            attendanceId = attendanceId,
        )

        assertFailsWith<ConflictException> {
            AttendanceService.mark(
                callerId = markerId,
                branchId = branchId,
                targetUserId = markerId,
                present = true,
                attendanceId = attendanceId,
            )
        }
    }

    @Test
    fun `present mark requires a client-generated attendance id`() {
        assertFailsWith<ValidationException> {
            AttendanceService.mark(
                callerId = markerId,
                branchId = branchId,
                targetUserId = targetId,
                present = true,
                attendanceId = null,
            )
        }
    }

    @Test
    fun `non-member caller is forbidden and nothing is written`() {
        assertFailsWith<ForbiddenException> {
            AttendanceService.mark(
                callerId = outsiderId,
                branchId = branchId,
                targetUserId = targetId,
                present = true,
                attendanceId = TestFixtures.uuid(),
            )
        }

        assertEquals(
            0L,
            transaction { AttendanceTable.selectAll().where { AttendanceTable.userId eq targetId }.count() },
        )
    }

    @Test
    fun `cross-branch marking is forbidden`() {
        // The marker is a member of branchId only; marking at otherBranchId must 403.
        assertFailsWith<ForbiddenException> {
            AttendanceService.mark(
                callerId = markerId,
                branchId = otherBranchId,
                targetUserId = targetId,
                present = true,
                attendanceId = TestFixtures.uuid(),
            )
        }
    }

    @Test
    fun `marking a non-member target is rejected`() {
        assertFailsWith<ForbiddenException> {
            AttendanceService.mark(
                callerId = markerId,
                branchId = branchId,
                targetUserId = outsiderId,
                present = true,
                attendanceId = TestFixtures.uuid(),
            )
        }
    }

    @Test
    fun `mark present with unknown target returns 404 without writing rows`() {
        val unknownUserId = TestFixtures.uuid()
        val attendanceId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            AttendanceService.mark(
                callerId = markerId,
                branchId = branchId,
                targetUserId = unknownUserId,
                present = true,
                attendanceId = attendanceId,
            )
        }

        assertEquals(0L, attendanceCount(attendanceId), "no attendance row for unknown target")
        assertEquals(0L, auditCount(attendanceId), "no audit row for unknown target")
    }

    @Test
    fun `mark absent with unknown target returns 404`() {
        assertFailsWith<NotFoundException> {
            AttendanceService.mark(
                callerId = markerId,
                branchId = branchId,
                targetUserId = TestFixtures.uuid(),
                present = false,
                attendanceId = null,
            )
        }
    }

    @Test
    fun `mark present with unknown branch returns 404 without writing rows`() {
        val unknownBranchId = TestFixtures.uuid()
        val attendanceId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            AttendanceService.mark(
                callerId = markerId,
                branchId = unknownBranchId,
                targetUserId = targetId,
                present = true,
                attendanceId = attendanceId,
            )
        }

        assertEquals(0L, attendanceCount(attendanceId), "no attendance row for unknown branch")
        assertEquals(0L, auditCount(attendanceId), "no audit row for unknown branch")
        assertEquals(
            0L,
            transaction {
                BranchDayTable.selectAll().where { BranchDayTable.branchId eq unknownBranchId }.count()
            },
            "resolveOrCreate must not leave a branch_day row for an unknown branch",
        )
    }

    @Test
    fun `mark absent with unknown branch returns 404`() {
        val unknownBranchId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            AttendanceService.mark(
                callerId = markerId,
                branchId = unknownBranchId,
                targetUserId = targetId,
                present = false,
                attendanceId = null,
            )
        }
    }

    @Test
    fun `deactivated member is neither markable nor rostered`() {
        deactivate(targetId)

        assertFailsWith<ForbiddenException> {
            AttendanceService.mark(
                callerId = markerId,
                branchId = branchId,
                targetUserId = targetId,
                present = true,
                attendanceId = TestFixtures.uuid(),
            )
        }
        assertTrue(
            AttendanceService.rosterToday(markerId, branchId).none { it.userId == targetId },
            "INACTIVE members never appear on the attendance roster",
        )
    }

    @Test
    fun `deactivated caller cannot mark`() {
        deactivate(markerId)

        assertFailsWith<ForbiddenException> {
            AttendanceService.mark(
                callerId = markerId,
                branchId = branchId,
                targetUserId = targetId,
                present = true,
                attendanceId = TestFixtures.uuid(),
            )
        }
    }

    @Test
    fun `mark absent closes the open window and attributes the update to the marker`() {
        val attendanceId = TestFixtures.uuid()
        AttendanceService.clockIn(attendanceId, branchId, targetId)

        val result =
            AttendanceService.mark(
                callerId = markerId,
                branchId = branchId,
                targetUserId = targetId,
                present = false,
                attendanceId = null,
            )

        val row = assertNotNull(result.attendance)
        assertNotNull(row.clockOut, "the open window was closed")

        val updates =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq "attendance") and
                            (AuditLogTable.recordId eq attendanceId) and
                            (AuditLogTable.action eq AuditAction.UPDATE)
                    }.map { it[AuditLogTable.changedBy] }
            }
        assertEquals(listOf(markerId), updates, "the audit attributes the marker")
    }

    @Test
    fun `closed window loses eligibility like a self clock-out`() {
        val today = BranchDayService.resolveOrCreate(branchId, BranchDayService.currentOperationalDate())
        AttendanceService.clockIn(TestFixtures.uuid(), branchId, targetId)

        AttendanceService.mark(
            callerId = markerId,
            branchId = branchId,
            targetUserId = targetId,
            present = false,
            attendanceId = null,
        )

        assertFalse(AttendanceService.hasActiveClockIn(targetId, today.id))
    }

    @Test
    fun `mark absent with no open window is an idempotent no-op`() {
        AttendanceService.clockIn(TestFixtures.uuid(), branchId, markerId)
        val auditsBefore = totalAttendanceAuditCount()

        val result =
            AttendanceService.mark(
                callerId = markerId,
                branchId = branchId,
                targetUserId = targetId,
                present = false,
                attendanceId = null,
            )

        assertNull(result.attendance)
        assertEquals(auditsBefore, totalAttendanceAuditCount(), "no audit row for a no-op")
    }

    @Test
    fun `mark absent never creates a missing branch day`() {
        val daysBefore = branchDayCount()

        AttendanceService.mark(
            callerId = markerId,
            branchId = branchId,
            targetUserId = targetId,
            present = false,
            attendanceId = null,
        )

        assertEquals(daysBefore, branchDayCount(), "an absent-mark must not bootstrap a day row")
    }

    @Test
    fun `roster lists members by slot with live presence flags`() {
        IdentityFixtures.insertTestUser(thirdMemberId, "aaa-third-slot")
        BranchWorkforceFixtures.insertTestAssignment(
            userId = thirdMemberId,
            branchId = branchId,
            slot = 3,
            assignedBy = markerId,
        )
        AttendanceService.clockIn(TestFixtures.uuid(), branchId, targetId)

        val roster = AttendanceService.rosterToday(markerId, branchId)

        assertEquals(listOf<Short>(1, 2, 3), roster.map { it.slot })
        assertEquals(listOf("Test marker", "Test target", "Test aaa-third-slot"), roster.map { it.displayName })
        assertEquals(listOf(false, true, false), roster.map { it.present }, "only the clocked-in member reads present")
    }

    @Test
    fun `roster read with no day row reports all absent and creates no day`() {
        val daysBefore = branchDayCount()

        val roster = AttendanceService.rosterToday(markerId, branchId)

        assertTrue(roster.isNotEmpty())
        assertTrue(roster.none { it.present })
        assertEquals(daysBefore, branchDayCount(), "the roster read must not bootstrap a day row")
    }

    @Test
    fun `roster read gates non-members`() {
        assertFailsWith<ForbiddenException> {
            AttendanceService.rosterToday(outsiderId, branchId)
        }
    }

    @Test
    fun `roster read with unknown branch returns 404`() {
        val unknownBranchId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            AttendanceService.rosterToday(markerId, unknownBranchId)
        }
    }

    private fun deactivate(userId: UUID) {
        transaction {
            AppUserTable.update({ AppUserTable.id eq userId }) { it[status] = UserStatus.INACTIVE }
        }
    }

    private fun branchDayAssignmentFor(
        userId: UUID,
        branchDayId: UUID,
    ): UUID? =
        transaction {
            BranchDayAssignmentTable
                .selectAll()
                .where {
                    (BranchDayAssignmentTable.userId eq userId) and
                        (BranchDayAssignmentTable.branchDayId eq branchDayId)
                }.singleOrNull()
                ?.get(BranchDayAssignmentTable.id)
        }

    private fun branchDayAssignmentCount(userId: UUID): Long =
        transaction {
            BranchDayAssignmentTable
                .selectAll()
                .where { BranchDayAssignmentTable.userId eq userId }
                .count()
        }

    private fun attendanceCount(attendanceId: UUID): Long =
        transaction {
            AttendanceTable.selectAll().where { AttendanceTable.id eq attendanceId }.count()
        }

    private fun branchDayCount(): Long =
        transaction { BranchDayTable.selectAll().where { BranchDayTable.branchId eq branchId }.count() }

    private data class AuditRow(
        val action: String,
        val newMarkedBy: String,
    )

    private fun auditRows(attendanceId: UUID): List<AuditRow> =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq "attendance") and
                        (AuditLogTable.recordId eq attendanceId)
                }.orderBy(AuditLogTable.changedAt to SortOrder.ASC)
                .map { row ->
                    AuditRow(
                        action = row[AuditLogTable.action].name,
                        newMarkedBy =
                            TestFixtures.extractJsonField(
                                row[AuditLogTable.newValue] ?: "{}",
                                "markedBy",
                            ),
                    )
                }
        }

    private fun auditCount(attendanceId: UUID): Long = auditRows(attendanceId).size.toLong()

    private fun latestAudit(attendanceId: UUID): AuditRow = auditRows(attendanceId).last()

    /** Every audit row on the attendance table regardless of record id (no-op assertions). */
    private fun totalAttendanceAuditCount(): Long =
        transaction {
            AuditLogTable.selectAll().where { AuditLogTable.auditTableName eq "attendance" }.count()
        }
}
