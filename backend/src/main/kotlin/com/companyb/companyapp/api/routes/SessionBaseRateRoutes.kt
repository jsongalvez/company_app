package com.companyb.companyapp.api.routes

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
        config.routes.post("/api/branches/{$BRANCH_ID_PARAM}/rates") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val branchId =
                runCatching { UUID.fromString(context.pathParam(BRANCH_ID_PARAM)) }
                    .getOrElse { throw BadRequestResponse("Invalid branch id") }
            val request = context.bodyAsClass<SetRateRequest>()
            val rateId =
                runCatching { UUID.fromString(request.id) }
                    .getOrElse { throw BadRequestResponse("Invalid rate id") }

            val result =
                SessionBaseRateService.setRate(
                    callerId = callerId,
                    id = rateId,
                    branchId = branchId,
                    sessionType = request.sessionType,
                    rate = request.rate,
                )

            context.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
            context.json(result.rate.toResponse())
        }

        config.routes.get("/api/branches/{$BRANCH_ID_PARAM}/rates") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val branchId =
                runCatching { UUID.fromString(context.pathParam(BRANCH_ID_PARAM)) }
                    .getOrElse { throw BadRequestResponse("Invalid branch id") }

            context.json(SessionBaseRateService.findActiveRates(callerId, branchId).map { it.toResponse() })
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
