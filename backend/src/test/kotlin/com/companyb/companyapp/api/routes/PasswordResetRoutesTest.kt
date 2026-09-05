package com.companyb.companyapp.api.routes

import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import io.javalin.Javalin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #353 — forgot-password routes over HTTP: uniform 204 responses (enumeration resistance),
 * the per-IP rate budget on the request leg, and the domain-400 shape of the reset leg.
 * Request-leg tests share one IP window, so the enumeration pair and the rate-limit hammer
 * live in one deterministic test.
 */
class PasswordResetRoutesTest : BasePostgresTest() {
    override fun initTestData() = Unit

    @Test
    fun `known and unknown identifiers answer identically until the rate budget bites`() {
        val userId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(userId, "http-resetee")

        val known =
            testServer.client.post(
                "/auth/forgot-password",
                mapOf("identifier" to "http-resetee-${userId.toString().take(8)}"),
            )
        assertEquals(204, known.code)
        assertEquals("", known.body.string())

        val unknown =
            testServer.client.post(
                "/auth/forgot-password",
                mapOf("identifier" to "no-such-account-xyz"),
            )
        assertEquals(204, unknown.code)
        assertEquals("", unknown.body.string())

        // Same window as the two requests above: the budget must bite within a bounded hammer.
        var sawRateLimit = false
        repeat(30) {
            val response =
                testServer.client.post(
                    "/auth/forgot-password",
                    mapOf("identifier" to "hammer-$it"),
                )
            if (response.code == 429) {
                sawRateLimit = true
                return@repeat
            }
            assertEquals(204, response.code)
        }
        assertTrue(sawRateLimit, "the per-IP rate budget must eventually answer 429")
    }

    @Test
    fun `the reset leg has its own surface — invalid codes are a plain domain 400`() {
        val response =
            testServer.client.post(
                "/auth/reset-password",
                mapOf("token" to "not-a-real-code", "newPassword" to "valid-password"),
            )
        assertEquals(400, response.code)
        val message =
            Json
                .parseToJsonElement(response.body.string())
                .jsonObject["error"]!!
                .jsonPrimitive.content
        assertTrue(message.contains("invalid"), "got: $message")
    }

    companion object {
        private val DEFAULT_USER = TestFixtures.uuid()

        @JvmField
        @ClassRule
        val testServer = JavalinTestServerRule(::createApp)

        private fun createApp(): Javalin {
            val config = AppConfig.parse()
            JwtService.init(config)
            Password.init(config.authDummyPassword)
            return Javalin.create { cfg ->
                cfg.jsonMapper(KotlinxSerializationMapper())
                cfg.routes.before { ctx ->
                    Database.connect(DatabaseTestHelper.requireTestDataSource())
                    ctx.attribute("userId", ctx.header("X-Test-User") ?: DEFAULT_USER.toString())
                }
                cfg.routes.exception(ValidationException::class.java) { e, ctx ->
                    ctx.status(400).json(mapOf("error" to (e.message ?: "Bad Request")))
                }
                AuthRoutes.forgotPassword(cfg)
                AuthRoutes.resetPassword(cfg)
            }
        }
    }
}
