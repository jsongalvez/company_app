package com.companyb.companyapp.branchday

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.authorization.Role
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.AppUserTable
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.ClassRule
import java.io.IOException
import java.security.KeyPair
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards site hub pairing, laptop revocation and server-managed certificate distribution:
 * any active staff member pairs a laptop, onboarding and deactivated accounts are refused,
 * Owners and Managers audit and revoke laptops, and the server issues one shared certificate
 * for `hub.kiel.party` instead of giving laptops DNS access.
 */
class SiteHubCertsRoutesTest : BasePostgresTest() {
    private val owner = TestFixtures.uuid()
    private val manager = TestFixtures.uuid()
    private val coordinator = TestFixtures.uuid()
    private val practitioner = TestFixtures.uuid()
    private val accountant = TestFixtures.uuid()
    private val superuser = TestFixtures.uuid()
    private val onboarding = TestFixtures.uuid()
    private val deactivated = TestFixtures.uuid()
    private val dns = FakeTxt()
    private val issuer = FakeIssuer()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(owner, "hubown")
        IdentityFixtures.insertTestUser(manager, "hubmgr")
        IdentityFixtures.insertTestUser(coordinator, "hubcoo")
        IdentityFixtures.insertTestUser(practitioner, "hubprac")
        IdentityFixtures.insertTestUser(accountant, "hubacc")
        IdentityFixtures.insertTestUser(superuser, "hubsup")
        IdentityFixtures.insertTestUser(onboarding, "hubnew")
        IdentityFixtures.insertTestUser(deactivated, "huboff")
        IdentityFixtures.hold(owner, Role.OWNER)
        IdentityFixtures.hold(manager, Role.MANAGER)
        IdentityFixtures.hold(coordinator, Role.COORDINATOR)
        IdentityFixtures.hold(practitioner, Role.PRACTITIONER)
        IdentityFixtures.hold(accountant, Role.ACCOUNTANT)
        IdentityFixtures.hold(superuser, Role.SUPERUSER)
        IdentityFixtures.hold(onboarding, Role.ONBOARDING)
        IdentityFixtures.hold(deactivated, Role.PRACTITIONER)
        transaction {
            AppUserTable.update({ AppUserTable.id eq deactivated }) {
                it[status] = UserStatus.INACTIVE
            }
        }
        SiteHubCerts.configure(dns, issuer, Duration.ZERO)
        SiteHubCerts.forgetCounts()
    }

    @AfterTest
    fun unset() = SiteHubCerts.configure(null)

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun pair(
        user: UUID,
        name: String = "LAPTOP-1",
    ): Pair<Int, String> {
        val r = testServer.client.post("/api/site-hub/laptops", mapOf("name" to name), asUser(user))
        return r.code to r.body.string()
    }

    private fun listLaptops(user: UUID): Pair<Int, String> {
        val r = testServer.client.get("/api/site-hub/laptops", asUser(user))
        return r.code to r.body.string()
    }

    private fun revokeLaptop(
        user: UUID,
        laptopId: String,
    ): Int = testServer.client.delete("/api/site-hub/laptops/$laptopId", req = asUser(user)).code

    private fun fetchCert(token: String): Pair<Int, String> {
        val r = testServer.client.post("/site-hub/cert", mapOf("token" to token))
        return r.code to r.body.string()
    }

    private fun jsonField(
        body: String,
        key: String,
    ) = Json
        .parseToJsonElement(body)
        .jsonObject[key]!!
        .jsonPrimitive
        .content

    @Test
    fun `any active staff role pairs a laptop while onboarding and inactive accounts are refused`() {
        for (staff in listOf(owner, manager, coordinator, practitioner, accountant, superuser)) {
            val (code, body) = pair(staff, "LAPTOP-$staff")
            assertEquals(200, code, "expected 200 for $staff, got $code: $body")
            assertTrue(jsonField(body, "token").startsWith("hl2."))
            assertTrue(jsonField(body, "id").isNotBlank())
        }
        assertEquals(403, pair(onboarding).first)
        assertEquals(403, pair(deactivated).first)
    }

    @Test
    fun `multiple paired laptops share one server-issued certificate and record last seen`() {
        val first = pair(practitioner, "FRONT-DESK").second
        val second = pair(coordinator, "BACK-DESK").second
        val (code1, body1) = fetchCert(jsonField(first, "token"))
        val (code2, body2) = fetchCert(jsonField(second, "token"))
        assertEquals(200, code1)
        assertEquals(200, code2)
        assertEquals(1, issuer.calls.get(), "second laptop must reuse cached server cert")
        assertEquals(jsonField(body1, "certPem"), jsonField(body2, "certPem"))
        assertEquals(jsonField(body1, "keyPem"), jsonField(body2, "keyPem"))

        val (listCode, listBody) = listLaptops(manager)
        assertEquals(200, listCode)
        val laptops = Json.parseToJsonElement(listBody).jsonObject["laptops"]!!.jsonArray
        assertEquals(2, laptops.size)
        assertTrue(laptops.all { it.jsonObject["lastSeenAt"]?.jsonPrimitive?.content != null })
        assertEquals(403, listLaptops(practitioner).first)
    }

    @Test
    fun `revoking a laptop rejects its token immediately and keeps audit metadata`() {
        val paired = pair(practitioner, "LOST-LAPTOP").second
        val id = jsonField(paired, "id")
        val token = jsonField(paired, "token")
        assertEquals(200, fetchCert(token).first)
        assertEquals(403, revokeLaptop(practitioner, id))
        assertEquals(204, revokeLaptop(manager, id))
        assertEquals(401, fetchCert(token).first)
        assertEquals(404, revokeLaptop(manager, UUID.randomUUID().toString()))

        val laptops = Json.parseToJsonElement(listLaptops(owner).second).jsonObject["laptops"]!!.jsonArray
        val row = laptops.single().jsonObject
        assertNotNull(row["revokedAt"]?.jsonPrimitive?.content)
        assertEquals(manager.toString(), row["revokedBy"]?.jsonPrimitive?.content)
    }

    @Test
    fun `forged, blank and legacy stateless tokens are rejected`() {
        assertEquals(401, fetchCert("").first)
        assertEquals(401, fetchCert("hl1.${UUID.randomUUID()}.9999999999.AAAA").first)
        assertEquals(401, fetchCert("hl2.not-a-real-token").first)
        assertEquals(0, issuer.calls.get())
    }

    @Test
    fun `certificate renews once at two thirds of its life`() {
        val token = jsonField(pair(practitioner).second, "token")
        val t0 = Instant.parse("2027-01-01T00:00:00Z")
        issuer.notBefore = t0
        issuer.notAfter = t0.plus(Duration.ofDays(90))
        assertTrue(SiteHubCerts.cert(token, t0) is SiteHubCerts.CertResult.Ready)
        assertEquals(1, issuer.calls.get())

        assertTrue(SiteHubCerts.cert(token, t0.plus(Duration.ofDays(59))) is SiteHubCerts.CertResult.Ready)
        assertEquals(1, issuer.calls.get())

        issuer.notBefore = t0.plus(Duration.ofDays(60))
        issuer.notAfter = t0.plus(Duration.ofDays(150))
        val renewed = SiteHubCerts.cert(token, t0.plus(Duration.ofDays(60)))
        assertTrue(renewed is SiteHubCerts.CertResult.Ready)
        assertEquals(2, issuer.calls.get())
        assertEquals(t0.plus(Duration.ofDays(150)), renewed.cert.notAfter)
    }

    @Test
    fun `missing DNS token returns 503 when no cert exists and issuer failure returns 502`() {
        val token = jsonField(pair(practitioner).second, "token")
        SiteHubCerts.configure(null, issuer, Duration.ZERO)
        assertEquals(503, fetchCert(token).first)

        issuer.failNext = true
        SiteHubCerts.configure(dns, issuer, Duration.ZERO)
        assertEquals(502, fetchCert(token).first)
    }

    @Test
    fun `pairing is rate-limited per staff user`() {
        val codes = (1..SiteHubCerts.PAIRS_PER_HOUR + 1).map { pair(practitioner, "PC-$it").first }
        assertEquals(200, codes.first())
        assertEquals(429, codes.last())
        val laptops = Json.parseToJsonElement(listLaptops(owner).second).jsonObject["laptops"]!!.jsonArray
        assertEquals(SiteHubCerts.PAIRS_PER_HOUR, laptops.size)
        assertNull(laptops.first().jsonObject["revokedAt"]?.takeIf { it.toString() != "null" })
    }

    private class FakeTxt : SiteHubCerts.Txt {
        override fun put(
            name: String,
            value: String,
        ) = Unit

        override fun remove(
            name: String,
            value: String,
        ) = Unit
    }

    private class FakeIssuer : SiteHubCerts.CertIssuer {
        val calls = AtomicInteger()
        var notBefore: Instant = Instant.now().minusSeconds(60)
        var notAfter: Instant = Instant.now().plus(Duration.ofDays(90))
        var failNext: Boolean = false

        override fun obtain(
            domain: String,
            accountKey: KeyPair,
            txt: SiteHubCerts.Txt,
            propagationWait: Duration,
        ): SiteHubCerts.IssuedCert {
            if (failNext) {
                failNext = false
                throw IOException("simulated ACME failure")
            }
            val n = calls.incrementAndGet()
            return SiteHubCerts.IssuedCert(
                certPem = "-----BEGIN CERTIFICATE-----\nCERT-$n-$domain\n-----END CERTIFICATE-----\n",
                keyPem = "-----BEGIN PRIVATE KEY-----\nKEY-$n\n-----END PRIVATE KEY-----\n",
                notBefore = notBefore,
                notAfter = notAfter,
            )
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
