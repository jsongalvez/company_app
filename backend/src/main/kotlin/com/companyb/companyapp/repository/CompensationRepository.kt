package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.Compensation
import com.companyb.companyapp.repository.model.CompensationTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class CompensationCreateResult(
    val compensation: Compensation,
    val created: Boolean,
)

object CompensationRepository {
    @Suppress("LongParameterList")
    fun create(
        id: UUID,
        workBranchDayId: UUID,
        payingBranchDayId: UUID,
        userId: UUID,
        amount: BigDecimal,
        assignedBy: UUID,
        note: String?,
    ): CompensationCreateResult =
        transaction {
            val existing = findByIdInTransaction(id)
            if (existing != null) {
                return@transaction CompensationCreateResult(existing, created = false)
            }

            CompensationTable.insert {
                it[CompensationTable.id] = id
                it[CompensationTable.workBranchDayId] = workBranchDayId
                it[CompensationTable.payingBranchDayId] = payingBranchDayId
                it[CompensationTable.userId] = userId
                it[CompensationTable.amount] = amount
                it[CompensationTable.assignedBy] = assignedBy
                if (note != null) it[CompensationTable.note] = note
            }

            val created = findByIdInTransaction(id) ?: error("compensation not found after insert for $id")

            AuditLogRepository.record(
                tableName = CompensationTable.tableName,
                recordId = created.id,
                action = AuditAction.INSERT,
                changedBy = assignedBy,
                newValue =
                    AuditLogRepository.jsonFields(
                        "id" to created.id.toString(),
                        "workBranchDayId" to created.workBranchDayId.toString(),
                        "payingBranchDayId" to created.payingBranchDayId.toString(),
                        "userId" to created.userId.toString(),
                        "amount" to created.amount.toPlainString(),
                    ),
            )
            CompensationCreateResult(created, created = true)
        }.also { result ->
            logger.info {
                "[CREATE-COMPENSATION] Compensation ${result.compensation.id.toString().maskUUID()}" +
                    " created=${result.created}"
            }
        }

    @Suppress("LongParameterList")
    fun update(
        compensationId: UUID,
        amount: BigDecimal,
        note: String?,
        expectedVersion: Int,
        changedBy: UUID,
    ): Compensation =
        transaction {
            val before =
                findByIdInTransaction(compensationId) ?: error("compensation not found for update $compensationId")

            val updatedCount =
                CompensationTable.update({
                    (CompensationTable.id eq compensationId) and
                        (CompensationTable.version eq expectedVersion)
                }) {
                    it[CompensationTable.amount] = amount
                    if (note != null) {
                        it[CompensationTable.note] = note
                    } else {
                        it[CompensationTable.note] = null
                    }
                    it[CompensationTable.version] = expectedVersion + 1
                }

            if (updatedCount == 0) {
                error("version_mismatch")
            }

            val after =
                findByIdInTransaction(compensationId)
                    ?: error("compensation not found after update for $compensationId")

            AuditLogRepository.record(
                tableName = CompensationTable.tableName,
                recordId = compensationId,
                action = AuditAction.UPDATE,
                changedBy = changedBy,
                oldValue =
                    AuditLogRepository.jsonFields(
                        "amount" to before.amount.toPlainString(),
                        "note" to (before.note ?: "null"),
                    ),
                newValue =
                    AuditLogRepository.jsonFields(
                        "amount" to after.amount.toPlainString(),
                        "note" to (after.note ?: "null"),
                    ),
            )
            after
        }.also {
            logger.info { "[UPDATE-COMPENSATION] Compensation ${compensationId.toString().maskUUID()} updated" }
        }

    fun findById(id: UUID): Compensation? =
        transaction {
            findByIdInTransaction(id)
        }.also { logger.info { "[FIND-COMPENSATION] Compensation ${id.toString().maskUUID()} found=${it != null}" } }

    fun findByUserAndPayingDay(
        userId: UUID,
        payingBranchDayId: UUID,
    ): Compensation? =
        transaction {
            CompensationTable
                .selectAll()
                .where {
                    (CompensationTable.userId eq userId) and
                        (CompensationTable.payingBranchDayId eq payingBranchDayId)
                }.singleOrNull()
                ?.toCompensation()
        }

    private fun findByIdInTransaction(id: UUID): Compensation? =
        CompensationTable
            .selectAll()
            .where { CompensationTable.id eq id }
            .singleOrNull()
            ?.toCompensation()

    private fun org.jetbrains.exposed.v1.core.ResultRow.toCompensation(): Compensation =
        Compensation(
            id = this[CompensationTable.id],
            workBranchDayId = this[CompensationTable.workBranchDayId],
            payingBranchDayId = this[CompensationTable.payingBranchDayId],
            userId = this[CompensationTable.userId],
            amount = this[CompensationTable.amount],
            assignedBy = this[CompensationTable.assignedBy],
            assignedAt = this[CompensationTable.assignedAt],
            note = this[CompensationTable.note],
            version = this[CompensationTable.version],
        )
}
