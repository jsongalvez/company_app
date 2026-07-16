package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.CommissionManualInclusion
import com.companyb.companyapp.repository.model.CommissionManualInclusionTable
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
    @Suppress("LongParameterList", "LongMethod")
    fun upsert(
        id: UUID,
        productSaleId: UUID,
        userId: UUID,
        isIncluded: Boolean,
        reason: String?,
        assignedBy: UUID,
    ): CommissionManualInclusion =
        transaction {
            val existing = findByProductSaleAndUserInTransaction(productSaleId, userId)

            if (existing != null) {
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

                AuditLogRepository.record(
                    tableName = CommissionManualInclusionTable.tableName,
                    recordId = updated.id,
                    action = AuditAction.UPDATE,
                    changedBy = assignedBy,
                    oldValue =
                        AuditLogRepository.jsonFields(
                            "isIncluded" to existing.isIncluded.toString(),
                            "reason" to (existing.reason ?: "null"),
                        ),
                    newValue =
                        AuditLogRepository.jsonFields(
                            "isIncluded" to updated.isIncluded.toString(),
                            "reason" to (updated.reason ?: "null"),
                        ),
                )

                updated
            } else {
                CommissionManualInclusionTable.insert {
                    it[CommissionManualInclusionTable.id] = id
                    it[CommissionManualInclusionTable.productSaleId] = productSaleId
                    it[CommissionManualInclusionTable.userId] = userId
                    it[CommissionManualInclusionTable.isIncluded] = isIncluded
                    if (reason != null) it[CommissionManualInclusionTable.reason] = reason
                    it[CommissionManualInclusionTable.assignedBy] = assignedBy
                }

                val created =
                    findByIdInTransaction(id)
                        ?: error("commission_manual_inclusion not found after insert for $id")

                AuditLogRepository.record(
                    tableName = CommissionManualInclusionTable.tableName,
                    recordId = created.id,
                    action = AuditAction.INSERT,
                    changedBy = assignedBy,
                    newValue =
                        AuditLogRepository.jsonFields(
                            "id" to created.id.toString(),
                            "productSaleId" to created.productSaleId.toString(),
                            "userId" to created.userId.toString(),
                            "isIncluded" to created.isIncluded.toString(),
                        ),
                )

                created
            }
        }.also { result ->
            logger.info {
                "[COMMISSION-INCLUSION] Inclusion ${result.id.toString().maskUUID()}" +
                    " productSale=${result.productSaleId.toString().maskUUID()}" +
                    " userId=${result.userId.toString().maskUUID()}" +
                    " isIncluded=${result.isIncluded}"
            }
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
