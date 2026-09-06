package com.companyb.companyapp.observability

import com.companyb.companyapp.api.ApiRoutes
import io.javalin.config.JavalinConfig
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse

// #473 annotation added in #475: the contract gate requires every registered
// route to carry a source annotation (mirrors HealthRoutes' public shape).
@OpenApi(
    path = ApiRoutes.METRICS,
    methods = [HttpMethod.GET],
    operationId = "metrics",
    security = [],
    responses = [OpenApiResponse(status = "200", content = [OpenApiContent(mimeType = "text/plain", type = "string")])],
)
object MetricsRoutes {
    private const val PROMETHEUS_CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8"

    fun register(config: JavalinConfig) {
        config.routes.get(ApiRoutes.METRICS) { context ->
            context.contentType(PROMETHEUS_CONTENT_TYPE)
            context.result(RequestMetrics.render())
        }
    }
}
