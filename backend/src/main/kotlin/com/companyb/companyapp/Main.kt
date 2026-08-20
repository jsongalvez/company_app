package com.companyb.companyapp

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.routes.AllowanceRoutes
import com.companyb.companyapp.api.routes.AttendanceRoutes
import com.companyb.companyapp.api.routes.AuditLogRoutes
import com.companyb.companyapp.api.routes.AuthRoutes
import com.companyb.companyapp.api.routes.BranchDayRoutes
import com.companyb.companyapp.api.routes.BranchInventoryRoutes
import com.companyb.companyapp.api.routes.BranchRoutes
import com.companyb.companyapp.api.routes.ClientRoutes
import com.companyb.companyapp.api.routes.CommissionRoutes
import com.companyb.companyapp.api.routes.CompensationRoutes
import com.companyb.companyapp.api.routes.DailySalesSummaryRoutes
import com.companyb.companyapp.api.routes.DashboardRoutes
import com.companyb.companyapp.api.routes.ExpenseRoutes
import com.companyb.companyapp.api.routes.ExportRoutes
import com.companyb.companyapp.api.routes.HealthRoutes
import com.companyb.companyapp.api.routes.MeRoutes
import com.companyb.companyapp.api.routes.MedicalMissionDelegateRoutes
import com.companyb.companyapp.api.routes.MonthlyRemittanceSummaryRoutes
import com.companyb.companyapp.api.routes.NotificationRoutes
import com.companyb.companyapp.api.routes.ProductCategoryRoutes
import com.companyb.companyapp.api.routes.ProductRoutes
import com.companyb.companyapp.api.routes.ProductSaleRoutes
import com.companyb.companyapp.api.routes.ReliefAccessRoutes
import com.companyb.companyapp.api.routes.ReliefInviteRoutes
import com.companyb.companyapp.api.routes.RemittanceRoutes
import com.companyb.companyapp.api.routes.SessionBaseRateRoutes
import com.companyb.companyapp.api.routes.SessionRoutes
import com.companyb.companyapp.api.routes.UserBranchAssignmentRoutes
import com.companyb.companyapp.api.routes.UserRoutes
import com.companyb.companyapp.auth.DenyList
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.database.DatabaseConfig
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.logging.DeltaTimeConverter
import com.companyb.companyapp.logging.RequestElapsedConverter
import com.companyb.companyapp.service.SchedulerLifecycle
import com.companyb.companyapp.utils.RandomIdGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.Javalin
import io.javalin.http.UnauthorizedResponse
import io.javalin.openapi.plugin.OpenApiPlugin
import io.javalin.openapi.plugin.swagger.SwaggerPlugin
import org.slf4j.MDC

private val logger = KotlinLogging.logger {}

private const val KB = 1024L
private const val MAX_REQUEST_SIZE_KB = 64L
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_CONFLICT = 409

fun initializeJavalin(config: AppConfig) {
    logger.info { "[INITIALIZE-JAVALIN] Starting application" }

    Javalin
        .create { cfg ->
            configureJavalin(cfg)
        }.start(config.appPort)
    logger.info { "[INITIALIZE-JAVALIN] Application started" }
}

