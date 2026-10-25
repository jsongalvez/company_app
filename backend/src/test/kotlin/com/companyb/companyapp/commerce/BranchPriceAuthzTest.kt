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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 503bf228, a6946922 — any member who can edit a branch sets each product's own price and commission there. */
class BranchPriceAuthzTest : BasePostgresTest() {
    private val member = TestFixtures.uuid()
    private val outsider = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()
    private val otherProductId = TestFixtures.uuid()

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
        CommerceFinanceFixtures.insertTestProduct(otherProductId, categoryId = categoryId)
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

    private fun putFor(
        user: UUID,
        branch: UUID,
        product: UUID,
        body: Map<String, String?>,
    ): Int = testServer.client.put("/api/branches/$branch/inventory/$product/price", body, asUser(user)).code

    private fun cards(): Map<UUID, Pair<String, String>> =
        Json
            .parseToJsonElement(inventory())
            .jsonArray
            .associate {
                val row = it.jsonObject
                UUID.fromString(row.getValue("productId").jsonPrimitive.content) to
                    (
                        row.getValue("unitPrice").jsonPrimitive.content to
                            row.getValue("commissionAmount").jsonPrimitive.content
                    )
            }

    private fun inventory(): String =
        testServer.client
            .get("/api/branches/$branchId/inventory", asUser(member))
            .body
            .string()

    @Test
    fun `each product keeps its own price and commission at the branch`() {
        assertEquals(204, put(member, branchId, mapOf("unitPrice" to "300", "commissionAmount" to "40")))
        assertEquals(204, putFor(member, branchId, otherProductId, mapOf("unitPrice" to "55")))
        val rows = cards()
        assertEquals("300.00" to "40.00", rows.getValue(productId))
        assertEquals("55.00" to "10.00", rows.getValue(otherProductId))
        assertEquals(204, put(member, branchId, mapOf("commissionAmount" to "7.50")))
        assertEquals("300.00" to "7.50", cards().getValue(productId))
    }

    @Test
    fun `a negative commission is refused and only branch editors set it`() {
        assertTrue(put(member, branchId, mapOf("commissionAmount" to "-1")) in 400..499)
        assertEquals(403, put(outsider, branchId, mapOf("commissionAmount" to "1")))
        assertEquals(403, put(member, otherBranchId, mapOf("commissionAmount" to "1")))
    }

    @Test
    fun `member sets a branch price and the inventory shows it`() {
        assertEquals(204, put(member, branchId, mapOf("unitPrice" to "777.50")))
        val shown =
            testServer.client
                .get("/api/branches/$branchId/inventory", asUser(member))
                .body
                .string()
        assertTrue(shown.contains("\"unitPrice\":\"777.50\""), shown)
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
