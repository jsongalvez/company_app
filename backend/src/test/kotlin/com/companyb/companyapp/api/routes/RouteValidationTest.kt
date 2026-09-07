@file:Suppress("LargeClass")

package com.companyb.companyapp.api.routes
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.client.ClientRoutes
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.commerce.BranchInventoryRoutes
import com.companyb.companyapp.commerce.ProductRoutes
import com.companyb.companyapp.commerce.ProductSaleRoutes
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.contracts.notification.NotificationHistoryResponse
import com.companyb.companyapp.contracts.notification.NotificationUnreadCountResponse
import com.companyb.companyapp.domain.ExpenseCategory
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceStatus
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.dto.DailySalesSummaryBrowseResponse
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.finance.AllowanceRoutes
import com.companyb.companyapp.finance.CompensationRoutes
import com.companyb.companyapp.finance.CompensationTable
import com.companyb.companyapp.finance.ExpenseRoutes
import com.companyb.companyapp.finance.ExpenseTable
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.notification.NotificationRoutes
import com.companyb.companyapp.notification.NotificationTable
import com.companyb.companyapp.remittance.RemittanceRoutes
import com.companyb.companyapp.remittance.RemittanceTable
import com.companyb.companyapp.reporting.DailySalesSummaryRoutes
import com.companyb.companyapp.reporting.ExportRoutes
import com.companyb.companyapp.reporting.MonthlyRemittanceSummaryRoutes
import com.companyb.companyapp.session.SessionRoutes
import com.companyb.companyapp.session.dashboard.DashboardRoutes
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import com.companyb.companyapp.workforce.UserBranchAssignmentRoutes
import io.javalin.Javalin
import io.javalin.config.JavalinConfig
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.ClassRule
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteValidationTest : BasePostgresTest() {
    private val json = Json { ignoreUnknownKeys = true }
    private val testUserId = TEST_USER_ID
    private val testBranchId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private var testBranchDayId: UUID = TestFixtures.uuid()
    private val testCategoryId = TestFixtures.uuid()
    private val testProductId = TestFixtures.uuid()
    private val testClientId = TestFixtures.uuid()
    private val testClientNoSessionsId = TestFixtures.uuid()
    private val testSessionId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(testUserId, "route-test")
        BranchWorkforceFixtures.insertTestBranch(testBranchId, "Route Test Branch $testBranchId")
        val allCodes =
            listOf(
                CapabilityCodes.VIEW_BRANCH_DATA,
                CapabilityCodes.EDIT_BRANCH_DATA,
                CapabilityCodes.EDIT_PAST_DAY,
                CapabilityCodes.VOID_SESSION,
                CapabilityCodes.SUBMIT_REMITTANCE,
                CapabilityCodes.ASSIGN_COMPENSATION,
                CapabilityCodes.MANAGE_PRODUCTS,
                CapabilityCodes.MANAGE_CATALOG,
                CapabilityCodes.MANAGE_USERS,
                CapabilityCodes.ASSIGN_DELEGATE,
            )
        for (code in allCodes) {
            IdentityFixtures.grantCapability(
                userId = testUserId,
                capabilityCode = code,
                contextType = CapabilityContextType.BRANCH,
                contextId = testBranchId,
                sourceId = sourceId,
            )
            IdentityFixtures.grantCapability(
                userId = testUserId,
                capabilityCode = code,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
                sourceId = sourceId,
            )
        }
        testBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(testBranchId)
        CommerceFinanceFixtures.insertTestCategory(testCategoryId)
        CommerceFinanceFixtures.insertTestProduct(testProductId, categoryId = testCategoryId)
        SessionClientFixtures.insertTestClient(testClientId)
        SessionClientFixtures.insertTestClient(testClientNoSessionsId)
        SessionClientFixtures.insertTestSession(
            id = testSessionId,
            clientId = testClientId,
            branchDayId = testBranchDayId,
        )
    }

    companion object {
        @JvmField
        @ClassRule
        val testServer = JavalinTestServerRule(::createApp)

        val TEST_USER_ID = TestFixtures.uuid()

        fun createApp(): Javalin {
            val config = AppConfig.parse()
            JwtService.init(config)
            Password.init(config.authDummyPassword)

            return Javalin.create { cfg ->
                cfg.jsonMapper(KotlinxSerializationMapper())
                cfg.routes.before { ctx ->
                    Database.connect(TestDatabaseLifecycle.requireTestDataSource())
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
            DashboardRoutes.register(config)
            SessionRoutes.register(config)
            ClientRoutes.register(config)
            ProductRoutes.register(config)
            BranchInventoryRoutes.register(config)
            ProductSaleRoutes.register(config)
            UserBranchAssignmentRoutes.register(config)
            RemittanceRoutes.register(config)
            ExportRoutes.register(config)
            DailySalesSummaryRoutes.register(config)
            ExpenseRoutes.register(config)
            NotificationRoutes.register(config)
        }
    }

    // ──────────────────────────────────────────────
    // AllowanceRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST allowance negative amount returns 400`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "branchDayId" to testBranchDayId.toString(),
                    "userId" to TestFixtures.uuid().toString(),
                    "amount" to "-1.00",
                )
            assertEquals(400, client.post("/api/allowances", body).code)
        }
    }

    @Test
    fun `POST allowance invalid amount string returns 400`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "branchDayId" to testBranchDayId.toString(),
                    "userId" to TestFixtures.uuid().toString(),
                    "amount" to "not-a-number",
                )
            assertEquals(400, client.post("/api/allowances", body).code)
        }
    }

    @Test
    fun `POST allowance invalid branchDayId UUID returns 400`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "branchDayId" to "not-a-uuid",
                    "userId" to TestFixtures.uuid().toString(),
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
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "workBranchDayId" to testBranchDayId.toString(),
                    "payingBranchDayId" to testBranchDayId.toString(),
                    "userId" to TestFixtures.uuid().toString(),
                    "amount" to "-50.00",
                )
            assertEquals(400, client.post("/api/compensation", body).code)
        }
    }

    @Test
    fun `POST compensation invalid amount returns 400`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "workBranchDayId" to testBranchDayId.toString(),
                    "payingBranchDayId" to testBranchDayId.toString(),
                    "userId" to TestFixtures.uuid().toString(),
                    "amount" to "abc",
                )
            assertEquals(400, client.post("/api/compensation", body).code)
        }
    }

    @Test
    fun `PATCH compensation negative amount returns 400`() {
        val compId = TestFixtures.uuid()
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
        testServer.client.let { client ->
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
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "clientId" to TestFixtures.uuid().toString(),
                    "branchId" to testBranchId.toString(),
                    "isWalkIn" to false,
                    "finalPrice" to "-100.00",
                )
            assertEquals(400, client.post("/api/sessions", body).code)
        }
    }

    @Test
    fun `POST session invalid finalPrice string returns 400`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "clientId" to TestFixtures.uuid().toString(),
                    "branchId" to testBranchId.toString(),
                    "isWalkIn" to false,
                    "finalPrice" to "abc",
                )
            assertEquals(400, client.post("/api/sessions", body).code)
        }
    }

    @Test
    fun `POST void session blank voidReason returns 400`() {
        testServer.client.let { client ->
            val body = mapOf("id" to TestFixtures.uuid().toString(), "voidReason" to "  ")
            assertEquals(400, client.post("/api/sessions/$testSessionId/void", body).code)
        }
    }

    @Test
    fun `POST unvoid session blank unvoidedReason returns 400`() {
        testServer.client.let { client ->
            assertEquals(400, client.post("/api/sessions/$testSessionId/unvoid", mapOf("unvoidedReason" to "  ")).code)
        }
    }

    @Test
    fun `POST promote concern blank label returns 400`() {
        testServer.client.let { client ->
            val body = mapOf("id" to TestFixtures.uuid().toString(), "label" to "  ")
            assertEquals(400, client.post("/api/sessions/$testSessionId/promote-concern", body).code)
        }
    }

    @Test
    fun `GET session concerns on REMITTED day returns 200 with EDIT_PAST_DAY`() {
        val remittedDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                testBranchId,
                TestFixtures.today.minusDays(3),
            )
        val remittedClientId = SessionClientFixtures.insertTestClient()
        val remittedSessionId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(remittedSessionId, remittedClientId, remittedDayId)

        testServer.client.let { client ->
            assertEquals(200, client.get("/api/sessions/$remittedSessionId/concerns").code)
        }
    }

    // ──────────────────────────────────────────────
    // ClientRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST client blank firstName returns 400`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
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
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
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
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
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
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
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
        testServer.client.let { client ->
            assertEquals(400, client.get("/api/clients?q=%20%20").code)
        }
    }

    @Test
    fun `GET clients missing search query returns 400`() {
        testServer.client.let { client ->
            assertEquals(400, client.get("/api/clients").code)
        }
    }

    @Test
    fun `GET clients includes authoritative session count`() {
        testServer.client.let { client ->
            val response = client.get("/api/clients?q=Test")
            val clients = json.decodeFromString<List<ClientResponse>>(response.body.string())

            assertEquals(200, response.code)
            assertEquals(1, clients.single { it.id == testClientId.toString() }.sessionCount)
            assertEquals(0, clients.single { it.id == testClientNoSessionsId.toString() }.sessionCount)
        }
    }

    @Test
    fun `PATCH client blank firstName returns 400`() {
        testServer.client.let { client ->
            assertEquals(400, client.patch("/api/clients/$testClientId", mapOf("firstName" to "  ")).code)
        }
    }

    @Test
    fun `PATCH client blank lastName returns 400`() {
        testServer.client.let { client ->
            assertEquals(400, client.patch("/api/clients/$testClientId", mapOf("lastName" to "  ")).code)
        }
    }

    @Test
    fun `PATCH client partial BP returns 400`() {
        testServer.client.let { client ->
            assertEquals(400, client.patch("/api/clients/$testClientId", mapOf("systolicBp" to 120)).code)
        }
    }

    @Test
    fun `PATCH client clear phone persists null`() {
        testServer.client.let { client ->
            assertEquals(
                200,
                client.patch("/api/clients/$testClientId", mapOf("phoneNumber" to "09171234567")).code,
            )
            val response =
                client.patch(
                    "/api/clients/$testClientId",
                    mapOf("clearFields" to listOf("phoneNumber")),
                )
            assertEquals(200, response.code)
            val updated = json.decodeFromString<ClientResponse>(response.body.string())
            assertNull(updated.phoneNumber)
            assertEquals("Test", updated.firstName)
        }
    }

    @Test
    fun `PATCH client clear BP pair persists nulls`() {
        testServer.client.let { client ->
            val seeded =
                client.patch(
                    "/api/clients/$testClientId",
                    mapOf("systolicBp" to 120, "diastolicBp" to 80),
                )
            assertEquals(200, seeded.code)
            val response =
                client.patch(
                    "/api/clients/$testClientId",
                    mapOf("clearFields" to listOf("systolicBp", "diastolicBp")),
                )
            assertEquals(200, response.code)
            val updated = json.decodeFromString<ClientResponse>(response.body.string())
            assertNull(updated.systolicBp)
            assertNull(updated.diastolicBp)
        }
    }

    @Test
    fun `PATCH client unknown clear field returns 400`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client.patch("/api/clients/$testClientId", mapOf("clearFields" to listOf("nickname"))).code,
            )
        }
    }

    @Test
    fun `PATCH client clear required field returns 400`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client.patch("/api/clients/$testClientId", mapOf("clearFields" to listOf("firstName"))).code,
            )
            assertEquals(
                400,
                client.patch("/api/clients/$testClientId", mapOf("clearFields" to listOf("age"))).code,
            )
        }
    }

    @Test
    fun `PATCH client half BP clear returns 400`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client.patch("/api/clients/$testClientId", mapOf("clearFields" to listOf("systolicBp"))).code,
            )
        }
    }

    @Test
    fun `PATCH client set and clear on the same field returns 400`() {
        testServer.client.let { client ->
            val response =
                client.patch(
                    "/api/clients/$testClientId",
                    mapOf("phoneNumber" to "0917", "clearFields" to listOf("phoneNumber")),
                )
            assertEquals(400, response.code)
        }
    }

    @Test
    fun `PATCH client blank optional returns 400 without storing whitespace`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client.patch("/api/clients/$testClientId", mapOf("phoneNumber" to "   ")).code,
            )
        }
    }

    @Test
    fun `POST anonymize client with pending session returns 409 and keeps PII`() {
        testServer.client.let { client ->
            val response = client.post("/api/clients/$testClientId/anonymize")

            assertEquals(409, response.code)
            transaction {
                val row = ClientTable.selectAll().where { ClientTable.id eq testClientId }.single()
                assertEquals("Test", row[ClientTable.firstName])
                assertEquals("Client", row[ClientTable.lastName])
                assertNull(row[ClientTable.deletedAt])
            }
        }
    }

    @Test
    fun `POST anonymize client without sessions returns 204`() {
        testServer.client.let { client ->
            val response = client.post("/api/clients/$testClientNoSessionsId/anonymize")

            assertEquals(204, response.code)
            transaction {
                val row =
                    ClientTable.selectAll().where { ClientTable.id eq testClientNoSessionsId }.single()
                assertNull(row[ClientTable.firstName])
                assertNull(row[ClientTable.lastName])
            }
        }
    }

    // ──────────────────────────────────────────────
    // ProductRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST product blank name returns 400`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
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
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
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
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
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
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "quantity" to 0,
                    "branchDayId" to testBranchDayId.toString(),
                )
            assertEquals(400, client.post("/api/branches/$testBranchId/inventory/$testProductId/restock", body).code)
        }
    }

    @Test
    fun `POST restock negative quantity returns 400`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "quantity" to -1,
                    "branchDayId" to testBranchDayId.toString(),
                )
            assertEquals(400, client.post("/api/branches/$testBranchId/inventory/$testProductId/restock", body).code)
        }
    }

    @Test
    fun `POST movement invalid reason returns 400`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "movementId" to TestFixtures.uuid().toString(),
                    "reason" to "INVALID_REASON",
                    "quantityChange" to -1,
                    "branchDayId" to testBranchDayId.toString(),
                    "expectedVersion" to 0,
                )
            assertEquals(400, client.post("/api/branches/$testBranchId/inventory/$testProductId/movement", body).code)
        }
    }

    @Test
    fun `POST movement RESTOCK reason is rejected by endpoint restriction`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "movementId" to TestFixtures.uuid().toString(),
                    "reason" to "RESTOCK",
                    "quantityChange" to 1,
                    "branchDayId" to testBranchDayId.toString(),
                    "expectedVersion" to 0,
                )
            assertEquals(400, client.post("/api/branches/$testBranchId/inventory/$testProductId/movement", body).code)
        }
    }

    @Test
    fun `POST movement MISSING without notes returns 400`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "movementId" to TestFixtures.uuid().toString(),
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
        testServer.client.let { client ->
            val body =
                mapOf(
                    "movementId" to TestFixtures.uuid().toString(),
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
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
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
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "branchDayId" to testBranchDayId.toString(),
                    "sessionId" to TestFixtures.uuid().toString(),
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
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "branchDayId" to testBranchDayId.toString(),
                    "sessionId" to TestFixtures.uuid().toString(),
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
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
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
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
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
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "userId" to TestFixtures.uuid().toString(),
                    "slot" to 0,
                )
            assertEquals(400, client.post("/api/branches/$testBranchId/assignments", body).code)
        }
    }

    @Test
    fun `PATCH assignment slot less than 1 returns 400`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client
                    .patch(
                        "/api/branches/$testBranchId/assignments/${TestFixtures.uuid()}/slot",
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
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
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
        val remittanceId = TestFixtures.uuid()
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
        testServer.client.let { client ->
            assertEquals(
                400,
                client
                    .post(
                        "/api/remittances/$remittanceId/lines",
                        mapOf(
                            "id" to TestFixtures.uuid().toString(),
                            "type" to "SESSION",
                            "amount" to "100.00",
                        ),
                    ).code,
            )
        }
    }

    @Test
    fun `POST remittance PRODUCT_SALE line without productSaleId returns 400`() {
        val remittanceId = TestFixtures.uuid()
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
        testServer.client.let { client ->
            assertEquals(
                400,
                client
                    .post(
                        "/api/remittances/$remittanceId/lines",
                        mapOf(
                            "id" to TestFixtures.uuid().toString(),
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
        testServer.client.let { client ->
            assertEquals(400, client.get("/api/branches/$testBranchId/export/daily?date=2024-01-15").code)
        }
    }

    @Test
    fun `GET export daily invalid format returns 400`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client.get("/api/branches/$testBranchId/export/daily?date=2024-01-15&format=invalid").code,
            )
        }
    }

    @Test
    fun `GET export range missing from returns 400`() {
        testServer.client.let { client ->
            assertEquals(400, client.get("/api/branches/$testBranchId/export/range?to=2024-01-15&format=csv").code)
        }
    }

    @Test
    fun `GET export range missing to returns 400`() {
        testServer.client.let { client ->
            assertEquals(400, client.get("/api/branches/$testBranchId/export/range?from=2024-01-15&format=csv").code)
        }
    }

    @Test
    fun `GET export range invalid date returns 400`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client.get("/api/branches/$testBranchId/export/range?from=not-a-date&to=2024-01-15&format=csv").code,
            )
        }
    }

    @Test
    fun `GET export range from after to returns 400`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client.get("/api/branches/$testBranchId/export/range?from=2024-01-16&to=2024-01-15&format=csv").code,
            )
        }
    }

    @Test
    fun `GET export range missing format returns 400`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client.get("/api/branches/$testBranchId/export/range?from=2024-01-15&to=2024-01-16").code,
            )
        }
    }

    @Test
    fun `GET export range invalid format returns 400`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client
                    .get("/api/branches/$testBranchId/export/range?from=2024-01-15&to=2024-01-16&format=invalid")
                    .code,
            )
        }
    }

    // ──────────────────────────────────────────────
    // DailySalesSummaryRoutes — paged daily-summaries feed
    // ──────────────────────────────────────────────

    @Test
    fun `GET daily-summaries returns 200 with seeded day`() {
        testServer.client.let { client ->
            val response = client.get("/api/branches/$testBranchId/daily-summaries")
            assertEquals(200, response.code)
            val body = json.decodeFromString<DailySalesSummaryBrowseResponse>(response.body.string())
            assertEquals(1, body.entries.size)
            assertEquals(testBranchDayId.toString(), body.entries.single().branchDayId)
            assertEquals(TestFixtures.today.toString(), body.entries.single().date)
            assertNull(body.nextCursor)
        }
    }

    @Test
    fun `GET daily-summaries returns 200 empty feed for branch without days`() {
        val noDaysBranchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(noDaysBranchId, "No-Days Branch")
        testServer.client.let { client ->
            val response = client.get("/api/branches/$noDaysBranchId/daily-summaries")
            assertEquals(200, response.code, "a feed is 200 not 404 when the branch has no days")
            val body = json.decodeFromString<DailySalesSummaryBrowseResponse>(response.body.string())
            assertTrue(body.entries.isEmpty())
            assertNull(body.nextCursor)
        }
    }

    @Test
    fun `GET daily-summaries rejects limit zero`() {
        testServer.client.let { client ->
            assertEquals(400, client.get("/api/branches/$testBranchId/daily-summaries?limit=0").code)
        }
    }

    @Test
    fun `GET daily-summaries rejects limit above max`() {
        testServer.client.let { client ->
            assertEquals(400, client.get("/api/branches/$testBranchId/daily-summaries?limit=101").code)
        }
    }

    @Test
    fun `GET daily-summaries rejects non-numeric limit`() {
        testServer.client.let { client ->
            assertEquals(400, client.get("/api/branches/$testBranchId/daily-summaries?limit=abc").code)
        }
    }

    @Test
    fun `GET daily-summaries rejects malformed cursor`() {
        testServer.client.let { client ->
            assertEquals(400, client.get("/api/branches/$testBranchId/daily-summaries?cursor=not-a-cursor").code)
        }
    }

    @Test
    fun `GET daily-summaries rejects malformed from date`() {
        testServer.client.let { client ->
            assertEquals(400, client.get("/api/branches/$testBranchId/daily-summaries?from=2026-13-99").code)
        }
    }

    @Test
    fun `GET daily-summaries rejects malformed to date`() {
        testServer.client.let { client ->
            assertEquals(400, client.get("/api/branches/$testBranchId/daily-summaries?to=not-a-date").code)
        }
    }

    @Test
    fun `GET daily-summaries rejects from after to`() {
        testServer.client.let { client ->
            val tomorrow = TestFixtures.today.plusDays(1)
            val yesterday = TestFixtures.today.minusDays(1)
            assertEquals(
                400,
                client
                    .get(
                        "/api/branches/$testBranchId/daily-summaries?from=$tomorrow&to=$yesterday",
                    ).code,
            )
        }
    }

    @Test
    fun `GET daily-summaries window filters the feed`() {
        testServer.client.let { client ->
            val today = TestFixtures.today
            val yesterday = today.minusDays(1)
            val response =
                client.get(
                    "/api/branches/$testBranchId/daily-summaries?from=$yesterday&to=$today",
                )
            assertEquals(200, response.code)
            val body = json.decodeFromString<DailySalesSummaryBrowseResponse>(response.body.string())
            assertEquals(1, body.entries.size, "the window admits only the seeded day")
            assertEquals(today.toString(), body.entries.single().date)
        }
    }

    // ──────────────────────────────────────────────
    // NotificationRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST notifications read-all returns 200 and marks all unread as read`() {
        val notificationId = TestFixtures.uuid()
        transaction {
            NotificationTable.insert {
                it[NotificationTable.id] = notificationId
                it[NotificationTable.sessionId] = testSessionId
                it[NotificationTable.userId] = testUserId
                it[NotificationTable.branchId] = testBranchId
                it[NotificationTable.message] = "Test notification"
                it[NotificationTable.dedupKey] = "APPT:$testSessionId:$notificationId"
            }
        }
        testServer.client.let { client ->
            val response = client.post("/api/notifications/read-all")
            assertEquals(200, response.code)
            assertTrue(response.body.string().contains("\"unreadCount\":0"))
        }
        val isRead =
            transaction {
                NotificationTable
                    .selectAll()
                    .where { NotificationTable.id eq notificationId }
                    .single()[NotificationTable.isRead]
            }
        assertTrue(isRead)
    }

    @Test
    fun `PATCH notification read still matches param route when read-all literal registered first`() {
        val notificationId = TestFixtures.uuid()
        transaction {
            NotificationTable.insert {
                it[NotificationTable.id] = notificationId
                it[NotificationTable.sessionId] = testSessionId
                it[NotificationTable.userId] = testUserId
                it[NotificationTable.branchId] = testBranchId
                it[NotificationTable.message] = "Test notification"
                it[NotificationTable.dedupKey] = "APPT:$testSessionId:$notificationId"
            }
        }
        testServer.client.let { client ->
            assertEquals(200, client.patch("/api/notifications/$notificationId/read").code)
        }
    }

    @Test
    fun `GET notifications unread-count returns count without rows`() {
        testServer.client.let { client ->
            val response = client.get("/api/notifications/unread-count")
            assertEquals(200, response.code)
            val body = json.decodeFromString<NotificationUnreadCountResponse>(response.body.string())
            assertEquals(0, body.unreadCount)
        }
    }

    @Test
    fun `GET notifications history pages bounded entries with cursor`() {
        testServer.client.let { client ->
            val first = client.get("/api/notifications/history?limit=1")
            assertEquals(200, first.code)
            val firstBody = json.decodeFromString<NotificationHistoryResponse>(first.body.string())
            assertTrue(firstBody.entries.size <= 1)

            val second = client.get("/api/notifications/history?cursor=bad-cursor&limit=1")
            assertEquals(400, second.code)
        }
    }

    // ──────────────────────────────────────────────
    // ExpenseRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST expense non-positive amount returns 400`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "branchDayId" to testBranchDayId.toString(),
                    "amount" to "-0.01",
                    "category" to "MISCELLANEOUS",
                )
            assertEquals(400, client.post("/api/expenses", body).code)
        }
    }

    @Test
    fun `POST expense zero amount returns 400`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "branchDayId" to testBranchDayId.toString(),
                    "amount" to "0.00",
                    "category" to "MISCELLANEOUS",
                )
            assertEquals(400, client.post("/api/expenses", body).code)
        }
    }

    @Test
    fun `POST expense invalid category returns 400`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "branchDayId" to testBranchDayId.toString(),
                    "amount" to "100.00",
                    "category" to "INVALID",
                )
            assertEquals(400, client.post("/api/expenses", body).code)
        }
    }

    @Test
    fun `DELETE expense blank reason returns 400`() {
        val expenseId = TestFixtures.uuid()
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
        testServer.client.let { client ->
            assertEquals(400, client.delete("/api/expenses/$expenseId", mapOf("reason" to "  ")).code)
        }
    }
}
