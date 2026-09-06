@file:Suppress("LargeClass")

package com.companyb.companyapp.commerce
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.exception.ForbiddenException
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
import java.time.LocalDate
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BranchInventoryAuthzTest : BasePostgresTest() {
    private val editOnlyUser = TestFixtures.uuid()
    private val manageOnlyUser = TestFixtures.uuid()
    private val noneUser = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private var branchDayId: UUID = TestFixtures.uuid()

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

        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        CommerceFinanceFixtures.insertTestCategory(categoryId)
        CommerceFinanceFixtures.insertTestProduct(productId, categoryId = categoryId)
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
                BranchInventoryRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun movementBody(
        reason: String,
        quantityChange: Int,
    ): Map<String, Any> =
        mapOf(
            "movementId" to TestFixtures.uuid().toString(),
            "reason" to reason,
            "quantityChange" to quantityChange,
            "branchDayId" to branchDayId.toString(),
            "expectedVersion" to 0,
        )

    // ──────────────────────────────────────────────
    // GET /api/branches/{branchId}/inventory → EDIT_BRANCH_DATA
    // ──────────────────────────────────────────────

    @Test
    fun `GET inventory allowed for EDIT_BRANCH_DATA user`() {
        testServer.client.let { client ->
            assertEquals(
                200,
                client.get("/api/branches/$branchId/inventory", asUser(editOnlyUser)).code,
            )
        }
    }

    @Test
    fun `GET inventory forbidden for MANAGE_PRODUCTS-only user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/branches/$branchId/inventory", asUser(manageOnlyUser)).code,
            )
        }
    }

    @Test
    fun `GET inventory forbidden for no-capability user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/branches/$branchId/inventory", asUser(noneUser)).code,
            )
        }
    }

    @Test
    fun `GET inventory forbidden for EDIT_BRANCH_DATA user on other branch`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/branches/$otherBranchId/inventory", asUser(editOnlyUser)).code,
            )
        }
    }

    // ──────────────────────────────────────────────
    // GET /api/branches/{branchId}/inventory/low-stock → EDIT_BRANCH_DATA
    // ──────────────────────────────────────────────

    @Test
    fun `GET low-stock allowed for EDIT_BRANCH_DATA user`() {
        testServer.client.let { client ->
            assertEquals(
                200,
                client.get("/api/branches/$branchId/inventory/low-stock", asUser(editOnlyUser)).code,
            )
        }
    }

    @Test
    fun `GET low-stock forbidden for MANAGE_PRODUCTS-only user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/branches/$branchId/inventory/low-stock", asUser(manageOnlyUser)).code,
            )
        }
    }

    @Test
    fun `GET low-stock forbidden for EDIT_BRANCH_DATA user on other branch`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/branches/$otherBranchId/inventory/low-stock", asUser(editOnlyUser)).code,
            )
        }
    }

    // ──────────────────────────────────────────────
    // POST /api/branches/{branchId}/inventory/{productId}/restock → MANAGE_PRODUCTS
    // ──────────────────────────────────────────────

    @Test
    fun `POST restock allowed for MANAGE_PRODUCTS user`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "quantity" to 10,
                    "branchDayId" to branchDayId.toString(),
                )
            val response =
                client.post(
                    "/api/branches/$branchId/inventory/$productId/restock",
                    body,
                    asUser(manageOnlyUser),
                )
            assertEquals(201, response.code)
        }
    }

    @Test
    fun `POST restock forbidden for EDIT_BRANCH_DATA-only user`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "quantity" to 10,
                    "branchDayId" to branchDayId.toString(),
                )
            assertEquals(
                403,
                client
                    .post(
                        "/api/branches/$branchId/inventory/$productId/restock",
                        body,
                        asUser(editOnlyUser),
                    ).code,
            )
        }
    }

    @Test
    fun `POST restock forbidden for MANAGE_PRODUCTS user on other branch`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "quantity" to 10,
                    "branchDayId" to branchDayId.toString(),
                )
            assertEquals(
                403,
                client
                    .post(
                        "/api/branches/$otherBranchId/inventory/$productId/restock",
                        body,
                        asUser(manageOnlyUser),
                    ).code,
            )
        }
    }

    // ──────────────────────────────────────────────
    // POST /api/branches/{branchId}/inventory/{productId}/movement
    // TESTER/SAMPLE/MISSING → EDIT_BRANCH_DATA; ADJUSTMENT → MANAGE_PRODUCTS
    // ──────────────────────────────────────────────

    @Test
    fun `POST movement TESTER allowed for EDIT_BRANCH_DATA user`() {
        testServer.client.let { client ->
            val restockBody =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "quantity" to 10,
                    "branchDayId" to branchDayId.toString(),
                )
            assertEquals(
                201,
                client
                    .post(
                        "/api/branches/$branchId/inventory/$productId/restock",
                        restockBody,
                        asUser(manageOnlyUser),
                    ).code,
            )
            val response =
                client.post(
                    "/api/branches/$branchId/inventory/$productId/movement",
                    movementBody("TESTER", -1),
                    asUser(editOnlyUser),
                )
            assertEquals(201, response.code)
        }
    }

    @Test
    fun `POST movement TESTER forbidden for MANAGE_PRODUCTS-only user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client
                    .post(
                        "/api/branches/$branchId/inventory/$productId/movement",
                        movementBody("TESTER", -1),
                        asUser(manageOnlyUser),
                    ).code,
            )
        }
    }

    @Test
    fun `POST movement TESTER forbidden for EDIT_BRANCH_DATA user on other branch`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client
                    .post(
                        "/api/branches/$otherBranchId/inventory/$productId/movement",
                        movementBody("TESTER", -1),
                        asUser(editOnlyUser),
                    ).code,
            )
        }
    }

    @Test
    fun `POST movement ADJUSTMENT allowed for MANAGE_PRODUCTS user`() {
        testServer.client.let { client ->
            val response =
                client.post(
                    "/api/branches/$branchId/inventory/$productId/movement",
                    movementBody("ADJUSTMENT", 5),
                    asUser(manageOnlyUser),
                )
            assertEquals(201, response.code)
        }
    }

    @Test
    fun `POST movement ADJUSTMENT forbidden for EDIT_BRANCH_DATA-only user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client
                    .post(
                        "/api/branches/$branchId/inventory/$productId/movement",
                        movementBody("ADJUSTMENT", 5),
                        asUser(editOnlyUser),
                    ).code,
            )
        }
    }

    // ──────────────────────────────────────────────
    // GET /api/branches/{branchId}/inventory/movements → EDIT_BRANCH_DATA
    // ──────────────────────────────────────────────

    @Test
    fun `GET movements allowed for EDIT_BRANCH_DATA user`() {
        testServer.client.let { client ->
            val restockBody =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "quantity" to 10,
                    "branchDayId" to branchDayId.toString(),
                )
            assertEquals(
                201,
                client
                    .post(
                        "/api/branches/$branchId/inventory/$productId/restock",
                        restockBody,
                        asUser(manageOnlyUser),
                    ).code,
            )
            assertEquals(
                200,
                client.get("/api/branches/$branchId/inventory/movements", asUser(editOnlyUser)).code,
            )
        }
    }

    @Test
    fun `GET movements returns movement payload for EDIT_BRANCH_DATA user`() {
        testServer.client.let { client ->
            val restockBody =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "quantity" to 10,
                    "branchDayId" to branchDayId.toString(),
                )
            assertEquals(
                201,
                client
                    .post(
                        "/api/branches/$branchId/inventory/$productId/restock",
                        restockBody,
                        asUser(manageOnlyUser),
                    ).code,
            )
            val body =
                client
                    .get("/api/branches/$branchId/inventory/movements", asUser(editOnlyUser))
                    .body
                    .string()
                    .orEmpty()
            assertTrue(body.contains("RESTOCK"))
            assertTrue(body.contains("quantityChange"))
        }
    }

    @Test
    fun `GET movements forbidden for MANAGE_PRODUCTS-only user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/branches/$branchId/inventory/movements", asUser(manageOnlyUser)).code,
            )
        }
    }

    @Test
    fun `GET movements forbidden for no-capability user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/branches/$branchId/inventory/movements", asUser(noneUser)).code,
            )
        }
    }

    @Test
    fun `GET movements forbidden for EDIT_BRANCH_DATA user on other branch`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/branches/$otherBranchId/inventory/movements", asUser(editOnlyUser)).code,
            )
        }
    }

    @Test
    fun `GET movements with valid date returns 200`() {
        testServer.client.let { client ->
            val restockBody =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "quantity" to 10,
                    "branchDayId" to branchDayId.toString(),
                )
            assertEquals(
                201,
                client
                    .post(
                        "/api/branches/$branchId/inventory/$productId/restock",
                        restockBody,
                        asUser(manageOnlyUser),
                    ).code,
            )
            val today = TestFixtures.today
            val response =
                client
                    .get(
                        "/api/branches/$branchId/inventory/movements?date=$today",
                        asUser(editOnlyUser),
                    )
            assertEquals(200, response.code)
            assertTrue(
                response.body
                    .string()
                    .orEmpty()
                    .contains("RESTOCK"),
            )
        }
    }

    @Test
    fun `GET movements with invalid date returns 400`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client
                    .get(
                        "/api/branches/$branchId/inventory/movements?date=not-a-date",
                        asUser(editOnlyUser),
                    ).code,
            )
        }
    }

    // ──────────────────────────────────────────────
    // POST /api/branches/{branchId}/inventory (ensureCard) → MANAGE_PRODUCTS
    // ──────────────────────────────────────────────

    @Test
    fun `POST ensureCard allowed for MANAGE_PRODUCTS user`() {
        testServer.client.let { client ->
            val response =
                client.post(
                    "/api/branches/$branchId/inventory",
                    mapOf("productId" to productId.toString()),
                    asUser(manageOnlyUser),
                )
            assertEquals(201, response.code)
        }
    }

    @Test
    fun `POST ensureCard forbidden for EDIT_BRANCH_DATA-only user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client
                    .post(
                        "/api/branches/$branchId/inventory",
                        mapOf("productId" to productId.toString()),
                        asUser(editOnlyUser),
                    ).code,
            )
        }
    }
}
