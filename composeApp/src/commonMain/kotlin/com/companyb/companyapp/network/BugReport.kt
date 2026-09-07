package com.companyb.companyapp.network

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
 *         traceId = null,
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
