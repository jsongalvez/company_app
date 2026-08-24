package com.companyb.companyapp.seeding

import com.companyb.companyapp.config.AppConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevSeederTest {
    private fun config(
        testUsername: String? = null,
        testPassword: String? = null,
        scopedTestUsername: String? = null,
        scopedTestPassword: String? = null,
    ) = AppConfig(
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
        testUsername = testUsername,
        testPassword = testPassword,
        scopedTestUsername = scopedTestUsername,
        scopedTestPassword = scopedTestPassword,
    )

    @Test
    fun `seed does nothing when testUsername is null`() {
        DevSeeder.seed(config(testUsername = null, testPassword = "pass"))
    }

    @Test
    fun `seed does nothing when testPassword is null`() {
        DevSeeder.seed(config(testUsername = "user", testPassword = null))
    }

    @Test
    fun `seed does nothing when testUsername is blank`() {
        DevSeeder.seed(config(testUsername = "  ", testPassword = "pass"))
    }

    @Test
    fun `seed invokes provided transaction block when conditions are met`() {
        var called = false
        DevSeeder.seed(
            config(testUsername = "user", testPassword = "pass"),
            runInTransaction = { called = true },
        )
        assertTrue(called, "Transaction block should be invoked when seed conditions are met")
    }

    @Test
    fun `seed invokes transaction block once per provisioned principal`() {
        var calls = 0
        DevSeeder.seed(
            config(
                testUsername = "user",
                testPassword = "pass",
                scopedTestUsername = "scoped",
                scopedTestPassword = "pass",
            ),
            runInTransaction = { calls += 1 },
        )
        assertEquals(2, calls, "Global and scoped principals each open one seeding transaction")

        calls = 0
        DevSeeder.seed(
            config(scopedTestUsername = "scoped", scopedTestPassword = "pass"),
            runInTransaction = { calls += 1 },
        )
        assertEquals(1, calls, "Scoped-only credentials seed exactly the scoped principal")

        calls = 0
        DevSeeder.seed(config(), runInTransaction = { calls += 1 })
        assertEquals(0, calls, "No credentials means no seeding transactions")
    }
}
