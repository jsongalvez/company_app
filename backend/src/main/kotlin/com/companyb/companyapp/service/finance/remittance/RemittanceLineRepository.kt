package com.companyb.companyapp.service.finance.remittance

import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.RemittanceLine
import com.companyb.companyapp.repository.model.RemittanceLineTable
import com.companyb.companyapp.repository.model.RemittanceLineType
import com.companyb.companyapp.repository.model.RemittanceTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.util.UUID

data class AddLineParams(
    val id: UUID,
    val remittanceId: UUID,
    val type: RemittanceLineType,
    val sessionId: UUID?,
    val productSaleId: UUID?,
    val amount: BigDecimal,
    val createdBy: UUID,
    val expectedVersion: Int,
)

private val logger = KotlinLogging.logger {}

internal object RemittanceLineRepository {
    fun addLine(
        params: AddLineParams,
        auditFn: (RemittanceLine) -> Unit = {},
    ): RemittanceLine =
        transaction {
            val existing =
                RemittanceLineTable
                    .selectAll()
                    .where { RemittanceLineTable.id eq params.id }
                    .singleOrNull()
            if (existing != null) {
                return@transaction existing.toRemittanceLine()
            }

            RemittanceLineTable.insertIgnore {
                it[RemittanceLineTable.id] = params.id
                it[RemittanceLineTable.remittanceId] = params.remittanceId
                it[RemittanceLineTable.type] = params.type
                it[RemittanceLineTable.sessionId] = params.sessionId
                it[RemittanceLineTable.productSaleId] = params.productSaleId
                it[RemittanceLineTable.amount] = params.amount
                it[RemittanceLineTable.createdBy] = params.createdBy
            }

            val versionUpdated =
                RemittanceTable.update({
                    (RemittanceTable.id eq params.remittanceId) and (RemittanceTable.version eq params.expectedVersion)
                }) {
                    it[RemittanceTable.version] = params.expectedVersion + 1
                }

            if (versionUpdated == 0) {
                throw VersionMismatchException(RemittanceTable.tableName, params.remittanceId)
            }

            val created =
                RemittanceLineTable
                    .selectAll()
                    .where { RemittanceLineTable.id eq params.id }
                    .single()
                    .toRemittanceLine()

            auditFn(created)
            created
        }.also { line ->
            logger.info {
                "[ADD-REMITTANCE-LINE] Line ${line.id.toString().maskUUID()} added to " +
                    "remittance ${line.remittanceId.toString().maskUUID()}"
            }
        }

    fun softDeleteLine(
        lineId: UUID,
        remittanceId: UUID,
        deletedBy: UUID,
        expectedVersion: Int,
        auditFn: (RemittanceLine) -> Unit = {},
    ): RemittanceLine? =
        transaction {
            val existing =
                RemittanceLineTable
                    .selectAll()
                    .where {
                        (RemittanceLineTable.id eq lineId) and (RemittanceLineTable.remittanceId eq remittanceId)
                    }.singleOrNull() ?: return@transaction null

            if (existing[RemittanceLineTable.deletedAt] != null) {
                return@transaction existing.toRemittanceLine()
            }

            val updated =
                RemittanceLineTable
                    .update({ RemittanceLineTable.id eq lineId and RemittanceLineTable.deletedAt.isNull() }) {
                        it[RemittanceLineTable.deletedBy] = deletedBy
                        it[RemittanceLineTable.deletedAt] =
                            CurrentTimestampWithTimeZone
                    }

            if (updated == 0) return@transaction null

            val versionUpdated =
                RemittanceTable.update({
                    (RemittanceTable.id eq remittanceId) and (RemittanceTable.version eq expectedVersion)
                }) {
                    it[RemittanceTable.version] = expectedVersion + 1
                }

            if (versionUpdated == 0) {
                throw VersionMismatchException(RemittanceTable.tableName, remittanceId)
            }

            val line =
                RemittanceLineTable
                    .selectAll()
                    .where { RemittanceLineTable.id eq lineId }
                    .single()
                    .toRemittanceLine()

            auditFn(line)
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

    private fun org.jetbrains.exposed.v1.core.ResultRow.toRemittanceLine(): RemittanceLine =
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
