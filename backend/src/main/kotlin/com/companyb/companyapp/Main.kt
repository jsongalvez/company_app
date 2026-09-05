package com.companyb.companyapp

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.middleware.TraceIdFilter
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
import com.companyb.companyapp.api.routes.FeedbackRoutes
import com.companyb.companyapp.api.routes.HealthRoutes
import com.companyb.companyapp.api.routes.MeRoutes
import com.companyb.companyapp.api.routes.MedicalMissionDelegateRoutes
import com.companyb.companyapp.api.routes.MetricsRoutes
import com.companyb.companyapp.api.routes.MonthlyRemittanceSummaryRoutes
import com.companyb.companyapp.api.routes.NotificationRoutes
import com.companyb.companyapp.api.routes.ProductCategoryRoutes
import com.companyb.companyapp.api.routes.ProductRoutes
import com.companyb.companyapp.api.routes.ProductSaleRoutes
import com.companyb.companyapp.api.routes.ReliefAccessRoutes
import com.companyb.companyapp.api.routes.ReliefInviteRoutes
import com.companyb.companyapp.api.routes.RemittancePickerRoutes
import com.companyb.companyapp.api.routes.RemittanceRoutes
import com.companyb.companyapp.api.routes.SessionBaseRateRoutes
import com.companyb.companyapp.api.routes.SessionRoutes
import com.companyb.companyapp.api.routes.UserBranchAssignmentRoutes
import com.companyb.companyapp.api.routes.UserRoutes
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.auth.PasswordResetDelivery
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.config.OpenApiCanonical
import com.companyb.companyapp.database.DatabaseConfig
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.logging.DeltaTimeConverter
import com.companyb.companyapp.logging.RequestElapsedConverter
import com.companyb.companyapp.logging.RequestLog
import com.companyb.companyapp.observability.Auto5xxReport
import com.companyb.companyapp.observability.IncidentDelivery
import com.companyb.companyapp.observability.IncidentService
import com.companyb.companyapp.observability.RequestMetrics
import com.companyb.companyapp.service.SchedulerLifecycle
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.Javalin
import io.javalin.http.HttpStatus
import io.javalin.http.UnauthorizedResponse
import io.javalin.openapi.plugin.OpenApiPlugin
import io.javalin.openapi.plugin.swagger.SwaggerPlugin

private val logger = KotlinLogging.logger {}

private const val KB = 1024L
private const val MAX_REQUEST_SIZE_KB = 64L
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_CONFLICT = 409
private const val HTTP_INTERNAL_ERROR = 500
private const val TRACE_UNKNOWN = "unknown"

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
            }
            // #495 — one canonical transformation owns the served document; offline
            // export calls the same function so the packaged app and export agree.
            openapi.withDefinitionProcessor(OpenApiCanonical.processor)
        },
    )
    config.registerPlugin(SwaggerPlugin())
    config.http.maxRequestSize = MAX_REQUEST_SIZE_KB * KB
    config.routes.before {
        // logback.xml %X{traceId} %X == %mdc
        RequestElapsedConverter.startRequest()
        DeltaTimeConverter.startRequest()
        TraceIdFilter.before(it)
        RequestLog.start(it)
    }
    config.routes.after {
        TraceIdFilter.echo(it)
        RequestMetrics.observe(it)
        RequestLog.complete(it)
    }
    config.events.serverStartFailed {
        shutdownLifecycle()
    }
    config.events.serverStopping {
        shutdownLifecycle(closeDb = false)
    }
    config.events.serverStopped {
        shutdownLifecycle(stopScheduler = false)
    }
    config.events.serverStopFailed {
        shutdownLifecycle(stopScheduler = false)
    }
    config.routes.before("${ApiRoutes.API_PREFIX}*") { context ->
        val token = context.header("Authorization")?.removePrefix("Bearer ") ?: throw UnauthorizedResponse()
        val userId = JwtService.verifyToken(token) ?: throw UnauthorizedResponse()
        context.attribute("userId", userId)
    }
    registerExceptionHandlers(config)
    registerServerErrorHandler(config)
    registerAllRoutes(config)
}

/**
 * #495 — narrow production route seam callable by contract tests without database
 * or scheduler startup. Retains #471 TraceIdFilter behavior via the caller's
 * config; tests capture method/path pairs with `config.events.handlerAdded`.
 */
