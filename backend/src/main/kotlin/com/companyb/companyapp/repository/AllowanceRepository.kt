package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.Allowance
import com.companyb.companyapp.repository.model.AllowanceCreateParams
import com.companyb.companyapp.repository.model.AllowanceTable
import com.companyb.companyapp.repository.model.AuditAction
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
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
    fun create(params: AllowanceCreateParams): AllowanceCreateResult =
        transaction {
            val existing = findByIdInTransaction(params.id)
            if (existing != null) {
                return@transaction AllowanceCreateResult(existing, created = false)
            }

            AllowanceTable.insert {
                it[AllowanceTable.id] = params.id
                it[AllowanceTable.branchDayId] = params.branchDayId
                it[AllowanceTable.userId] = params.userId
                it[AllowanceTable.amount] = params.amount
                it[AllowanceTable.assignedBy] = params.assignedBy
            }

            val created = findByIdInTransaction(params.id) ?: error("allowance not found after insert for ${params.id}")

            AuditLogRepository.record(
                tableName = AllowanceTable.tableName,
                recordId = created.id,
                action = AuditAction.INSERT,
                changedBy = params.assignedBy,
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
