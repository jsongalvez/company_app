package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.ExpenseCategory
import com.companyb.companyapp.repository.model.ExpenseTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpenseServicePostgresTest : BasePostgresTest() {
    private val callerId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()

    private lateinit var branchDayId: UUID

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "expense-caller")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        DatabaseTestHelper.insertTestBranch(branchId, "Test Expense Branch")
        trackOwned(BranchTable, BranchTable.id, branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.id, branchDayId)
        grantEditBranchData(callerId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        trackOwned(ExpenseTable, ExpenseTable.branchDayId, branchDayId)
    }

    @Test
    fun `create expense succeeds with all fields`() {
        val expenseId = UUID.randomUUID()

        val expense =
            ExpenseService.create(
                callerId = callerId,
                id = expenseId,
                branchDayId = branchDayId,
                amount = BigDecimal("500.00"),
                category = ExpenseCategory.PANTRY,
                notes = "Office pantry supplies",
            )

        assertNotNull(expense)
        assertEquals(expenseId, expense.id)
        assertEquals(branchDayId, expense.branchDayId)
        assertEquals(0, BigDecimal("500.00").compareTo(expense.amount))
        assertEquals(ExpenseCategory.PANTRY, expense.category)
        assertEquals(callerId, expense.createdBy)
        assertEquals("Office pantry supplies", expense.notes)
        assertNull(expense.deletedBy)
        assertNull(expense.deletedAt)
    }

    @Test
    fun `create expense succeeds without notes`() {
        val expenseId = UUID.randomUUID()

        val expense =
            ExpenseService.create(
                callerId = callerId,
                id = expenseId,
                branchDayId = branchDayId,
                amount = BigDecimal("300.00"),
                category = ExpenseCategory.WATER,
                notes = null,
            )

        assertNotNull(expense)
        assertNull(expense.notes)
    }

    @Test
    fun `create idempotent duplicate returns existing`() {
        val expenseId = UUID.randomUUID()

        val first =
            ExpenseService.create(
                callerId = callerId,
                id = expenseId,
                branchDayId = branchDayId,
                amount = BigDecimal("500.00"),
                category = ExpenseCategory.PANTRY,
                notes = "First",
            )

        val second =
            ExpenseService.create(
                callerId = callerId,
                id = expenseId,
                branchDayId = branchDayId,
                amount = BigDecimal("500.00"),
                category = ExpenseCategory.PANTRY,
                notes = "First",
            )

        assertEquals(first.id, second.id)
    }

    @Test
    fun `create without EDIT_BRANCH_DATA is allowed at service layer`() {
        DatabaseTestHelper.revokeAllCapabilities(callerId)

        val expense =
            ExpenseService.create(
                callerId = callerId,
                id = UUID.randomUUID(),
                branchDayId = branchDayId,
                amount = BigDecimal("500.00"),
                category = ExpenseCategory.PANTRY,
                notes = null,
            )

        assertNotNull(expense)
    }

    @Test
    fun `create with non-existent branch day returns not found`() {
        assertFailsWith<NotFoundException> {
            ExpenseService.create(
                callerId = callerId,
                id = UUID.randomUUID(),
                branchDayId = UUID.randomUUID(),
                amount = BigDecimal("500.00"),
                category = ExpenseCategory.PANTRY,
                notes = null,
            )
        }
    }

    @Test
    fun `create writes audit log entry`() {
        val expenseId = UUID.randomUUID()

        ExpenseService.create(
            callerId = callerId,
            id = expenseId,
            branchDayId = branchDayId,
            amount = BigDecimal("500.00"),
            category = ExpenseCategory.PANTRY,
            notes = "Test",
        )

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq ExpenseTable.tableName)
                    }.count()
            }
        assertTrue(auditCount > 0)
    }

    @Test
    fun `update expense succeeds with version bump`() {
        val expenseId = UUID.randomUUID()
        val created =
            ExpenseService.create(
                callerId = callerId,
                id = expenseId,
                branchDayId = branchDayId,
                amount = BigDecimal("500.00"),
                category = ExpenseCategory.PANTRY,
                notes = "Original",
            )

        val updated =
            ExpenseService.update(
                callerId = callerId,
                expenseId = expenseId,
                amount = BigDecimal("750.00"),
                category = ExpenseCategory.WATER,
                notes = "Updated",
                expectedVersion = created.version,
            )

        assertEquals(0, BigDecimal("750.00").compareTo(updated.amount))
        assertEquals(ExpenseCategory.WATER, updated.category)
        assertEquals("Updated", updated.notes)
        assertEquals(created.version + 1, updated.version)
    }

    @Test
    fun `update expense clears notes when null`() {
        val expenseId = UUID.randomUUID()
        val created =
            ExpenseService.create(
                callerId = callerId,
                id = expenseId,
                branchDayId = branchDayId,
                amount = BigDecimal("500.00"),
                category = ExpenseCategory.PANTRY,
                notes = "Original",
            )

        val updated =
            ExpenseService.update(
                callerId = callerId,
                expenseId = expenseId,
                amount = BigDecimal("500.00"),
                category = ExpenseCategory.PANTRY,
                notes = null,
                expectedVersion = created.version,
            )

        assertNull(updated.notes)
    }

    @Test
    fun `update with wrong version returns conflict`() {
        val expenseId = UUID.randomUUID()
        ExpenseService.create(
            callerId = callerId,
            id = expenseId,
            branchDayId = branchDayId,
            amount = BigDecimal("500.00"),
            category = ExpenseCategory.PANTRY,
            notes = null,
        )

        assertFailsWith<ConflictException> {
            ExpenseService.update(
                callerId = callerId,
                expenseId = expenseId,
                amount = BigDecimal("750.00"),
                category = ExpenseCategory.PANTRY,
                notes = null,
                expectedVersion = 999,
            )
        }
    }

    @Test
    fun `update non-existent expense returns not found`() {
        assertFailsWith<NotFoundException> {
            ExpenseService.update(
                callerId = callerId,
                expenseId = UUID.randomUUID(),
                amount = BigDecimal("750.00"),
                category = ExpenseCategory.PANTRY,
                notes = null,
                expectedVersion = 1,
            )
        }
    }

    @Test
    fun `update soft-deleted expense is rejected`() {
        val expenseId = UUID.randomUUID()
        ExpenseService.create(
            callerId = callerId,
            id = expenseId,
            branchDayId = branchDayId,
            amount = BigDecimal("500.00"),
            category = ExpenseCategory.PANTRY,
            notes = null,
        )
        ExpenseService.softDelete(
            callerId = callerId,
            expenseId = expenseId,
            reason = "Incorrect entry",
        )

        assertFailsWith<ValidationException> {
            ExpenseService.update(
                callerId = callerId,
                expenseId = expenseId,
                amount = BigDecimal("750.00"),
                category = ExpenseCategory.PANTRY,
                notes = null,
                expectedVersion = 1,
            )
        }
    }

    @Test
    fun `update writes audit log entry`() {
        val expenseId = UUID.randomUUID()
        val created =
            ExpenseService.create(
                callerId = callerId,
                id = expenseId,
                branchDayId = branchDayId,
                amount = BigDecimal("500.00"),
                category = ExpenseCategory.PANTRY,
                notes = null,
            )

        ExpenseService.update(
            callerId = callerId,
            expenseId = expenseId,
            amount = BigDecimal("750.00"),
            category = ExpenseCategory.PANTRY,
            notes = "Updated",
            expectedVersion = created.version,
        )

        val updateAuditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq ExpenseTable.tableName) and
                            (AuditLogTable.action eq AuditAction.UPDATE)
                    }.count()
            }
        assertTrue(updateAuditCount > 0)
    }

    @Test
    fun `soft delete expense succeeds`() {
        val expenseId = UUID.randomUUID()
        ExpenseService.create(
            callerId = callerId,
            id = expenseId,
            branchDayId = branchDayId,
            amount = BigDecimal("500.00"),
            category = ExpenseCategory.PANTRY,
            notes = "Test",
        )

        val deleted =
            ExpenseService.softDelete(
                callerId = callerId,
                expenseId = expenseId,
                reason = "Incorrect entry",
            )

        assertNotNull(deleted)
        assertNotNull(deleted.deletedBy)
        assertEquals(callerId, deleted.deletedBy)
        assertNotNull(deleted.deletedAt)
    }

    @Test
    fun `soft delete non-existent expense returns not found`() {
        assertFailsWith<NotFoundException> {
            ExpenseService.softDelete(
                callerId = callerId,
                expenseId = UUID.randomUUID(),
                reason = "Wrong entry",
            )
        }
    }

    @Test
    fun `soft delete without EDIT_BRANCH_DATA is allowed at service layer`() {
        val expenseId = UUID.randomUUID()
        ExpenseService.create(
            callerId = callerId,
            id = expenseId,
            branchDayId = branchDayId,
            amount = BigDecimal("500.00"),
            category = ExpenseCategory.PANTRY,
            notes = "Test",
        )

        DatabaseTestHelper.revokeAllCapabilities(callerId)

        val deleted =
            ExpenseService.softDelete(
                callerId = callerId,
                expenseId = expenseId,
                reason = "Test reason",
            )

        assertNotNull(deleted)
    }

    @Test
    fun `soft delete writes audit log entry`() {
        val expenseId = UUID.randomUUID()
        ExpenseService.create(
            callerId = callerId,
            id = expenseId,
            branchDayId = branchDayId,
            amount = BigDecimal("500.00"),
            category = ExpenseCategory.PANTRY,
            notes = "Test",
        )

        ExpenseService.softDelete(
            callerId = callerId,
            expenseId = expenseId,
            reason = "Incorrect entry",
        )

        val deleteAuditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq ExpenseTable.tableName) and
                            (AuditLogTable.action eq AuditAction.DELETE)
                    }.count()
            }
        assertTrue(deleteAuditCount > 0)
    }

    @Test
    fun `list expenses returns non-deleted expenses`() {
        val expense1Id = UUID.randomUUID()
        val expense2Id = UUID.randomUUID()
        val expense3Id = UUID.randomUUID()

        ExpenseService.create(
            callerId = callerId,
            id = expense1Id,
            branchDayId = branchDayId,
            amount = BigDecimal("100.00"),
            category = ExpenseCategory.PANTRY,
            notes = "First",
        )
        ExpenseService.create(
            callerId = callerId,
            id = expense2Id,
            branchDayId = branchDayId,
            amount = BigDecimal("200.00"),
            category = ExpenseCategory.WATER,
            notes = "Second",
        )
        ExpenseService.create(
            callerId = callerId,
            id = expense3Id,
            branchDayId = branchDayId,
            amount = BigDecimal("300.00"),
            category = ExpenseCategory.TRANSPORTATION,
            notes = "Third",
        )

        val expenses = ExpenseService.findByBranchDayId(callerId, branchDayId)

        assertEquals(3, expenses.size)
    }

    @Test
    fun `list expenses excludes soft-deleted expenses`() {
        val activeId = UUID.randomUUID()
        val deletedId = UUID.randomUUID()

        ExpenseService.create(
            callerId = callerId,
            id = activeId,
            branchDayId = branchDayId,
            amount = BigDecimal("100.00"),
            category = ExpenseCategory.PANTRY,
            notes = "Active expense",
        )
        ExpenseService.create(
            callerId = callerId,
            id = deletedId,
            branchDayId = branchDayId,
            amount = BigDecimal("200.00"),
            category = ExpenseCategory.WATER,
            notes = "Will be deleted",
        )

        ExpenseService.softDelete(
            callerId = callerId,
            expenseId = deletedId,
            reason = "Incorrect entry",
        )

        val expenses = ExpenseService.findByBranchDayId(callerId, branchDayId)

        assertEquals(1, expenses.size)
        assertEquals(activeId, expenses[0].id)
    }

    @Test
    fun `list expenses for non-existent branch day returns not found`() {
        assertFailsWith<NotFoundException> {
            ExpenseService.findByBranchDayId(callerId, UUID.randomUUID())
        }
    }

    @Test
    fun `create expense on REMITTED day without reason is rejected`() {
        val remittedDayId =
            DatabaseTestHelper.createRemittedBranchDay(
                branchId,
                LocalDate.now(BranchDayService.manilaZone).minusDays(3),
            )
        trackOwned(BranchDayTable, BranchDayTable.id, remittedDayId)
        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        assertFailsWith<ValidationException> {
            ExpenseService.create(
                callerId = callerId,
                id = UUID.randomUUID(),
                branchDayId = remittedDayId,
                amount = BigDecimal("500.00"),
                category = ExpenseCategory.PANTRY,
                notes = "Test",
            )
        }
    }

    @Test
    fun `create expense on REMITTED day with reason succeeds and flags audit entry`() {
        val remittedDayId =
            DatabaseTestHelper.createRemittedBranchDay(
                branchId,
                LocalDate.now(BranchDayService.manilaZone).minusDays(3),
            )
        trackOwned(BranchDayTable, BranchDayTable.id, remittedDayId)
        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val expenseId = UUID.randomUUID()

        val expense =
            ExpenseService.create(
                callerId = callerId,
                id = expenseId,
                branchDayId = remittedDayId,
                amount = BigDecimal("500.00"),
                category = ExpenseCategory.PANTRY,
                notes = "Test",
                reason = "Coordinator correction",
            )

        assertNotNull(expense)
        trackOwned(ExpenseTable, ExpenseTable.id, expenseId)
        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq ExpenseTable.tableName) and
                            (AuditLogTable.recordId eq expenseId)
                    }.single()
            }
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Coordinator correction", audit[AuditLogTable.reason])
    }

    private fun grantEditBranchData(userId: UUID) {
        DatabaseTestHelper.grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
    }
}