fun registerAllRoutes(config: io.javalin.config.JavalinConfig) {
    HealthRoutes.register(config)
    MetricsRoutes.register(config)
    AuthRoutes.login(config)
    AuthRoutes.acceptInvite(config)
    AuthRoutes.forgotPassword(config)
    AuthRoutes.resetPassword(config)
    AuthRoutes.logout(config)
    MeRoutes.getMe(config)
    MeRoutes.getCapabilities(config)
    MeRoutes.getBranches(config)
    AttendanceRoutes.clockIn(config)
    AttendanceRoutes.clockOut(config)
    AttendanceRoutes.rosterToday(config)
    AttendanceRoutes.mark(config)
    BranchRoutes.register(config)
    UserBranchAssignmentRoutes.register(config)
    UserRoutes.register(config)
    ReliefAccessRoutes.requestReliefAccess(config)
    ReliefAccessRoutes.grantReliefAccess(config)
    ReliefAccessRoutes.denyReliefAccess(config)
    ReliefAccessRoutes.cancelReliefAccess(config)
    ReliefAccessRoutes.listReliefAccess(config)
    ReliefAccessRoutes.listMine(config)
    ReliefAccessRoutes.listBranchOptions(config)
    ReliefInviteRoutes.register(config)
    MedicalMissionDelegateRoutes.listDelegates(config)
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
    FeedbackRoutes.register(config)
    MonthlyRemittanceSummaryRoutes.register(config)
    RemittanceRoutes.register(config)
    RemittancePickerRoutes.register(config)
    AuditLogRoutes.register(config)
    ExportRoutes.register(config)
}

private fun registerExceptionHandlers(config: io.javalin.config.JavalinConfig) {
    config.routes.exception(ValidationException::class.java) { e, ctx ->
        TraceIdFilter.echo(ctx)
        ctx.status(HTTP_BAD_REQUEST).json(mapOf("error" to (e.message ?: "Bad Request")))
        RequestLog.complete(ctx)
    }
    config.routes.exception(ForbiddenException::class.java) { e, ctx ->
        TraceIdFilter.echo(ctx)
        ctx.status(HTTP_FORBIDDEN).json(mapOf("error" to (e.message ?: "Forbidden")))
        RequestLog.complete(ctx)
    }
    config.routes.exception(NotFoundException::class.java) { e, ctx ->
        TraceIdFilter.echo(ctx)
        ctx.status(HTTP_NOT_FOUND).json(mapOf("error" to (e.message ?: "Not Found")))
        RequestLog.complete(ctx)
    }
    config.routes.exception(ConflictException::class.java) { e, ctx ->
        TraceIdFilter.echo(ctx)
        ctx.status(HTTP_CONFLICT).json(mapOf("error" to (e.message ?: "Conflict")))
        RequestLog.complete(ctx)
    }
}

/**
 * #475 — 5xx auto-file. A status handler (not a generic `Exception` handler)
 * so the domain handlers above keep their specificity: any response that
 * leaves the server as 500 files the same packet shape as a user report,
 * without user action. Dedup by trace id collapses the auto-file with a
 * user report for the same request.
 */
private fun registerServerErrorHandler(config: io.javalin.config.JavalinConfig) {
    config.routes.error(HttpStatus.INTERNAL_SERVER_ERROR) { ctx ->
        TraceIdFilter.echo(ctx)
        IncidentService.fileAuto5xx(
            Auto5xxReport(
                traceId = ctx.attribute<String>(TraceIdFilter.ATTRIBUTE) ?: TRACE_UNKNOWN,
                method = ctx.method().name,
                route = ctx.path(),
                status = runCatching { ctx.statusCode() }.getOrDefault(HTTP_INTERNAL_ERROR),
                elapsedMs = RequestElapsedConverter.currentElapsedMs(),
                reporterRaw = ctx.attribute<String>("userId"),
            ),
        )
        ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(mapOf("error" to "Internal Server Error"))
        RequestLog.complete(ctx)
    }
}

private val schedulerLifecycle = SchedulerLifecycle()

/**
 * Single shutdown seam (#455): every Javalin lifecycle event and the init
 * failure path drain through here with the same order (scheduler → mail →
 * database). Flags select the subset each event owns: stopping never owned
 * the database, stopped/stop-failed never owned the scheduler.
 */
private fun shutdownLifecycle(
    stopScheduler: Boolean = true,
    closeDb: Boolean = true,
) {
    if (stopScheduler) shutdownScheduler()
    PasswordResetDelivery.shutdown()
    IncidentDelivery.shutdown()
    if (closeDb) DatabaseConfig.close()
}

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
    PasswordResetDelivery.configure(config.smtp)
    IncidentDelivery.configure(config.githubIssue)

    DatabaseConfig.initialize(config)
    runCatching {
        initializeScheduler()
        initializeJavalin(config)
    }.onFailure {
        shutdownLifecycle()
    }.getOrThrow()

    val elapsed = RequestElapsedConverter.currentElapsedMs()
    logger.info { "[INITIALIZATION] Completed in $elapsed ms." }
    RequestElapsedConverter.endRequest()
    DeltaTimeConverter.endRequest()
}
