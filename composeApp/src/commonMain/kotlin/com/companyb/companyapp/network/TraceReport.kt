package com.companyb.companyapp.network

import com.companyb.companyapp.api.ApiRoutes
import io.ktor.client.statement.HttpResponse

/**
 * Reads the server-minted trace id (#471) off a response. Pair it with
 * [BugReport.format] when filing a slow-request report so the backend can
 * pinpoint the exact request in its logs.
 */
fun HttpResponse.traceId(): String? = headers[ApiRoutes.TRACE_ID_HEADER]

/**
 * Copy-pasteable slow-request report (#471): endpoint + app version + user +
 * timestamp + trace id. Build one at the call site that observed the slow
 * request and paste [format] output into the bug report:
 *
 * ```
 * handler.launch(..., block = { apiClient.httpClient.get(url) }) { response ->
 *     val report = BugReport(
 *         endpoint = "GET $url",
 *         appVersion = appVersion,
 *         user = userId,
 *         timestamp = Clock.System.now().toString(),
 *         traceId = response.traceId(),
 *     )
 *     logInfo("SlowVM", report.format())
 *     response.body()
 * }
 * ```
 */
data class BugReport(
    val endpoint: String,
    val appVersion: String,
    val user: String,
    val timestamp: String,
    val traceId: String?,
) {
    fun format(): String =
        "endpoint=$endpoint appVersion=$appVersion user=$user timestamp=$timestamp traceId=${traceId ?: "missing"}"
}
