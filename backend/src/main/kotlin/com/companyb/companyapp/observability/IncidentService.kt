package com.companyb.companyapp.observability

import com.companyb.companyapp.database.DatabaseConfig
import com.companyb.companyapp.domain.IncidentSource
import com.companyb.companyapp.dto.FeedbackRequest
import com.companyb.companyapp.dto.IncidentPacket
import com.companyb.companyapp.dto.PoolSnapshot
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.logging.maskUUID
import java.time.Instant
import java.util.UUID

data class FiledIncident(
    val packet: IncidentPacket,
    val duplicate: Boolean,
)

/**
 * #475 — builds and files triage-ready incident packets. User reports and
 * 5xx auto-files share this seam so the packet shape is identical (only
 * [IncidentSource] differs). Filing is deduped by trace id, delivered
 * asynchronously, and writes no app tables — there is nothing to audit.
 */
object IncidentService {
    private const val TRACE_ID_MAX_LENGTH = 64
    private const val ENDPOINT_MAX_LENGTH = 280
    private const val APP_VERSION_MAX_LENGTH = 64
    private const val AUTO_APP_VERSION = "unknown"
    private const val SERVER_REPORTER = "server"
    private const val UNKNOWN = "unknown"

    private val knownMethods = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

    fun fileUserReport(
        callerId: UUID,
        request: FeedbackRequest,
    ): FiledIncident = fileUserReport(callerId, request, senderOverride = null)

    /** Test seam for capturing delivery without network calls. */
    internal fun fileUserReport(
        callerId: UUID,
        request: FeedbackRequest,
        senderOverride: IncidentSender?,
    ): FiledIncident {
        val packet =
            IncidentPacket(
                traceId = validated(request.traceId, TRACE_ID_MAX_LENGTH, "traceId"),
                method = parseMethod(request.endpoint),
                route = normalizedRoute(extractRoute(validated(request.endpoint, ENDPOINT_MAX_LENGTH, "endpoint"))),
                status = null,
                elapsedMs = request.elapsedMs?.takeIf { it >= 0 },
                appVersion = validated(request.appVersion, APP_VERSION_MAX_LENGTH, "appVersion"),
                timestamp = Instant.now().toString(),
                pool = currentPool(),
                reporter = callerId.toString().maskUUID(),
                source = IncidentSource.USER_REPORT,
            )
        return file(packet, senderOverride)
    }

    fun fileAuto5xx(
        traceId: String,
        method: String,
        route: String,
        status: Int,
        elapsedMs: Long,
        reporterRaw: String?,
    ): FiledIncident = fileAuto5xx(traceId, method, route, status, elapsedMs, reporterRaw, senderOverride = null)

    /** Test seam for capturing delivery without network calls. */
    internal fun fileAuto5xx(
        traceId: String,
        method: String,
        route: String,
        status: Int,
        elapsedMs: Long,
        reporterRaw: String?,
        senderOverride: IncidentSender?,
    ): FiledIncident {
        val packet =
            IncidentPacket(
                traceId = traceId.take(TRACE_ID_MAX_LENGTH),
                method = method,
                route = normalizedRoute(route),
                status = status,
                elapsedMs = elapsedMs.takeIf { it >= 0 },
                appVersion = AUTO_APP_VERSION,
                timestamp = Instant.now().toString(),
                pool = currentPool(),
                reporter = maskReporter(reporterRaw),
                source = IncidentSource.AUTO_5XX,
            )
        return file(packet, senderOverride)
    }

    private fun file(
        packet: IncidentPacket,
        senderOverride: IncidentSender?,
    ): FiledIncident {
        val (stored, duplicate) = IncidentRegistry.fileIfAbsent(packet.traceId, packet)
        if (!duplicate) IncidentDelivery.deliver(stored, senderOverride)
        return FiledIncident(stored, duplicate)
    }

    private fun validated(
        value: String,
        maxLength: Int,
        name: String,
    ): String {
        if (value.isBlank()) throw ValidationException("$name is required")
        if (value.length > maxLength) throw ValidationException("$name is too long (max $maxLength)")
        return value
    }

    private fun parseMethod(endpoint: String): String {
        val candidate = endpoint.substringBefore(" ").uppercase()
        return candidate.takeIf { it in knownMethods } ?: UNKNOWN
    }

    private fun extractRoute(endpoint: String): String = endpoint.substringAfter(" ", endpoint)

    private fun normalizedRoute(raw: String): String =
        RequestMetrics.normalizeRoute(raw.substringBefore("?")).maskUUID()

    private fun maskReporter(raw: String?): String =
        raw?.let { runCatching { UUID.fromString(it).toString() }.getOrNull()?.maskUUID() } ?: SERVER_REPORTER

    private fun currentPool(): PoolSnapshot {
        val stats = DatabaseConfig.poolStats()
        return PoolSnapshot(
            active = stats.active,
            idle = stats.idle,
            awaiting = stats.awaiting,
            total = stats.total,
        )
    }
}
