package com.companyb.companyapp

import com.companyb.companyapp.api.routes.AttendanceRoutes
import com.companyb.companyapp.api.routes.AuthRoutes
import com.companyb.companyapp.api.routes.BranchRoutes
import com.companyb.companyapp.api.routes.ClientRoutes
import com.companyb.companyapp.api.routes.MedicalMissionDelegateRoutes
import com.companyb.companyapp.api.routes.ProductCategoryRoutes
import com.companyb.companyapp.api.routes.ProductRoutes
import com.companyb.companyapp.api.routes.ReliefAccessRoutes
import com.companyb.companyapp.api.routes.SessionBaseRateRoutes
import com.companyb.companyapp.api.routes.SessionRoutes
import com.companyb.companyapp.api.routes.UserBranchAssignmentRoutes
import com.companyb.companyapp.api.routes.UserRoutes
import com.companyb.companyapp.auth.DenyList
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.database.DatabaseConfig
import com.companyb.companyapp.logging.DeltaTimeConverter
import com.companyb.companyapp.logging.RequestElapsedConverter
import com.companyb.companyapp.utils.Helper
import io.github.cdimascio.dotenv.dotenv
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.Javalin
import io.javalin.http.UnauthorizedResponse
import org.slf4j.MDC

private val logger = KotlinLogging.logger {}

val dotenv = dotenv()
private const val KB = 1024L
private const val MAX_REQUEST_SIZE_KB = 64L

fun initializeJavalin() {
    logger.info { "[INITIALIZE-JAVALIN] Starting application" }

    Javalin
        .create { config ->
            config.jsonMapper(KotlinxSerializationMapper())
            config.http.maxRequestSize = MAX_REQUEST_SIZE_KB * KB
            config.routes.before {
                // logback.xml %X{traceId} %X == %mdc
                RequestElapsedConverter.startRequest()
                DeltaTimeConverter.startRequest()
                val traceId = Helper().generateRandomId()
                MDC.put("traceId", traceId)
                logger.info { "[REQUEST] starting request" }
            }
            config.routes.after {
                val elapsed = RequestElapsedConverter.currentElapsedMs()
                logger.info { "[REQUEST] completed in ${elapsed}ms" }
                RequestElapsedConverter.endRequest()
                DeltaTimeConverter.endRequest()
                MDC.clear()
            }
            config.routes.before("/api/*") { context ->
                val token = context.header("Authorization")?.removePrefix("Bearer ") ?: throw UnauthorizedResponse()
                val userId = JwtService.verifyToken(token) ?: throw UnauthorizedResponse()
                context.attribute("userId", userId)
            }
            AuthRoutes.login(config)
            AuthRoutes.register(config)
            AttendanceRoutes.clockIn(config)
            AttendanceRoutes.clockOut(config)
            BranchRoutes.register(config)
            UserBranchAssignmentRoutes.register(config)
            UserRoutes.deactivate(config)
            ReliefAccessRoutes.requestReliefAccess(config)
            ReliefAccessRoutes.grantReliefAccess(config)
            ReliefAccessRoutes.denyReliefAccess(config)
            MedicalMissionDelegateRoutes.assignDelegate(config)
            MedicalMissionDelegateRoutes.revokeDelegate(config)
            ClientRoutes.register(config)
            SessionBaseRateRoutes.register(config)
            SessionRoutes.register(config)
            ProductCategoryRoutes.register(config)
            ProductRoutes.register(config)
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

fun initializeDenyList() {
    logger.info { "[INITIALIZE-DENY-LIST] Loading inactive users into deny list" }
    DenyList.loadInactiveUsers()
    logger.info { "[INITIALIZE-DENY-LIST] Deny list initialized" }
}

fun main() {
    RequestElapsedConverter.startRequest()
    DeltaTimeConverter.startRequest()
    logger.info { "[INITIALIZATION] Starting initialization" }
    initializeHikariCP()
    initializeFlyway()
    initializeExposed()
    initializeDenyList()
    initializeJavalin()
    val elapsed = RequestElapsedConverter.currentElapsedMs()
    logger.info { "[INITIALIZATION] Completed in $elapsed ms." }
    RequestElapsedConverter.endRequest()
    DeltaTimeConverter.endRequest()
}
