package com.companyb.companyapp.observability

import io.javalin.http.Context

/** Bounded request labels for logs and error reports: never a raw path, so probes cannot mint new values. */
internal object RouteLabels {
    /** Fixed label for requests that matched no registered route. */
    const val UNMATCHED_ROUTE = "UNMATCHED"
    private const val UNKNOWN_METHOD = "UNKNOWN"

    private val knownMethods = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

    fun normalizeMethod(method: String): String {
        val candidate = method.uppercase()
        return candidate.takeIf { it in knownMethods } ?: UNKNOWN_METHOD
    }

    /**
     * Finite label for one request: the matched route template while a route
     * matched, else [UNMATCHED_ROUTE]. Reads the last matched HTTP endpoint;
     * `endpoint()` itself is useless here (the wildcard after-filter), per the
     * same warning in `RequestLog`.
     */
    fun routeLabel(context: Context): String =
        runCatching { context.endpoints().matchedHttpEndpoint()?.path }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: UNMATCHED_ROUTE
}
