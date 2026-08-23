package com.companyb.companyapp.service.finance.remittance

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.RemittanceDayBreakdownTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class AddDayBreakdownResult(
    val breakdown: RemittanceDayBreakdown,
    val created: Boolean,
)

/**
 * Remittance day-breakdown store (#320, ADR-0024). Mutating functions are in-transaction store
 * operations on the caller's (command-owned) transaction; the read helper keeps its wrapper.
 */
@Suppress("UnreachableCode")
internal object RemittanceDayBreakdownRepository {
    /**
     * Inserts the breakdown. `created` distinguishes a fresh insert (the command audits it)
     * from an idempotent return of the same (remittance, day) pair (no new audit).
     */
    fun addDayBreakdownInTransaction(
        id: UUID,
        remittanceId: UUID,
        branchDayId: UUID,
    ): AddDayBreakdownResult {
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
                return AddDayBreakdownResult(existingByParentAndDay.toRemittanceDayBreakdown(), created = false)
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

        logger.info {
            "[ADD-REMITTANCE-BREAKDOWN] Day breakdown ${created.id.toString().maskUUID()} " +
                "added to remittance ${created.remittanceId.toString().maskUUID()}"
        }
        return AddDayBreakdownResult(created, created = true)
    }

    fun findByRemittanceId(remittanceId: UUID): List<RemittanceDayBreakdown> =
        transaction {
            findByRemittanceIdInTransaction(remittanceId)
        }

    /** In-transaction read for command-owned flows — runs on the caller's open transaction. */
    fun findByRemittanceIdInTransaction(remittanceId: UUID): List<RemittanceDayBreakdown> =
        RemittanceDayBreakdownTable
            .selectAll()
            .where { RemittanceDayBreakdownTable.remittanceId eq remittanceId }
            .map { it.toRemittanceDayBreakdown() }

    /**
     * Deletes the breakdown scoped to its parent remittance and returns the deleted row for the
     * audit before-image; null when it does not exist under the parent.
     */
    fun deleteDayBreakdownInTransaction(
        breakdownId: UUID,
        remittanceId: UUID,
    ): RemittanceDayBreakdown? {
        val existing =
            RemittanceDayBreakdownTable
                .selectAll()
                .where {
                    (RemittanceDayBreakdownTable.id eq breakdownId) and
                        (RemittanceDayBreakdownTable.remittanceId eq remittanceId)
                }.singleOrNull() ?: return null

        RemittanceDayBreakdownTable.deleteWhere {
            (RemittanceDayBreakdownTable.id eq breakdownId) and
                (RemittanceDayBreakdownTable.remittanceId eq remittanceId)
        }

        val before = existing.toRemittanceDayBreakdown()
        logger.info {
            "[DELETE-REMITTANCE-BREAKDOWN] Day breakdown ${before.id.toString().maskUUID()} " +
                "deleted from remittance ${before.remittanceId.toString().maskUUID()}"
        }
        return before
    }

    private fun ResultRow.toRemittanceDayBreakdown(): RemittanceDayBreakdown =
        RemittanceDayBreakdown(
            id = this[RemittanceDayBreakdownTable.id],
            remittanceId = this[RemittanceDayBreakdownTable.remittanceId],
            branchDayId = this[RemittanceDayBreakdownTable.branchDayId],
        )
}
