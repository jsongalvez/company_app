package com.companyb.companyapp.observability

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.contracts.telemetry.TelemetryBatchRequest
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.identity.RateLimiter
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.abs

@OpenApi(
    path = ApiRoutes.TELEMETRY,
    methods = [HttpMethod.POST],
    operationId = "telemetry_send",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = TelemetryBatchRequest::class)]),
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "429"),
    ],
)
object TelemetryRoutes {
    const val BATCH_MAX = 200
    const val DATA_MAX = 1000
    val KINDS = setOf("boot", "nav", "tap", "input", "api", "error", "net", "vis")
    private const val KEY_PREFIX = "telemetry:user:"
    private const val CLOCK_SKEW_MAX_MS = 7L * 24 * 60 * 60 * 1000

    /** Anyone signed in uploads their own page's events; nobody reads them over the API. */
    fun register(config: JavalinConfig) {
        config.routes.post(ApiRoutes.TELEMETRY) { context ->
            val callerId = context.callerUuid()
            val batch = context.bodyAsClass<TelemetryBatchRequest>()
            val sessionId = uuidOrThrow(batch.sessionId, "session id")
            val events = parse(batch, System.currentTimeMillis())
            if (!RateLimiter.isAllowed(KEY_PREFIX + callerId)) {
                context.status(HttpStatus.TOO_MANY_REQUESTS)
            } else {
                TelemetryService.record(callerId, sessionId, events)
                context.status(HttpStatus.NO_CONTENT)
            }
        }
    }

    /** A phone clock more than a week off is replaced by the server's, so retention still works. */
    internal fun parse(
        batch: TelemetryBatchRequest,
        nowMs: Long,
    ): List<TelemetryEvent> {
        if (batch.events.isEmpty() || batch.events.size > BATCH_MAX) {
            throw BadRequestResponse("Send 1 to $BATCH_MAX events")
        }
        return batch.events.map { e ->
            if (e.seq < 0 || e.kind !in KINDS ||
                e.data.length > DATA_MAX
            ) {
                throw BadRequestResponse("Bad event ${e.seq}")
            }
            val atMs = if (abs(e.at - nowMs) > CLOCK_SKEW_MAX_MS) nowMs else e.at
            val at = OffsetDateTime.ofInstant(Instant.ofEpochMilli(atMs), ZoneOffset.UTC)
            TelemetryEvent(e.seq, at, e.kind, LabelSeal.seal(e.data))
        }
    }
}
