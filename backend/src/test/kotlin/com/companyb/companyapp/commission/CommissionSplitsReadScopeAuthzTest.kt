package com.companyb.companyapp.commission

import com.companyb.companyapp.api.ApiRoutes
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
import io.javalin.http.UnauthorizedResponse
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Commission splits read scope (owed #26): "Only members of the same branch can see their
 * commisions." GET /commission-splits/{branchDayId} needs VIEW_COMMISSION at the day's branch,
 * which membership gives (active_user_capabilities leg (d)). Seeing the branch is not enough,
 * at that branch or everywhere. Gate-pass on an existing day is 200; gate-block is 403.
 */
class CommissionSplitsReadScopeAuthzTest : BasePostgresTest() {
    private val memberUser = TestFixtures.uuid()
    private val branchViewUser = TestFixtures.uuid()
    private val globalViewUser = TestFixtures.uuid()
    private val noneUser = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    private lateinit var branchDayId: UUID

    override fun initTestData() {
        IdentityFixtures.insertTestUser(memberUser, "commission-member")
        IdentityFixtures.insertTestUser(branchViewUser, "commission-branch-view")
        IdentityFixtures.insertTestUser(globalViewUser, "commission-global-view")
        IdentityFixtures.insertTestUser(noneUser, "commission-no-caps")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Commission Read Scope Branch")
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        IdentityFixtures.grantCapability(
            userId = memberUser,
            capabilityCode = CapabilityCodes.VIEW_COMMISSION,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
        IdentityFixtures.grantCapability(
            userId = branchViewUser,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
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
        private val defaultUser = TestFixtures.uuid()

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
                    ctx.attribute("userId", ctx.header("X-Test-User") ?: defaultUser.toString())
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
                CommissionRoutes.register(cfg)
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
    fun `a member of the branch reads splits`() {
        assertEquals(200, getStatus(memberUser, "/api/commission-splits/$branchDayId"))
    }

    @Test
    fun `seeing the branch is not enough`() {
        assertEquals(403, getStatus(branchViewUser, "/api/commission-splits/$branchDayId"))
    }

    @Test
    fun `seeing every branch is not enough`() {
        assertEquals(403, getStatus(globalViewUser, "/api/commission-splits/$branchDayId"))
    }

    @Test
    fun `zero-grant caller is blocked`() {
        assertEquals(403, getStatus(noneUser, "/api/commission-splits/$branchDayId"))
    }

    @Test
    fun `missing branch day is 404`() {
        assertEquals(404, getStatus(memberUser, "/api/commission-splits/${TestFixtures.uuid()}"))
    }
}
