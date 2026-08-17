package com.companyb.companyapp.api.routes

import io.javalin.config.JavalinConfig
import io.javalin.http.HttpStatus
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiResponse
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@OpenApi(
    path = "/health",
    methods = [HttpMethod.GET],
    operationId = "health_check",
    security = [],
    responses = [OpenApiResponse(status = "200"), OpenApiResponse(status = "503")],
)
object HealthRoutes {
    fun register(config: JavalinConfig) {
        config.routes.get("/health") { context ->
            if (isDatabaseReachable()) {
                context.status(HttpStatus.OK)
                context.json(mapOf("status" to "UP"))
            } else {
                context.status(HttpStatus.SERVICE_UNAVAILABLE)
                context.json(mapOf("status" to "DOWN", "error" to "Postgres unreachable"))
            }
        }
    }

    internal fun isDatabaseReachable(): Boolean =
        runCatching {
            transaction {
                exec("SELECT 1")
            }
        }.isSuccess
}
