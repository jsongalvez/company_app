@file:Suppress("LargeClass")

package com.companyb.companyapp.api.routes

import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.JavalinTestServerRule
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
    private val editOnlyUser = UUID.randomUUID()
    private val manageOnlyUser = UUID.randomUUID()
    private val noneUser = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val otherBranchId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()

    override fun initTestData() {
        trackOwned(AppUserTable, AppUserTable.id, editOnlyUser)
        DatabaseTestHelper.insertTestUser(editOnlyUser, "edit-only")
        trackOwned(AppUserTable, AppUserTable.id, manageOnlyUser)
        DatabaseTestHelper.insertTestUser(manageOnlyUser, "manage-only")
        trackOwned(AppUserTable, AppUserTable.id, noneUser)
        DatabaseTestHelper.insertTestUser(noneUser, "no-caps")

        trackOwned(BranchTable, BranchTable.id, branchId)
        DatabaseTestHelper.insertTestBranch(branchId, "Authz Branch $branchId")
        trackOwned(BranchTable, BranchTable.id, otherBranchId)
        DatabaseTestHelper.insertTestBranch(otherBranchId, "Other Branch $otherBranchId")

        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

        DatabaseTestHelper.grantCapability(
            userId = editOnlyUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
        DatabaseTestHelper.grantCapability(
            userId = manageOnlyUser,
            capabilityCode = CapabilityCodes.MANAGE_PRODUCTS,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, editOnlyUser)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, manageOnlyUser)
    }

    companion object {
        private val DEFAULT_USER = UUID.randomUUID()

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
                    Database.connect(DatabaseTestHelper.requireTestDataSource())
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
        DatabaseTestHelper.createBranchDayForToday(branchId)
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
        val missingBranchId = UUID.randomUUID()
        DatabaseTestHelper.grantCapability(
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
