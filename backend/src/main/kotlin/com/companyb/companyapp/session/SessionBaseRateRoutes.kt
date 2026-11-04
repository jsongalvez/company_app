package com.companyb.companyapp.session

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.Gate
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.get
import com.companyb.companyapp.api.post
import com.companyb.companyapp.api.routes.parseNonNegativeBigDecimal
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.session.RateResponse
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.contracts.session.SetRateRequest
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.exception.ValidationException
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HandlerType
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.math.BigDecimal
import java.util.UUID

@OpenApi(
    path = ApiRoutes.BRANCH_RATES_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "session_rates_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<RateResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_RATES_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "session_rates_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = SetRateRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = RateResponse::class)]),
        OpenApiResponse(status = "201", content = [OpenApiContent(from = RateResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object SessionBaseRateRoutes {
    private const val BRANCH_ID_PARAM = "branchId"

    fun register(config: JavalinConfig) {
        fun branchRatesGate(act: Act) =
            Gate.Before { context ->
                val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
                // 404 precedence for an unknown branch before the capability gate:
                // the access check alone conflates "unknown branch" with
                // "known but non-member" (the createInvite / #715 listSent order, resolving
                // the #724-deferred session-rate filter policy with the same precedence).
                BranchService.findById(branchId)
                Access.require(context, act, Place.Branch(branchId))
            }

        config.post(ApiRoutes.BRANCH_RATES_PATH, branchRatesGate(Act.WORK_HOME_BRANCH)) { context ->
            val callerId = context.callerUuid()
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
            val request = context.bodyAsClass<SetRateRequest>()
            val rateId = uuidOrThrow(request.id, "rate id")
            val rate = parseNonNegativeBigDecimal(request.rate, "rate")
            requireSessionMoney(rate, "rate")

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

        config.get(ApiRoutes.BRANCH_RATES_PATH, branchRatesGate(Act.SEE_BRANCH)) { context ->
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

    /**
     * persisted-range mirror (same shape as SessionRoutes.requireSessionMoney,
     * kept per-route like #923 — the service owns the bound).
     */
    private fun requireSessionMoney(
        amount: BigDecimal,
        field: String,
    ) {
        try {
            validateSessionMoney(amount, field)
        } catch (e: ValidationException) {
            throw BadRequestResponse(e.message ?: "Invalid amount").apply { initCause(e) }
        }
    }
}
