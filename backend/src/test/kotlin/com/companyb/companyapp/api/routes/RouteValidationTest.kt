@file:Suppress("LargeClass")

package com.companyb.companyapp.api.routes

import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.CompensationTable
import com.companyb.companyapp.repository.model.ExpenseCategory
import com.companyb.companyapp.repository.model.ExpenseTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.RemittanceMethod
import com.companyb.companyapp.repository.model.RemittanceStatus
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.repository.model.RemittanceType
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.CapabilityService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.Javalin
import io.javalin.config.JavalinConfig
import io.javalin.testtools.JavalinTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteValidationTest : BasePostgresTest() {
    private val testUserId = TEST_USER_ID
    private val testBranchId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private var testBranchDayId: UUID = UUID.randomUUID()
    private val testCategoryId = UUID.randomUUID()
    private val testProductId = UUID.randomUUID()
    private val testClientId = UUID.randomUUID()
    private val testSessionId = UUID.randomUUID()

    override fun initTestData() {
        trackOwned(AppUserTable, AppUserTable.id, testUserId)
        DatabaseTestHelper.insertTestUser(testUserId, "route-test")
        trackOwned(BranchTable, BranchTable.id, testBranchId)
        DatabaseTestHelper.insertTestBranch(testBranchId, "Route Test Branch $testBranchId")
        val allCodes =
            listOf(
                CapabilityCodes.VIEW_BRANCH_DATA,
                CapabilityCodes.EDIT_BRANCH_DATA,
                CapabilityCodes.EDIT_PAST_DAY,
                CapabilityCodes.VOID_SESSION,
                CapabilityCodes.SUBMIT_REMITTANCE,
                CapabilityCodes.ASSIGN_COMPENSATION,
                CapabilityCodes.MANAGE_PRODUCTS,
                CapabilityCodes.MANAGE_USERS,
                CapabilityCodes.ASSIGN_DELEGATE,
            )
        for (code in allCodes) {
            DatabaseTestHelper.grantCapability(
                userId = testUserId,
                capabilityCode = code,
                contextType = CapabilityContextType.BRANCH,
                contextId = testBranchId,
                sourceId = sourceId,
            )
            DatabaseTestHelper.grantCapability(
                userId = testUserId,
                capabilityCode = code,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
                sourceId = sourceId,
            )
        }
        testBranchDayId = DatabaseTestHelper.createBranchDayForToday(testBranchId)
        trackOwned(BranchDayTable, BranchDayTable.id, testBranchDayId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, testUserId)
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, testCategoryId)
        DatabaseTestHelper.insertTestCategory(testCategoryId)
        trackOwned(ProductTable, ProductTable.id, testProductId)
        DatabaseTestHelper.insertTestProduct(testProductId, categoryId = testCategoryId)
        trackOwned(ClientTable, ClientTable.id, testClientId)
        DatabaseTestHelper.insertTestClient(testClientId)
        trackOwned(SessionTable, SessionTable.id, testSessionId)
        DatabaseTestHelper.insertTestSession(
            id = testSessionId,
            clientId = testClientId,
            branchDayId = testBranchDayId,
        )
    }

    companion object {
        val TEST_USER_ID = UUID.randomUUID()

        fun createApp(): Javalin {
            val config = AppConfig.parse()
            JwtService.init(config)
            Password.init(config.authDummyPassword)

            return Javalin.create { cfg ->
                cfg.jsonMapper(KotlinxSerializationMapper())
                cfg.routes.before { ctx ->
                    Database.connect(DatabaseTestHelper.requireTestDataSource())
                    ctx.attribute("userId", TEST_USER_ID.toString())
                }
                cfg.routes.exception(ValidationException::class.java) { e, ctx ->
                    ctx.status(400).json(mapOf("error" to (e.message ?: "Bad Request")))
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
                registerAllRoutes(cfg)
            }
        }

        private fun registerAllRoutes(config: JavalinConfig) {
            AllowanceRoutes.register(config)
            CompensationRoutes.register(config)
            SessionRoutes.register(config)
            ClientRoutes.register(config)
            ProductRoutes.register(config)
            BranchInventoryRoutes.register(config)
            ProductSaleRoutes.register(config)
            UserBranchAssignmentRoutes.register(config)
            RemittanceRoutes.register(config)
            ExportRoutes.register(config)
            ExpenseRoutes.register(config)
        }
    }

    // ──────────────────────────────────────────────
    // AllowanceRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST allowance negative amount returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "branchDayId" to testBranchDayId.toString(),
                    "userId" to UUID.randomUUID().toString(),
                    "amount" to "-1.00",
                )
            assertEquals(400, client.post("/api/allowances", body).code)
        }
    }

    @Test
    fun `POST allowance invalid amount string returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "branchDayId" to testBranchDayId.toString(),
                    "userId" to UUID.randomUUID().toString(),
                    "amount" to "not-a-number",
                )
            assertEquals(400, client.post("/api/allowances", body).code)
        }
    }

    @Test
    fun `POST allowance invalid branchDayId UUID returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "branchDayId" to "not-a-uuid",
                    "userId" to UUID.randomUUID().toString(),
                    "amount" to "100.00",
                )
            assertEquals(400, client.post("/api/allowances", body).code)
        }
    }

    // ──────────────────────────────────────────────
    // CompensationRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST compensation negative amount returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "workBranchDayId" to testBranchDayId.toString(),
                    "payingBranchDayId" to testBranchDayId.toString(),
                    "userId" to UUID.randomUUID().toString(),
                    "amount" to "-50.00",
                )
            assertEquals(400, client.post("/api/compensation", body).code)
        }
    }

    @Test
    fun `POST compensation invalid amount returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "workBranchDayId" to testBranchDayId.toString(),
                    "payingBranchDayId" to testBranchDayId.toString(),
                    "userId" to UUID.randomUUID().toString(),
                    "amount" to "abc",
                )
            assertEquals(400, client.post("/api/compensation", body).code)
        }
    }

    @Test
    fun `PATCH compensation negative amount returns 400`() {
        val compId = UUID.randomUUID()
        transaction {
            CompensationTable.insert {
                it[CompensationTable.id] = compId
                it[CompensationTable.workBranchDayId] = testBranchDayId
                it[CompensationTable.payingBranchDayId] = testBranchDayId
                it[CompensationTable.userId] = testUserId
                it[CompensationTable.amount] = BigDecimal("100.00")
                it[CompensationTable.assignedBy] = testUserId
            }
        }
        trackOwned(CompensationTable, CompensationTable.id, compId)
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(
                400,
                client
                    .patch(
                        "/api/compensation/$compId",
                        mapOf("amount" to "-10.00", "expectedVersion" to 0),
                    ).code,
            )
        }
    }

    // ──────────────────────────────────────────────
    // SessionRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST session negative finalPrice returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "clientId" to UUID.randomUUID().toString(),
                    "branchId" to testBranchId.toString(),
                    "isWalkIn" to false,
                    "finalPrice" to "-100.00",
                )
            assertEquals(400, client.post("/api/sessions", body).code)
        }
    }

    @Test
    fun `POST session invalid finalPrice string returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "clientId" to UUID.randomUUID().toString(),
                    "branchId" to testBranchId.toString(),
                    "isWalkIn" to false,
                    "finalPrice" to "abc",
                )
            assertEquals(400, client.post("/api/sessions", body).code)
        }
    }

    @Test
    fun `POST void session blank voidReason returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body = mapOf("id" to UUID.randomUUID().toString(), "voidReason" to "  ")
            assertEquals(400, client.post("/api/sessions/$testSessionId/void", body).code)
        }
    }

    @Test
    fun `POST unvoid session blank unvoidedReason returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(400, client.post("/api/sessions/$testSessionId/unvoid", mapOf("unvoidedReason" to "  ")).code)
        }
    }

    @Test
    fun `POST promote concern blank label returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body = mapOf("id" to UUID.randomUUID().toString(), "label" to "  ")
            assertEquals(400, client.post("/api/sessions/$testSessionId/promote-concern", body).code)
        }
    }

    // ──────────────────────────────────────────────
    // ClientRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST client blank firstName returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "firstName" to "  ",
                    "lastName" to "Doe",
                    "gender" to "M",
                    "age" to 25,
                )
            assertEquals(400, client.post("/api/clients", body).code)
        }
    }

    @Test
    fun `POST client blank lastName returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "firstName" to "John",
                    "lastName" to "  ",
                    "gender" to "M",
                    "age" to 25,
                )
            assertEquals(400, client.post("/api/clients", body).code)
        }
    }

    @Test
    fun `POST client partial BP systolic only returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "firstName" to "John",
                    "lastName" to "Doe",
                    "gender" to "M",
                    "age" to 25,
                    "systolicBp" to 120,
                )
            assertEquals(400, client.post("/api/clients", body).code)
        }
    }

    @Test
    fun `POST client partial BP diastolic only returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "firstName" to "John",
                    "lastName" to "Doe",
                    "gender" to "M",
                    "age" to 25,
                    "diastolicBp" to 80,
                )
            assertEquals(400, client.post("/api/clients", body).code)
        }
    }

    @Test
    fun `GET clients blank search query returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(400, client.get("/api/clients?q=%20%20").code)
        }
    }

    @Test
    fun `GET clients missing search query returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(400, client.get("/api/clients").code)
        }
    }

    @Test
    fun `PATCH client blank firstName returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(400, client.patch("/api/clients/$testClientId", mapOf("firstName" to "  ")).code)
        }
    }

    @Test
    fun `PATCH client blank lastName returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(400, client.patch("/api/clients/$testClientId", mapOf("lastName" to "  ")).code)
        }
    }

    @Test
    fun `PATCH client partial BP returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(400, client.patch("/api/clients/$testClientId", mapOf("systolicBp" to 120)).code)
        }
    }

    // ──────────────────────────────────────────────
    // ProductRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST product blank name returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "name" to "  ",
                    "productCategoryId" to testCategoryId.toString(),
                    "unitPrice" to "100.00",
                    "commissionAmount" to "10.00",
                )
            assertEquals(400, client.post("/api/products", body).code)
        }
    }

    @Test
    fun `POST product negative price returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "name" to "Test Product",
                    "productCategoryId" to testCategoryId.toString(),
                    "unitPrice" to "-1.00",
                    "commissionAmount" to "10.00",
                )
            assertEquals(400, client.post("/api/products", body).code)
        }
    }

    @Test
    fun `POST product negative commission returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "name" to "Test Product",
                    "productCategoryId" to testCategoryId.toString(),
                    "unitPrice" to "100.00",
                    "commissionAmount" to "-5.00",
                )
            assertEquals(400, client.post("/api/products", body).code)
        }
    }

    // ──────────────────────────────────────────────
    // BranchInventoryRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST restock zero quantity returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "quantity" to 0,
                    "branchDayId" to testBranchDayId.toString(),
                )
            assertEquals(400, client.post("/api/branches/$testBranchId/inventory/$testProductId/restock", body).code)
        }
    }

    @Test
    fun `POST restock negative quantity returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "quantity" to -1,
                    "branchDayId" to testBranchDayId.toString(),
                )
            assertEquals(400, client.post("/api/branches/$testBranchId/inventory/$testProductId/restock", body).code)
        }
    }

    @Test
    fun `POST movement invalid reason returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "movementId" to UUID.randomUUID().toString(),
                    "reason" to "INVALID_REASON",
                    "quantityChange" to -1,
                    "branchDayId" to testBranchDayId.toString(),
                    "expectedVersion" to 0,
                )
            assertEquals(400, client.post("/api/branches/$testBranchId/inventory/$testProductId/movement", body).code)
        }
    }

    @Test
    fun `POST movement MISSING without notes returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "movementId" to UUID.randomUUID().toString(),
                    "reason" to "MISSING",
                    "quantityChange" to -1,
                    "branchDayId" to testBranchDayId.toString(),
                    "expectedVersion" to 0,
                )
            assertEquals(400, client.post("/api/branches/$testBranchId/inventory/$testProductId/movement", body).code)
        }
    }

    @Test
    fun `POST movement MISSING with blank notes returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "movementId" to UUID.randomUUID().toString(),
                    "reason" to "MISSING",
                    "quantityChange" to -1,
                    "notes" to "  ",
                    "branchDayId" to testBranchDayId.toString(),
                    "expectedVersion" to 0,
                )
            assertEquals(400, client.post("/api/branches/$testBranchId/inventory/$testProductId/movement", body).code)
        }
    }

    // ──────────────────────────────────────────────
    // ProductSaleRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST product sale zero quantity returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "branchDayId" to testBranchDayId.toString(),
                    "isWalkIn" to true,
                    "productId" to testProductId.toString(),
                    "quantity" to 0,
                    "expectedVersion" to 0,
                )
            assertEquals(400, client.post("/api/product-sales", body).code)
        }
    }

    @Test
    fun `POST product sale sessionId plus clientId returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "branchDayId" to testBranchDayId.toString(),
                    "sessionId" to UUID.randomUUID().toString(),
                    "clientId" to testClientId.toString(),
                    "isWalkIn" to false,
                    "productId" to testProductId.toString(),
                    "quantity" to 1,
                    "expectedVersion" to 0,
                )
            assertEquals(400, client.post("/api/product-sales", body).code)
        }
    }

    @Test
    fun `POST product sale sessionId with walkIn true returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "branchDayId" to testBranchDayId.toString(),
                    "sessionId" to UUID.randomUUID().toString(),
                    "isWalkIn" to true,
                    "productId" to testProductId.toString(),
                    "quantity" to 1,
                    "expectedVersion" to 0,
                )
            assertEquals(400, client.post("/api/product-sales", body).code)
        }
    }

    @Test
    fun `POST product sale known client without walkIn returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "branchDayId" to testBranchDayId.toString(),
                    "clientId" to testClientId.toString(),
                    "isWalkIn" to false,
                    "productId" to testProductId.toString(),
                    "quantity" to 1,
                    "expectedVersion" to 0,
                )
            assertEquals(400, client.post("/api/product-sales", body).code)
        }
    }

    @Test
    fun `POST product sale anonymous without walkIn returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "branchDayId" to testBranchDayId.toString(),
                    "isWalkIn" to false,
                    "productId" to testProductId.toString(),
                    "quantity" to 1,
                    "expectedVersion" to 0,
                )
            assertEquals(400, client.post("/api/product-sales", body).code)
        }
    }

    // ──────────────────────────────────────────────
    // UserBranchAssignmentRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST assignment slot less than 1 returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "userId" to UUID.randomUUID().toString(),
                    "slot" to 0,
                )
            assertEquals(400, client.post("/api/branches/$testBranchId/assignments", body).code)
        }
    }

    @Test
    fun `PATCH assignment slot less than 1 returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(
                400,
                client
                    .patch(
                        "/api/branches/$testBranchId/assignments/${UUID.randomUUID()}/slot",
                        mapOf("slot" to 0),
                    ).code,
            )
        }
    }

    // ──────────────────────────────────────────────
    // RemittanceRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST remittance draft reversed date range returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "type" to "SESSION",
                    "branchId" to testBranchId.toString(),
                    "method" to "BANK_TRANSFER",
                    "dateRangeStart" to "2024-12-31",
                    "dateRangeEnd" to "2024-01-01",
                )
            assertEquals(400, client.post("/api/remittances", body).code)
        }
    }

    @Test
    fun `POST remittance SESSION line without sessionId returns 400`() {
        val remittanceId = UUID.randomUUID()
        transaction {
            RemittanceTable.insert {
                it[RemittanceTable.id] = remittanceId
                it[RemittanceTable.type] = RemittanceType.SESSION
                it[RemittanceTable.branchId] = testBranchId
                it[RemittanceTable.method] = RemittanceMethod.BANK_TRANSFER
                it[RemittanceTable.submittedDate] = LocalDate.of(2024, 1, 15)
                it[RemittanceTable.dateRangeStart] = LocalDate.of(2024, 1, 1)
                it[RemittanceTable.dateRangeEnd] = LocalDate.of(2024, 1, 15)
                it[RemittanceTable.submittedBy] = testUserId
            }
        }
        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(
                400,
                client
                    .post(
                        "/api/remittances/$remittanceId/lines",
                        mapOf(
                            "id" to UUID.randomUUID().toString(),
                            "type" to "SESSION",
                            "amount" to "100.00",
                        ),
                    ).code,
            )
        }
    }

    @Test
    fun `POST remittance PRODUCT_SALE line without productSaleId returns 400`() {
        val remittanceId = UUID.randomUUID()
        transaction {
            RemittanceTable.insert {
                it[RemittanceTable.id] = remittanceId
                it[RemittanceTable.type] = RemittanceType.SESSION
                it[RemittanceTable.branchId] = testBranchId
                it[RemittanceTable.method] = RemittanceMethod.BANK_TRANSFER
                it[RemittanceTable.submittedDate] = LocalDate.of(2024, 1, 15)
                it[RemittanceTable.dateRangeStart] = LocalDate.of(2024, 1, 1)
                it[RemittanceTable.dateRangeEnd] = LocalDate.of(2024, 1, 15)
                it[RemittanceTable.submittedBy] = testUserId
            }
        }
        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(
                400,
                client
                    .post(
                        "/api/remittances/$remittanceId/lines",
                        mapOf(
                            "id" to UUID.randomUUID().toString(),
                            "type" to "PRODUCT_SALE",
                            "amount" to "100.00",
                        ),
                    ).code,
            )
        }
    }

    // ──────────────────────────────────────────────
    // ExportRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `GET export daily missing format returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(400, client.get("/api/branches/$testBranchId/export/daily?date=2024-01-15").code)
        }
    }

    @Test
    fun `GET export daily invalid format returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(
                400,
                client.get("/api/branches/$testBranchId/export/daily?date=2024-01-15&format=invalid").code,
            )
        }
    }

    // ──────────────────────────────────────────────
    // ExpenseRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST expense non-positive amount returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "branchDayId" to testBranchDayId.toString(),
                    "amount" to "-0.01",
                    "category" to "MISCELLANEOUS",
                )
            assertEquals(400, client.post("/api/expenses", body).code)
        }
    }

    @Test
    fun `POST expense zero amount returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "branchDayId" to testBranchDayId.toString(),
                    "amount" to "0.00",
                    "category" to "MISCELLANEOUS",
                )
            assertEquals(400, client.post("/api/expenses", body).code)
        }
    }

    @Test
    fun `POST expense invalid category returns 400`() {
        JavalinTest.test(createApp()) { _, client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "branchDayId" to testBranchDayId.toString(),
                    "amount" to "100.00",
                    "category" to "INVALID",
                )
            assertEquals(400, client.post("/api/expenses", body).code)
        }
    }

    @Test
    fun `DELETE expense blank reason returns 400`() {
        val expenseId = UUID.randomUUID()
        transaction {
            ExpenseTable.insert {
                it[ExpenseTable.id] = expenseId
                it[ExpenseTable.branchDayId] = testBranchDayId
                it[ExpenseTable.amount] = BigDecimal("100.00")
                it[ExpenseTable.category] = ExpenseCategory.MISCELLANEOUS
                it[ExpenseTable.createdBy] = testUserId
                it[ExpenseTable.notes] = "Test expense"
            }
        }
        trackOwned(ExpenseTable, ExpenseTable.id, expenseId)
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(400, client.delete("/api/expenses/$expenseId", mapOf("reason" to "  ")).code)
        }
    }
}
