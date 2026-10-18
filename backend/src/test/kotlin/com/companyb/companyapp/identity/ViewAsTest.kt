package com.companyb.companyapp.identity

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.contracts.identity.StopViewAsRequest
import com.companyb.companyapp.contracts.identity.UpdateProfileRequest
import com.companyb.companyapp.contracts.identity.ViewAsRequest
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import io.javalin.Javalin
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Developer "View as" (feedback 378fdb6d): superuser-only, look-only unless changes are allowed. */
class ViewAsTest : BasePostgresTest() {
    private val dev = TestFixtures.uuid()
    private val otherDev = TestFixtures.uuid()
    private val pat = TestFixtures.uuid()
    private val owner = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(dev, "dev")
        IdentityFixtures.insertTestUser(otherDev, "dev2")
        IdentityFixtures.insertTestUser(pat, "pat")
        IdentityFixtures.insertTestUser(owner, "owner")
        transaction {
            val superuser = RoleRepository.findIdByNameInTransaction(ViewAs.SUPERUSER_ROLE)!!
            RoleRepository.assignRoleInTransaction(dev, superuser)
            RoleRepository.assignRoleInTransaction(otherDev, superuser)
        }
    }

    @Test
    fun `superuser sees the other person's account`() {
        testServer.client.let { client ->
            val me = client.get("/api/me", asUser(dev, viewAs = pat))
            assertEquals(200, me.code)
            assertTrue(
                me.body
                    .string()
                    .orEmpty()
                    .contains("\"id\":\"$pat\""),
            )
        }
    }

    @Test
    fun `only a superuser may view as someone`() {
        testServer.client.let { client ->
            assertEquals(403, client.get("/api/me", asUser(owner, viewAs = pat)).code)
            assertEquals(403, client.post("/api/view-as", startBody(pat), asUser(owner)).code)
        }
    }

    @Test
    fun `me says whether the caller can view as`() {
        testServer.client.let { client ->
            val devMe =
                client
                    .get("/api/me", asUser(dev))
                    .body
                    .string()
                    .orEmpty()
            assertTrue(devMe.contains("\"canViewAs\":true"), devMe)
            // false is the default, so it is left out of the JSON.
            val ownerMe =
                client
                    .get("/api/me", asUser(owner))
                    .body
                    .string()
                    .orEmpty()
            assertTrue(!ownerMe.contains("\"canViewAs\":true"), ownerMe)
        }
    }

    @Test
    fun `look-only refuses changes`() {
        testServer.client.let { client ->
            val response = client.patch("/api/me", profileBody("Pat Changed"), asUser(dev, viewAs = pat))
            assertEquals(403, response.code)
            assertTrue(
                response.body
                    .string()
                    .orEmpty()
                    .contains("Look-only"),
            )
        }
    }

    @Test
    fun `allowed changes run as the person and history names the superuser`() {
        testServer.client.let { client ->
            val response = client.patch("/api/me", profileBody("Pat Viewed"), asUser(dev, viewAs = pat, changes = true))
            assertEquals(200, response.code, response.body.string())
        }
        val row =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where { (AuditLogTable.recordId eq pat) and (AuditLogTable.changedBy eq pat) }
                    .single()
            }
        assertEquals(dev, row[AuditLogTable.viewedAsBy])
    }

    @Test
    fun `start checks the target and is logged`() {
        testServer.client.let { client ->
            val ok = client.post("/api/view-as", startBody(pat), asUser(dev))
            assertEquals(200, ok.code)
            assertTrue(
                ok.body
                    .string()
                    .orEmpty()
                    .contains("\"userId\":\"$pat\""),
            )
            assertEquals(400, client.post("/api/view-as", startBody(dev), asUser(dev)).code)
            assertEquals(400, client.post("/api/view-as", startBody(otherDev), asUser(dev)).code)
            assertEquals(404, client.post("/api/view-as", startBody(TestFixtures.uuid()), asUser(dev)).code)
            assertEquals(
                204,
                client.post("/api/view-as/stop", StopViewAsRequest(userId = pat.toString()), asUser(dev)).code,
            )
        }
        val events =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where { (AuditLogTable.recordId eq pat) and (AuditLogTable.changedBy eq dev) }
                    .count()
            }
        assertEquals(2, events)
    }

    private fun startBody(target: UUID) = ViewAsRequest(userId = target.toString(), allowChanges = false)

    private fun profileBody(name: String) =
        UpdateProfileRequest(displayName = name, email = "pat-${UUID.randomUUID()}@example.com".take(EMAIL_MAX))

    private fun asUser(
        user: UUID,
        viewAs: UUID? = null,
        changes: Boolean = false,
    ): Consumer<Request.Builder> =
        Consumer {
            it.header("X-Test-User", user.toString())
            if (viewAs != null) it.header(ViewAs.HEADER, viewAs.toString())
            if (changes) it.header(ViewAs.CHANGES_HEADER, "1")
        }

    companion object {
        private val DEFAULT_USER = TestFixtures.uuid()
        private const val EMAIL_MAX = 50

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
                    Database.connect(TestDatabaseLifecycle.requireTestDataSource())
                    val caller = ctx.header("X-Test-User") ?: DEFAULT_USER.toString()
                    ctx.attribute("userId", ViewAs.resolve(ctx, caller))
                }
                cfg.routes.exception(ValidationException::class.java) { e, ctx ->
                    ctx.status(400).json(mapOf("error" to (e.message ?: "Bad Request")))
                }
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(mapOf("error" to (e.message ?: "Forbidden")))
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(mapOf("error" to (e.message ?: "Not Found")))
                }
                cfg.routes.exception(ConflictException::class.java) { e, ctx ->
                    ctx.status(409).json(mapOf("error" to (e.message ?: "Conflict")))
                }
                MeRoutes.getMe(cfg)
                MeRoutes.updateMe(cfg)
                ViewAsRoutes.register(cfg)
            }
        }
    }
}
