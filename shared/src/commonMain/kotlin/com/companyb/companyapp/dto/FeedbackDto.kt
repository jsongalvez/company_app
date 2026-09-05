package com.companyb.companyapp.dto

import com.companyb.companyapp.domain.IncidentSource
import kotlinx.serialization.Serializable

/**
 * #475 — in-app incident report call. [traceId] is the #471 echoed id of the slow
 * request; [endpoint] is the client-observed route in `BugReport` shape
 * (`"METHOD /path"`, e.g. `"GET /api/clients"`); [appVersion] is the client
 * build; [elapsedMs] is the optional client-measured latency. No free-text
 * note field: unstructured text is the PII vector, so the packet carries
 * structured fields only.
 */
@Serializable
data class FeedbackRequest(
    val traceId: String,
    val endpoint: String,
    val appVersion: String,
    val elapsedMs: Long? = null,
)

@Serializable
data class PoolSnapshot(
    val active: Int,
    val idle: Int,
    val awaiting: Int,
    val total: Int,
)

/**
 * #475 — triage-ready incident packet. Identical shape for user reports and
 * 5xx auto-files (only [source] differs) so the wolf-fence step 1 starts
 * done either way. [timestamp] is always server-stamped; [reporter] is the
 * masked caller id; the route carries no query string or raw ids.
 */
@Serializable
data class IncidentPacket(
    val traceId: String,
    val method: String,
    val route: String,
    val status: Int?,
    val elapsedMs: Long?,
    val appVersion: String,
    val timestamp: String,
    val pool: PoolSnapshot,
    val reporter: String,
    val source: IncidentSource,
)

@Serializable
data class FeedbackResponse(
    val packet: IncidentPacket,
    val duplicate: Boolean,
)
