package com.companyb.companyapp.finance
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.finance.ExpenseTable
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpenseListAndRestorePostgresTest : ExpenseServicePostgresBase() {
    @Test
    fun `list expenses returns all non-deleted expenses for the day`() {
        val expense1Id = TestFixtures.uuid()
        val expense2Id = TestFixtures.uuid()
        val expense3Id = TestFixtures.uuid()

        ExpenseService.create(
            callerId = callerId,
            expense(expense1Id, BigDecimal("100.00"), notes = "First"),
        )
        ExpenseService.create(
            callerId = callerId,
            expense(expense2Id, BigDecimal("200.00"), ExpenseCategory.WATER, "Second"),
        )
        ExpenseService.create(
            callerId = callerId,
            expense(expense3Id, BigDecimal("300.00"), ExpenseCategory.TRANSPORTATION, "Third"),
        )

        val expenses = ExpenseService.findByBranchDayId(branchDayId)

        assertEquals(3, expenses.size)
    }

    @Test
    fun `list expenses includes soft-deleted expenses with their reason`() {
        val activeId = TestFixtures.uuid()
        val deletedId = TestFixtures.uuid()

        ExpenseService.create(
            callerId = callerId,
            expense(activeId, BigDecimal("100.00"), notes = "Active expense"),
        )
        val toDelete =
            ExpenseService.create(
                callerId = callerId,
                expense(deletedId, BigDecimal("200.00"), ExpenseCategory.WATER, "Will be deleted"),
            )

        ExpenseService.softDelete(
            callerId = callerId,
            expenseId = deletedId,
            reason = "Incorrect entry",
            expectedVersion = toDelete.version,
        )

        // #153 Q1 (CR-022 flip, commit f8b288a) — the GET includes soft-deleted rows: the
        // Finance screen renders them dimmed + "removed" + reason (an undo affordance); the
        // P&L totals still exclude them (the summary view filters).
        val expenses = ExpenseService.findByBranchDayId(branchDayId)

        assertEquals(2, expenses.size)
        val deleted = expenses.first { it.id == deletedId }
        assertNotNull(deleted.deletedAt)
        assertEquals("Incorrect entry", deleted.deletedReason)
    }

    @Test
    fun `restore soft-deleted expense succeeds and clears the deletion fields`() {
        val expenseId = TestFixtures.uuid()
        val created =
            ExpenseService.create(
                callerId = callerId,
                expense(expenseId, BigDecimal("200.00"), ExpenseCategory.WATER, "Will be restored"),
            )
        val deleted =
            ExpenseService.softDelete(
                callerId = callerId,
                expenseId = expenseId,
                reason = "Incorrect entry",
                expectedVersion = created.version,
            )

        val restored =
            ExpenseService.restore(
                callerId = callerId,
                expenseId = expenseId,
            )

        assertNotNull(restored)
        assertNull(restored.deletedBy)
        assertNull(restored.deletedAt)
        assertNull(restored.deletedReason)
        assertEquals(
            deleted.version,
            restored.version,
            "#153 Q6 — no version bump on restore (#663: delete bumps, restore keeps)",
        )
        assertEquals(created.version + 1, restored.version, "#663 — delete bumps version once")
        assertEquals(ExpenseCategory.WATER, restored.category)
    }

    @Test
    fun `restore non-existent expense returns not found`() {
        assertFailsWith<NotFoundException> {
            ExpenseService.restore(
                callerId = callerId,
                expenseId = TestFixtures.uuid(),
            )
        }
    }

    @Test
    fun `restore already-live expense is rejected`() {
        val expenseId = TestFixtures.uuid()
        ExpenseService.create(
            callerId = callerId,
            expense(expenseId, BigDecimal("200.00"), ExpenseCategory.WATER, "Never deleted"),
        )

        assertFailsWith<ValidationException> {
            ExpenseService.restore(
                callerId = callerId,
                expenseId = expenseId,
            )
        }
    }

    @Test
    fun `restore writes an audit update row`() {
        val expenseId = TestFixtures.uuid()
        val createdForAudit =
            ExpenseService.create(
                callerId = callerId,
                expense(expenseId, BigDecimal("200.00"), ExpenseCategory.WATER, "Will be restored"),
            )
        ExpenseService.softDelete(
            callerId = callerId,
            expenseId = expenseId,
            reason = "Incorrect entry",
            expectedVersion = createdForAudit.version,
        )

        ExpenseService.restore(
            callerId = callerId,
            expenseId = expenseId,
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
    fun `list expenses for non-existent branch day returns not found`() {
        assertFailsWith<NotFoundException> {
            ExpenseService.findByBranchDayId(TestFixtures.uuid())
        }
    }
}
