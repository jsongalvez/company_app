@file:Suppress("LargeClass")

package com.companyb.companyapp.finance
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.finance.ExpenseCreateParams
import com.companyb.companyapp.finance.ExpenseRepository
import com.companyb.companyapp.finance.ExpenseTable
import com.companyb.companyapp.remittance.RemittanceService
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpenseServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()

    private lateinit var branchDayId: UUID

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "expense-caller")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Test Expense Branch")
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        grantEditBranchData(callerId)
    }

    @Test
    fun `create expense succeeds with all fields`() {
        val expenseId = TestFixtures.uuid()

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
        val expenseId = TestFixtures.uuid()

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
        val expenseId = TestFixtures.uuid()

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
    fun `create rejects same UUID for another branch day`() {
        val otherBranchId = TestFixtures.uuid()
        val otherSourceId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Other Expense Branch")
        val otherBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(otherBranchId)
        IdentityFixtures.grantCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = otherBranchId,
            sourceId = otherSourceId,
        )
        val expenseId = TestFixtures.uuid()

        ExpenseService.create(
            callerId = callerId,
            id = expenseId,
            branchDayId = branchDayId,
            amount = BigDecimal("500.00"),
            category = ExpenseCategory.PANTRY,
            notes = null,
        )

        assertFailsWith<NotFoundException> {
            ExpenseService.create(
                callerId = callerId,
                id = expenseId,
                branchDayId = otherBranchDayId,
                amount = BigDecimal("500.00"),
                category = ExpenseCategory.PANTRY,
                notes = null,
            )
        }
    }

    @Test
    fun `create rejects same UUID for another creator`() {
        val otherCallerId = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherCallerId, "expense-other-caller")
        grantEditBranchData(otherCallerId)
        val expenseId = TestFixtures.uuid()

        ExpenseService.create(
            callerId = callerId,
            id = expenseId,
            branchDayId = branchDayId,
            amount = BigDecimal("500.00"),
            category = ExpenseCategory.PANTRY,
            notes = null,
        )

        assertFailsWith<ConflictException> {
            ExpenseService.create(
                callerId = otherCallerId,
                id = expenseId,
                branchDayId = branchDayId,
                amount = BigDecimal("500.00"),
                category = ExpenseCategory.PANTRY,
                notes = null,
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
                                    id = expenseId,
                                    branchDayId = branchDayId,
                                    amount = BigDecimal("500.00"),
                                    category = ExpenseCategory.PANTRY,
                                    notes = null,
                                )
                            },
                            Callable {
                                ExpenseService.create(
                                    callerId = callerId,
                                    id = expenseId,
                                    branchDayId = branchDayId,
                                    amount = BigDecimal("500.00"),
                                    category = ExpenseCategory.PANTRY,
                                    notes = null,
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
        IdentityFixtures.revokeAllCapabilities(callerId)

        val expense =
            ExpenseService.create(
                callerId = callerId,
                id = TestFixtures.uuid(),
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
                id = TestFixtures.uuid(),
                branchDayId = TestFixtures.uuid(),
                amount = BigDecimal("500.00"),
                category = ExpenseCategory.PANTRY,
                notes = null,
            )
        }
    }

    @Test
    fun `create writes audit log entry`() {
        val expenseId = TestFixtures.uuid()

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
        val expenseId = TestFixtures.uuid()
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
        val expenseId = TestFixtures.uuid()
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
        val expenseId = TestFixtures.uuid()
        ExpenseService.create(
            callerId = callerId,
            id = expenseId,
            branchDayId = branchDayId,
            amount = BigDecimal("500.00"),
            category = ExpenseCategory.PANTRY,
            notes = null,
        )

        val auditsBefore = callerAuditCount()

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
        assertEquals(auditsBefore, callerAuditCount(), "conflicted update writes no audit rows")
    }

    @Test
    fun `update non-existent expense returns not found`() {
        assertFailsWith<NotFoundException> {
            ExpenseService.update(
                callerId = callerId,
                expenseId = TestFixtures.uuid(),
                amount = BigDecimal("750.00"),
                category = ExpenseCategory.PANTRY,
                notes = null,
                expectedVersion = 1,
            )
        }
    }

    @Test
    fun `update soft-deleted expense is rejected`() {
        val expenseId = TestFixtures.uuid()
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
    fun `update after soft delete race is rejected`() {
        val expenseId = TestFixtures.uuid()
        val created =
            ExpenseService.create(
                callerId = callerId,
                id = expenseId,
                branchDayId = branchDayId,
                amount = BigDecimal("500.00"),
                category = ExpenseCategory.PANTRY,
                notes = "Original",
            )

        ExpenseService.softDelete(
            callerId = callerId,
            expenseId = expenseId,
            reason = "Incorrect entry",
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
        assertEquals(created.version, after.version)
        assertEquals("Incorrect entry", after.deletedReason)
        assertEquals(BigDecimal("500.00"), after.amount)
    }

    @Test
    fun `update writes audit log entry`() {
        val expenseId = TestFixtures.uuid()
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
    fun `soft delete expense succeeds and records the reason`() {
        val expenseId = TestFixtures.uuid()
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
        // #153 Q2 — the reason is denormalized on the row (written in the same UPDATE as the
        // soft delete) so the GET stays single-query; the audit row remains the history record.
        assertEquals("Incorrect entry", deleted.deletedReason)
    }

    @Test
    fun `soft delete non-existent expense returns not found`() {
        assertFailsWith<NotFoundException> {
            ExpenseService.softDelete(
                callerId = callerId,
                expenseId = TestFixtures.uuid(),
                reason = "Wrong entry",
            )
        }
    }

    @Test
    fun `soft delete without EDIT_BRANCH_DATA is allowed at service layer`() {
        val expenseId = TestFixtures.uuid()
        ExpenseService.create(
            callerId = callerId,
            id = expenseId,
            branchDayId = branchDayId,
            amount = BigDecimal("500.00"),
            category = ExpenseCategory.PANTRY,
            notes = "Test",
        )

        IdentityFixtures.revokeAllCapabilities(callerId)

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
        val expenseId = TestFixtures.uuid()
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
    fun `list expenses returns all non-deleted expenses for the day`() {
        val expense1Id = TestFixtures.uuid()
        val expense2Id = TestFixtures.uuid()
        val expense3Id = TestFixtures.uuid()

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
    fun `list expenses includes soft-deleted expenses with their reason`() {
        val activeId = TestFixtures.uuid()
        val deletedId = TestFixtures.uuid()

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

        // #153 Q1 (CR-022 flip, commit f8b288a) — the GET includes soft-deleted rows: the
        // Finance screen renders them dimmed + "removed" + reason (an undo affordance); the
        // P&L totals still exclude them (the summary view filters).
        val expenses = ExpenseService.findByBranchDayId(callerId, branchDayId)

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
                id = expenseId,
                branchDayId = branchDayId,
                amount = BigDecimal("200.00"),
                category = ExpenseCategory.WATER,
                notes = "Will be restored",
            )
        ExpenseService.softDelete(
            callerId = callerId,
            expenseId = expenseId,
            reason = "Incorrect entry",
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
        assertEquals(created.version, restored.version, "#153 Q6 — no version bump on restore")
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
            id = expenseId,
            branchDayId = branchDayId,
            amount = BigDecimal("200.00"),
            category = ExpenseCategory.WATER,
            notes = "Never deleted",
        )

        assertFailsWith<ValidationException> {
            ExpenseService.restore(
                callerId = callerId,
                expenseId = expenseId,
            )
        }
    }

    @Test
    fun `restore on REMITTED day without reason is rejected`() {
        val remittedDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)
        val expenseId = TestFixtures.uuid()
        ExpenseService.create(
            callerId = callerId,
            id = expenseId,
            branchDayId = remittedDayId,
            amount = BigDecimal("200.00"),
            category = ExpenseCategory.WATER,
            notes = null,
            reason = "Recorded late",
        )
        ExpenseService.softDelete(
            callerId = callerId,
            expenseId = expenseId,
            reason = "Incorrect entry",
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
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)
        val expenseId = TestFixtures.uuid()
        ExpenseService.create(
            callerId = callerId,
            id = expenseId,
            branchDayId = remittedDayId,
            amount = BigDecimal("200.00"),
            category = ExpenseCategory.WATER,
            notes = null,
            reason = "Recorded late",
        )
        ExpenseService.softDelete(
            callerId = callerId,
            expenseId = expenseId,
            reason = "Incorrect entry",
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
    fun `restore writes an audit update row`() {
        val expenseId = TestFixtures.uuid()
        ExpenseService.create(
            callerId = callerId,
            id = expenseId,
            branchDayId = branchDayId,
            amount = BigDecimal("200.00"),
            category = ExpenseCategory.WATER,
            notes = "Will be restored",
        )
        ExpenseService.softDelete(
            callerId = callerId,
            expenseId = expenseId,
            reason = "Incorrect entry",
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
            ExpenseService.findByBranchDayId(callerId, TestFixtures.uuid())
        }
    }

    @Test
    fun `create expense on REMITTED day without reason is rejected`() {
        val remittedDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)
        val auditsBefore = callerAuditCount()

        assertFailsWith<ValidationException> {
            ExpenseService.create(
                callerId = callerId,
                id = TestFixtures.uuid(),
                branchDayId = remittedDayId,
                amount = BigDecimal("500.00"),
                category = ExpenseCategory.PANTRY,
                notes = "Test",
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
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)
        val expenseId = TestFixtures.uuid()

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

    // ===== #319 contract proofs — command-owned mutation transaction (ADR-0024) =====

    @Test
    fun `command commits domain write and audit atomically`() {
        val expenseId = TestFixtures.uuid()

        ExpenseService.create(
            callerId = callerId,
            id = expenseId,
            branchDayId = branchDayId,
            amount = BigDecimal("500.00"),
            category = ExpenseCategory.PANTRY,
            notes = "Proof",
        )

        val (expenseRows, insertAudits) =
            transaction {
                val expenses =
                    ExpenseTable.selectAll().where { ExpenseTable.id eq expenseId }.count()
                val audits =
                    AuditLogTable
                        .selectAll()
                        .where {
                            (AuditLogTable.auditTableName eq ExpenseTable.tableName) and
                                (AuditLogTable.recordId eq expenseId) and
                                (AuditLogTable.action eq AuditAction.INSERT)
                        }.count()
                expenses to audits
            }
        assertEquals(1L, expenseRows, "domain row committed")
        assertEquals(1L, insertAudits, "audit row committed in the same transaction")
    }

    @Test
    fun `audit failure inside the command rolls the mutation back`() {
        val expenseId = TestFixtures.uuid()

        // Exercises the exact composition a migrated command runs — store write on the command
        // transaction, then the audit insert into that same transaction. A failing audit
        // statement must abort the whole transaction: no domain row, no partial audit.
        val error =
            runCatching {
                transaction {
                    ExpenseRepository.createInTransaction(
                        ExpenseCreateParams(
                            id = expenseId,
                            branchDayId = branchDayId,
                            amount = BigDecimal("500.00"),
                            category = ExpenseCategory.PANTRY,
                            createdBy = callerId,
                            notes = null,
                        ),
                    )
                    AuditLog.record(
                        tableName = ExpenseTable.tableName,
                        recordId = expenseId,
                        action = AuditAction.INSERT,
                        changedBy = callerId,
                        oldValue = "{not-valid-json",
                    )
                }
            }.exceptionOrNull()

        assertNotNull(error, "malformed jsonb audit payload must fail the statement")

        val (expenseRows, allAudits) =
            transaction {
                val expenses = ExpenseTable.selectAll().where { ExpenseTable.id eq expenseId }.count()
                val audits =
                    AuditLogTable
                        .selectAll()
                        .where {
                            (AuditLogTable.auditTableName eq ExpenseTable.tableName) and
                                (AuditLogTable.recordId eq expenseId)
                        }.count()
                expenses to audits
            }
        assertEquals(0L, expenseRows, "mutation rolled back with the failed audit")
        assertEquals(0L, allAudits, "no partial audit row survived")
    }

    @Test
    fun `mutation failure writes no audit row`() {
        val expenseId = TestFixtures.uuid()
        val created =
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
                category = ExpenseCategory.WATER,
                notes = "Rejected",
                expectedVersion = 999,
            )
        }

        val (rowVersion, updateAudits) =
            transaction {
                val row = ExpenseTable.selectAll().where { ExpenseTable.id eq expenseId }.single()
                val audits =
                    AuditLogTable
                        .selectAll()
                        .where {
                            (AuditLogTable.auditTableName eq ExpenseTable.tableName) and
                                (AuditLogTable.recordId eq expenseId) and
                                (AuditLogTable.action eq AuditAction.UPDATE)
                        }.count()
                row[ExpenseTable.version] to audits
            }
        assertEquals(created.version, rowVersion, "row untouched")
        assertEquals(0L, updateAudits, "no audit row for a failed mutation")
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
                id = expenseId,
                branchDayId = branchDayId,
                amount = BigDecimal("500.00"),
                category = ExpenseCategory.PANTRY,
                notes = "Late expense",
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
            id = expenseId,
            branchDayId = branchDayId,
            amount = BigDecimal("500.00"),
            category = ExpenseCategory.PANTRY,
            notes = "First",
        )

        submitRemittanceCoveringDay(branchDayId)

        val insertsBefore = expenseInsertAuditCount(expenseId)
        val replayed =
            ExpenseService.create(
                callerId = callerId,
                id = expenseId,
                branchDayId = branchDayId,
                amount = BigDecimal("500.00"),
                category = ExpenseCategory.PANTRY,
                notes = "First",
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
                id = expenseId,
                branchDayId = branchDayId,
                amount = BigDecimal("500.00"),
                category = ExpenseCategory.PANTRY,
                notes = null,
            )

        submitRemittanceCoveringDay(branchDayId)

        val auditsBefore = callerAuditCount()
        assertFailsWith<ForbiddenException> {
            ExpenseService.update(
                callerId = callerId,
                expenseId = expenseId,
                amount = BigDecimal("750.00"),
                category = ExpenseCategory.PANTRY,
                notes = null,
                expectedVersion = created.version,
            )
        }
        assertEquals(auditsBefore, callerAuditCount(), "rejected update writes no audit rows")
    }

    private fun submitRemittanceCoveringDay(dayId: UUID) {
        val remittanceId = TestFixtures.uuid()
        RemittanceService.createDraft(
            callerId = callerId,
            id = remittanceId,
            type = RemittanceType.SESSION,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = TestFixtures.today.minusDays(1),
            dateRangeEnd = TestFixtures.today,
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

    private fun callerAuditCount(): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where { AuditLogTable.changedBy eq callerId }
                .count()
        }

    private fun grantEditBranchData(userId: UUID) {
        IdentityFixtures.grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
    }
}
