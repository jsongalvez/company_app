package com.companyb.companyapp.branch

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.authorization.CapabilityService
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
import io.javalin.Javalin
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #656: GET /api/branches/{branchId} had no capability gate — the exact-segment
 * before(BRANCHES) filter (#114/#653 lesson) never fires on the 3-segment item
 * path, so any authenticated caller could read any branch by UUID, bypassing
 * the BranchReadScope window (a zero-grant caller gets an empty
 * accessible-list yet could fetch any branch directly). The item read now gates
 * on the #131 window (BRANCH or GLOBAL VIEW_BRANCH_DATA, mirroring the
 * summary/browse reads).
 *
 * Gate-block is proven by 403; gate-pass by 200 with data (or 404 on a missing
 * branch — the handler ran after the gate passed).
 */
class BranchReadAuthzTest : BasePostgresTest() {
    private val branchViewUser = TestFixtures.uuid()
    private val globalViewUser = TestFixtures.uuid()
    private val noneUser = TestFixtures.uuid()
    private val branchA = TestFixtures.uuid()
    private val branchB = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    override fun initTestData() {
        listOf(
            branchViewUser to "branchread-view",
            globalViewUser to "branchread-global",
            noneUser to "branchread-none",
        ).forEach { (id, prefix) ->
            IdentityFixtures.insertTestUser(id, prefix)
        }

        BranchWorkforceFixtures.insertTestBranch(branchA, "Branch Read A")
        BranchWorkforceFixtures.insertTestBranch(branchB, "Branch Read B")

        IdentityFixtures.grantCapability(
            userId = branchViewUser,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchA,
            sourceId = sourceId,
        )
        IdentityFixtures.grantCapability(
            userId = globalViewUser,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
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
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(mapOf("error" to (e.message ?: "Forbidden")))
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(mapOf("error" to (e.message ?: "Not Found")))
                }
                BranchRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun getStatus(
        user: UUID,
        path: String,
    ): Int {
        var status = 0
        testServer.client.let { client ->
            status = client.get(path, asUser(user)).code
        }
        return status
    }

    @Test
    fun `zero-grant caller is blocked on the item read`() {
        assertEquals(403, getStatus(noneUser, "/api/branches/$branchA"))
    }

    @Test
    fun `branch-scoped holder reads the granted branch`() {
        var status = 0
        var body = ""
        testServer.client.let { client ->
            val response = client.get("/api/branches/$branchA", asUser(branchViewUser))
            status = response.code
            body = response.body.string()
        }
        assertEquals(200, status)
        assertTrue(body.contains("Branch Read A"))
    }

    @Test
    fun `branch-scoped holder is blocked on another branch`() {
        assertEquals(403, getStatus(branchViewUser, "/api/branches/$branchB"))
    }

    @Test
    fun `global holder reads a branch with no grants`() {
        assertEquals(200, getStatus(globalViewUser, "/api/branches/$branchB"))
    }

    @Test
    fun `global holder passes the gate on a missing branch`() {
        assertEquals(404, getStatus(globalViewUser, "/api/branches/${TestFixtures.uuid()}"))
    }

    @Test
    fun `accessible picker stays open beside the item gate`() {
        assertEquals(200, getStatus(noneUser, "/api/branches/accessible"))
    }

    @Test
    fun `unknown branch item read is 404 not 403`() {
        val unknown = TestFixtures.uuid()
        assertEquals(404, getStatus(branchViewUser, "/api/branches/$unknown"))
        assertEquals(404, getStatus(noneUser, "/api/branches/$unknown"))
    }
}
