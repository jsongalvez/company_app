package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.contracts.batch.BatchItem
import com.companyb.companyapp.contracts.batch.BatchRequest
import com.companyb.companyapp.contracts.batch.BatchResponse
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.identity.ViewAs
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Several reads in one request, for phones where each request in a row costs a round trip.
 * Each path runs as its own GET through the app's own front door with the caller's own sign-in, so every
 * check, answer and log line is the one that read has alone: nothing here decides who may see what.
 */
@OpenApi(
    path = ApiRoutes.BATCH,
    methods = [HttpMethod.POST],
    operationId = "batch",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = BatchRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = BatchResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object BatchRoutes {
    const val MAX_PATHS = 16
    private const val READ_TIMEOUT_SECONDS = 25L
    private const val MAX_PATH_CHARS = 2048
    private const val UNAVAILABLE = 502
    private val FORWARDED = listOf("Authorization", ViewAs.HEADER)
    private val SAFE_PATH = Regex("/api/[A-Za-z0-9._~!*'(),:@&=+$/%?-]*")
    private val client: HttpClient =
        HttpClient
            .newBuilder()
            .version(
                HttpClient.Version.HTTP_1_1,
            ).connectTimeout(Duration.ofSeconds(2))
            .build()

    fun register(config: JavalinConfig) {
        config.routes.post(ApiRoutes.BATCH) { context ->
            val paths = context.bodyAsClass<BatchRequest>().paths
            if (paths.isEmpty() || paths.size > MAX_PATHS) throw BadRequestResponse("Send 1 to $MAX_PATHS paths")
            paths.forEach(::requireReadablePath)
            val base = "http://127.0.0.1:${context.req().localPort}"
            val pending =
                paths.map { path ->
                    client.sendAsync(read(context, base + path), HttpResponse.BodyHandlers.ofString())
                }
            context.json(BatchResponse(pending.map(::answer)))
        }
    }

    /** Only reads of this app's own API: no other address, no way back into the batch. */
    private fun requireReadablePath(path: String) {
        val ok =
            path.length <= MAX_PATH_CHARS &&
                SAFE_PATH.matches(path) &&
                !path.contains("..") &&
                !path.contains("//") &&
                !path.startsWith(ApiRoutes.BATCH)
        if (!ok) throw BadRequestResponse("Not a readable path")
    }

    private fun read(
        context: Context,
        url: String,
    ): HttpRequest {
        val request = HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
        FORWARDED.forEach { name -> context.header(name)?.let { request.header(name, it) } }
        return request.build()
    }

    private fun answer(pending: java.util.concurrent.CompletableFuture<HttpResponse<String>>): BatchItem =
        try {
            val response = pending.join()
            BatchItem(
                response.statusCode(),
                response.body(),
                response.headers().firstValue(ApiRoutes.TRACE_ID_HEADER).orElse(null),
            )
        } catch (e: java.util.concurrent.CompletionException) {
            logger.warn { "[BATCH] A read did not answer: ${e.cause?.javaClass?.simpleName}" }
            BatchItem(UNAVAILABLE, "", null)
        }
}
