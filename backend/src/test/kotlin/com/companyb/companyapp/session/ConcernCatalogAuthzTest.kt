package com.companyb.companyapp.session

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
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
import io.javalin.http.NotFoundResponse
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #660: the concern catalog list (GET /api/concerns) and the session-anchored
 * promote write (POST /api/sessions/{sessionId}/promote-concern) gate on the same
 * EDIT_BRANCH_DATA capability with unioned contexts. A BRANCH holder lists and
 * promotes; a BRANCH_DAY relief holder lists and promotes at the granted day; a
 * GLOBAL-only holder lists but never promotes (#131 strictness); a grantless
 * caller gets 403 on both.
 */
class ConcernCatalogAuthzTest : BasePostgresTest() {
    private val branchUser = TestFixtures.uuid()
    private val globalUser = TestFixtures.uuid()
    private val reliefUser = TestFixtures.uuid()
    private val noneUser = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val sessionId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private var branchDayId: UUID = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(branchUser, "concern-branch")
        IdentityFixtures.insertTestUser(globalUser, "concern-global")
        IdentityFixtures.insertTestUser(reliefUser, "concern-relief")
        IdentityFixtures.insertTestUser(noneUser, "concern-none")
        BranchWorkforceFixtures.insertTestBranch(branchId)
        IdentityFixtures.grantCapability(
            userId = branchUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
        IdentityFixtures.grantEditBranchData(globalUser, sourceId)
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        IdentityFixtures.grantCapability(
            userId = reliefUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH_DAY,
            contextId = branchDayId,
            sourceId = sourceId,
        )
        SessionClientFixtures.insertTestClient(clientId)
        SessionClientFixtures.insertTestSession(sessionId, clientId, branchDayId)
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
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(mapOf("error" to (e.message ?: "Forbidden")))
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(mapOf("error" to (e.message ?: "Not Found")))
                }
                cfg.routes.exception(NotFoundResponse::class.java) { e, ctx ->
                    ctx.status(404).json(mapOf("error" to (e.message ?: "Not Found")))
                }
                SessionRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun promoteBody(label: String): Map<String, String> =
        mapOf(
            "id" to TestFixtures.uuid().toString(),
            "label" to label,
        )

    @Test
    fun `GET concerns allowed for BRANCH EDIT holder`() {
        assertEquals(200, testServer.client.get("/api/concerns", asUser(branchUser)).code)
    }

    @Test
    fun `GET concerns allowed for GLOBAL EDIT holder`() {
        assertEquals(200, testServer.client.get("/api/concerns", asUser(globalUser)).code)
    }

    @Test
    fun `GET concerns allowed for BRANCH_DAY relief holder`() {
        assertEquals(200, testServer.client.get("/api/concerns", asUser(reliefUser)).code)
    }

    @Test
    fun `GET concerns forbidden without grant`() {
        assertEquals(403, testServer.client.get("/api/concerns", asUser(noneUser)).code)
    }

    @Test
    fun `POST promote-concern allowed for BRANCH holder at the session branch`() {
        val response =
            testServer.client.post(
                "/api/sessions/$sessionId/promote-concern",
                promoteBody("Branch Pain"),
                asUser(branchUser),
            )
        assertEquals(201, response.code)
    }

    @Test
    fun `POST promote-concern allowed for BRANCH_DAY relief holder at the granted day`() {
        val response =
            testServer.client.post(
                "/api/sessions/$sessionId/promote-concern",
                promoteBody("Relief Pain"),
                asUser(reliefUser),
            )
        assertEquals(201, response.code)
    }

    @Test
    fun `POST promote-concern forbidden for GLOBAL holder without a branch grant`() {
        val response =
            testServer.client.post(
                "/api/sessions/$sessionId/promote-concern",
                promoteBody("Global Pain"),
                asUser(globalUser),
            )
        assertEquals(403, response.code)
    }

    @Test
    fun `POST promote-concern forbidden without grant`() {
        val response =
            testServer.client.post(
                "/api/sessions/$sessionId/promote-concern",
                promoteBody("No Grant Pain"),
                asUser(noneUser),
            )
        assertEquals(403, response.code)
    }
}
