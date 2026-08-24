package com.companyb.companyapp.seeding

import com.companyb.companyapp.config.AppConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class DemoSeedTest {
    private fun config(demoSeed: Boolean) =
        AppConfig(
            appHost = "localhost",
            appPort = 8080,
            dbHost = "localhost",
            dbPort = "5432",
            dbName = "test",
            dbUser = "test",
            dbPassword = "test",
            jwtSecret = "test-secret-that-is-at-least-32-chars",
            jwtIssuer = "test",
            jwtAudience = "test",
            authDummyPassword = "test-dummy-password-at-least-32-characters",
            testUsername = null,
            testPassword = null,
            scopedTestUsername = null,
            scopedTestPassword = null,
            reliefTestUsername = null,
            reliefTestPassword = null,
            demoSeed = demoSeed,
        )

    @Test
    fun `seed does nothing when demoSeed is disabled`() {
        var calls = 0
        DemoSeed.seed(config(demoSeed = false)) { _ -> calls += 1 }
        assertEquals(0, calls, "No transaction may open when DEMO_SEED is not enabled")
    }

    @Test
    fun `seed opens exactly one transaction when enabled`() {
        var calls = 0
        DemoSeed.seed(config(demoSeed = true)) { _ -> calls += 1 }
        assertEquals(1, calls)
    }
}
