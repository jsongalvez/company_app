// #600 scenario coverage stays whole (same precedent as BranchInventoryAuthzTest #597).
@file:Suppress("LargeClass") // #600

package com.companyb.companyapp.branchday
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.branchday.DayStatus
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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BranchDayAuthzTest : BasePostgresTest() {
    private val editOnlyUser = TestFixtures.uuid()
    private val manageOnlyUser = TestFixtures.uuid()
    private val noneUser = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(editOnlyUser, "edit-only")
        IdentityFixtures.insertTestUser(manageOnlyUser, "manage-only")
        IdentityFixtures.insertTestUser(noneUser, "no-caps")

        BranchWorkforceFixtures.insertTestBranch(branchId, "Authz Branch $branchId")
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Other Branch $otherBranchId")

        IdentityFixtures.grantCapability(
            userId = editOnlyUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
        IdentityFixtures.grantCapability(
            userId = manageOnlyUser,
            capabilityCode = CapabilityCodes.MANAGE_PRODUCTS,
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
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(mapOf("error" to (e.message ?: "Forbidden")))
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(mapOf("error" to (e.message ?: "Not Found")))
                }
                BranchDayRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    // ──────────────────────────────────────────────
    // GET /api/branches/{branchId}/today → EDIT_BRANCH_DATA
    // ──────────────────────────────────────────────

    @Test
    fun `GET today allowed for EDIT_BRANCH_DATA user`() {
        testServer.client.let { client ->
            assertEquals(
                200,
                client.get("/api/branches/$branchId/today", asUser(editOnlyUser)).code,
            )
        }
    }

    @Test
    fun `GET today returns branchDayId and OPEN status payload`() {
        testServer.client.let { client ->
            val body =
                client
                    .get("/api/branches/$branchId/today", asUser(editOnlyUser))
                    .body
                    .string()
                    .orEmpty()
            assertTrue(body.contains("branchDayId"))
            assertTrue(body.contains("OPEN"))
        }
    }

    @Test
    fun `GET today returns REMITTED status for remitted day`() {
        BranchWorkforceFixtures.createBranchDayForToday(branchId)
        transaction {
            BranchDayTable.update({ BranchDayTable.branchId eq branchId }) {
                it[BranchDayTable.status] = DayStatus.REMITTED
            }
        }
        testServer.client.let { client ->
            val body =
                client
                    .get("/api/branches/$branchId/today", asUser(editOnlyUser))
                    .body
                    .string()
                    .orEmpty()
            assertTrue(body.contains("REMITTED"))
        }
    }

    @Test
    fun `GET today forbidden for MANAGE_PRODUCTS-only user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/branches/$branchId/today", asUser(manageOnlyUser)).code,
            )
        }
    }

    @Test
    fun `GET today forbidden for no-capability user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/branches/$branchId/today", asUser(noneUser)).code,
            )
        }
    }

    @Test
    fun `GET today forbidden for EDIT_BRANCH_DATA user on other branch`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/branches/$otherBranchId/today", asUser(editOnlyUser)).code,
            )
        }
    }

    @Test
    fun `GET today returns 404 for missing branch with grant`() {
        val missingBranchId = TestFixtures.uuid()
        IdentityFixtures.grantCapability(
            userId = editOnlyUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = missingBranchId,
            sourceId = sourceId,
        )
        testServer.client.let { client ->
            assertEquals(
                404,
                client.get("/api/branches/$missingBranchId/today", asUser(editOnlyUser)).code,
            )
        }
    }
}
