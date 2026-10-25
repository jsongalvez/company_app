package com.companyb.companyapp.observability

import com.companyb.companyapp.contracts.incident.IncidentPacket
import com.companyb.companyapp.contracts.incident.IncidentSource
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Pushes a deduped 5xx incident to the operator's phone through ntfy. The text holds only what the packet
 * already holds after scrubbing: method, normalized route, status and trace id. No reporter, client name,
 * query, body or exception message.
 *
 * Rate limit: the first alert goes out at once, then at most one per [windowMs]. A burst (one broken page
 * firing the same error again and again) sends one alert; the next one says how many were held back.
 */
internal class NtfyIncidentSender(
    private val config: NtfyConfig,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : IncidentSender {
    private var lastSentAt: Long? = null
    private var held = 0

    override fun send(packet: IncidentPacket) {
        if (packet.source != IncidentSource.AUTO_5XX) return
        val heldBefore =
            synchronized(this) {
                val now = nowMs()
                val last = lastSentAt
                if (last != null && now - last < windowMs) {
                    held++
                    return
                }
                lastSentAt = now
                held.also { held = 0 }
            }
        post(text(packet, heldBefore))
    }

    internal fun text(
        packet: IncidentPacket,
        heldBefore: Int,
    ): String =
        buildString {
            append("${packet.method} ${packet.route} answered ${packet.status ?: 500}\n")
            append("trace ${packet.traceId}")
            if (heldBefore > 0) append("\n+$heldBefore more errors since the last alert")
        }

    private fun post(text: String) {
        val request =
            HttpRequest
                .newBuilder(URI.create("${config.url}/${config.topic}"))
                .timeout(REQUEST_TIMEOUT)
                .header("Title", "CompanyApp server error")
                .header("Priority", "high")
                .header("Tags", "warning")
                .POST(HttpRequest.BodyPublishers.ofString(text))
                .build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding())
        if (response.statusCode() !in HTTP_OK_MIN..HTTP_OK_MAX) {
            throw IOException("ntfy answered ${response.statusCode()}")
        }
    }

    private companion object {
        const val DEFAULT_WINDOW_MS = 10 * 60 * 1000L
        const val HTTP_OK_MIN = 200
        const val HTTP_OK_MAX = 299
        val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
