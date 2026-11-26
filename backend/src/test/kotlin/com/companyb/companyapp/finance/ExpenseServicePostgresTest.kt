package com.companyb.companyapp.finance
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.finance.ExpenseRepository
import com.companyb.companyapp.finance.ExpenseTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpenseServicePostgresTest : ExpenseServicePostgresBase() {
    @Test
    fun `create expense succeeds with all fields`() {
        val expenseId = TestFixtures.uuid()

        val expense =
            ExpenseService.create(
                callerId = callerId,
                expense(expenseId, notes = "Office pantry supplies"),
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
        val expenseId = TestFixtures.uuid()

        val expense =
            ExpenseService.create(
                callerId = callerId,
                expense(expenseId, BigDecimal("300.00"), ExpenseCategory.WATER),
            )

        assertNotNull(expense)
        assertNull(expense.notes)
    }

    @Test
    fun `create idempotent duplicate returns existing`() {
        val expenseId = TestFixtures.uuid()

        val first =
            ExpenseService.create(
                callerId = callerId,
                expense(expenseId, notes = "First"),
            )

        val second =
            ExpenseService.create(
                callerId = callerId,
                expense(expenseId, notes = "First"),
            )

        assertEquals(first.id, second.id)
    }

    @Test
    fun `create rejects same id with divergent payload`() {
        val expenseId = TestFixtures.uuid()

        ExpenseService.create(
            callerId = callerId,
            expense(expenseId, notes = "First"),
        )

        assertFailsWith<ConflictException> {
            ExpenseService.create(
                callerId = callerId,
                expense(expenseId, BigDecimal("999.00"), notes = "First"),
            )
        }
    }

    @Test
    fun `create rejects same UUID for another branch day`() {
        val otherBranchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Other Expense Branch")
        val otherBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(otherBranchId)
        val expenseId = TestFixtures.uuid()

        ExpenseService.create(
            callerId = callerId,
            expense(expenseId),
        )

        assertFailsWith<NotFoundException> {
            ExpenseService.create(
                callerId = callerId,
                NewExpense(
                    id = expenseId,
                    branchDayId = otherBranchDayId,
                    amount = BigDecimal("500.00"),
                    category = ExpenseCategory.PANTRY,
                    notes = null,
                ),
            )
        }
    }

    @Test
    fun `create rejects same UUID for another creator`() {
        val otherCallerId = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherCallerId, "expense-other-caller")
        val expenseId = TestFixtures.uuid()

        ExpenseService.create(
            callerId = callerId,
            expense(expenseId),
        )

        assertFailsWith<ConflictException> {
            ExpenseService.create(
                callerId = otherCallerId,
                expense(expenseId),
            )
        }
    }

    @Test
    fun `concurrent same UUID retry creates and audits once`() {
        val expenseId = TestFixtures.uuid()
        val executor = Executors.newFixedThreadPool(2)
        val results =
            try {
                executor
                    .invokeAll(
                        listOf(
                            Callable {
                                ExpenseService.create(
                                    callerId = callerId,
                                    expense(expenseId),
                                )
                            },
                            Callable {
                                ExpenseService.create(
                                    callerId = callerId,
                                    expense(expenseId),
                                )
                            },
                        ),
                    ).map { it.get() }
            } finally {
                executor.shutdown()
            }

        assertEquals(listOf(expenseId, expenseId), results.map { it.id })
        val insertAuditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq ExpenseTable.tableName) and
                            (AuditLogTable.recordId eq expenseId) and
                            (AuditLogTable.action eq AuditAction.INSERT)
                    }.count()
            }
        assertEquals(1L, insertAuditCount)
    }

    @Test
    fun `create without EDIT_BRANCH_DATA is allowed at service layer`() {
        val expense =
            ExpenseService.create(
                callerId = callerId,
                expense(TestFixtures.uuid()),
            )

        assertNotNull(expense)
    }

    @Test
    fun `create with non-existent branch day returns not found`() {
        assertFailsWith<NotFoundException> {
            ExpenseService.create(
                callerId = callerId,
                NewExpense(
                    id = TestFixtures.uuid(),
                    branchDayId = TestFixtures.uuid(),
                    amount = BigDecimal("500.00"),
                    category = ExpenseCategory.PANTRY,
                    notes = null,
                ),
            )
        }
    }

    @Test
    fun `create writes audit log entry`() {
        val expenseId = TestFixtures.uuid()

        ExpenseService.create(
            callerId = callerId,
            expense(expenseId, notes = "Test"),
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

    // #925 persisted-range pre-gate (the #924 fail-closed class):
    // NUMERIC(10,2) overflow must 400 before the write, never 500 from Postgres.
    @Test
    fun `create expense rejects amount over NUMERIC(10,2) range with no row written`() {
        val blockedId = TestFixtures.uuid()

        assertFailsWith<ValidationException> {
            ExpenseService.create(
                callerId = callerId,
                expense(blockedId, BigDecimal("9999999999.99")),
            )
        }
        assertNull(ExpenseRepository.findById(blockedId))
    }

    @Test
    fun `create expense persists boundary max money amount`() {
        val expense =
            ExpenseService.create(
                callerId = callerId,
                expense(TestFixtures.uuid(), BigDecimal("99999999.99")),
            )

        assertEquals("99999999.99", expense.amount.toPlainString())
    }

    @Test
    fun `update expense rejects amount over NUMERIC(10,2) range`() {
        val expenseId = TestFixtures.uuid()
        ExpenseService.create(
            callerId = callerId,
            expense(expenseId),
        )

        assertFailsWith<ValidationException> {
            ExpenseService.update(
                callerId = callerId,
                expenseId = expenseId,
                ExpenseEdit(amount = BigDecimal("100000000"), category = ExpenseCategory.PANTRY, notes = null),
                expectedVersion = 1,
            )
        }
    }
}
