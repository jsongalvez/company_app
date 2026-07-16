package com.companyb.companyapp.api.routes

import io.javalin.config.JavalinConfig
import io.javalin.http.HttpStatus
import org.jetbrains.exposed.sql.transactions.transaction

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
