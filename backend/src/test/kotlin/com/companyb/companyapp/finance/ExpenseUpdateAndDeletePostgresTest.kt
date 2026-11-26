package com.companyb.companyapp.finance
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.finance.ExpenseRepository
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

class ExpenseUpdateAndDeletePostgresTest : ExpenseServicePostgresBase() {
    @Test
    fun `update expense succeeds with version bump`() {
        val expenseId = TestFixtures.uuid()
        val created =
            ExpenseService.create(
                callerId = callerId,
                expense(expenseId, notes = "Original"),
            )

        val updated =
            ExpenseService.update(
                callerId = callerId,
                expenseId = expenseId,
                ExpenseEdit(amount = BigDecimal("750.00"), category = ExpenseCategory.WATER, notes = "Updated"),
                expectedVersion = created.version,
            )

        assertEquals(0, BigDecimal("750.00").compareTo(updated.amount))
        assertEquals(ExpenseCategory.WATER, updated.category)
        assertEquals("Updated", updated.notes)
        assertEquals(created.version + 1, updated.version)
    }

    @Test
    fun `update expense clears notes when null`() {
        val expenseId = TestFixtures.uuid()
        val created =
            ExpenseService.create(
                callerId = callerId,
                expense(expenseId, notes = "Original"),
            )

        val updated =
            ExpenseService.update(
                callerId = callerId,
                expenseId = expenseId,
                ExpenseEdit(amount = BigDecimal("500.00"), category = ExpenseCategory.PANTRY, notes = null),
                expectedVersion = created.version,
            )

        assertNull(updated.notes)
    }

    @Test
    fun `update with wrong version returns conflict`() {
        val expenseId = TestFixtures.uuid()
        ExpenseService.create(
            callerId = callerId,
            expense(expenseId),
        )

        val auditsBefore = callerAuditCount()

        assertFailsWith<ConflictException> {
            ExpenseService.update(
                callerId = callerId,
                expenseId = expenseId,
                ExpenseEdit(amount = BigDecimal("750.00"), category = ExpenseCategory.PANTRY, notes = null),
                expectedVersion = 999,
            )
        }
        assertEquals(auditsBefore, callerAuditCount(), "conflicted update writes no audit rows")
    }

    @Test
    fun `update non-existent expense returns not found`() {
        assertFailsWith<NotFoundException> {
            ExpenseService.update(
                callerId = callerId,
                expenseId = TestFixtures.uuid(),
                ExpenseEdit(amount = BigDecimal("750.00"), category = ExpenseCategory.PANTRY, notes = null),
                expectedVersion = 1,
            )
        }
    }

    @Test
    fun `update soft-deleted expense is rejected`() {
        val expenseId = TestFixtures.uuid()
        val created =
            ExpenseService.create(
                callerId = callerId,
                expense(expenseId),
            )
        ExpenseService.softDelete(
            callerId = callerId,
            expenseId = expenseId,
            reason = "Incorrect entry",
            expectedVersion = created.version,
        )

        assertFailsWith<ValidationException> {
            ExpenseService.update(
                callerId = callerId,
                expenseId = expenseId,
                ExpenseEdit(amount = BigDecimal("750.00"), category = ExpenseCategory.PANTRY, notes = null),
                expectedVersion = 1,
            )
        }
    }

    @Test
    fun `update after soft delete race is rejected`() {
        val expenseId = TestFixtures.uuid()
        val created =
            ExpenseService.create(
                callerId = callerId,
                expense(expenseId, notes = "Original"),
            )

        ExpenseService.softDelete(
            callerId = callerId,
            expenseId = expenseId,
            reason = "Incorrect entry",
            expectedVersion = created.version,
        )

        assertFailsWith<VersionMismatchException> {
            transaction {
                ExpenseRepository.updateInTransaction(
                    expenseId = expenseId,
                    amount = BigDecimal("750.00"),
                    category = ExpenseCategory.WATER,
                    notes = "Stale update",
                    expectedVersion = created.version,
                )
            }
        }

        val after = ExpenseRepository.findById(expenseId)
        assertNotNull(after)
        assertEquals(created.version + 1, after.version)
        assertEquals("Incorrect entry", after.deletedReason)
        assertEquals(BigDecimal("500.00"), after.amount)
    }

