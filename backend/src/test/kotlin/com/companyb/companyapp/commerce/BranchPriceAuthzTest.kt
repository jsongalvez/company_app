package com.companyb.companyapp.commerce

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
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
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

/** 503bf228 — any member who can edit a branch sets its own product price and commission. */
class BranchPriceAuthzTest : BasePostgresTest() {
    private val member = TestFixtures.uuid()
    private val outsider = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(member, "price-member")
        IdentityFixtures.insertTestUser(outsider, "price-outsider")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Price Branch $branchId")
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Other Branch $otherBranchId")
        IdentityFixtures.grantCapability(
            userId = member,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = TestFixtures.uuid(),
        )
        CommerceFinanceFixtures.insertTestCategory(categoryId)
        CommerceFinanceFixtures.insertTestProduct(productId, categoryId = categoryId)
    }

    companion object {
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
                    ctx.attribute("userId", ctx.header("X-Test-User") ?: TestFixtures.uuid().toString())
                }
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(mapOf("error" to (e.message ?: "Forbidden")))
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(mapOf("error" to (e.message ?: "Not Found")))
                }
                BranchInventoryRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun put(
        user: UUID,
        branch: UUID,
        body: Map<String, String?>,
    ): Int = testServer.client.put("/api/branches/$branch/inventory/$productId/price", body, asUser(user)).code

    @Test
    fun `member sets a branch price, the inventory shows it, and shared puts it back`() {
        assertEquals(204, put(member, branchId, mapOf("unitPrice" to "777.50", "commissionAmount" to "12")))
        val shown =
            testServer.client
                .get("/api/branches/$branchId/inventory", asUser(member))
                .body!!
                .string()
        assertTrue(shown.contains("\"unitPrice\":\"777.50\""), shown)
        assertTrue(shown.contains("\"branchCommissionAmount\":\"12"), shown)

        assertEquals(204, put(member, branchId, mapOf()))
        val back =
            testServer.client
                .get("/api/branches/$branchId/inventory", asUser(member))
                .body!!
                .string()
        assertTrue(!back.contains("777.50") && !back.contains("\"branchUnitPrice\":\""), back)
    }

    @Test
    fun `someone outside the branch cannot set its price`() {
        assertEquals(403, put(outsider, branchId, mapOf("unitPrice" to "1")))
        assertEquals(403, put(member, otherBranchId, mapOf("unitPrice" to "1")))
    }

    @Test
    fun `unknown branch is 404 before the capability check`() {
        assertEquals(404, put(outsider, TestFixtures.uuid(), mapOf("unitPrice" to "1")))
    }

    @Test
    fun `a negative price is refused`() {
        assertTrue(put(member, branchId, mapOf("unitPrice" to "-5")) in 400..499)
    }
}
