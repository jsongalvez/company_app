package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.RateResponse
import com.companyb.companyapp.dto.SetRateRequest
import com.companyb.companyapp.repository.model.SessionBaseRate
import com.companyb.companyapp.service.SessionBaseRateService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.util.UUID

object SessionBaseRateRoutes {
    private const val BRANCH_ID_PARAM = "branchId"

    @Suppress("ThrowsCount")
    fun register(config: JavalinConfig) {
        config.routes.before("/api/branches/{branchId}/rates") { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.MANAGE_PRODUCTS,
            )
        }

        config.routes.post("/api/branches/{$BRANCH_ID_PARAM}/rates") { context ->
            val callerId = context.callerUuid()
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
            val request = context.bodyAsClass<SetRateRequest>()
            val rateId = uuidOrThrow(request.id, "rate id")
            val rate = parseNonNegativeBigDecimal(request.rate, "rate")

            val result =
                SessionBaseRateService.setRate(
                    callerId = callerId,
                    id = rateId,
                    branchId = branchId,
                    sessionType = request.sessionType,
                    rate = rate,
                )

            context.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
            context.json(result.rate.toResponse())
        }

        config.routes.get("/api/branches/{$BRANCH_ID_PARAM}/rates") { context ->
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)

            context.json(SessionBaseRateService.findActiveRates(branchId).map { it.toResponse() })
        }
    }

    private fun SessionBaseRate.toResponse(): RateResponse =
        RateResponse(
            id = id.toString(),
            branchId = branchId.toString(),
            sessionType = sessionType,
            rate = rate.toPlainString(),
            effectiveFrom = effectiveFrom.toString(),
            effectiveUntil = effectiveUntil.toString(),
        )
}
