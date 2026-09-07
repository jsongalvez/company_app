package com.companyb.companyapp.observability

import com.companyb.companyapp.contracts.incident.FeedbackRequest
import com.companyb.companyapp.contracts.incident.IncidentPacket
import com.companyb.companyapp.contracts.incident.IncidentSource
import com.companyb.companyapp.logging.maskUUID
import java.time.Instant
import java.util.UUID

data class FiledIncident(
    val packet: IncidentPacket,
    val duplicate: Boolean,
)

/** Server-measured 5xx report; data class so call sites stay LongParameterList-clean. */
data class Auto5xxReport(
    val traceId: String,
    val method: String,
    val route: String,
    val status: Int,
    val elapsedMs: Long,
    val reporterRaw: String?,
)

/**
 * #475 — builds and files triage-ready incident packets. User reports and
 * 5xx auto-files share this seam so the packet shape is identical (only
 * [IncidentSource] differs). Filing is deduped by trace id, delivered
 * asynchronously, and writes no app tables — there is nothing to audit.
 * #526 — the dedup receipt survives every outcome except a pre-send failure
 * (absent/rejected executor, which evicts so a retry honestly re-files).
 */
object IncidentService {
    private const val TRACE_ID_MAX_LENGTH = 64
    private const val ENDPOINT_MAX_LENGTH = 280
    private const val APP_VERSION_MAX_LENGTH = 64
    private const val AUTO_APP_VERSION = "unknown"

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
                traceId = IncidentFields.validated(request.traceId, TRACE_ID_MAX_LENGTH, "traceId"),
                method = IncidentFields.parseMethod(request.endpoint),
                route =
                    IncidentFields.normalizedRoute(
                        IncidentFields.extractRoute(
                            IncidentFields.validated(request.endpoint, ENDPOINT_MAX_LENGTH, "endpoint"),
                        ),
                    ),
                status = null,
                elapsedMs = request.elapsedMs?.takeIf { it >= 0 },
                appVersion = IncidentFields.validated(request.appVersion, APP_VERSION_MAX_LENGTH, "appVersion"),
                timestamp = Instant.now().toString(),
                pool = IncidentFields.currentPool(),
                reporter = callerId.toString().maskUUID(),
                source = IncidentSource.USER_REPORT,
            )
        return file(packet, senderOverride)
    }

    fun fileAuto5xx(report: Auto5xxReport): FiledIncident = fileAuto5xx(report, senderOverride = null)

    /** Test seam for capturing delivery without network calls. */
    internal fun fileAuto5xx(
        report: Auto5xxReport,
        senderOverride: IncidentSender?,
    ): FiledIncident {
        val packet =
            IncidentPacket(
                traceId = report.traceId.take(TRACE_ID_MAX_LENGTH),
                method = report.method,
                route = IncidentFields.normalizedRoute(report.route),
                status = report.status,
                elapsedMs = report.elapsedMs.takeIf { it >= 0 },
                appVersion = AUTO_APP_VERSION,
                timestamp = Instant.now().toString(),
                pool = IncidentFields.currentPool(),
                reporter = IncidentFields.maskReporter(report.reporterRaw),
                source = IncidentSource.AUTO_5XX,
            )
        return file(packet, senderOverride)
    }

    private fun file(
        packet: IncidentPacket,
        senderOverride: IncidentSender?,
    ): FiledIncident {
        val (stored, duplicate) = IncidentRegistry.fileIfAbsent(packet.traceId, packet)
        if (!duplicate &&
            IncidentDelivery.deliver(stored, senderOverride) == IncidentDeliveryOutcome.FAILED_PRE_SEND
        ) {
            IncidentRegistry.remove(stored.traceId)
        }
        return FiledIncident(stored, duplicate)
    }
}
