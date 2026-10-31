package com.companyb.companyapp.identity

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import io.javalin.Javalin
import io.javalin.testtools.HttpClient
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.util.function.Consumer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Prod serves every request through one reverse proxy (Traefik at 10.0.2.2), so the direct
 * peer is the same for every user. Sign-in is limited to 10 per minute per client; the client
 * must be the address the proxy forwards, and only a trusted peer may say who that is.
 */
class LoginClientIpRoutesTest : BasePostgresTest() {
    override fun initTestData() = Unit

    @BeforeTest
    fun reset() {
        RateLimiter.resetForTest()
    }

    @AfterTest
    fun leaveNoStateBehind() {
        ClientIp.resetForTest()
        // These tests exhaust budgets, including the loopback one other test classes sign in from.
        RateLimiter.resetForTest()
    }

    private var names = 0

    private fun forwardedFor(address: String): Consumer<Request.Builder> =
        Consumer { it.header("X-Forwarded-For", address) }

    private fun signIn(
        client: HttpClient,
        address: String,
    ): Int =
        client
            .post(
                "/auth/login",
                // A name per call, so only the per-address budget is under test, not the per-account one.
                mapOf("username" to "nobody-${names++}", "password" to "wrong-password"),
                forwardedFor(address),
            ).code

    /** Sends sign-ins until one is refused; returns how many were answered before that, or null if none was. */
    private fun answeredBeforeRefusal(address: String): Int? {
        repeat(MAX_TRIES) { sent ->
            if (signIn(testServer.client, address) == 429) return sent
        }
        return null
    }

    @Test
    fun `two staff behind the proxy get separate sign-in budgets`() {
        val first = answeredBeforeRefusal("203.0.113.1")
        assertTrue(first != null, "ten-a-minute limit must bite one client")

        val second = signIn(testServer.client, "203.0.113.2")
        assertEquals(401, second, "a second client behind the same proxy must not inherit the first one's refusal")
    }

    @Test
    fun `a peer that is not a trusted proxy cannot pick its own address`() {
        ClientIp.trustedForTest(emptyList())

        val first = answeredBeforeRefusal("203.0.113.1")
        assertTrue(first != null, "ten-a-minute limit must bite one client")

        val relabelled = signIn(testServer.client, "203.0.113.2")
        assertEquals(429, relabelled, "changing the forwarded header must not reset the budget")
    }

    companion object {
        private const val MAX_TRIES = 30

        @JvmField
        @ClassRule
        val testServer = JavalinTestServerRule(::createApp)

        private fun createApp(): Javalin {
            val config = AppConfig.parse()
            JwtService.init(config)
            Password.init(config.authDummyPassword)
            return Javalin.create { cfg ->
                cfg.jsonMapper(KotlinxSerializationMapper())
                cfg.routes.before { Database.connect(TestDatabaseLifecycle.requireTestDataSource()) }
                AuthRoutes.login(cfg)
            }
        }
    }
}
