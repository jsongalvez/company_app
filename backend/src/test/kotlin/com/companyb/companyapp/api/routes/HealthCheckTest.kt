package com.companyb.companyapp.api.routes

import com.companyb.companyapp.test.DatabaseTestHelper
import kotlin.test.Test
import kotlin.test.assertTrue

class HealthCheckTest {
    @Test
    fun `health check query succeeds against Postgres`() {
        DatabaseTestHelper.ensureDatabase()
        assertTrue(HealthRoutes.isDatabaseReachable())
    }
}
