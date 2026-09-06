package com.companyb.companyapp.identity
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.dto.MeBranchResponse
import com.companyb.companyapp.dto.MeResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.identity.MeService
import io.javalin.config.JavalinConfig
import io.javalin.http.HttpStatus
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.ME,
    methods = [HttpMethod.GET],
    operationId = "me",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = MeResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.ME_BRANCHES,
    methods = [HttpMethod.GET],
    operationId = "me_branches",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<MeBranchResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.ME_CAPABILITIES,
    methods = [HttpMethod.GET],
    operationId = "me_capabilities",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<UserCapabilityResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object MeRoutes {
    fun getMe(config: JavalinConfig) {
        config.routes.get(ApiRoutes.ME) { context ->
            val callerId = context.callerUuid()
            val response: MeResponse = MeService.getMe(callerId)
            context.status(HttpStatus.OK)
            context.json(response)
        }
    }

    fun getCapabilities(config: JavalinConfig) {
        config.routes.get(ApiRoutes.ME_CAPABILITIES) { context ->
            val callerId = context.callerUuid()
            val response: List<UserCapabilityResponse> = MeService.getCapabilities(callerId)
            context.status(HttpStatus.OK)
            context.json(response)
        }
    }

    fun getBranches(config: JavalinConfig) {
        config.routes.get(ApiRoutes.ME_BRANCHES) { context ->
            val callerId = context.callerUuid()
            val response: List<MeBranchResponse> = MeService.getBranches(callerId)
            context.status(HttpStatus.OK)
            context.json(response)
        }
    }
}
