package com.companyb.companyapp.identity

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.contracts.identity.StartResponse
import com.companyb.companyapp.dto.ErrorResponse
import io.javalin.config.JavalinConfig
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.START,
    methods = [HttpMethod.GET],
    queryParams =
        [
            OpenApiParam(name = "branchId", type = UUID::class, required = false),
            OpenApiParam(name = "withToday", type = Boolean::class, required = false),
        ],
    operationId = "start",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = StartResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object StartRoutes {
    fun register(config: JavalinConfig) {
        config.routes.get(ApiRoutes.START) { context ->
            val asked = context.queryParam("branchId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            context.json(StartService.get(context.callerUuid(), asked, context.queryParam("withToday") == "true"))
        }
    }
}
