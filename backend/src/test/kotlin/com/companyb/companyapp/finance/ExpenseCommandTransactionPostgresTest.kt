package com.companyb.companyapp.finance
import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.audit.AuditedRow
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.finance.ExpenseCreateParams
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

class ExpenseCommandTransactionPostgresTest : ExpenseServicePostgresBase() {
    // ===== #319 contract proofs — command-owned mutation transaction (ADR-0024) =====
    @Test
    fun `command commits domain write and audit atomically`() {
        val expenseId = TestFixtures.uuid()

        ExpenseService.create(
            callerId = callerId,
            expense(expenseId, notes = "Proof"),
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
                        context = AuditContext(changedBy = callerId),
                        row = AuditedRow(ExpenseTable.tableName, expenseId),
                        action = AuditAction.INSERT,
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
                expense(expenseId),
            )

        assertFailsWith<ConflictException> {
            ExpenseService.update(
                callerId = callerId,
                expenseId = expenseId,
                ExpenseEdit(amount = BigDecimal("750.00"), category = ExpenseCategory.WATER, notes = "Rejected"),
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
}
