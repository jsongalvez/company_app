package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.database.DatabaseHealth
import com.companyb.companyapp.dto.ErrorResponse
import io.javalin.config.JavalinConfig
import io.javalin.http.HttpStatus
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse

@OpenApi(
    path = "/health",
    methods = [HttpMethod.GET],
    operationId = "health_check",
    security = [],
    responses = [
        OpenApiResponse(status = "200"),
        OpenApiResponse(status = "503", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object HealthRoutes {
    fun register(config: JavalinConfig) {
        config.routes.get(ApiRoutes.HEALTH) { context ->
            if (isDatabaseReachable()) {
                context.status(HttpStatus.OK)
                context.json(mapOf("status" to "UP"))
            } else {
                context.status(HttpStatus.SERVICE_UNAVAILABLE)
                context.json(mapOf("status" to "DOWN", "error" to "Postgres unreachable"))
            }
        }
    }

    internal fun isDatabaseReachable(): Boolean = DatabaseHealth.isReachable()
}
