package com.companyb.companyapp.observability

import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.HttpStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Phone alert for what went wrong in someone's web app, read from their uploaded telemetry: an uncaught
 * script error, a queued save the server refused, or a call the server refused (5xx is alerted by the server
 * itself, and 401/409/429 are the app working as designed). The alert names the error class, the screen and
 * the page load's session id (`tools/replay/replay.sh <session id>` finds it); never the error's message,
 * which can quote a person.
 */
internal object WebErrorAlerts {
    private val logger = KotlinLogging.logger {}
    private val EXPECTED =
        setOf(
            HttpStatus.UNAUTHORIZED,
            HttpStatus.CONFLICT,
            HttpStatus.TOO_MANY_REQUESTS,
        ).map { it.code }
    private const val CLIENT_ERROR_MIN = 400
    private const val SERVER_ERROR_MIN = 500
    private const val QUEUE = 20
    private val ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", RegexOption.IGNORE_CASE)
    private val ERROR_CLASS = Regex("\\b([A-Z][A-Za-z]*(?:Error|Exception))\\b")

    @Volatile
    private var channel: NtfyChannel? = null

    private val sender =
        ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(QUEUE)) { r ->
            Thread(r, "web-error-alert").apply { isDaemon = true }
        }

    fun configure(ntfy: NtfyConfig?) {
        channel = ntfy?.let { NtfyChannel(it, "CompanyApp web error") }
    }

    /** Queues one alert per alerting event; the channel's window turns a burst into one. Never blocks. */
    fun notice(
        sessionId: UUID,
        events: List<TelemetryEvent>,
    ) {
        val target = channel ?: return
        for (line in events.mapNotNull(::line)) {
            try {
                sender.execute { send(target, "$line\nsession $sessionId") }
            } catch (full: RejectedExecutionException) {
                logger.warn(full) { "[WEB-ERROR] alert queue full, dropped" }
                return
            }
        }
    }

    private fun send(
        target: NtfyChannel,
        text: String,
    ) {
        try {
            target.alert(text)
        } catch (failure: IOException) {
            logger.warn(failure) { "[WEB-ERROR] ntfy alert failed" }
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.warn(failure) { "[WEB-ERROR] ntfy alert interrupted" }
        }
    }

    /** The alert's first line for [event], or null when it isn't one to alert on. */
    internal fun line(event: TelemetryEvent): String? {
        val data = runCatching { Json.parseToJsonElement(event.data) as? JsonObject }.getOrNull() ?: return null

        fun text(key: String) = data[key]?.jsonPrimitive?.contentOrNull
        val screen = (text("r") ?: "?").replace(ID, "{id}").substringBefore('?')
        return when (event.kind) {
            "error" -> {
                val outbox = text("outbox")
                if (outbox != null) {
                    "Offline save refused: ${outbox.replace(ID, "{id}")} answered ${text("s")} on $screen"
                } else {
                    val type = text("msg")?.let { ERROR_CLASS.find(it)?.groupValues?.get(1) } ?: "Script error"
                    "$type on $screen"
                }
            }

            "api" -> {
                data["s"]
                    ?.jsonPrimitive
                    ?.intOrNull
                    ?.takeIf { it in CLIENT_ERROR_MIN until SERVER_ERROR_MIN && it !in EXPECTED }
                    ?.let { "${text("m")} ${text("p")?.replace(ID, "{id}")} answered $it on $screen" }
            }

            else -> {
                null
            }
        }
    }
}
