package com.companyb.companyapp.repository

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.Allowance
import com.companyb.companyapp.repository.model.AllowanceCreateParams
import com.companyb.companyapp.repository.model.AllowanceTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class AllowanceCreateResult(
    val allowance: Allowance,
    val created: Boolean,
)

object AllowanceRepository {
    /** In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction. */
    fun createInTransaction(params: AllowanceCreateParams): AllowanceCreateResult {
        val insertedCount =
            AllowanceTable
                .insertIgnore {
                    it[AllowanceTable.id] = params.id
                    it[AllowanceTable.branchDayId] = params.branchDayId
                    it[AllowanceTable.userId] = params.userId
                    it[AllowanceTable.amount] = params.amount
                    it[AllowanceTable.assignedBy] = params.assignedBy
                    it[AllowanceTable.assignedAt] = CurrentTimestampWithTimeZone
                }.insertedCount

        val existing =
            findByIdInTransaction(params.id)
                ?: error("allowance not found after insert for ${params.id}")
        if (existing.branchDayId != params.branchDayId) {
            throw NotFoundException("Allowance not found for this branch day")
        }
        if (insertedCount == 0) {
            return AllowanceCreateResult(existing, created = false)
        }

        return AllowanceCreateResult(existing, created = true)
    }

    fun findByBranchDayId(branchDayId: UUID): List<Allowance> =
        transaction {
            AllowanceTable
                .selectAll()
                .where { AllowanceTable.branchDayId eq branchDayId }
                .map { it.toAllowance() }
        }.also {
            logger.info { "[FIND-ALLOWANCES] Found ${it.size} allowances for branchDay $branchDayId" }
        }

    private fun findByIdInTransaction(id: UUID): Allowance? =
        AllowanceTable
            .selectAll()
            .where { AllowanceTable.id eq id }
            .singleOrNull()
            ?.toAllowance()

    private fun org.jetbrains.exposed.v1.core.ResultRow.toAllowance(): Allowance =
        Allowance(
            id = this[AllowanceTable.id],
            branchDayId = this[AllowanceTable.branchDayId],
            userId = this[AllowanceTable.userId],
            amount = this[AllowanceTable.amount],
            assignedBy = this[AllowanceTable.assignedBy],
            assignedAt = this[AllowanceTable.assignedAt],
        )
}
