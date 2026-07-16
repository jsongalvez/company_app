package com.companyb.companyapp.service

import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.repository.SessionBaseRateRepository
import com.companyb.companyapp.repository.SetRateResult
import com.companyb.companyapp.repository.model.SessionBaseRate
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

object SessionBaseRateService {
    private val logger = KotlinLogging.logger {}
    private const val FAR_FUTURE_YEAR = 9999
    private const val FAR_FUTURE_MONTH = 12
    private const val FAR_FUTURE_DAY = 31
    private const val FAR_FUTURE_HOUR = 23
    private const val FAR_FUTURE_MINUTE = 59
    private const val FAR_FUTURE_SECOND = 59
    private const val RATE_SCALE = 2

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

    @Suppress("ThrowsCount")
    fun setRate(
        callerId: UUID,
        id: UUID,
        branchId: UUID,
        sessionType: SessionType,
        rate: String,
    ): SetRateResult {
        val rateAmount =
            runCatching { BigDecimal(rate).setScale(RATE_SCALE, RoundingMode.HALF_UP) }
                .getOrElse { throw BadRequestResponse("Invalid rate amount: $rate") }
        if (rateAmount < BigDecimal.ZERO) {
            throw BadRequestResponse("Rate must be non-negative")
        }

        val now = OffsetDateTime.now(ZoneOffset.UTC)

        SessionBaseRateRepository.deactivatePreviousRates(branchId, sessionType, now)

        return SessionBaseRateRepository.setRate(id, callerId, branchId, sessionType, rateAmount, FAR_FUTURE)
    }

    fun findActiveRates(branchId: UUID): List<SessionBaseRate> {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        return SessionBaseRateRepository.findActiveByBranch(branchId, now)
    }
}
