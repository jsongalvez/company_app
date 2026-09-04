package com.companyb.companyapp.seeding

import com.companyb.companyapp.config.AppConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevSeederTest {
    // Credential triples grouped as pairs to stay under LongParameterList while letting
    // each test provision exactly the principals it exercises (either side nullable).
    private fun config(
        global: Pair<String?, String?>? = null,
        scoped: Pair<String?, String?>? = null,
        relief: Pair<String?, String?>? = null,
    ) = AppConfig(
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
        testUsername = global?.first,
        testPassword = global?.second,
        scopedTestUsername = scoped?.first,
        scopedTestPassword = scoped?.second,
        reliefTestUsername = relief?.first,
        reliefTestPassword = relief?.second,
    )

    @Test
    fun `seed does nothing when testUsername is null`() {
        DevSeeder.seed(config(global = null to "pass"))
    }

    @Test
    fun `seed does nothing when testPassword is null`() {
        DevSeeder.seed(config(global = "user" to null))
    }

    @Test
    fun `seed does nothing when testUsername is blank`() {
        DevSeeder.seed(config(global = "  " to "pass"))
    }

    @Test
    fun `seed invokes provided transaction block when conditions are met`() {
        var called = false
        DevSeeder.seed(
            config(global = "user" to "pass"),
            runInTransaction = { called = true },
        )
        assertTrue(called, "Transaction block should be invoked when seed conditions are met")
    }

    @Test
    fun `seed invokes transaction block once per provisioned principal`() {
        var calls = 0
        DevSeeder.seed(
            config(
                global = "user" to "pass",
                scoped = "scoped" to "pass",
                relief = "relief" to "pass",
            ),
            runInTransaction = { calls += 1 },
        )
        assertEquals(3, calls, "Each provisioned principal opens exactly one seeding transaction")

        calls = 0
        DevSeeder.seed(
            config(scoped = "scoped" to "pass"),
            runInTransaction = { calls += 1 },
        )
        assertEquals(1, calls, "Scoped-only credentials seed exactly the scoped principal")

        calls = 0
        DevSeeder.seed(
            config(relief = "relief" to "pass"),
            runInTransaction = { calls += 1 },
        )
        assertEquals(1, calls, "Relief-only credentials seed exactly the relief principal")

        calls = 0
        DevSeeder.seed(config(), runInTransaction = { calls += 1 })
        assertEquals(0, calls, "No credentials means no seeding transactions")
    }
}
