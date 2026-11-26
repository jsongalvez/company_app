package com.companyb.companyapp.workforce

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.authorization.Role
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.workforce.UserBranchAssignmentTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Ending an assignment, moving its slot and swapping two slots, and stale ids that must not edit a replacement. */
class UserBranchAssignmentEditPostgresTest : UserBranchAssignmentServicePostgresBase() {
    private fun assignmentEndedAt(assignmentId: UUID): OffsetDateTime? =
        transaction {
            UserBranchAssignmentTable
                .selectAll()
                .where { UserBranchAssignmentTable.id eq assignmentId }
                .singleOrNull()
                ?.let { it[UserBranchAssignmentTable.endedAt] }
        }

    private data class AssignmentAudit(
        val action: String,
        val oldSlot: String,
        val newSlot: String,
    )

    private fun latestAuditEntry(assignmentId: UUID): AssignmentAudit =
        transaction {
            val row =
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq "user_branch_assignment") and
                            (AuditLogTable.recordId eq assignmentId)
                    }.orderBy(AuditLogTable.changedAt to SortOrder.DESC)
                    .limit(1)
                    .single()

            AssignmentAudit(
                action = row[AuditLogTable.action].name,
                oldSlot = TestFixtures.extractJsonField(row[AuditLogTable.oldValue] ?: "{}", "slot"),
                newSlot = TestFixtures.extractJsonField(row[AuditLogTable.newValue] ?: "{}", "slot"),
            )
        }

    @Test
    fun `remove sets endedAt and writes audit row`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        UserBranchAssignmentService.remove(callerId, branchId, assignmentId)

        assertNotNull(assignmentEndedAt(assignmentId))
        assertEquals(2L, auditEntryCount(assignmentId))
        val auditRow = latestAuditEntry(assignmentId)
        assertEquals("UPDATE", auditRow.action)
    }

    @Test
    fun `stale remove identity cannot end replacement assignment`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val staleAssignmentId = TestFixtures.uuid()
        val replacementAssignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, staleAssignmentId, branchId, userAId, 1)
        UserBranchAssignmentService.remove(callerId, branchId, staleAssignmentId)
        UserBranchAssignmentService.create(callerId, replacementAssignmentId, branchId, userAId, 2)

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.remove(callerId, branchId, staleAssignmentId)
        }

        assertNull(assignmentEndedAt(replacementAssignmentId))
        assertEquals(2, assignedSlot(replacementAssignmentId))
    }

    @Test
    fun `remove without MANAGE_USERS is forbidden at service layer`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        assertFailsWith<ForbiddenException> {
            UserBranchAssignmentService.remove(nonManagerId, branchId, assignmentId)
        }

        assertNull(assignmentEndedAt(assignmentId))
    }

    @Test
    fun `remove with non-existent branch throws NotFound`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val unknownBranchId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.remove(callerId, unknownBranchId, TestFixtures.uuid())
        }
    }

    @Test
    fun `remove with no active assignment throws NotFound`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.remove(callerId, branchId, TestFixtures.uuid())
        }
    }

    @Test
    fun `updateSlot by manager updates slot and writes audit`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        UserBranchAssignmentService.updateSlot(callerId, branchId, assignmentId, 5)

        assertEquals(5, assignedSlot(assignmentId))
        val audit = latestAuditEntry(assignmentId)
        assertEquals("UPDATE", audit.action)
        assertEquals("1", audit.oldSlot)
        assertEquals("5", audit.newSlot)
    }

    @Test
    fun `updateSlot self-update allowed without MANAGE_USERS`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, nonManagerId, 2)

        UserBranchAssignmentService.updateSlot(nonManagerId, branchId, assignmentId, 7)

        assertEquals(7, assignedSlot(assignmentId))
    }

    @Test
    fun `updateSlot with no active assignment throws NotFound`() {
        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.updateSlot(callerId, branchId, TestFixtures.uuid(), 1)
        }
    }

    @Test
    fun `updateSlot by non-manager for other user throws Forbidden`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        assertFailsWith<ForbiddenException> {
            UserBranchAssignmentService.updateSlot(nonManagerId, branchId, assignmentId, 3)
        }

        assertEquals(1, assignedSlot(assignmentId))
    }

    @Test
    fun `stale slot update identity cannot edit replacement assignment`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val staleAssignmentId = TestFixtures.uuid()
        val replacementAssignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, staleAssignmentId, branchId, userAId, 1)
        UserBranchAssignmentService.remove(callerId, branchId, staleAssignmentId)
        UserBranchAssignmentService.create(callerId, replacementAssignmentId, branchId, userAId, 2)

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.updateSlot(callerId, branchId, staleAssignmentId, 9)
        }

        assertEquals(2, assignedSlot(replacementAssignmentId))
    }

    @Test
    fun `swapSlots swaps slots and writes audit`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val idA = TestFixtures.uuid()
        val idB = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idA, branchId, userAId, 2)
        UserBranchAssignmentService.create(callerId, idB, branchId, userBId, 8)

        UserBranchAssignmentService.swapSlots(callerId, branchId, idA, idB)

        assertEquals(8, assignedSlot(idA))
        assertEquals(2, assignedSlot(idB))
        assertEquals(2L, auditEntryCount(idA))
    }

    @Test
    fun `swapSlots without MANAGE_USERS is forbidden at service layer`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val idA = TestFixtures.uuid()
        val idB = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idA, branchId, userAId, 1)
        UserBranchAssignmentService.create(callerId, idB, branchId, userBId, 2)

        assertFailsWith<ForbiddenException> {
            UserBranchAssignmentService.swapSlots(nonManagerId, branchId, idA, idB)
        }

        assertEquals(1, assignedSlot(idA))
        assertEquals(2, assignedSlot(idB))
    }

    @Test
    fun `swapSlots self-service by participant is allowed without MANAGE_USERS`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val idA = TestFixtures.uuid()
        val idB = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idA, branchId, userAId, 1)
        UserBranchAssignmentService.create(callerId, idB, branchId, userBId, 2)

        UserBranchAssignmentService.swapSlots(userAId, branchId, idA, idB)

        assertEquals(2, assignedSlot(idA))
        assertEquals(1, assignedSlot(idB))
    }

    @Test
    fun `swapSlots with the same user on both sides throws validation without audit rows`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val idA = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idA, branchId, userAId, 1)

        assertFailsWith<ValidationException> {
            UserBranchAssignmentService.swapSlots(callerId, branchId, idA, idA)
        }

        assertEquals(1, assignedSlot(idA))
        assertEquals(1L, auditEntryCount(idA))
    }

    @Test
    fun `swapSlots with missing assignment A throws NotFound`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val idB = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idB, branchId, userBId, 1)

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.swapSlots(callerId, branchId, idB, TestFixtures.uuid())
        }
    }

    @Test
    fun `swapSlots with missing assignment B throws NotFound`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val idA = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idA, branchId, userAId, 1)

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.swapSlots(callerId, branchId, idA, TestFixtures.uuid())
        }
    }

    @Test
    fun `stale swap identity cannot edit replacement assignment`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val staleAssignmentId = TestFixtures.uuid()
        val replacementAssignmentId = TestFixtures.uuid()
        val otherAssignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, staleAssignmentId, branchId, userAId, 1)
        UserBranchAssignmentService.remove(callerId, branchId, staleAssignmentId)
        UserBranchAssignmentService.create(callerId, replacementAssignmentId, branchId, userAId, 3)
        UserBranchAssignmentService.create(callerId, otherAssignmentId, branchId, userBId, 7)

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.swapSlots(callerId, branchId, staleAssignmentId, otherAssignmentId)
        }

        assertEquals(3, assignedSlot(replacementAssignmentId))
        assertEquals(7, assignedSlot(otherAssignmentId))
    }
}
