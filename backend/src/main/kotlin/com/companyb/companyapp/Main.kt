package com.companyb.companyapp

import com.companyb.companyapp.api.routes.AuthRoutes
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.database.DatabaseConfig
import com.companyb.companyapp.utils.Helper
import io.github.cdimascio.dotenv.dotenv
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.Javalin
import org.slf4j.MDC

private val logger = KotlinLogging.logger {}

val dotenv = dotenv()

fun initializeJavalin() {
    logger.info { "[INITIALIZE-JAVALIN] Starting application" }

    Javalin
        .create { config ->
            config.jsonMapper(KotlinxSerializationMapper())
            config.routes.before {
                // logback.xml %X{traceId} %X == %mdc
                MDC.clear()
                val traceId = Helper().generateTraceId()
                MDC.put("traceId", traceId)
            }
            config.routes.after {
                MDC.clear()
            }
            AuthRoutes.login(config)
        }.start(dotenv["APP_PORT"].toInt())
    logger.info { "[INITIALIZE-JAVALIN] Application started" }
}

fun initializeHikariCP() {
    logger.info { "[INITIALIZE-HIKARI-CP] Starting HikariCP connection" }
    DatabaseConfig.dataSource
    logger.info { "[INITIALIZE-HIKARI-CP] HikariCP connection enabled" }
}

fun initializeFlyway() {
    logger.info { "[INITIALIZE-FLYWAY] Starting Flyway initialization" }
    DatabaseConfig.runMigrations()
    logger.info { "[INITIALIZE-FLYWAY] Flyway initialization done" }
}

fun initializeExposed() {
    logger.info { "[INITIALIZE-EXPOSED] Starting Exposed connection" }
    DatabaseConfig.runExposed()
    logger.info { "[INITIALIZE-EXPOSED] Exposed connection enabled" }
}

fun main() {
    initializeHikariCP()
    initializeFlyway()
    initializeExposed()
    initializeJavalin()
}
