package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes

import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.dto.MeBranchResponse
import com.companyb.companyapp.dto.MeResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.service.MeService
import io.javalin.config.JavalinConfig
import io.javalin.http.HttpStatus
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.ME,
    methods = [HttpMethod.GET],
    operationId = "me",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = ApiRoutes.ME_BRANCHES,
    methods = [HttpMethod.GET],
    operationId = "me_branches",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = ApiRoutes.ME_CAPABILITIES,
    methods = [HttpMethod.GET],
    operationId = "me_capabilities",
    security = [OpenApiSecurity(name = "BearerAuth")],
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
