package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.Expense
import com.companyb.companyapp.repository.model.ExpenseCategory
import com.companyb.companyapp.repository.model.ExpenseTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

private val logger = KotlinLogging.logger {}

object ExpenseRepository {
    @Suppress("LongParameterList")
    fun create(
        id: UUID,
        branchDayId: UUID,
        amount: java.math.BigDecimal,
        category: ExpenseCategory,
        createdBy: UUID,
        notes: String?,
    ): Expense =
        transaction {
            val existing = findByIdInTransaction(id)
            if (existing != null) {
                return@transaction existing
            }

            ExpenseTable.insert {
                it[ExpenseTable.id] = id
                it[ExpenseTable.branchDayId] = branchDayId
                it[ExpenseTable.amount] = amount
                it[ExpenseTable.category] = category
                it[ExpenseTable.createdBy] = createdBy
                if (notes != null) it[ExpenseTable.notes] = notes
            }

            val created =
                findByIdInTransaction(id) ?: error("expense not found after insert for $id")

            AuditLogRepository.record(
                tableName = ExpenseTable.tableName,
                recordId = created.id,
                action = AuditAction.INSERT,
                changedBy = createdBy,
                newValue =
                    AuditLogRepository.jsonFields(
                        "id" to created.id.toString(),
                        "branchDayId" to created.branchDayId.toString(),
                        "amount" to created.amount.toPlainString(),
                        "category" to created.category.name,
                    ),
            )
            created
        }.also { logger.info { "[CREATE-EXPENSE] Expense ${id.toString().maskUUID()} created" } }

    fun softDelete(
        expenseId: UUID,
        deletedBy: UUID,
        reason: String,
    ): Expense? =
        transaction {
            val before =
                findByIdInTransaction(expenseId) ?: return@transaction null

            ExpenseTable.update({ ExpenseTable.id eq expenseId }) {
                it[ExpenseTable.deletedBy] = deletedBy
                it[ExpenseTable.deletedAt] =
                    org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
            }

            val after =
                findByIdInTransaction(expenseId)
                    ?: error("expense not found after soft delete for $expenseId")

            AuditLogRepository.record(
                tableName = ExpenseTable.tableName,
                recordId = expenseId,
                action = AuditAction.DELETE,
                changedBy = deletedBy,
                oldValue =
                    AuditLogRepository.jsonFields(
                        "amount" to before.amount.toPlainString(),
                        "category" to before.category.name,
                    ),
                newValue =
                    AuditLogRepository.jsonFields(
                        "amount" to after.amount.toPlainString(),
                        "category" to after.category.name,
                    ),
                reason = reason,
            )
            after
        }.also { result ->
            logger.info { "[SOFT-DELETE-EXPENSE] Expense ${expenseId.toString().maskUUID()} deleted=${result != null}" }
        }

    fun findByBranchDayId(branchDayId: UUID): List<Expense> =
        transaction {
            ExpenseTable
                .selectAll()
                .where { ExpenseTable.branchDayId eq branchDayId }
                .map { it.toExpense() }
        }.also { logger.info { "[FIND-EXPENSES] Found ${it.size} expenses for branch_day $branchDayId" } }

    fun findById(id: UUID): Expense? =
        transaction {
            findByIdInTransaction(id)
        }.also { logger.info { "[FIND-EXPENSE] Expense ${id.toString().maskUUID()} found=${it != null}" } }

    private fun findByIdInTransaction(id: UUID): Expense? =
        ExpenseTable
            .selectAll()
            .where { ExpenseTable.id eq id }
            .singleOrNull()
            ?.toExpense()

    private fun org.jetbrains.exposed.sql.ResultRow.toExpense(): Expense =
        Expense(
            id = this[ExpenseTable.id],
            branchDayId = this[ExpenseTable.branchDayId],
            amount = this[ExpenseTable.amount],
            category = this[ExpenseTable.category],
            notes = this[ExpenseTable.notes],
            createdBy = this[ExpenseTable.createdBy],
            createdAt = this[ExpenseTable.createdAt],
            deletedBy = this[ExpenseTable.deletedBy],
            deletedAt = this[ExpenseTable.deletedAt],
        )
}
