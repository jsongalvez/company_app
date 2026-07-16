package com.companyb.companyapp.service

import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserBranchAssignmentServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val nonManagerId = UUID.randomUUID()
    private val userAId = UUID.randomUUID()
    private val userBId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()

    private val allUserIds = listOf(callerId, nonManagerId, userAId, userBId)

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows()
        DatabaseTestHelper.insertTestUser(callerId, "caller")
        DatabaseTestHelper.insertTestUser(nonManagerId, "nonmgr")
        DatabaseTestHelper.insertTestUser(userAId, "usera")
        DatabaseTestHelper.insertTestUser(userBId, "userb")
        DatabaseTestHelper.insertTestBranch(branchId, name = "Test Branch")
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows()
        }
    }

    @Test
    fun `create persists assignment and writes audit row`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val assignmentId = UUID.randomUUID()
        val slot: Short = 3

        val result = UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, slot)

        assertTrue(result.created)
        assertEquals(assignmentId, result.assignment.id)
        assertEquals(userAId, result.assignment.userId)
        assertEquals(branchId, result.assignment.branchId)
        assertEquals(slot, result.assignment.slot)
        assertEquals(callerId, result.assignment.assignedBy)
        assertNotNull(result.assignment.assignedAt)
        assertEquals(1L, auditEntryCount(assignmentId))
        assertEquals(callerId.toString(), auditChangedBy(assignmentId))
    }

    @Test
    fun `duplicate assignment id returns existing row without extra audit`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val assignmentId = UUID.randomUUID()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        val duplicate = UserBranchAssignmentService.create(callerId, assignmentId, branchId, userBId, 5)

        assertTrue(duplicate.created.not())
        assertEquals(assignmentId, duplicate.assignment.id)
        assertEquals(userAId, duplicate.assignment.userId)
        assertEquals(1, duplicate.assignment.slot)
        assertEquals(1L, auditEntryCount(assignmentId))
    }

    @Test
    fun `create with slot less than 1 throws BadRequest`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val assignmentId = UUID.randomUUID()

        assertFailsWith<BadRequestResponse> {
            UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 0)
        }
    }

    @Test
    fun `create with non-existent branch throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val assignmentId = UUID.randomUUID()
        val unknownBranchId = UUID.randomUUID()

        assertFailsWith<NotFoundResponse> {
            UserBranchAssignmentService.create(callerId, assignmentId, unknownBranchId, userAId, 1)
        }
    }

    @Test
    fun `create with non-existent user throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val assignmentId = UUID.randomUUID()
        val unknownUserId = UUID.randomUUID()

        assertFailsWith<NotFoundResponse> {
            UserBranchAssignmentService.create(callerId, assignmentId, branchId, unknownUserId, 1)
        }
    }

    @Test
    fun `create when user already has active assignment at branch throws BadRequest`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val firstId = UUID.randomUUID()
        UserBranchAssignmentService.create(callerId, firstId, branchId, userAId, 1)

        val secondId = UUID.randomUUID()
        assertFailsWith<BadRequestResponse> {
            UserBranchAssignmentService.create(callerId, secondId, branchId, userAId, 2)
        }
    }

    @Test
    fun `create without MANAGE_USERS is forbidden and does not insert`() {
        val assignmentId = UUID.randomUUID()

        assertFailsWith<ForbiddenResponse> {
            UserBranchAssignmentService.create(nonManagerId, assignmentId, branchId, userAId, 1)
        }

        assertEquals(0L, auditEntryCount(assignmentId))
        assertEquals(null, assignmentExists(assignmentId))
    }

    @Test
    fun `remove sets endedAt and writes audit row`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val assignmentId = UUID.randomUUID()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        UserBranchAssignmentService.remove(callerId, branchId, userAId)

        assertNotNull(assignmentEndedAt(assignmentId))
        assertEquals(2L, auditEntryCount(assignmentId))
        val auditRow = latestAuditEntry(assignmentId)
        assertEquals("UPDATE", auditRow.action)
    }

    @Test
    fun `remove without MANAGE_USERS is forbidden`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val assignmentId = UUID.randomUUID()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        assertFailsWith<ForbiddenResponse> {
            UserBranchAssignmentService.remove(nonManagerId, branchId, userAId)
        }

        assertEquals(null, assignmentEndedAt(assignmentId))
    }

    @Test
    fun `remove with non-existent branch throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val unknownBranchId = UUID.randomUUID()

        assertFailsWith<NotFoundResponse> {
            UserBranchAssignmentService.remove(callerId, unknownBranchId, userAId)
        }
    }

    @Test
    fun `remove with no active assignment throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)

        assertFailsWith<NotFoundResponse> {
            UserBranchAssignmentService.remove(callerId, branchId, userAId)
        }
    }

    @Test
    fun `updateSlot by manager updates slot and writes audit`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val assignmentId = UUID.randomUUID()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        UserBranchAssignmentService.updateSlot(callerId, branchId, userAId, 5)

        assertEquals(5, assignedSlot(assignmentId))
        val audit = latestAuditEntry(assignmentId)
        assertEquals("UPDATE", audit.action)
        assertEquals("1", audit.oldSlot)
        assertEquals("5", audit.newSlot)
    }

    @Test
    fun `updateSlot self-update allowed without MANAGE_USERS`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val assignmentId = UUID.randomUUID()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, nonManagerId, 2)

        UserBranchAssignmentService.updateSlot(nonManagerId, branchId, nonManagerId, 7)

        assertEquals(7, assignedSlot(assignmentId))
    }

    @Test
    fun `updateSlot with slot less than 1 throws BadRequest`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val assignmentId = UUID.randomUUID()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        assertFailsWith<BadRequestResponse> {
            UserBranchAssignmentService.updateSlot(callerId, branchId, userAId, 0)
        }
    }

    @Test
    fun `updateSlot with no active assignment throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)

        assertFailsWith<NotFoundResponse> {
            UserBranchAssignmentService.updateSlot(callerId, branchId, userAId, 1)
        }
    }

    @Test
    fun `updateSlot by non-manager for other user throws Forbidden`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val assignmentId = UUID.randomUUID()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        assertFailsWith<ForbiddenResponse> {
            UserBranchAssignmentService.updateSlot(nonManagerId, branchId, userAId, 3)
        }

        assertEquals(1, assignedSlot(assignmentId))
    }

    @Test
    fun `swapSlots swaps slots and writes audit`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val idA = UUID.randomUUID()
        val idB = UUID.randomUUID()
        UserBranchAssignmentService.create(callerId, idA, branchId, userAId, 2)
        UserBranchAssignmentService.create(callerId, idB, branchId, userBId, 8)

        UserBranchAssignmentService.swapSlots(callerId, branchId, userAId, userBId)

        assertEquals(8, assignedSlot(idA))
        assertEquals(2, assignedSlot(idB))
        assertEquals(2L, auditEntryCount(idA))
    }

    @Test
    fun `swapSlots without MANAGE_USERS is forbidden`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val idA = UUID.randomUUID()
        val idB = UUID.randomUUID()
        UserBranchAssignmentService.create(callerId, idA, branchId, userAId, 1)
        UserBranchAssignmentService.create(callerId, idB, branchId, userBId, 2)

        assertFailsWith<ForbiddenResponse> {
            UserBranchAssignmentService.swapSlots(nonManagerId, branchId, userAId, userBId)
        }

        assertEquals(1, assignedSlot(idA))
        assertEquals(2, assignedSlot(idB))
    }

    @Test
    fun `swapSlots with missing assignment A throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val idB = UUID.randomUUID()
        UserBranchAssignmentService.create(callerId, idB, branchId, userBId, 1)

        assertFailsWith<NotFoundResponse> {
            UserBranchAssignmentService.swapSlots(callerId, branchId, userAId, userBId)
        }
    }

    @Test
    fun `swapSlots with missing assignment B throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val idA = UUID.randomUUID()
        UserBranchAssignmentService.create(callerId, idA, branchId, userAId, 1)

        assertFailsWith<NotFoundResponse> {
            UserBranchAssignmentService.swapSlots(callerId, branchId, userAId, userBId)
        }
    }

    @Test
    fun `findActiveByBranch returns active assignments sorted by slot`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        UserBranchAssignmentService.create(callerId, UUID.randomUUID(), branchId, userBId, 5)
        UserBranchAssignmentService.create(callerId, UUID.randomUUID(), branchId, userAId, 1)

        val assignments = UserBranchAssignmentService.findActiveByBranch(callerId, branchId)

        assertEquals(2, assignments.size)
        assertEquals(userAId, assignments[0].userId)
        assertEquals(1, assignments[0].slot)
        assertEquals(userBId, assignments[1].userId)
        assertEquals(5, assignments[1].slot)
    }

    @Test
    fun `findActiveByBranch excludes ended assignments`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        UserBranchAssignmentService.create(callerId, UUID.randomUUID(), branchId, userAId, 1)
        val idB = UUID.randomUUID()
        UserBranchAssignmentService.create(callerId, idB, branchId, userBId, 2)
        UserBranchAssignmentService.remove(callerId, branchId, userBId)

        val assignments = UserBranchAssignmentService.findActiveByBranch(callerId, branchId)

        assertEquals(1, assignments.size)
        assertEquals(userAId, assignments[0].userId)
    }

    @Test
    fun `findActiveByBranch without MANAGE_USERS is forbidden`() {
        assertFailsWith<ForbiddenResponse> {
            UserBranchAssignmentService.findActiveByBranch(nonManagerId, branchId)
        }
    }

    private fun assignedSlot(assignmentId: UUID): Short =
        transaction {
            UserBranchAssignmentTable
                .selectAll()
                .where { UserBranchAssignmentTable.id eq assignmentId }
                .single()[UserBranchAssignmentTable.slot]
        }

    private fun assignmentExists(assignmentId: UUID): UUID? =
        transaction {
            UserBranchAssignmentTable
                .selectAll()
                .where { UserBranchAssignmentTable.id eq assignmentId }
                .singleOrNull()
                ?.let { it[UserBranchAssignmentTable.id] }
        }

    private fun assignmentEndedAt(assignmentId: UUID): OffsetDateTime? =
        transaction {
            UserBranchAssignmentTable
                .selectAll()
                .where { UserBranchAssignmentTable.id eq assignmentId }
                .singleOrNull()
                ?.let { it[UserBranchAssignmentTable.endedAt] }
        }

    private fun auditEntryCount(assignmentId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq "user_branch_assignment") and
                        (AuditLogTable.recordId eq assignmentId)
                }.count()
        }

    private fun auditChangedBy(assignmentId: UUID): String =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq "user_branch_assignment") and
                        (AuditLogTable.recordId eq assignmentId)
                }.orderBy(AuditLogTable.changedAt to SortOrder.DESC)
                .limit(1)
                .single()[AuditLogTable.changedBy]
                .toString()
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
                oldSlot = DatabaseTestHelper.extractJsonField(row[AuditLogTable.oldValue] ?: "{}", "slot"),
                newSlot = DatabaseTestHelper.extractJsonField(row[AuditLogTable.newValue] ?: "{}", "slot"),
            )
        }

    private fun deleteTestRows() {
        transaction {
            AuditLogTable.deleteWhere {
                val ids = allUserIds
                (AuditLogTable.changedBy eq ids[0]) or (AuditLogTable.changedBy eq ids[1]) or
                    (AuditLogTable.changedBy eq ids[2]) or (AuditLogTable.changedBy eq ids[3])
            }
            UserBranchAssignmentTable.deleteWhere { UserBranchAssignmentTable.branchId eq branchId }
            UserCapabilityTable.deleteWhere {
                val ids = allUserIds
                (UserCapabilityTable.userId eq ids[0]) or (UserCapabilityTable.userId eq ids[1]) or
                    (UserCapabilityTable.userId eq ids[2]) or (UserCapabilityTable.userId eq ids[3])
            }
            BranchTable.deleteWhere { BranchTable.id eq branchId }
            AppUserTable.deleteWhere {
                val ids = allUserIds
                (AppUserTable.id eq ids[0]) or (AppUserTable.id eq ids[1]) or
                    (AppUserTable.id eq ids[2]) or (AppUserTable.id eq ids[3])
            }
        }
    }
}
