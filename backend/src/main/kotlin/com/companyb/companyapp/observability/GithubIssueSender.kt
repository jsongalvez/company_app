package com.companyb.companyapp.observability

import com.companyb.companyapp.dto.IncidentPacket
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Serializable
internal data class GithubIssueRequest(
    val title: String,
    val body: String,
    val labels: List<String>,
)

/**
 * #475 — posts the incident packet as a needs-triage GitHub issue. The packet
 * is already PII-scrubbed (masked reporter, normalized route, no query/body),
 * so the issue body renders packet fields only. Stdlib HTTP client, no new
 * dependency — same zero-dep stance as #472/#473.
 */
internal class GithubIssueSender(
    private val config: GithubIssueConfig,
) : IncidentSender {
    override fun send(packet: IncidentPacket) {
        val payload = Json.encodeToString(renderIssue(packet))
        val request =
            HttpRequest
                .newBuilder(URI.create("$API_BASE/repos/${config.repository}/issues"))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", GITHUB_JSON)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${config.token}")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in HTTP_OK_MIN..HTTP_OK_MAX) {
            throw IOException("GitHub issue creation failed with status ${response.statusCode()}")
        }
    }

    internal fun renderIssue(packet: IncidentPacket): GithubIssueRequest {
        val shortTrace = packet.traceId.take(TRACE_SHORT_LENGTH)
        return GithubIssueRequest(
            title = "Incident $shortTrace ${packet.method} ${packet.route}",
            body = renderBody(packet),
            labels = listOf(NEEDS_TRIAGE_LABEL),
        )
    }

    private fun renderBody(packet: IncidentPacket): String =
        "Auto-filed incident packet (`${packet.source}`). " +
            "Start the wolf-fence here: correlate `${packet.traceId}` with the request logs.\n\n" +
            "- traceId: ${packet.traceId}\n" +
            "- route: ${packet.method} ${packet.route}\n" +
            "- status: ${packet.status?.toString() ?: "n/a"}\n" +
            "- elapsedMs: ${packet.elapsedMs?.toString() ?: "n/a"}\n" +
            "- appVersion: ${packet.appVersion}\n" +
            "- timestamp: ${packet.timestamp}\n" +
            "- pool: active=${packet.pool.active} idle=${packet.pool.idle} " +
            "awaiting=${packet.pool.awaiting} total=${packet.pool.total}\n" +
            "- reporter: ${packet.reporter}\n"

    private companion object {
        const val API_BASE = "https://api.github.com"
        const val GITHUB_JSON = "application/vnd.github+json"
        const val NEEDS_TRIAGE_LABEL = "needs-triage"
        const val TRACE_SHORT_LENGTH = 8
        const val HTTP_OK_MIN = 200
        const val HTTP_OK_MAX = 299
        const val REQUEST_TIMEOUT_SECONDS = 10L
        val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS)
    }
}
