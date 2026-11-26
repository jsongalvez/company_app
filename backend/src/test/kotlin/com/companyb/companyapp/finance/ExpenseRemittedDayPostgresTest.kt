package com.companyb.companyapp.finance
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.finance.ExpenseRepository
import com.companyb.companyapp.finance.ExpenseTable
import com.companyb.companyapp.remittance.NewRemittance
import com.companyb.companyapp.remittance.RemittanceService
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ExpenseRemittedDayPostgresTest : ExpenseServicePostgresBase() {
    @Test
    fun `restore on REMITTED day without reason is rejected`() {
        val remittedDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId)
        val expenseId = TestFixtures.uuid()
        val createdRemitted =
            ExpenseService.create(
                callerId = callerId,
                NewExpense(
                    id = expenseId,
                    branchDayId = remittedDayId,
                    amount = BigDecimal("200.00"),
                    category = ExpenseCategory.WATER,
                    notes = null,
                    reason = "Recorded late",
                ),
            )
        ExpenseService.softDelete(
            callerId = callerId,
            expenseId = expenseId,
            reason = "Incorrect entry",
            expectedVersion = createdRemitted.version,
        )

        assertFailsWith<ValidationException> {
            ExpenseService.restore(
                callerId = callerId,
                expenseId = expenseId,
            )
        }
    }

    @Test
    fun `restore on REMITTED day with reason succeeds`() {
        val remittedDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId)
        val expenseId = TestFixtures.uuid()
        val createdRemittedWithReason =
            ExpenseService.create(
                callerId = callerId,
                NewExpense(
                    id = expenseId,
                    branchDayId = remittedDayId,
                    amount = BigDecimal("200.00"),
                    category = ExpenseCategory.WATER,
                    notes = null,
                    reason = "Recorded late",
                ),
            )
        ExpenseService.softDelete(
            callerId = callerId,
            expenseId = expenseId,
            reason = "Incorrect entry",
            expectedVersion = createdRemittedWithReason.version,
        )

        val restored =
            ExpenseService.restore(
                callerId = callerId,
                expenseId = expenseId,
                reason = "Reversed per owner review",
            )

        assertNotNull(restored)
        assertNull(restored.deletedAt)
    }

    @Test
    fun `create expense on REMITTED day without reason is rejected`() {
        val remittedDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId)
        val auditsBefore = callerAuditCount()

        assertFailsWith<ValidationException> {
            ExpenseService.create(
                callerId = callerId,
                NewExpense(
                    id = TestFixtures.uuid(),
                    branchDayId = remittedDayId,
                    amount = BigDecimal("500.00"),
                    category = ExpenseCategory.PANTRY,
                    notes = "Test",
                ),
            )
        }
        assertEquals(auditsBefore, callerAuditCount(), "rejected create writes no audit rows")
    }

    @Test
    fun `create expense on REMITTED day with reason succeeds and flags audit entry`() {
        val remittedDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId)
        val expenseId = TestFixtures.uuid()

        val expense =
            ExpenseService.create(
                callerId = callerId,
                NewExpense(
                    id = expenseId,
                    branchDayId = remittedDayId,
                    amount = BigDecimal("500.00"),
                    category = ExpenseCategory.PANTRY,
                    notes = "Test",
                    reason = "Coordinator correction",
                ),
            )

        assertNotNull(expense)
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

    // ===== #510 — expense mutations serialize with the remittance REMITTED transition =====
    @Test
    fun `create on day REMITTED by remittance submit is rejected with no row`() {
        submitRemittanceCoveringDay(branchDayId)

        val expenseId = TestFixtures.uuid()
        val auditsBefore = callerAuditCount()

        assertFailsWith<ForbiddenException> {
            ExpenseService.create(
                callerId = callerId,
                expense(expenseId, notes = "Late expense"),
            )
        }
        assertEquals(auditsBefore, callerAuditCount(), "rejected create writes no audit rows")
        assertNull(ExpenseRepository.findById(expenseId), "rejected create writes no expense row")
    }

    @Test
    fun `replay of pre-submit create after remittance submit acks without duplicate audit`() {
        val expenseId = TestFixtures.uuid()
        ExpenseService.create(
            callerId = callerId,
            expense(expenseId, notes = "First"),
        )

        submitRemittanceCoveringDay(branchDayId)

        val insertsBefore = expenseInsertAuditCount(expenseId)
        val replayed =
            ExpenseService.create(
                callerId = callerId,
                expense(expenseId, notes = "First"),
            )

        assertEquals(expenseId, replayed.id)
        assertEquals(insertsBefore, expenseInsertAuditCount(expenseId), "replay writes no duplicate audit")
    }

    @Test
    fun `update on day REMITTED by remittance submit is rejected`() {
        val expenseId = TestFixtures.uuid()
        val created =
            ExpenseService.create(
                callerId = callerId,
                expense(expenseId),
            )

        submitRemittanceCoveringDay(branchDayId)

        val auditsBefore = callerAuditCount()
        assertFailsWith<ForbiddenException> {
            ExpenseService.update(
                callerId = callerId,
                expenseId = expenseId,
                ExpenseEdit(amount = BigDecimal("750.00"), category = ExpenseCategory.PANTRY, notes = null),
                expectedVersion = created.version,
            )
        }
        assertEquals(auditsBefore, callerAuditCount(), "rejected update writes no audit rows")
    }

    private fun submitRemittanceCoveringDay(dayId: UUID) {
        val remittanceId = TestFixtures.uuid()
        RemittanceService.createDraft(
            callerId = callerId,
            NewRemittance(
                id = remittanceId,
                type = RemittanceType.SESSION,
                branchId = branchId,
                method = RemittanceMethod.BANK_TRANSFER,
                dateRangeStart = TestFixtures.today.minusDays(1),
                dateRangeEnd = TestFixtures.today,
            ),
        )
        RemittanceService.addDayBreakdown(
            callerId = callerId,
            remittanceId = remittanceId,
            id = TestFixtures.uuid(),
            branchDayId = dayId,
        )
        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)
    }

    private fun expenseInsertAuditCount(expenseId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq ExpenseTable.tableName) and
                        (AuditLogTable.recordId eq expenseId) and
                        (AuditLogTable.action eq AuditAction.INSERT)
                }.count()
        }
}
