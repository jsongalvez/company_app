package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.RemittanceLine
import com.companyb.companyapp.repository.model.RemittanceLineTable
import com.companyb.companyapp.repository.model.RemittanceLineType
import com.companyb.companyapp.repository.model.RemittanceTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.util.UUID

private val logger = KotlinLogging.logger {}

object RemittanceLineRepository {
    @Suppress("LongParameterList", "LongMethod")
    fun addLine(
        id: UUID,
        remittanceId: UUID,
        type: RemittanceLineType,
        sessionId: UUID?,
        productSaleId: UUID?,
        amount: BigDecimal,
        createdBy: UUID,
        expectedVersion: Int,
    ): RemittanceLine =
        transaction {
            val existing =
                RemittanceLineTable
                    .selectAll()
                    .where { RemittanceLineTable.id eq id }
                    .singleOrNull()
            if (existing != null) {
                return@transaction existing.toRemittanceLine()
            }

            RemittanceLineTable.insertIgnore {
                it[RemittanceLineTable.id] = id
                it[RemittanceLineTable.remittanceId] = remittanceId
                it[RemittanceLineTable.type] = type
                it[RemittanceLineTable.sessionId] = sessionId
                it[RemittanceLineTable.productSaleId] = productSaleId
                it[RemittanceLineTable.amount] = amount
                it[RemittanceLineTable.createdBy] = createdBy
            }

            val versionUpdated =
                RemittanceTable.update({
                    (RemittanceTable.id eq remittanceId) and (RemittanceTable.version eq expectedVersion)
                }) {
                    it[RemittanceTable.version] = expectedVersion + 1
                }

            if (versionUpdated == 0) {
                error("version_mismatch")
            }

            val created =
                RemittanceLineTable
                    .selectAll()
                    .where { RemittanceLineTable.id eq id }
                    .single()
                    .toRemittanceLine()

            AuditLogRepository.record(
                tableName = RemittanceLineTable.tableName,
                recordId = created.id,
                action = AuditAction.INSERT,
                changedBy = createdBy,
                newValue =
                    AuditLogRepository.jsonFields(
                        "id" to created.id.toString(),
                        "remittanceId" to created.remittanceId.toString(),
                        "type" to created.type.name,
                        "amount" to created.amount.toPlainString(),
                    ),
            )

            created
        }.also { line ->
            logger.info {
                "[ADD-REMITTANCE-LINE] Line ${line.id.toString().maskUUID()} added to " +
                    "remittance ${line.remittanceId.toString().maskUUID()}"
            }
        }

    @Suppress("LongMethod")
    fun softDeleteLine(
        lineId: UUID,
        deletedBy: UUID,
        expectedVersion: Int,
    ): RemittanceLine? =
        transaction {
            val existing =
                RemittanceLineTable
                    .selectAll()
                    .where { RemittanceLineTable.id eq lineId }
                    .singleOrNull() ?: return@transaction null

            if (existing[RemittanceLineTable.deletedAt] != null) {
                return@transaction existing.toRemittanceLine()
            }

            val remittanceId = existing[RemittanceLineTable.remittanceId]

            val updated =
                RemittanceLineTable
                    .update({ RemittanceLineTable.id eq lineId and RemittanceLineTable.deletedAt.isNull() }) {
                        it[RemittanceLineTable.deletedBy] = deletedBy
                        it[RemittanceLineTable.deletedAt] =
                            org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
                    }

            if (updated == 0) return@transaction null

            val versionUpdated =
                RemittanceTable.update({
                    (RemittanceTable.id eq remittanceId) and (RemittanceTable.version eq expectedVersion)
                }) {
                    it[RemittanceTable.version] = expectedVersion + 1
                }

            if (versionUpdated == 0) {
                error("version_mismatch")
            }

            val line =
                RemittanceLineTable
                    .selectAll()
                    .where { RemittanceLineTable.id eq lineId }
                    .single()
                    .toRemittanceLine()

            AuditLogRepository.record(
                tableName = RemittanceLineTable.tableName,
                recordId = line.id,
                action = AuditAction.UPDATE,
                changedBy = deletedBy,
                oldValue = AuditLogRepository.jsonField("deletedAt", "null"),
                newValue = AuditLogRepository.jsonField("deletedAt", "now()"),
            )

            line
        }.also { line ->
            if (line != null) {
                logger.info {
                    "[DELETE-REMITTANCE-LINE] Line ${line.id.toString().maskUUID()} deleted"
                }
            }
        }

    fun findByRemittanceId(remittanceId: UUID): List<RemittanceLine> =
        transaction {
            RemittanceLineTable
                .selectAll()
                .where {
                    (RemittanceLineTable.remittanceId eq remittanceId) and
                        RemittanceLineTable.deletedAt.isNull()
                }.map { it.toRemittanceLine() }
        }

    fun sumAmountsByRemittanceId(remittanceId: UUID): BigDecimal =
        transaction {
            RemittanceLineTable
                .selectAll()
                .where {
                    (RemittanceLineTable.remittanceId eq remittanceId) and
                        RemittanceLineTable.deletedAt.isNull()
                }.map { it[RemittanceLineTable.amount] }
                .fold(BigDecimal.ZERO) { acc, amount -> acc.add(amount) }
        }

    private fun org.jetbrains.exposed.sql.ResultRow.toRemittanceLine(): RemittanceLine =
        RemittanceLine(
            id = this[RemittanceLineTable.id],
            remittanceId = this[RemittanceLineTable.remittanceId],
            type = this[RemittanceLineTable.type],
            sessionId = this[RemittanceLineTable.sessionId],
            productSaleId = this[RemittanceLineTable.productSaleId],
            createdBy = this[RemittanceLineTable.createdBy],
            createdAt = this[RemittanceLineTable.createdAt],
            deletedBy = this[RemittanceLineTable.deletedBy],
            deletedAt = this[RemittanceLineTable.deletedAt],
            amount = this[RemittanceLineTable.amount],
        )
}
