package com.companyb.companyapp.repository

import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.SessionBaseRate
import com.companyb.companyapp.repository.model.SessionBaseRateCreateParams
import com.companyb.companyapp.repository.model.SessionBaseRateTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class SetRateResult(
    val rate: SessionBaseRate,
    val created: Boolean,
)

object SessionBaseRateRepository {
    fun setRate(
        params: SessionBaseRateCreateParams,
        auditFn: (SessionBaseRate) -> Unit = {},
    ): SetRateResult =
        transaction {
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
                    ?: error("session_base_rate not found after idempotent insert for ${params.id}")

            if (inserted) {
                auditFn(rateRow)
                SetRateResult(rateRow, created = true)
            } else {
                SetRateResult(rateRow, created = false)
            }
        }.also {
            logger.info {
                "[SET-RATE] Rate ${it.rate.id.toString().maskUUID()} created=${it.created}"
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
