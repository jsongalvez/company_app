package com.companyb.companyapp.api.middleware

import io.javalin.http.BadRequestResponse
import io.javalin.http.Context

/**
 * Postgres text cannot hold U+0000; a string carrying one reaches the driver and fails
 * as a 500 ("invalid byte sequence for encoding UTF8: 0x00"). Reject it once at the edge
 * instead of in every route that stores or looks up a string.
 */
object NulCharFilter {
    // A JSON escape for U+0000 that is not itself escaped by a preceding backslash.
    private val jsonNulEscape = Regex("""(?<!\\)(?:\\\\)*\\u0000""")

    fun before(context: Context) {
        if (queryHasNul(context) || bodyHasNul(context)) throw BadRequestResponse("NUL characters are not allowed")
    }

    private fun queryHasNul(context: Context): Boolean =
        context.queryParamMap().any { (name, values) -> name.contains(NUL) || values.any { it.contains(NUL) } }

    private fun bodyHasNul(context: Context): Boolean {
        if (context.contentType()?.contains("json", ignoreCase = true) != true) return false
        val body = context.body()
        return body.contains(NUL) || jsonNulEscape.containsMatchIn(body)
    }

    private const val NUL = '\u0000'
}
