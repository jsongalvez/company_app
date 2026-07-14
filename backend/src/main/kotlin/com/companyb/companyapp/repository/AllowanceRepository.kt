package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.Allowance
import com.companyb.companyapp.repository.model.AllowanceTable
import com.companyb.companyapp.repository.model.AuditAction
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class AllowanceCreateResult(
    val allowance: Allowance,
    val created: Boolean,
)

object AllowanceRepository {
    @Suppress("LongParameterList")
    fun create(
        id: UUID,
        branchDayId: UUID,
        userId: UUID,
        amount: BigDecimal,
        assignedBy: UUID,
    ): AllowanceCreateResult =
        transaction {
            val existing = findByIdInTransaction(id)
            if (existing != null) {
                return@transaction AllowanceCreateResult(existing, created = false)
            }

            AllowanceTable.insert {
                it[AllowanceTable.id] = id
                it[AllowanceTable.branchDayId] = branchDayId
                it[AllowanceTable.userId] = userId
                it[AllowanceTable.amount] = amount
                it[AllowanceTable.assignedBy] = assignedBy
            }

            val created = findByIdInTransaction(id) ?: error("allowance not found after insert for $id")

            AuditLogRepository.record(
                tableName = AllowanceTable.tableName,
                recordId = created.id,
                action = AuditAction.INSERT,
                changedBy = assignedBy,
                newValue =
                    AuditLogRepository.jsonFields(
                        "id" to created.id.toString(),
                        "branchDayId" to created.branchDayId.toString(),
                        "userId" to created.userId.toString(),
                        "amount" to created.amount.toPlainString(),
                    ),
            )
            AllowanceCreateResult(created, created = true)
        }.also { result ->
            logger.info {
                "[CREATE-ALLOWANCE] Allowance ${result.allowance.id.toString().maskUUID()}" +
                    " created=${result.created}"
            }
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

    private fun org.jetbrains.exposed.sql.ResultRow.toAllowance(): Allowance =
        Allowance(
            id = this[AllowanceTable.id],
            branchDayId = this[AllowanceTable.branchDayId],
            userId = this[AllowanceTable.userId],
            amount = this[AllowanceTable.amount],
            assignedBy = this[AllowanceTable.assignedBy],
            assignedAt = this[AllowanceTable.assignedAt],
        )
}
