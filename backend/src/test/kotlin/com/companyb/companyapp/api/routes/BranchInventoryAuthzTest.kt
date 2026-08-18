@file:Suppress("LargeClass")

package com.companyb.companyapp.api.routes

import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.JavalinTestServerRule
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
    private val editOnlyUser = UUID.randomUUID()
    private val manageOnlyUser = UUID.randomUUID()
    private val noneUser = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val otherBranchId = UUID.randomUUID()
    private val categoryId = UUID.randomUUID()
    private val productId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private var branchDayId: UUID = UUID.randomUUID()

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

        branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

        trackOwned(ProductCategoryTable, ProductCategoryTable.id, categoryId)
        DatabaseTestHelper.insertTestCategory(categoryId)
        trackOwned(ProductTable, ProductTable.id, productId)
        DatabaseTestHelper.insertTestProduct(productId, categoryId = categoryId)
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
            "movementId" to UUID.randomUUID().toString(),
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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, manageOnlyUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, manageOnlyUser)
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
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
                    "id" to UUID.randomUUID().toString(),
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
                    "id" to UUID.randomUUID().toString(),
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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, manageOnlyUser)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, editOnlyUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, manageOnlyUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, editOnlyUser)
        testServer.client.let { client ->
            val restockBody =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, manageOnlyUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, manageOnlyUser)
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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, manageOnlyUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, manageOnlyUser)
        testServer.client.let { client ->
            val restockBody =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, manageOnlyUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, manageOnlyUser)
        testServer.client.let { client ->
            val restockBody =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
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
                    ?.string()
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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, manageOnlyUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, manageOnlyUser)
        testServer.client.let { client ->
            val restockBody =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
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
            val today = LocalDate.now(BranchDayService.manilaZone)
            val response =
                client
                    .get(
                        "/api/branches/$branchId/inventory/movements?date=$today",
                        asUser(editOnlyUser),
                    )
            assertEquals(200, response.code)
            assertTrue(
                response.body
                    ?.string()
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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, manageOnlyUser)
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
