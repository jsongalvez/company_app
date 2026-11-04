package com.companyb.companyapp.observability

import io.javalin.http.Context
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Scope
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * The server's OpenTelemetry tracer. Until [start] is given an exporter, spans are made (so every
 * request still gets a W3C trace id) and dropped. [start] batches finished spans to the exporter on
 * its own thread, so a request never waits on a span write.
 */
object Tracing {
    private const val NAME = "company-app"
    private const val QUEUE_SPANS = 8192
    private const val BATCH_SPANS = 512
    private const val SHUTDOWN_SECONDS = 5L
    private val EXPORT_DELAY: Duration = Duration.ofSeconds(2)

    /** Keeps spans real but stores none: with no processor the SDK hands out a no-op tracer (all-zero ids). */
    private val dropAll =
        object : SpanProcessor {
            override fun onStart(
                parentContext: io.opentelemetry.context.Context,
                span: ReadWriteSpan,
            ) = Unit

            override fun isStartRequired() = false

            override fun onEnd(span: ReadableSpan) = Unit

            override fun isEndRequired() = false
        }

    @Volatile
    private var provider: SdkTracerProvider = SdkTracerProvider.builder().addSpanProcessor(dropAll).build()

    @Volatile
    var tracer: Tracer = provider.get(NAME)
        private set

    @Synchronized
    fun start(exporter: SpanExporter) {
        val processor =
            BatchSpanProcessor
                .builder(exporter)
                .setScheduleDelay(EXPORT_DELAY)
                .setMaxQueueSize(QUEUE_SPANS)
                .setMaxExportBatchSize(BATCH_SPANS)
                .build()
        replace(SdkTracerProvider.builder().addSpanProcessor(processor).build())
    }

    @Synchronized
    fun stop() = replace(SdkTracerProvider.builder().addSpanProcessor(dropAll).build())

    /** Writes every finished span now; tests and shutdown call it. */
    fun flush() {
        provider.forceFlush().join(SHUTDOWN_SECONDS, TimeUnit.SECONDS)
    }

    private fun replace(next: SdkTracerProvider) {
        val old = provider
        provider = next
        tracer = next.get(NAME)
        old.shutdown().join(SHUTDOWN_SECONDS, TimeUnit.SECONDS)
    }
}

/**
 * The span for one function: wrap the whole body of a function that reads the database or calls
 * out, named `Owner.function`. detekt's `TracedAtDefinition` rule keeps every such function wrapped.
 */
inline fun <T> traced(
    name: String,
    block: () -> T,
): T {
    val span = Tracing.tracer.spanBuilder(name).startSpan()
    val scope = span.makeCurrent()
    var finished = false
    try {
        return block().also { finished = true }
    } finally {
        if (!finished) span.setStatus(StatusCode.ERROR)
        scope.close()
        span.end()
    }
}

/** What one request's root span says about it once it is answered: the wide event. */
data class RequestOutcome(
    val method: String,
    val template: String,
    val status: Int,
    val elapsedMs: Long,
    val dbMs: Long,
    val queries: Int,
    val user: String?,
    val branch: String?,
    val viewAs: Boolean,
    val error: Throwable?,
) {
    val label: String
        get() =
            when {
                status >= SERVER_ERROR -> "server_error"
                status >= CLIENT_ERROR -> "refused"
                else -> "ok"
            }

    private companion object {
        const val CLIENT_ERROR = 400
        const val SERVER_ERROR = 500
    }
}

/**
 * A request's root span. It continues the caller's trace when the request carries a W3C
 * `traceparent` (the web app sends one), so the page's call and the server's work share one trace id.
 */
object RequestSpan {
    private const val ATTRIBUTE = "requestSpan"
    private const val SERVER_ERROR = 500
    private const val APP_PACKAGE = "com.companyb.companyapp."
    private const val ERROR_FRAMES = 3

    private class Open(
        val span: Span,
        val scope: Scope,
    )

    private val headers =
        object : TextMapGetter<Context> {
            override fun get(
                carrier: Context?,
                key: String,
            ): String? = carrier?.header(key)
        }

    /** Opens the root span on this thread and returns its trace id. */
    fun start(ctx: Context): String {
        val parent =
            W3CTraceContextPropagator
                .getInstance()
                .extract(
                    io.opentelemetry.context.Context
                        .root(),
                    ctx,
                    headers,
                )
        val span =
            Tracing.tracer
                .spanBuilder(ctx.method().name)
                .setSpanKind(SpanKind.SERVER)
                .setParent(parent)
                .startSpan()
        ctx.attribute(ATTRIBUTE, Open(span, span.makeCurrent()))
        return span.spanContext.traceId
    }

    fun end(
        ctx: Context,
        outcome: RequestOutcome,
    ) {
        val open = ctx.attribute<Open>(ATTRIBUTE) ?: return
        ctx.attribute(ATTRIBUTE, null)
        val span = open.span
        span.updateName("${outcome.method} ${outcome.template}")
        span.setAttribute("http.request.method", outcome.method)
        span.setAttribute("http.route", outcome.template)
        span.setAttribute("http.response.status_code", outcome.status.toLong())
        span.setAttribute("outcome", outcome.label)
        span.setAttribute("db.ms", outcome.dbMs)
        span.setAttribute("db.statements", outcome.queries.toLong())
        span.setAttribute("elapsed.ms", outcome.elapsedMs)
        outcome.user?.let { span.setAttribute("user", it) }
        outcome.branch?.let { span.setAttribute("branch", it) }
        if (outcome.viewAs) span.setAttribute("view_as", true)
        outcome.error?.let { e ->
            span.setAttribute("error.type", e.className())
            span.setAttribute("error.at", appFrames(e))
        }
        if (outcome.status >= SERVER_ERROR) span.setStatus(StatusCode.ERROR)
        open.scope.close()
        span.end()
    }

    /** Where in our code it failed, as `File.kt:line` frames: never the message, which can quote values. */
    private fun appFrames(e: Throwable): String =
        e.stackTrace
            .filter { it.className.startsWith(APP_PACKAGE) }
            .take(ERROR_FRAMES)
            .joinToString(" < ") { "${it.fileName}:${it.lineNumber}" }
}

/** The exception's class name without its message (`toString()` is `class: message`; messages can quote values). */
fun Throwable.className(): String = toString().substringBefore(':')
