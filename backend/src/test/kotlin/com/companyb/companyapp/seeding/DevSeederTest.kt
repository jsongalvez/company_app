package com.companyb.companyapp.seeding

import com.companyb.companyapp.config.AppConfig
import kotlin.test.Test

class DevSeederTest {
    private fun config(
        seedDevUser: Boolean = false,
        testUsername: String? = null,
        testPassword: String? = null,
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
        seedDevUser = seedDevUser,
        testUsername = testUsername,
        testPassword = testPassword,
    )

    @Test
    fun `seed does nothing when seedDevUser is false`() {
        DevSeeder.seed(config(seedDevUser = false, testUsername = "user", testPassword = "pass"))
    }

    @Test
    fun `seed does nothing when testUsername is null`() {
        DevSeeder.seed(config(seedDevUser = true, testUsername = null, testPassword = "pass"))
    }

    @Test
    fun `seed does nothing when testPassword is null`() {
        DevSeeder.seed(config(seedDevUser = true, testUsername = "user", testPassword = null))
    }

    @Test
    fun `seed does nothing when testUsername is blank`() {
        DevSeeder.seed(config(seedDevUser = true, testUsername = "  ", testPassword = "pass"))
    }
}
