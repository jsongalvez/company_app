package com.companyb.companyapp.branchday

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.authorization.Role
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import io.javalin.Javalin
import io.javalin.testtools.Request
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Guards the one door to the hub certificate: only an Owner or Manager pairs a laptop, only a
 * paired laptop's token makes the server write the `_acme-challenge` record (whoever can write it
 * can get a certificate for hub.kiel.party), and the writes per laptop are capped.
 */
class SiteHubCertsRoutesTest : BasePostgresTest() {
    private val manager = TestFixtures.uuid()
    private val practitioner = TestFixtures.uuid()
    private val dns = FakeTxt()
    private val value = "A".repeat(43)

    override fun initTestData() {
        IdentityFixtures.insertTestUser(manager, "hubmgr")
        IdentityFixtures.insertTestUser(practitioner, "hubprac")
        IdentityFixtures.hold(manager, Role.MANAGER)
        IdentityFixtures.hold(practitioner, Role.PRACTITIONER)
        SiteHubCerts.configure(dns)
        SiteHubCerts.forgetCounts()
    }

    @AfterTest
    fun unset() = SiteHubCerts.configure(null)

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun pair(user: UUID): Pair<Int, String> {
        val r = testServer.client.post("/api/site-hub/laptops", mapOf("name" to "LAPTOP-1"), asUser(user))
        return r.code to r.body.string()
    }

    private fun tokenOf(body: String) =
        Json
            .parseToJsonElement(body)
            .jsonObject["token"]!!
            .jsonPrimitive.content

    private fun challenge(
        method: String,
        token: String,
        v: String = value,
    ): Pair<Int, String> {
        val body = mapOf("token" to token, "value" to v)
        val r =
            if (method == "PUT") {
                testServer.client.put("/site-hub/challenge", body)
            } else {
                testServer.client.delete("/site-hub/challenge", body)
            }
        return r.code to r.body.string()
    }

    @Test
    fun `only an Owner or Manager pairs a laptop`() {
        assertEquals(403, pair(practitioner).first)
        val (code, body) = pair(manager)
        assertEquals(200, code)
        assertTrue(tokenOf(body).isNotBlank())
    }

    @Test
    fun `a paired laptop writes and removes the challenge record and gets a fresh token`() {
        val token = tokenOf(pair(manager).second)
        val (code, body) = challenge("PUT", token)
        assertEquals(200, code)
        assertEquals(listOf("put ${SiteHubCerts.RECORD} $value"), dns.calls)
        assertNotEquals("", tokenOf(body))
        assertEquals(204, challenge("DELETE", token).first)
        assertEquals("remove ${SiteHubCerts.RECORD} $value", dns.calls.last())
    }

    @Test
    fun `a forged or tampered token writes nothing`() {
        val token = tokenOf(pair(manager).second)
        assertEquals(401, challenge("PUT", token.dropLast(2) + "xx").first)
        assertEquals(401, challenge("PUT", "hl1.${UUID.randomUUID()}.9999999999.AAAA").first)
        assertEquals(400, challenge("PUT", token, "bad value with spaces").first)
        assertTrue(dns.calls.isEmpty())
    }

    @Test
    fun `writes per laptop are capped`() {
        val token = tokenOf(pair(manager).second)
        val codes = (1..SiteHubCerts.PUTS_PER_HOUR + 1).map { challenge("PUT", token).first }
        assertEquals(200, codes.first())
        assertEquals(429, codes.last())
        assertEquals(SiteHubCerts.PUTS_PER_HOUR, dns.calls.size)
    }

    @Test
    fun `with no DNS token the server says the certificate is not set up`() {
        val token = tokenOf(pair(manager).second)
        SiteHubCerts.configure(null)
        assertEquals(503, challenge("PUT", token).first)
    }

    private class FakeTxt : SiteHubCerts.Txt {
        val calls = mutableListOf<String>()

        override fun put(
            name: String,
            value: String,
        ) {
            calls += "put $name $value"
        }

        override fun remove(
            name: String,
            value: String,
        ) {
            calls += "remove $name $value"
        }
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
            SiteHub.configure(config.jwtSecret)
            return Javalin.create { cfg ->
                cfg.jsonMapper(KotlinxSerializationMapper())
                cfg.routes.before { ctx ->
                    Database.connect(TestDatabaseLifecycle.requireTestDataSource())
                    ctx.attribute("userId", ctx.header("X-Test-User") ?: DEFAULT_USER.toString())
                }
                cfg.routes.exception(ValidationException::class.java) { e, ctx ->
                    ctx.status(400).json(mapOf("error" to (e.message ?: "Bad Request")))
                }
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(mapOf("error" to (e.message ?: "Forbidden")))
                }
                SiteHubRoutes.register(cfg)
            }
        }
    }
}
