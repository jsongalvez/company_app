package com.companyb.companyapp.repository

import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.Compensation
import com.companyb.companyapp.repository.model.CompensationTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class CompensationCreateParams(
    val id: UUID,
    val workBranchDayId: UUID,
    val payingBranchDayId: UUID,
    val userId: UUID,
    val amount: BigDecimal,
    val assignedBy: UUID,
    val note: String?,
)

data class CompensationCreateResult(
    val compensation: Compensation,
    val created: Boolean,
)

data class CompensationWithUser(
    val compensation: Compensation,
    val userName: String,
)

object CompensationRepository {
    fun create(
        params: CompensationCreateParams,
        auditFn: (Compensation) -> Unit = {},
    ): CompensationCreateResult =
        transaction {
            val existing = findByIdInTransaction(params.id)
            if (existing != null) {
                return@transaction CompensationCreateResult(existing, created = false)
            }

            CompensationTable.insert {
                it[CompensationTable.id] = params.id
                it[CompensationTable.workBranchDayId] = params.workBranchDayId
                it[CompensationTable.payingBranchDayId] = params.payingBranchDayId
                it[CompensationTable.userId] = params.userId
                it[CompensationTable.amount] = params.amount
                it[CompensationTable.assignedBy] = params.assignedBy
                if (params.note != null) it[CompensationTable.note] = params.note
            }

            val created =
                findByIdInTransaction(params.id) ?: error("compensation not found after insert for ${params.id}")

            auditFn(created)
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
        auditFn: (Compensation) -> Unit = {},
    ): Compensation =
        transaction {
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
                throw VersionMismatchException(CompensationTable.tableName, compensationId)
            }

            val after =
                findByIdInTransaction(compensationId)
                    ?: error("compensation not found after update for $compensationId")

            auditFn(after)
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

    fun findByPayingBranchDayId(branchDayId: UUID): List<CompensationWithUser> =
        transaction {
            CompensationTable
                .innerJoin(AppUserTable, { CompensationTable.userId }, { AppUserTable.id })
                .selectAll()
                .where { CompensationTable.payingBranchDayId eq branchDayId }
                .orderBy(
                    CompensationTable.assignedAt to SortOrder.DESC,
                    CompensationTable.id to SortOrder.DESC,
                ).map { row ->
                    CompensationWithUser(
                        compensation = row.toCompensation(),
                        userName = row[AppUserTable.displayName],
                    )
                }
        }.also {
            logger.info {
                "[FIND-COMPENSATIONS] Found ${it.size} compensations for paying branch day $branchDayId"
            }
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
