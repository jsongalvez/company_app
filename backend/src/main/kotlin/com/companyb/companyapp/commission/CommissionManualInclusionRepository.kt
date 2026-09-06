package com.companyb.companyapp.commission

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.util.UUID

internal object CommissionManualInclusionRepository {
    /**
     * In-transaction store operation (#323, ADR-0024) — upserts the manual inclusion on the
     * caller's command transaction and reports whether a row pre-existed so the command can
     * classify its audit write.
     */
    fun upsertInTransaction(params: CommissionManualInclusionUpsertParams): CommissionManualInclusionUpsertResult {
        val existing = findByProductSaleAndUserInTransaction(params.productSaleId, params.userId)

        return if (existing != null) {
            updateInclusion(existing, params.isIncluded, params.reason, params.assignedBy)
        } else {
            insertInclusion(params, params.isIncluded, params.reason, params.assignedBy)
        }
    }

    private fun updateInclusion(
        existing: CommissionManualInclusion,
        isIncluded: Boolean,
        reason: String?,
        assignedBy: UUID,
    ): CommissionManualInclusionUpsertResult {
        CommissionManualInclusionTable.update({
            CommissionManualInclusionTable.id eq existing.id
        }) {
            it[CommissionManualInclusionTable.isIncluded] = isIncluded
            if (reason != null) {
                it[CommissionManualInclusionTable.reason] = reason
            } else {
                it[CommissionManualInclusionTable.reason] = null
            }
            it[CommissionManualInclusionTable.assignedBy] = assignedBy
        }

        val updated =
            findByIdInTransaction(existing.id)
                ?: error("commission_manual_inclusion not found after update for ${existing.id}")

        return CommissionManualInclusionUpsertResult(existing, updated)
    }

    private fun insertInclusion(
        params: CommissionManualInclusionUpsertParams,
        isIncluded: Boolean,
        reason: String?,
        assignedBy: UUID,
    ): CommissionManualInclusionUpsertResult {
        val inserted =
            CommissionManualInclusionTable.insertIgnore {
                it[CommissionManualInclusionTable.id] = params.id
                it[CommissionManualInclusionTable.productSaleId] = params.productSaleId
                it[CommissionManualInclusionTable.userId] = params.userId
                it[CommissionManualInclusionTable.isIncluded] = isIncluded
                if (reason != null) it[CommissionManualInclusionTable.reason] = reason
                it[CommissionManualInclusionTable.assignedBy] = assignedBy
            }

        if (inserted.insertedCount == 0) {
            val existing =
                findByProductSaleAndUserInTransaction(params.productSaleId, params.userId)
                    ?: error("commission_manual_inclusion conflict row not found")
            return updateInclusion(existing, isIncluded, reason, assignedBy)
        }

        val created =
            findByIdInTransaction(params.id)
                ?: error("commission_manual_inclusion not found after insert for ${params.id}")

        return CommissionManualInclusionUpsertResult(null, created)
    }

    fun findByProductSaleAndUser(
        productSaleId: UUID,
        userId: UUID,
    ): CommissionManualInclusion? =
        transaction {
            findByProductSaleAndUserInTransaction(productSaleId, userId)
        }

    fun findBySaleIds(saleIds: List<UUID>): List<CommissionManualInclusion> =
        if (saleIds.isEmpty()) {
            emptyList()
        } else {
            transaction {
                findBySaleIdsInTransaction(saleIds)
            }
        }

    /** In-transaction batched read (#497) — one query for the shared commission aggregation. */
    fun findBySaleIdsInTransaction(saleIds: List<UUID>): List<CommissionManualInclusion> =
        if (saleIds.isEmpty()) {
            emptyList()
        } else {
            CommissionManualInclusionTable
                .selectAll()
                .where { CommissionManualInclusionTable.productSaleId inList saleIds }
                .map { it.toCommissionManualInclusion() }
        }

    fun findById(id: UUID): CommissionManualInclusion? =
        transaction {
            findByIdInTransaction(id)
        }

    private fun findByIdInTransaction(id: UUID): CommissionManualInclusion? =
        CommissionManualInclusionTable
            .selectAll()
            .where { CommissionManualInclusionTable.id eq id }
            .singleOrNull()
            ?.toCommissionManualInclusion()

    private fun findByProductSaleAndUserInTransaction(
        productSaleId: UUID,
        userId: UUID,
    ): CommissionManualInclusion? =
        CommissionManualInclusionTable
            .selectAll()
            .where {
                (CommissionManualInclusionTable.productSaleId eq productSaleId) and
                    (CommissionManualInclusionTable.userId eq userId)
            }.singleOrNull()
            ?.toCommissionManualInclusion()

    private fun org.jetbrains.exposed.v1.core.ResultRow.toCommissionManualInclusion(): CommissionManualInclusion =
        CommissionManualInclusion(
            id = this[CommissionManualInclusionTable.id],
            productSaleId = this[CommissionManualInclusionTable.productSaleId],
            userId = this[CommissionManualInclusionTable.userId],
            isIncluded = this[CommissionManualInclusionTable.isIncluded],
            reason = this[CommissionManualInclusionTable.reason],
            assignedBy = this[CommissionManualInclusionTable.assignedBy],
            assignedAt = this[CommissionManualInclusionTable.assignedAt],
        )
}
