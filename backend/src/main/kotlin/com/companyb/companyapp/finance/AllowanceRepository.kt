package com.companyb.companyapp.finance

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.logging.maskUUID
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class AllowanceCreateResult(
    val allowance: Allowance,
    val created: Boolean,
)

internal object AllowanceRepository {
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
        validateReplayOwnership(existing, params)
        if (insertedCount == 0) {
            return AllowanceCreateResult(existing, created = false)
        }

        return AllowanceCreateResult(existing, created = true)
    }

    /**
     * Ownership-validated replay (mirrors expense #510 / compensation #511): a same-id row
     * only acks the caller's own identical request; a foreign row fails closed so the
     * service-level pre-gate replay is not silently widened here.
     */
    private fun validateReplayOwnership(
        existing: Allowance,
        params: AllowanceCreateParams,
    ): Allowance {
        if (existing.branchDayId != params.branchDayId) {
            throw NotFoundException("Allowance not found for this branch day")
        }
        if (existing.userId != params.userId ||
            existing.assignedBy != params.assignedBy ||
            !sameAmount(existing, params)
        ) {
            throw ConflictException("Allowance id already belongs to another create request")
        }
        return existing
    }

    private fun sameAmount(
        existing: Allowance,
        params: AllowanceCreateParams,
    ): Boolean = existing.amount.compareTo(params.amount) == 0

    fun findByBranchDayId(branchDayId: UUID): List<Allowance> =
        transaction {
            AllowanceTable
                .selectAll()
                .where { AllowanceTable.branchDayId eq branchDayId }
                .map { it.toAllowance() }
        }.also {
            logger.info { "[FIND-ALLOWANCES] Found ${it.size} allowances for branchDay $branchDayId" }
        }

    /** In-transaction read for command-owned flows — runs on the caller's open transaction. */
    fun findByIdInTransaction(id: UUID): Allowance? =
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
