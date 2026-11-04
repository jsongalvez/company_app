package com.companyb.companyapp.observability

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.Gate
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.post
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.contracts.telemetry.TelemetryBatchRequest
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.identity.ClientIp
import com.companyb.companyapp.identity.RateLimiter
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
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
import java.util.UUID
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
@OpenApi(
    path = ApiRoutes.TELEMETRY_SIGNED_OUT,
    methods = [HttpMethod.POST],
    operationId = "telemetry_send_signed_out",
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = TelemetryBatchRequest::class)]),
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "429"),
    ],
)
object TelemetryRoutes {
    const val BATCH_MAX = 200

    /** Smaller than [BATCH_MAX] so one address can't flush signed-in rows out of the capped table quickly. */
    const val SIGNED_OUT_BATCH_MAX = 50
    const val DATA_MAX = 1000
    val KINDS = setOf("boot", "nav", "tap", "input", "api", "error", "net", "vis")
    private const val KEY_PREFIX = "telemetry:user:"
    private const val SIGNED_OUT_KEY_PREFIX = "telemetry:ip:"
    private const val CLOCK_SKEW_MAX_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * Anyone signed in uploads their own page's events; nobody reads them over the API. A page with no sign-in
     * (the sign-in screen, an expired session) uploads to the open route, stored with no user and limited per address.
     */
    fun register(config: JavalinConfig) {
        config.post(ApiRoutes.TELEMETRY, Gate.SignedIn("anyone signed in may send timings")) { context ->
            val callerId = context.callerUuid()
            receive(context, callerId, KEY_PREFIX + callerId, BATCH_MAX)
        }
        config.post(
            ApiRoutes.TELEMETRY_SIGNED_OUT,
            Gate.Open("a signed-out page's errors and timings, limited per address"),
        ) { context ->
            receive(context, null, SIGNED_OUT_KEY_PREFIX + ClientIp.of(context), SIGNED_OUT_BATCH_MAX)
        }
    }

    private fun receive(
        context: Context,
        userId: UUID?,
        budget: String,
        batchMax: Int,
    ) {
        val batch = context.bodyAsClass<TelemetryBatchRequest>()
        val sessionId = uuidOrThrow(batch.sessionId, "session id")
        val events = parse(batch, System.currentTimeMillis(), batchMax)
        if (!RateLimiter.isAllowed(budget)) {
            context.status(HttpStatus.TOO_MANY_REQUESTS)
        } else {
            TelemetryService.record(userId, sessionId, events)
            WebErrorAlerts.notice(sessionId, events)
            context.status(HttpStatus.NO_CONTENT)
        }
    }

    /** A phone clock more than a week off is replaced by the server's, so retention still works. */
    internal fun parse(
        batch: TelemetryBatchRequest,
        nowMs: Long,
        batchMax: Int = BATCH_MAX,
    ): List<TelemetryEvent> {
        if (batch.events.isEmpty() || batch.events.size > batchMax) {
            throw BadRequestResponse("Send 1 to $batchMax events")
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