    @Test
    fun `update writes audit log entry`() {
        val expenseId = TestFixtures.uuid()
        val created =
            ExpenseService.create(
                callerId = callerId,
                expense(expenseId),
            )

        ExpenseService.update(
            callerId = callerId,
            expenseId = expenseId,
            ExpenseEdit(amount = BigDecimal("750.00"), category = ExpenseCategory.PANTRY, notes = "Updated"),
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
    fun `soft delete expense succeeds and records the reason`() {
        val expenseId = TestFixtures.uuid()
        val created =
            ExpenseService.create(
                callerId = callerId,
                expense(expenseId, notes = "Test"),
            )

        val deleted =
            ExpenseService.softDelete(
                callerId = callerId,
                expenseId = expenseId,
                reason = "Incorrect entry",
                expectedVersion = created.version,
            )

        assertNotNull(deleted)
        assertNotNull(deleted.deletedBy)
        assertEquals(callerId, deleted.deletedBy)
        assertNotNull(deleted.deletedAt)
        // #153 Q2 — the reason is denormalized on the row (written in the same UPDATE as the
        // soft delete) so the GET stays single-query; the audit row remains the history record.
        assertEquals("Incorrect entry", deleted.deletedReason)
        assertEquals(created.version + 1, deleted.version, "#663 — delete bumps version mirroring update")
    }

    @Test
    fun `soft delete non-existent expense returns not found`() {
        assertFailsWith<NotFoundException> {
            ExpenseService.softDelete(
                callerId = callerId,
                expenseId = TestFixtures.uuid(),
                reason = "Wrong entry",
                expectedVersion = 1,
            )
        }
    }

    @Test
    fun `soft delete without EDIT_BRANCH_DATA is allowed at service layer`() {
        val expenseId = TestFixtures.uuid()
        val created =
            ExpenseService.create(
                callerId = callerId,
                expense(expenseId, notes = "Test"),
            )

        val deleted =
            ExpenseService.softDelete(
                callerId = callerId,
                expenseId = expenseId,
                reason = "Test reason",
                expectedVersion = created.version,
            )

        assertNotNull(deleted)
    }

    @Test
    fun `soft delete writes audit log entry`() {
        val expenseId = TestFixtures.uuid()
        val created =
            ExpenseService.create(
                callerId = callerId,
                expense(expenseId, notes = "Test"),
            )

        ExpenseService.softDelete(
            callerId = callerId,
            expenseId = expenseId,
            reason = "Incorrect entry",
            expectedVersion = created.version,
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
    fun `soft delete with stale version returns conflict and writes no audit`() {
        val expenseId = TestFixtures.uuid()
        ExpenseService.create(
            callerId = callerId,
            expense(expenseId),
        )

        val auditsBefore = callerAuditCount()

        assertFailsWith<ConflictException> {
            ExpenseService.softDelete(
                callerId = callerId,
                expenseId = expenseId,
                reason = "Stale delete",
                expectedVersion = 999,
            )
        }
        assertEquals(auditsBefore, callerAuditCount(), "conflicted delete writes no audit rows")
    }

    @Test
    fun `stale delete after concurrent update is rejected`() {
        val expenseId = TestFixtures.uuid()
        val created =
            ExpenseService.create(
                callerId = callerId,
                expense(expenseId, notes = "Original"),
            )

        ExpenseService.update(
            callerId = callerId,
            expenseId = expenseId,
            ExpenseEdit(amount = BigDecimal("750.00"), category = ExpenseCategory.WATER, notes = "Concurrent edit"),
            expectedVersion = created.version,
        )

        assertFailsWith<ConflictException> {
            ExpenseService.softDelete(
                callerId = callerId,
                expenseId = expenseId,
                reason = "Stale delete over fresh edit",
                expectedVersion = created.version,
            )
        }

        val after = ExpenseRepository.findById(expenseId)
        assertNotNull(after)
        assertNull(after.deletedAt, "failed delete leaves the row live")
        assertEquals(created.version + 1, after.version)
        assertEquals(0, BigDecimal("750.00").compareTo(after.amount))
    }

    @Test
    fun `second soft delete is rejected`() {
        val expenseId = TestFixtures.uuid()
        val created =
            ExpenseService.create(
                callerId = callerId,
                expense(expenseId),
            )
        val deleted =
            ExpenseService.softDelete(
                callerId = callerId,
                expenseId = expenseId,
                reason = "First delete",
                expectedVersion = created.version,
            )

        assertFailsWith<ValidationException> {
            ExpenseService.softDelete(
                callerId = callerId,
                expenseId = expenseId,
                reason = "Second delete",
                expectedVersion = deleted.version,
            )
        }
    }
}
