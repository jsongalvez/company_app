package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.CommissionManualInclusion
import com.companyb.companyapp.repository.model.CommissionManualInclusionTable
import com.companyb.companyapp.repository.model.CommissionManualInclusionUpsertParams
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

object CommissionManualInclusionRepository {
    fun upsert(
        params: CommissionManualInclusionUpsertParams,
        auditFn: (existing: CommissionManualInclusion?, result: CommissionManualInclusion) -> Unit = { _, _ -> },
    ): CommissionManualInclusion =
        transaction {
            val existing = findByProductSaleAndUserInTransaction(params.productSaleId, params.userId)

            if (existing != null) {
                updateInclusion(existing, params.isIncluded, params.reason, params.assignedBy, auditFn)
            } else {
                insertInclusion(params, auditFn)
            }
        }.also { result ->
            logger.info {
                "[COMMISSION-INCLUSION] Inclusion ${result.id.toString().maskUUID()}" +
                    " productSale=${result.productSaleId.toString().maskUUID()}" +
                    " userId=${result.userId.toString().maskUUID()}" +
                    " isIncluded=${result.isIncluded}"
            }
        }

    private fun updateInclusion(
        existing: CommissionManualInclusion,
        isIncluded: Boolean,
        reason: String?,
        assignedBy: UUID,
        auditFn: (CommissionManualInclusion?, CommissionManualInclusion) -> Unit,
    ): CommissionManualInclusion {
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

        auditFn(existing, updated)
        return updated
    }

    private fun insertInclusion(
        params: CommissionManualInclusionUpsertParams,
        auditFn: (CommissionManualInclusion?, CommissionManualInclusion) -> Unit,
    ): CommissionManualInclusion {
        CommissionManualInclusionTable.insert {
            it[CommissionManualInclusionTable.id] = params.id
            it[CommissionManualInclusionTable.productSaleId] = params.productSaleId
            it[CommissionManualInclusionTable.userId] = params.userId
            it[CommissionManualInclusionTable.isIncluded] = params.isIncluded
            if (params.reason != null) it[CommissionManualInclusionTable.reason] = params.reason
            it[CommissionManualInclusionTable.assignedBy] = params.assignedBy
        }

        val created =
            findByIdInTransaction(params.id)
                ?: error("commission_manual_inclusion not found after insert for ${params.id}")

        auditFn(null, created)
        return created
    }

    fun findByProductSaleAndUser(
        productSaleId: UUID,
        userId: UUID,
    ): CommissionManualInclusion? =
        transaction {
            findByProductSaleAndUserInTransaction(productSaleId, userId)
        }

    fun findByProductSaleId(productSaleId: UUID): List<CommissionManualInclusion> =
        transaction {
            CommissionManualInclusionTable
                .selectAll()
                .where { CommissionManualInclusionTable.productSaleId eq productSaleId }
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
