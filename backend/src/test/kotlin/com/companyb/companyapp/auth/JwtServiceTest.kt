package com.companyb.companyapp.auth

import com.auth0.jwt.JWT
import com.companyb.companyapp.config.AppConfig
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JwtServiceTest {
    @AfterTest
    fun tearDown() {
        JwtService.init(configurations.first())
    }

    @Test
    fun `reinitialization publishes complete runtime snapshot`() {
        JwtService.init(configurations[1])

        val decoded = JWT.decode(JwtService.generateToken(USER_ID))

        assertEquals("issuer-1", decoded.issuer)
        assertEquals("audience-1", decoded.audience.single())
    }

    @Test
    fun `concurrent generation never mixes runtime values`() {
        JwtService.init(configurations.first())
        val failures = mutableListOf<Throwable>()
        val initializer =
            thread {
                repeat(INITIALIZATION_COUNT) { index ->
                    JwtService.init(configurations[index % configurations.size])
                }
            }
        val generators =
            List(GENERATOR_COUNT) {
                thread {
                    repeat(TOKENS_PER_GENERATOR) {
                        try {
                            val decoded = JWT.decode(JwtService.generateToken(USER_ID))
                            val pair = decoded.issuer to decoded.audience.single()
                            synchronized(failures) {
                                if (pair !in CONFIGURED_PAIRS) {
                                    failures += AssertionError("Mixed JWT runtime values: $pair")
                                }
                            }
                        } catch (failure: Throwable) {
                            synchronized(failures) { failures += failure }
                        }
                    }
                }
            }

        initializer.join()
        generators.forEach { it.join() }

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private companion object {
        const val GENERATOR_COUNT = 4
        const val INITIALIZATION_COUNT = 100
        const val TOKENS_PER_GENERATOR = 250
        const val USER_ID = "00000000-0000-0000-0000-000000000001"
        val configurations = listOf(createConfig(0), createConfig(1), createConfig(2))
        val CONFIGURED_PAIRS = configurations.map { it.jwtIssuer to it.jwtAudience }.toSet()

        fun createConfig(index: Int) =
            AppConfig(
                appPort = 8080,
                dbHost = "localhost",
                dbPort = "5432",
                dbName = "test",
                dbUser = "test",
                dbPassword = "test",
                jwtSecret = "test-secret-that-is-at-least-32-chars-$index",
                jwtIssuer = "issuer-$index",
                jwtAudience = "audience-$index",
                authDummyPassword = "dummy",
                testUsername = null,
                testPassword = null,
                scopedTestUsername = null,
                scopedTestPassword = null,
                reliefTestUsername = null,
                reliefTestPassword = null,
            )
    }
}