@Suppress("LongMethod")
private fun configureJavalin(config: io.javalin.config.JavalinConfig) {
    config.jsonMapper(KotlinxSerializationMapper())
    config.registerPlugin(
        OpenApiPlugin { openapi ->
            openapi.withDefinitionConfiguration { _, builder ->
                builder.info { info ->
                    info.title("CompanyApp Backend API")
                    info.version("1.0.0")
                }
                builder.withBearerAuth("BearerAuth")
                builder.withGlobalSecurity("BearerAuth")
            }
        },
    )
    config.registerPlugin(SwaggerPlugin())
    config.http.maxRequestSize = MAX_REQUEST_SIZE_KB * KB
    config.routes.before {
        // logback.xml %X{traceId} %X == %mdc
        RequestElapsedConverter.startRequest()
        DeltaTimeConverter.startRequest()
        val traceId = RandomIdGenerator.generate()
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
    config.events.serverStartFailed { shutdownScheduler() }
    config.events.serverStopping { shutdownScheduler() }
    config.routes.before("${ApiRoutes.API_PREFIX}*") { context ->
        val token = context.header("Authorization")?.removePrefix("Bearer ") ?: throw UnauthorizedResponse()
        val userId = JwtService.verifyToken(token) ?: throw UnauthorizedResponse()
        context.attribute("userId", userId)
    }
    registerExceptionHandlers(config)
    HealthRoutes.register(config)
    AuthRoutes.login(config)
    AuthRoutes.register(config)
    AuthRoutes.logout(config)
    MeRoutes.getMe(config)
    MeRoutes.getCapabilities(config)
    MeRoutes.getBranches(config)
    AttendanceRoutes.clockIn(config)
    AttendanceRoutes.clockOut(config)
    BranchRoutes.register(config)
    UserBranchAssignmentRoutes.register(config)
    UserRoutes.register(config)
    ReliefAccessRoutes.requestReliefAccess(config)
    ReliefAccessRoutes.grantReliefAccess(config)
    ReliefAccessRoutes.denyReliefAccess(config)
    ReliefInviteRoutes.register(config)
    MedicalMissionDelegateRoutes.assignDelegate(config)
    MedicalMissionDelegateRoutes.revokeDelegate(config)
    ClientRoutes.register(config)
    SessionBaseRateRoutes.register(config)
    SessionRoutes.register(config)
    ProductCategoryRoutes.register(config)
    ProductRoutes.register(config)
    BranchInventoryRoutes.register(config)
    BranchDayRoutes.register(config)
    DashboardRoutes.register(config)
    ProductSaleRoutes.register(config)
    CompensationRoutes.register(config)
    CommissionRoutes.register(config)
    DailySalesSummaryRoutes.register(config)
    ExpenseRoutes.register(config)
    AllowanceRoutes.register(config)
    NotificationRoutes.register(config)
    MonthlyRemittanceSummaryRoutes.register(config)
    RemittanceRoutes.register(config)
    AuditLogRoutes.register(config)
    ExportRoutes.register(config)
}

private fun registerExceptionHandlers(config: io.javalin.config.JavalinConfig) {
    config.routes.exception(ValidationException::class.java) { e, ctx ->
        ctx.status(HTTP_BAD_REQUEST).json(mapOf("error" to (e.message ?: "Bad Request")))
    }
    config.routes.exception(ForbiddenException::class.java) { e, ctx ->
        ctx.status(HTTP_FORBIDDEN).json(mapOf("error" to (e.message ?: "Forbidden")))
    }
    config.routes.exception(NotFoundException::class.java) { e, ctx ->
        ctx.status(HTTP_NOT_FOUND).json(mapOf("error" to (e.message ?: "Not Found")))
    }
    config.routes.exception(ConflictException::class.java) { e, ctx ->
        ctx.status(HTTP_CONFLICT).json(mapOf("error" to (e.message ?: "Conflict")))
    }
}

fun initializeDenyList() {
    logger.info { "[INITIALIZE-DENY-LIST] Loading persisted revocations into deny list" }
    DenyList.loadPersistedRevocations()
    logger.info { "[INITIALIZE-DENY-LIST] Deny list initialized" }
}

private val schedulerLifecycle = SchedulerLifecycle()

fun initializeScheduler() = schedulerLifecycle.start()

fun shutdownScheduler() = schedulerLifecycle.stop()

fun main() {
    main(AppConfig.parse())
}

fun main(config: AppConfig) {
    RequestElapsedConverter.startRequest()
    DeltaTimeConverter.startRequest()
    logger.info { "[INITIALIZATION] Starting initialization" }

    JwtService.init(config)
    Password.init(config.authDummyPassword)

    DatabaseConfig.initialize(config)
    initializeDenyList()
    initializeScheduler()
    runCatching { initializeJavalin(config) }
        .onFailure {
            shutdownScheduler()
        }.getOrThrow()

    val elapsed = RequestElapsedConverter.currentElapsedMs()
    logger.info { "[INITIALIZATION] Completed in $elapsed ms." }
    RequestElapsedConverter.endRequest()
    DeltaTimeConverter.endRequest()
}
