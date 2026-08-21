package com.companyb.companyapp.service.session

import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.repository.AuditContext
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.SessionBaseRateRepository
import com.companyb.companyapp.repository.SetRateResult
import com.companyb.companyapp.repository.model.SessionBaseRate
import com.companyb.companyapp.repository.model.SessionBaseRateCreateParams
import com.companyb.companyapp.repository.model.SessionBaseRateTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Session base-rate command (#323, ADR-0024). Owns exactly one business transaction: the branch
 * lock, retry-ownership check, rate rotation/insert, and both audit rows (previous rate close +
 * new rate insert) run on it.
 */
internal object SessionBaseRateService {
    private val logger = KotlinLogging.logger {}
    private const val FAR_FUTURE_YEAR = 9999
    private const val FAR_FUTURE_MONTH = 12
    private const val FAR_FUTURE_DAY = 31
    private const val FAR_FUTURE_HOUR = 23
    private const val FAR_FUTURE_MINUTE = 59
    private const val FAR_FUTURE_SECOND = 59

    private val FAR_FUTURE: OffsetDateTime =
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

    fun setRate(
        callerId: UUID,
        id: UUID,
        branchId: UUID,
        sessionType: SessionType,
        rate: BigDecimal,
    ): SetRateResult =
        transaction {
            val result =
                SessionBaseRateRepository.setRateInTransaction(
                    SessionBaseRateCreateParams(
                        id = id,
                        setBy = callerId,
                        branchId = branchId,
                        sessionType = sessionType,
                        rate = rate,
                        effectiveUntil = FAR_FUTURE,
                    ),
                )
            if (result.created) {
                // Preserve the original order: close-audit on the rotated previous rate, then
                // the insert audit on the new rate.
                result.previousAfter?.let { after ->
                    SessionBaseRateAudit.updated(
                        AuditContext(callerId),
                        result.previousBefore ?: after,
                        after,
                    )
                }
                SessionBaseRateAudit.inserted(
                    AuditContext(callerId, branchId),
                    result.rate,
                )
            }

            logger.info {
                "[SET-RATE] Rate ${result.rate.id} created=${result.created}"
            }

            result
        }

    fun findActiveRates(branchId: UUID): List<SessionBaseRate> = SessionBaseRateRepository.findActiveByBranch(branchId)
}

/**
 * Session base-rate audit vocabulary (#323, ADR-0024 rule 3). Called by the command inside its
 * own transaction so audit rows commit atomically with the mutation. Owns the persistence-table
 * import so the public command surface does not.
 */
internal object SessionBaseRateAudit {
    fun inserted(
        context: AuditContext,
        rateRecord: SessionBaseRate,
    ) = AuditLogRepository.recordInsert(
        tableName = SessionBaseRateTable.tableName,
        recordId = rateRecord.id,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = SessionBaseRateTable.auditFields(rateRecord),
    )

    fun updated(
        context: AuditContext,
        before: SessionBaseRate,
        after: SessionBaseRate,
    ) = AuditLogRepository.recordUpdate(
        tableName = SessionBaseRateTable.tableName,
        recordId = before.id,
        before = before,
        after = after,
        changedBy = context.changedBy,
        branchId = before.branchId,
        auditFields = SessionBaseRateTable::auditFields,
    )
}
