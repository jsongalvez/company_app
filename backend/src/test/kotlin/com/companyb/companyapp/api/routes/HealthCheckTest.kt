package com.companyb.companyapp.api.routes

import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import kotlin.test.Test
import kotlin.test.assertTrue

class HealthCheckTest {
    @Test
    fun `health check query succeeds against Postgres`() {
        TestDatabaseLifecycle.ensureDatabase()
        assertTrue(HealthRoutes.isDatabaseReachable())
    }
}
