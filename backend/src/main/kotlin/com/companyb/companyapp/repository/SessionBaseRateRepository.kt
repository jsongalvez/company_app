package com.companyb.companyapp.repository

import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.SessionBaseRate
import com.companyb.companyapp.repository.model.SessionBaseRateCreateParams
import com.companyb.companyapp.repository.model.SessionBaseRateTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}
private const val RATE_OVERLAP_SQL_STATE = "23P01"

data class SetRateResult(
    val rate: SessionBaseRate,
    val created: Boolean,
)

object SessionBaseRateRepository {
    @Suppress("LongMethod")
    fun setRate(
        params: SessionBaseRateCreateParams,
        auditFn: (SessionBaseRate) -> Unit = {},
        auditUpdateFn: (SessionBaseRate, SessionBaseRate) -> Unit = { _, _ -> },
    ): SetRateResult =
        try {
            transaction {
                BranchTable
                    .selectAll()
                    .where { BranchTable.id eq params.branchId }
                    .forUpdate(ForUpdateOption.ForUpdate)
                    .singleOrNull()
                    ?: throw NotFoundException("Branch not found")

                val existing = findByIdInTransaction(params.id)
                if (existing != null) {
                    validateRetryOwnership(existing, params)
                    return@transaction SetRateResult(existing, created = false)
                }

                val previousRate =
                    SessionBaseRateTable
                        .selectAll()
                        .where {
                            (SessionBaseRateTable.branchId eq params.branchId) and
                                (SessionBaseRateTable.sessionType eq params.sessionType) and
                                (SessionBaseRateTable.effectiveUntil greater CurrentTimestampWithTimeZone)
                        }.singleOrNull()
                        ?.toSessionBaseRate()
                SessionBaseRateTable
                    .update({
                        (SessionBaseRateTable.branchId eq params.branchId) and
                            (SessionBaseRateTable.sessionType eq params.sessionType) and
                            (SessionBaseRateTable.effectiveUntil greater CurrentTimestampWithTimeZone)
                    }) {
                        it[SessionBaseRateTable.effectiveUntil] = CurrentTimestampWithTimeZone
                    }
                val insertedCount =
                    SessionBaseRateTable
                        .insertIgnore {
                            it[SessionBaseRateTable.id] = params.id
                            it[SessionBaseRateTable.setBy] = params.setBy
                            it[SessionBaseRateTable.branchId] = params.branchId
                            it[SessionBaseRateTable.sessionType] = params.sessionType
                            it[SessionBaseRateTable.rate] = params.rate
                            it[SessionBaseRateTable.effectiveFrom] = CurrentTimestampWithTimeZone
                            it[SessionBaseRateTable.effectiveUntil] = params.effectiveUntil
                        }.insertedCount
                val inserted = insertedCount > 0
                val rateRow =
                    findByIdInTransaction(params.id)
                        ?: throw ConflictException("Another rate was created for this branch and session type")

                if (inserted) {
                    if (previousRate != null) {
                        val updatedPreviousRate =
                            SessionBaseRateTable
                                .selectAll()
                                .where { SessionBaseRateTable.id eq previousRate.id }
                                .single()
                                .toSessionBaseRate()
                        auditUpdateFn(previousRate, updatedPreviousRate)
                    }
                    auditFn(rateRow)
                    SetRateResult(rateRow, created = true)
                } else {
                    validateRetryOwnership(rateRow, params)
                    SetRateResult(rateRow, created = false)
                }
            }.also {
                logger.info {
                    "[SET-RATE] Rate ${it.rate.id.toString().maskUUID()} created=${it.created}"
                }
            }
        } catch (error: ExposedSQLException) {
            if (error.sqlState == RATE_OVERLAP_SQL_STATE) {
                throw ConflictException("Another rate was created for this branch and session type")
            }
            throw error
        }

    @Suppress("ComplexCondition")
    private fun validateRetryOwnership(
        existing: SessionBaseRate,
        params: SessionBaseRateCreateParams,
    ) {
        if (existing.branchId != params.branchId) {
            throw NotFoundException("Session base rate not found for this branch")
        }
        if (
            existing.setBy != params.setBy ||
            existing.sessionType != params.sessionType ||
            existing.rate.compareTo(params.rate) != 0
        ) {
            throw ConflictException("Session base rate id already belongs to another create request")
        }
    }

    fun findActiveByBranch(branchId: UUID): List<SessionBaseRate> =
        transaction {
            SessionBaseRateTable
                .selectAll()
                .where {
                    (SessionBaseRateTable.branchId eq branchId) and
                        (SessionBaseRateTable.effectiveUntil greater CurrentTimestampWithTimeZone)
                }.orderBy(
                    SessionBaseRateTable.sessionType to SortOrder.ASC,
                    SessionBaseRateTable.effectiveFrom to SortOrder.DESC,
                ).map { it.toSessionBaseRate() }
        }.also {
            logger.info {
                "[FIND-ACTIVE-RATES] Fetched ${it.size} rate(s) for branch ${branchId.toString().maskUUID()}"
            }
        }

    private fun findByIdInTransaction(id: UUID): SessionBaseRate? =
        SessionBaseRateTable
            .selectAll()
            .where { SessionBaseRateTable.id eq id }
            .singleOrNull()
            ?.let { it.toSessionBaseRate() }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toSessionBaseRate(): SessionBaseRate =
        SessionBaseRate(
            id = this[SessionBaseRateTable.id],
            setBy = this[SessionBaseRateTable.setBy],
            branchId = this[SessionBaseRateTable.branchId],
            sessionType = this[SessionBaseRateTable.sessionType],
            rate = this[SessionBaseRateTable.rate],
            effectiveFrom = this[SessionBaseRateTable.effectiveFrom],
            effectiveUntil = this[SessionBaseRateTable.effectiveUntil],
        )
}
