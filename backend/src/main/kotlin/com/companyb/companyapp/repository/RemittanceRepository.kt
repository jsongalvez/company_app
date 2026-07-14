package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.Remittance
import com.companyb.companyapp.repository.model.RemittanceMethod
import com.companyb.companyapp.repository.model.RemittanceStatus
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.repository.model.RemittanceType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class RemittanceCreateResult(
    val remittance: Remittance,
    val created: Boolean,
)

object RemittanceRepository {
    fun findById(id: UUID): Remittance? =
        transaction {
            RemittanceTable
                .selectAll()
                .where { RemittanceTable.id eq id }
                .singleOrNull()
                ?.toRemittance()
        }

    @Suppress("LongParameterList")
    fun createDraft(
        id: UUID,
        type: RemittanceType,
        branchId: UUID,
        method: RemittanceMethod,
        dateRangeStart: LocalDate,
        dateRangeEnd: LocalDate,
        submittedDate: LocalDate,
        submittedBy: UUID,
    ): RemittanceCreateResult =
        transaction {
            val existing = findByIdInTransaction(id)
            if (existing != null) {
                return@transaction RemittanceCreateResult(existing, created = false)
            }

            RemittanceTable.insertIgnore {
                it[RemittanceTable.id] = id
                it[RemittanceTable.type] = type
                it[RemittanceTable.branchId] = branchId
                it[RemittanceTable.method] = method
                it[RemittanceTable.dateRangeStart] = dateRangeStart
                it[RemittanceTable.dateRangeEnd] = dateRangeEnd
                it[RemittanceTable.submittedDate] = submittedDate
                it[RemittanceTable.submittedBy] = submittedBy
            }

            val created = findByIdInTransaction(id) ?: error("remittance not found after insert for $id")

            AuditLogRepository.record(
                tableName = RemittanceTable.tableName,
                recordId = created.id,
                action = AuditAction.INSERT,
                changedBy = submittedBy,
                newValue =
                    AuditLogRepository.jsonFields(
                        "id" to created.id.toString(),
                        "type" to created.type.name,
                        "status" to created.status.name,
                        "branchId" to created.branchId.toString(),
                        "method" to created.method.name,
                        "dateRangeStart" to created.dateRangeStart.toString(),
                        "dateRangeEnd" to created.dateRangeEnd.toString(),
                    ),
            )
            RemittanceCreateResult(created, created = true)
        }.also { result ->
            logger.info {
                "[CREATE-REMITTANCE-DRAFT] Remittance ${result.remittance.id.toString().maskUUID()}" +
                    " created=${result.created}"
            }
        }

    private fun findByIdInTransaction(id: UUID): Remittance? =
        RemittanceTable
            .selectAll()
            .where { RemittanceTable.id eq id }
            .singleOrNull()
            ?.toRemittance()

    private fun org.jetbrains.exposed.sql.ResultRow.toRemittance(): Remittance =
        Remittance(
            id = this[RemittanceTable.id],
            type = this[RemittanceTable.type],
            status = this[RemittanceTable.status],
            branchId = this[RemittanceTable.branchId],
            method = this[RemittanceTable.method],
            submittedDate = this[RemittanceTable.submittedDate],
            submittedBy = this[RemittanceTable.submittedBy],
            dateRangeStart = this[RemittanceTable.dateRangeStart],
            dateRangeEnd = this[RemittanceTable.dateRangeEnd],
            createdAt = this[RemittanceTable.createdAt],
            version = this[RemittanceTable.version],
        )
}
