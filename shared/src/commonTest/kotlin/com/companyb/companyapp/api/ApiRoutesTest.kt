package com.companyb.companyapp.api

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiRoutesTest {
    @Test
    fun migrated_routes_preserve_request_url_bytes() {
        assertEquals("/api/branch-days/day-1/users", ApiRoutes.branchDayUsers("day-1"))
        assertEquals("/api/branches/branch-1/assignments/user-1", ApiRoutes.branchAssignment("branch-1", "user-1"))
        assertEquals(
            "/api/branches/branch-1/assignments/user-1/slot",
            ApiRoutes.branchAssignmentSlot("branch-1", "user-1"),
        )
        assertEquals("/api/sessions/session-1/status", ApiRoutes.sessionStatus("session-1"))
        assertEquals(
            "/api/branches/branch-1/export/monthly?year=2026&month=8&format=csv",
            ApiRoutes.branchExportWithQuery(
                ApiRoutes.branchExportMonthly("branch-1"),
                "year=2026&month=8&format=csv",
            ),
        )
        assertEquals(
            "/api/branches/branch-1/export/all-time?format=xlsx",
            ApiRoutes.branchExportWithQuery(ApiRoutes.branchExportAllTime("branch-1"), "format=xlsx"),
        )
        assertEquals(
            "/api/branches/branch-1/export/range?from=2026-01-01&to=2026-08-18&format=csv",
            ApiRoutes.branchExportWithQuery(
                ApiRoutes.branchExportRange("branch-1"),
                "from=2026-01-01&to=2026-08-18&format=csv",
            ),
        )
    }
}
