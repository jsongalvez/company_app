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
 */
internal class NtfyIncidentSender(
    config: NtfyConfig,
    windowMs: Long = NtfyChannel.DEFAULT_WINDOW_MS,
    nowMs: () -> Long = System::currentTimeMillis,
) : IncidentSender {
    private val channel = NtfyChannel(config, "CompanyApp server error", windowMs, nowMs)

    override fun send(packet: IncidentPacket) {
        if (packet.source != IncidentSource.AUTO_5XX) return
        channel.alert(
            "${packet.method} ${packet.route} answered ${packet.status ?: DEFAULT_STATUS}\ntrace ${packet.traceId}",
        )
    }

    private companion object {
        const val DEFAULT_STATUS = 500
    }
}

/**
 * One kind of phone alert through ntfy, rate-limited: the first alert goes out at once, then at most one per
 * [windowMs]. A burst (one broken page firing the same error again and again) sends one alert; the next one
 * says how many were held back. Each kind has its own channel, so web errors never hold back a server error.
 */
internal class NtfyChannel(
    private val config: NtfyConfig,
    private val title: String,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private var lastSentAt: Long? = null
    private var held = 0

    /** Sends [text] unless the window is still closed. Blocks on the network: call it off the request thread. */
    fun alert(text: String) {
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
        post(if (heldBefore > 0) "$text\n+$heldBefore more errors since the last alert" else text)
    }

    private fun post(text: String) {
        traced("NtfyChannel.post") {
            val request =
                HttpRequest
                    .newBuilder(URI.create("${config.url}/${config.topic}"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Title", title)
                    .header("Priority", "high")
                    .header("Tags", "warning")
                    .POST(HttpRequest.BodyPublishers.ofString(text))
                    .build()
            val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding())
            if (response.statusCode() !in HTTP_OK_MIN..HTTP_OK_MAX) {
                throw IOException("ntfy answered ${response.statusCode()}")
            }
        }
    }

    companion object {
        val DEFAULT_WINDOW_MS: Long = Duration.ofMinutes(WINDOW_MINUTES).toMillis()
        private const val WINDOW_MINUTES = 10L
        private const val HTTP_OK_MIN = 200
        private const val HTTP_OK_MAX = 299
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
