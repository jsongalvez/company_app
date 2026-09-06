@file:Suppress("LargeClass")

package com.companyb.companyapp.session
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.session.ConcernTable
import com.companyb.companyapp.session.SessionConcernTable
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
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #304 — session-concern DELETE authorization. The DELETE child path
 * `/api/sessions/{sessionId}/concerns/{concernId}` must gate on the same
 * BRANCH-or-BRANCH_DAY EDIT_BRANCH_DATA capability as concern GET/POST and the
 * promote flow: Javalin path filters match exact literals, so the parent
 * `/concerns` filter never fires for the child path. Unauthorized OPEN-day
 * callers get 403; branch holders and Branch Day relief grants retain deletion.
 */
class SessionConcernDeleteAuthzTest : BasePostgresTest() {
    private val editorUser = TestFixtures.uuid()
    private val reliefUser = TestFixtures.uuid()
    private val noGrantUser = TestFixtures.uuid()
    private val wrongBranchUser = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val sessionId = TestFixtures.uuid()
    private val concernId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    private lateinit var branchDayId: UUID

    override fun initTestData() {
        IdentityFixtures.insertTestUser(editorUser, "concern-editor")
        IdentityFixtures.insertTestUser(reliefUser, "concern-relief")
        IdentityFixtures.insertTestUser(noGrantUser, "concern-no-grant")
        IdentityFixtures.insertTestUser(wrongBranchUser, "concern-wrong-branch")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Concern Branch A")
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Concern Branch B")
        SessionClientFixtures.insertTestClient(clientId)

        // OPEN day dated today (evaluateStatus keeps it OPEN).
        branchDayId = BranchDayService.resolveOrCreate(branchId, TestFixtures.today).id
        SessionClientFixtures.insertTestSession(sessionId, clientId, branchDayId)
        transaction {
            ConcernTable.insert {
                it[ConcernTable.id] = concernId
                it[ConcernTable.label] = "Knee Pain"
            }
        }
        insertSessionConcern(sessionId, concernId)

        // editorUser: BRANCH grant at the session's branch.
        IdentityFixtures.grantCapability(
            userId = editorUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
        // reliefUser: BRANCH_DAY grant for the session's exact day (the #157 relief form).
        IdentityFixtures.grantCapability(
            userId = reliefUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH_DAY,
            contextId = branchDayId,
            sourceId = sourceId,
        )
        // wrongBranchUser: BRANCH grant at a different branch.
        IdentityFixtures.grantCapability(
            userId = wrongBranchUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = otherBranchId,
            sourceId = sourceId,
        )
        // noGrantUser: deliberately no grant.
    }

    @Test
    fun `delete concern forbidden without grant`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.delete("/api/sessions/$sessionId/concerns/$concernId", null, asUser(noGrantUser)).code,
            )
            assertTrue(concernLinkExists(), "link must survive a 403")
        }
    }

    @Test
    fun `delete concern forbidden with wrong-branch grant`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.delete("/api/sessions/$sessionId/concerns/$concernId", null, asUser(wrongBranchUser)).code,
            )
        }
    }

    @Test
    fun `delete concern allowed with branch grant`() {
        testServer.client.let { client ->
            val response = client.delete("/api/sessions/$sessionId/concerns/$concernId", null, asUser(editorUser))
            assertEquals(204, response.code, response.body.string().orEmpty())
            assertTrue(!concernLinkExists(), "link must be gone after 204")
        }
    }

    @Test
    fun `delete concern allowed with branch-day relief grant`() {
        testServer.client.let { client ->
            val response = client.delete("/api/sessions/$sessionId/concerns/$concernId", null, asUser(reliefUser))
            assertEquals(204, response.code, response.body.string().orEmpty())
            assertTrue(!concernLinkExists(), "link must be gone after 204")
        }
    }

    // #128 lesson — the X-Test-User harness can't exercise the real auth filter; 401 needs
    // the real-JWT harness (the #147/ReportsReadScopeAuthzTest precedent).
    @Test
    fun `unauthenticated delete concern gets 401`() {
        testServer.client.let { client ->
            assertEquals(401, client.delete("/api/sessions/$sessionId/concerns/$concernId").code)
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun insertSessionConcern(
        session: UUID,
        concern: UUID,
    ) {
        transaction {
            SessionConcernTable.insert {
                it[SessionConcernTable.sessionId] = session
                it[SessionConcernTable.concernId] = concern
            }
        }
    }

    private fun concernLinkExists(): Boolean =
        transaction {
            SessionConcernTable
                .selectAll()
                .where {
                    (SessionConcernTable.sessionId eq sessionId) and (SessionConcernTable.concernId eq concernId)
                }.empty()
                .not()
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
}
