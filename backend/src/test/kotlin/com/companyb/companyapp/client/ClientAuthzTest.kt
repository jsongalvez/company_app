package com.companyb.companyapp.client

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import io.javalin.Javalin
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #653: item + anonymize routes gate GLOBAL EDIT_BRANCH_DATA — the same gate as
 * the collection. The #114 exact-segment lesson: before(CLIENTS) never fires on
 * child segments, so each path carries its own filter. A BRANCH-scoped grant
 * must not satisfy the GLOBAL gate (#131 strictness).
 */
class ClientAuthzTest : BasePostgresTest() {
    private val globalHolder = TestFixtures.uuid()
    private val branchHolder = TestFixtures.uuid()
    private val noneUser = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val anonymizeClientId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(globalHolder, "client-global")
        IdentityFixtures.insertTestUser(branchHolder, "client-branch")
        IdentityFixtures.insertTestUser(noneUser, "client-none")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Client Authz Branch $branchId")
        IdentityFixtures.grantCapability(
            userId = globalHolder,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
        IdentityFixtures.grantCapability(
            userId = branchHolder,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
        SessionClientFixtures.insertTestClient(clientId)
        SessionClientFixtures.insertTestClient(anonymizeClientId)
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
                    Database.connect(TestDatabaseLifecycle.requireTestDataSource())
                    ctx.attribute("userId", ctx.header("X-Test-User") ?: DEFAULT_USER.toString())
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
                ClientRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    @Test
    fun `GET client detail allowed for GLOBAL holder`() {
        assertEquals(200, testServer.client.get("/api/clients/$clientId", asUser(globalHolder)).code)
    }

    @Test
    fun `GET client detail forbidden without grant`() {
        assertEquals(403, testServer.client.get("/api/clients/$clientId", asUser(noneUser)).code)
    }

    @Test
    fun `GET client detail forbidden for BRANCH holder`() {
        assertEquals(403, testServer.client.get("/api/clients/$clientId", asUser(branchHolder)).code)
    }

    @Test
    fun `PATCH client allowed for GLOBAL holder`() {
        assertEquals(
            200,
            testServer.client
                .patch(
                    "/api/clients/$clientId",
                    mapOf("firstName" to "Renamed"),
                    asUser(globalHolder),
                ).code,
        )
    }

    @Test
    fun `PATCH client forbidden without grant`() {
        assertEquals(
            403,
            testServer.client.patch("/api/clients/$clientId", mapOf("firstName" to "Hacked"), asUser(noneUser)).code,
        )
    }

    @Test
    fun `PATCH client forbidden for BRANCH holder`() {
        assertEquals(
            403,
            testServer.client
                .patch(
                    "/api/clients/$clientId",
                    mapOf("firstName" to "Hacked"),
                    asUser(branchHolder),
                ).code,
        )
    }

    @Test
    fun `POST anonymize allowed for GLOBAL holder`() {
        assertEquals(
            204,
            testServer.client
                .post(
                    "/api/clients/$anonymizeClientId/anonymize",
                    emptyMap<String, String>(),
                    asUser(globalHolder),
                ).code,
        )
    }

    @Test
    fun `POST anonymize forbidden without grant`() {
        assertEquals(
            403,
            testServer.client
                .post(
                    "/api/clients/$anonymizeClientId/anonymize",
                    emptyMap<String, String>(),
                    asUser(noneUser),
                ).code,
        )
    }

    @Test
    fun `POST anonymize forbidden for BRANCH holder`() {
        assertEquals(
            403,
            testServer.client
                .post(
                    "/api/clients/$anonymizeClientId/anonymize",
                    emptyMap<String, String>(),
                    asUser(branchHolder),
                ).code,
        )
    }

    @Test
    fun `GET clients collection forbidden without grant`() {
        assertEquals(403, testServer.client.get("/api/clients?q=Test", asUser(noneUser)).code)
    }
}
