package com.companyb.companyapp.session

import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.logging.maskUUID
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

private val logger = KotlinLogging.logger {}
private const val RATE_OVERLAP_SQL_STATE = "23P01"

// Far-future open-ended window shared by every rate writer (set rotation + #418 default
// provisioning); matches the V22 backfill literal.
private const val FAR_FUTURE_YEAR = 9999
private const val FAR_FUTURE_MONTH = 12
private const val FAR_FUTURE_DAY = 31
private const val FAR_FUTURE_HOUR = 23
private const val FAR_FUTURE_MINUTE = 59
private const val FAR_FUTURE_SECOND = 59

internal val FAR_FUTURE: OffsetDateTime =
    OffsetDateTime.of(
        FAR_FUTURE_YEAR,
        FAR_FUTURE_MONTH,
        FAR_FUTURE_DAY,
        FAR_FUTURE_HOUR,
        FAR_FUTURE_MINUTE,
        FAR_FUTURE_SECOND,
        0,
        ZoneOffset.UTC,
    )

/**
 * The BR "Base Rates" documented defaults, one per session type — provisioned at branch
 * creation (#418) so a fresh branch never fails session create with "No base rate configured".
 */
internal val DEFAULT_BASE_RATES: Map<SessionType, BigDecimal> =
    mapOf(
        SessionType.REGULAR to BigDecimal("2500.00"),
        SessionType.SECOND_SESSION to BigDecimal("2000.00"),
        SessionType.SUBSEQUENT to BigDecimal("1500.00"),
        SessionType.PROVINCIAL_FIRST to BigDecimal("3500.00"),
        SessionType.MEDICAL_MISSION to BigDecimal("0.00"),
    )

data class SetRateResult(
    val rate: SessionBaseRate,
    val created: Boolean,
    /** Previous active rate (before/after its effectiveUntil close) when this set rotated one. */
    val previousBefore: SessionBaseRate? = null,
    val previousAfter: SessionBaseRate? = null,
)

internal object SessionBaseRateRepository {
    /**
     * In-transaction store operation (#323, ADR-0024) — seeds the BR-documented default base
     * rates for a freshly created branch (#418). Runs on the caller's command transaction
     * (branch creation); no per-rate audit rows — mechanism write riding the audited
     * branch-create domain event (#414 deflation precedent).
     */
    fun insertDefaultsInTransaction(
        branchId: UUID,
        setBy: UUID,
    ) {
        DEFAULT_BASE_RATES.forEach { (sessionType, rate) ->
            SessionBaseRateTable.insert {
                it[SessionBaseRateTable.id] = UUID.randomUUID()
                it[SessionBaseRateTable.setBy] = setBy
                it[SessionBaseRateTable.branchId] = branchId
                it[SessionBaseRateTable.sessionType] = sessionType
                it[SessionBaseRateTable.rate] = rate
                it[SessionBaseRateTable.effectiveUntil] = FAR_FUTURE
            }
        }
        val maskedBranch = branchId.toString().maskUUID()
        logger.info {
            "[SEED-DEFAULT-RATES] ${DEFAULT_BASE_RATES.size} default rate(s) provisioned for branch $maskedBranch"
        }
    }

    /**
     * In-transaction store operation (#323, ADR-0024) — branch lock, retry-ownership check,
     * rate rotation, and insert run on the caller's command transaction; the SQLSTATE 23P01
     * exclusion-violation translation is preserved here.
     */
    fun setRateInTransaction(params: SessionBaseRateCreateParams): SetRateResult =
        try {
            BranchTable
                .selectAll()
                .where { BranchTable.id eq params.branchId }
                .forUpdate(ForUpdateOption.ForUpdate)
                .singleOrNull()
                ?: throw NotFoundException("Branch not found")

            val existing = findByIdInTransaction(params.id)
            if (existing != null) {
                validateRetryOwnership(existing, params)
                return SetRateResult(existing, created = false)
            }

            val previousRate = closePreviousRate(params.branchId, params.sessionType)
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
                    SetRateResult(
                        rate = rateRow,
                        created = true,
                        previousBefore = previousRate,
                        previousAfter = updatedPreviousRate,
                    )
                } else {
                    SetRateResult(rateRow, created = true)
                }
            } else {
                validateRetryOwnership(rateRow, params)
                SetRateResult(rateRow, created = false)
            }
        } catch (error: ExposedSQLException) {
            if (error.sqlState == RATE_OVERLAP_SQL_STATE) {
                throw ConflictException("Another rate was created for this branch and session type")
            }
            throw error
        }

    /**
     * Reads the still-open previous rate for the branch+session type and closes it by rotating
     * `effectiveUntil` to now — the rotation half of the rate handover (#323, ADR-0024).
     */
    private fun closePreviousRate(
        branchId: UUID,
        sessionType: SessionType,
    ): SessionBaseRate? {
        val previous =
            SessionBaseRateTable
                .selectAll()
                .where {
                    (SessionBaseRateTable.branchId eq branchId) and
                        (SessionBaseRateTable.sessionType eq sessionType) and
                        (SessionBaseRateTable.effectiveUntil greater CurrentTimestampWithTimeZone)
                }.singleOrNull()
                ?.toSessionBaseRate()
                ?: return null
        SessionBaseRateTable
            .update({
                (SessionBaseRateTable.branchId eq branchId) and
                    (SessionBaseRateTable.sessionType eq sessionType) and
                    (SessionBaseRateTable.effectiveUntil greater CurrentTimestampWithTimeZone)
            }) {
                it[SessionBaseRateTable.effectiveUntil] = CurrentTimestampWithTimeZone
            }
        return previous
    }

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
