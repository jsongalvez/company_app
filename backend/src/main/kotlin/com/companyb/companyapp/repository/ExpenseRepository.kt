package com.companyb.companyapp.repository

import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.Expense
import com.companyb.companyapp.repository.model.ExpenseCategory
import com.companyb.companyapp.repository.model.ExpenseCreateParams
import com.companyb.companyapp.repository.model.ExpenseTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.util.UUID

private val logger = KotlinLogging.logger {}

object ExpenseRepository {
    fun create(
        params: ExpenseCreateParams,
        auditFn: (Expense) -> Unit = {},
    ): Expense =
        transaction {
            val existing = findByIdInTransaction(params.id)
            if (existing != null) {
                return@transaction existing
            }

            ExpenseTable.insert {
                it[ExpenseTable.id] = params.id
                it[ExpenseTable.branchDayId] = params.branchDayId
                it[ExpenseTable.amount] = params.amount
                it[ExpenseTable.category] = params.category
                it[ExpenseTable.createdBy] = params.createdBy
                if (params.notes != null) it[ExpenseTable.notes] = params.notes
            }

            val created =
                findByIdInTransaction(params.id) ?: error("expense not found after insert for ${params.id}")

            auditFn(created)
            created
        }.also { logger.info { "[CREATE-EXPENSE] Expense ${params.id.toString().maskUUID()} created" } }

    fun softDelete(
        expenseId: UUID,
        deletedBy: UUID,
        auditFn: (Expense) -> Unit = {},
    ): Expense? =
        transaction {
            ExpenseTable.update({ ExpenseTable.id eq expenseId }) {
                it[ExpenseTable.deletedBy] = deletedBy
                it[ExpenseTable.deletedAt] =
                    CurrentTimestampWithTimeZone
            }

            val after =
                findByIdInTransaction(expenseId)
                    ?: error("expense not found after soft delete for $expenseId")

            auditFn(after)
            after
        }.also { result ->
            logger.info { "[SOFT-DELETE-EXPENSE] Expense ${expenseId.toString().maskUUID()} deleted=${result != null}" }
        }

    fun findByBranchDayId(branchDayId: UUID): List<Expense> =
        transaction {
            ExpenseTable
                .selectAll()
                .where {
                    (ExpenseTable.branchDayId eq branchDayId) and
                        ExpenseTable.deletedAt.isNull()
                }.map { it.toExpense() }
        }.also { logger.info { "[FIND-EXPENSES] Found ${it.size} expenses for branch_day $branchDayId" } }

    @Suppress("LongParameterList")
    fun update(
        expenseId: UUID,
        amount: BigDecimal,
        category: ExpenseCategory,
        notes: String?,
        expectedVersion: Int,
        auditFn: (Expense) -> Unit = {},
    ): Expense =
        transaction {
            val updatedCount =
                ExpenseTable.update({
                    (ExpenseTable.id eq expenseId) and
                        (ExpenseTable.version eq expectedVersion)
                }) {
                    it[ExpenseTable.amount] = amount
                    it[ExpenseTable.category] = category
                    if (notes != null) {
                        it[ExpenseTable.notes] = notes
                    } else {
                        it[ExpenseTable.notes] = null
                    }
                    it[ExpenseTable.version] = expectedVersion + 1
                }

            if (updatedCount == 0) {
                throw VersionMismatchException(ExpenseTable.tableName, expenseId)
            }

            val after =
                findByIdInTransaction(expenseId)
                    ?: error("expense not found after update for $expenseId")

            auditFn(after)
            after
        }.also { logger.info { "[UPDATE-EXPENSE] Expense ${expenseId.toString().maskUUID()} updated" } }

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

    private fun org.jetbrains.exposed.v1.core.ResultRow.toExpense(): Expense =
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
            version = this[ExpenseTable.version],
        )
}
