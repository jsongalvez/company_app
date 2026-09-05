package com.companyb.companyapp.api.middleware

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.utils.RandomIdGenerator
import io.javalin.http.Context
import org.slf4j.MDC

/**
 * Per-request trace id (#471): minted once in the global `before` filter so it
 * survives the auth/capability filters, echoed as [ApiRoutes.TRACE_ID_HEADER]
 * on every response so a caller can cite the exact slow request. The value is
 * an opaque random id — no PII.
 */
object TraceIdFilter {
    const val ATTRIBUTE = "traceId"

    fun before(context: Context) {
        val traceId = RandomIdGenerator.generate()
        MDC.put(ATTRIBUTE, traceId)
        context.attribute(ATTRIBUTE, traceId)
        context.header(ApiRoutes.TRACE_ID_HEADER, traceId)
    }

    fun echo(context: Context) {
        context.attribute<String>(ATTRIBUTE)?.let { context.header(ApiRoutes.TRACE_ID_HEADER, it) }
    }
}
