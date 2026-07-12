package com.companyb.companyapp.repository

import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.SessionBaseRate
import com.companyb.companyapp.repository.model.SessionBaseRateTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class SetRateResult(
    val rate: SessionBaseRate,
    val created: Boolean,
)

object SessionBaseRateRepository {
    @Suppress("LongParameterList")
    fun setRate(
        id: UUID,
        setBy: UUID,
        branchId: UUID,
        sessionType: SessionType,
        rate: BigDecimal,
        effectiveUntil: OffsetDateTime,
    ): SetRateResult =
        transaction {
            val insertedCount =
                SessionBaseRateTable
                    .insertIgnore {
                        it[SessionBaseRateTable.id] = id
                        it[SessionBaseRateTable.setBy] = setBy
                        it[SessionBaseRateTable.branchId] = branchId
                        it[SessionBaseRateTable.sessionType] = sessionType
                        it[SessionBaseRateTable.rate] = rate
                        it[SessionBaseRateTable.effectiveFrom] = OffsetDateTime.now(ZoneOffset.UTC)
                        it[SessionBaseRateTable.effectiveUntil] = effectiveUntil
                    }.insertedCount
            val inserted = insertedCount > 0
            val rateRow =
                findByIdInTransaction(id) ?: error("session_base_rate not found after idempotent insert for $id")

            if (inserted) {
                AuditLogRepository.record(
                    tableName = SessionBaseRateTable.tableName,
                    recordId = rateRow.id,
                    action = AuditAction.INSERT,
                    changedBy = setBy,
                    newValue =
                        AuditLogRepository.jsonFields(
                            "id" to rateRow.id.toString(),
                            "branchId" to rateRow.branchId.toString(),
                            "sessionType" to rateRow.sessionType.name,
                            "rate" to rateRow.rate.toPlainString(),
                            "effectiveFrom" to rateRow.effectiveFrom.toString(),
                            "effectiveUntil" to rateRow.effectiveUntil.toString(),
                        ),
                )
                SetRateResult(rateRow, created = true)
            } else {
                SetRateResult(rateRow, created = false)
            }
        }.also {
            logger.info {
                "[SET-RATE] Rate ${it.rate.id.toString().maskUUID()} created=${it.created}"
            }
        }

    fun deactivatePreviousRates(
        branchId: UUID,
        sessionType: SessionType,
        now: OffsetDateTime,
    ) {
        transaction {
            SessionBaseRateTable
                .update({
                    (SessionBaseRateTable.branchId eq branchId) and
                        (SessionBaseRateTable.sessionType eq sessionType) and
                        (SessionBaseRateTable.effectiveUntil greater now)
                }) {
                    it[SessionBaseRateTable.effectiveUntil] = now
                }
        }
    }

    fun findActiveByBranch(
        branchId: UUID,
        now: OffsetDateTime,
    ): List<SessionBaseRate> =
        transaction {
            SessionBaseRateTable
                .selectAll()
                .where {
                    (SessionBaseRateTable.branchId eq branchId) and
                        (SessionBaseRateTable.effectiveUntil greater now)
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

    private fun org.jetbrains.exposed.sql.ResultRow.toSessionBaseRate(): SessionBaseRate =
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
