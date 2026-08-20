package com.companyb.companyapp.service.finance.remittance

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.RemittanceDayBreakdown
import com.companyb.companyapp.repository.model.RemittanceDayBreakdownTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Suppress("UnreachableCode")
internal object RemittanceDayBreakdownRepository {
    fun addDayBreakdown(
        id: UUID,
        remittanceId: UUID,
        branchDayId: UUID,
        auditFn: (RemittanceDayBreakdown) -> Unit = {},
    ): RemittanceDayBreakdown =
        transaction {
            val inserted =
                RemittanceDayBreakdownTable.insertIgnore {
                    it[RemittanceDayBreakdownTable.id] = id
                    it[RemittanceDayBreakdownTable.remittanceId] = remittanceId
                    it[RemittanceDayBreakdownTable.branchDayId] = branchDayId
                }

            if (inserted.insertedCount == 0) {
                val existingByParentAndDay =
                    RemittanceDayBreakdownTable
                        .selectAll()
                        .where {
                            (RemittanceDayBreakdownTable.remittanceId eq remittanceId) and
                                (RemittanceDayBreakdownTable.branchDayId eq branchDayId)
                        }.singleOrNull()
                if (existingByParentAndDay != null) {
                    return@transaction existingByParentAndDay.toRemittanceDayBreakdown()
                }
                throw ConflictException("Day breakdown ID already belongs to another remittance")
            }

            val created =
                RemittanceDayBreakdownTable
                    .selectAll()
                    .where {
                        (RemittanceDayBreakdownTable.id eq id) and
                            (RemittanceDayBreakdownTable.remittanceId eq remittanceId)
                    }.single()
                    .toRemittanceDayBreakdown()

            auditFn(created)
            created
        }.also { breakdown ->
            logger.info {
                "[ADD-REMITTANCE-BREAKDOWN] Day breakdown ${breakdown.id.toString().maskUUID()} " +
                    "added to remittance ${breakdown.remittanceId.toString().maskUUID()}"
            }
        }

    fun findByRemittanceId(remittanceId: UUID): List<RemittanceDayBreakdown> =
        transaction {
            RemittanceDayBreakdownTable
                .selectAll()
                .where { RemittanceDayBreakdownTable.remittanceId eq remittanceId }
                .map { it.toRemittanceDayBreakdown() }
        }

    fun deleteDayBreakdown(
        breakdownId: UUID,
        remittanceId: UUID,
        auditFn: (RemittanceDayBreakdown) -> Unit = {},
    ): RemittanceDayBreakdown? =
        transaction {
            val existing =
                RemittanceDayBreakdownTable
                    .selectAll()
                    .where {
                        (RemittanceDayBreakdownTable.id eq breakdownId) and
                            (RemittanceDayBreakdownTable.remittanceId eq remittanceId)
                    }.singleOrNull() ?: return@transaction null

            RemittanceDayBreakdownTable.deleteWhere {
                (RemittanceDayBreakdownTable.id eq breakdownId) and
                    (RemittanceDayBreakdownTable.remittanceId eq remittanceId)
            }

            val before = existing.toRemittanceDayBreakdown()
            auditFn(before)
            before
        }.also { breakdown ->
            if (breakdown != null) {
                logger.info {
                    "[DELETE-REMITTANCE-BREAKDOWN] Day breakdown ${breakdown.id.toString().maskUUID()} " +
                        "deleted from remittance ${breakdown.remittanceId.toString().maskUUID()}"
                }
            }
        }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toRemittanceDayBreakdown(): RemittanceDayBreakdown =
        RemittanceDayBreakdown(
            id = this[RemittanceDayBreakdownTable.id],
            remittanceId = this[RemittanceDayBreakdownTable.remittanceId],
            branchDayId = this[RemittanceDayBreakdownTable.branchDayId],
        )
}
