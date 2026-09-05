package com.companyb.companyapp.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TraceReportTest {
    @Test
    fun `format carries endpoint version user timestamp and trace id`() {
        val report =
            BugReport(
                endpoint = "GET /api/clients?q=jhn",
                appVersion = "1.2.3",
                user = "user-1",
                timestamp = "2026-09-05T00:00:00Z",
                traceId = "abc123XYZ",
            )
        val line = report.format()
        assertTrue(line.contains("endpoint=GET /api/clients?q=jhn"))
        assertTrue(line.contains("appVersion=1.2.3"))
        assertTrue(line.contains("user=user-1"))
        assertTrue(line.contains("timestamp=2026-09-05T00:00:00Z"))
        assertTrue(line.contains("traceId=abc123XYZ"))
    }

    @Test
    fun `format marks a missing trace id instead of dropping the field`() {
        val report =
            BugReport(
                endpoint = "GET /api/branches",
                appVersion = "1.2.3",
                user = "user-1",
                timestamp = "2026-09-05T00:00:00Z",
                traceId = null,
            )
        assertEquals(
            "endpoint=GET /api/branches appVersion=1.2.3 user=user-1 " +
                "timestamp=2026-09-05T00:00:00Z traceId=missing",
            report.format(),
        )
    }
}
