package com.companyb.companyapp.session
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.exception.ConflictException
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
import io.javalin.http.UnauthorizedResponse
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Session inline-edit PATCH authz (#149): the status + final-price endpoints gate on
 * BRANCH-scoped EDIT_BRANCH_DATA like the existing status PATCH (strict context — never
 * the GLOBAL-or-branch relaxation, the #131 leak class). Exact-path-gate discipline: each
 * before-filter must fire on its own literal path.
 */
class SessionEditAuthzTest : BasePostgresTest() {
    private val editorUser = TestFixtures.uuid()
    private val noGrantUser = TestFixtures.uuid()
    private val coordinatorUser = TestFixtures.uuid()
    private val wrongBranchUser = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val sessionId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(editorUser, "session-editor")
        IdentityFixtures.insertTestUser(noGrantUser, "session-no-grant")
        IdentityFixtures.insertTestUser(coordinatorUser, "session-coordinator")
        IdentityFixtures.insertTestUser(wrongBranchUser, "session-wrong-branch")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Branch A")
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Branch B")
        SessionClientFixtures.insertTestClient(clientId)

        val branchDay =
            BranchDayService.resolveOrCreate(branchId, TestFixtures.today)
        SessionClientFixtures.insertTestSession(
            sessionId,
            clientId,
            branchDay.id,
            sessionStatus = SessionStatus.NO_SHOW,
        )

        IdentityFixtures.grantCapability(
            userId = editorUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
        IdentityFixtures.grantCapability(
            userId = wrongBranchUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = otherBranchId,
            sourceId = sourceId,
        )
        IdentityFixtures.grantCapability(
            userId = coordinatorUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
        IdentityFixtures.grantCapability(
            userId = coordinatorUser,
            capabilityCode = CapabilityCodes.EDIT_PAST_DAY,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
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
                cfg.routes.before("${ApiRoutes.API_PREFIX}*") { ctx ->
                    if (ctx.header("X-Test-User") == null) {
                        val token = ctx.header("Authorization")?.removePrefix("Bearer ") ?: throw UnauthorizedResponse()
                        ctx.attribute("userId", JwtService.verifyToken(token) ?: throw UnauthorizedResponse())
                    }
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
                SessionRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    @Test
    fun `granted user patches final price`() {
        testServer.client.let { client ->
            val body = mapOf("finalPrice" to "2750.00", "version" to 1)
            val response = client.patch("/api/sessions/$sessionId/final-price", body, asUser(editorUser))

            assertEquals(200, response.code)
            val responseBody = response.body.string().orEmpty()
            assertTrue(responseBody.contains("\"finalPrice\":\"2750.00\""))
            assertTrue(responseBody.contains("\"version\":2"))
        }
    }

    @Test
    fun `final price patch forbidden without grant`() {
        testServer.client.let { client ->
            val body = mapOf("finalPrice" to "2750.00", "version" to 1)
            assertEquals(403, client.patch("/api/sessions/$sessionId/final-price", body, asUser(noGrantUser)).code)
        }
    }

    @Test
    fun `final price patch negative value gets 400`() {
        testServer.client.let { client ->
            val body = mapOf("finalPrice" to "-100.00", "version" to 1)
            assertEquals(400, client.patch("/api/sessions/$sessionId/final-price", body, asUser(editorUser)).code)
        }
    }

    @Test
    fun `status correction is forbidden without Coordinator capability`() {
        testServer.client.let { client ->
            val body = mapOf("status" to "PENDING", "version" to 1)
            assertEquals(403, client.patch("/api/sessions/$sessionId/status", body, asUser(editorUser)).code)
        }
    }

    @Test
    fun `Coordinator can patch status correction`() {
        testServer.client.let { client ->
            val body = mapOf("status" to "PENDING", "version" to 1)
            val response = client.patch("/api/sessions/$sessionId/status", body, asUser(coordinatorUser))

            assertEquals(200, response.code)
            assertTrue(
                response.body
                    .string()
                    .orEmpty()
                    .contains("\"sessionStatus\":\"PENDING\""),
            )
        }
    }

    // #128 lesson — the X-Test-User harness can't exercise the real auth filter; 401 needs
    // the real-JWT harness (the #147/ReportsReadScopeAuthzTest precedent).
    @Test
    fun `unauthenticated request gets 401`() {
        testServer.client.let { client ->
            val body = mapOf("status" to "COMPLETED", "version" to 1)
            assertEquals(401, client.patch("/api/sessions/$sessionId/status", body).code)
        }
    }
}
