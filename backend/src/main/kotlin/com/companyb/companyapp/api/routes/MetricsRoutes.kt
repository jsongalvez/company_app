package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.observability.RequestMetrics
import io.javalin.config.JavalinConfig

object MetricsRoutes {
    private const val PROMETHEUS_CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8"

    fun register(config: JavalinConfig) {
        config.routes.get(ApiRoutes.METRICS) { context ->
            context.contentType(PROMETHEUS_CONTENT_TYPE)
            context.result(RequestMetrics.render())
        }
    }
}
