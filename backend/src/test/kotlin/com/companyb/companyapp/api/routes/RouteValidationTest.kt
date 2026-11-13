@file:Suppress("LargeClass") // #600

package com.companyb.companyapp.api.routes

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.authorization.Role
import com.companyb.companyapp.client.ClientRoutes
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.commerce.BranchInventoryRoutes
import com.companyb.companyapp.commerce.ProductRoutes
import com.companyb.companyapp.commerce.ProductSaleRoutes
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.contracts.notification.NotificationHistoryResponse
import com.companyb.companyapp.contracts.notification.NotificationUnreadCountResponse
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.contracts.reporting.DailySalesSummaryBrowseResponse
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
    private var testBranchDayId: UUID = TestFixtures.uuid()
    private val testCategoryId = TestFixtures.uuid()
    private val testProductId = TestFixtures.uuid()
    private val testClientId = TestFixtures.uuid()
    private val testClientNoSessionsId = TestFixtures.uuid()
    private val testSessionId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(testUserId, "route-test")
        BranchWorkforceFixtures.insertTestBranch(testBranchId, "Route Test Branch $testBranchId")
        IdentityFixtures.member(testUserId, testBranchId, Role.SUPERUSER)
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

    private fun uuid() = TestFixtures.uuid().toString()

    private fun assertPost(
        code: Int,
        path: String,
        body: Map<String, Any?>? = null,
    ) {
        val response = body?.let { testServer.client.post(path, it) } ?: testServer.client.post(path)
        assertEquals(code, response.code, "POST $path $body")
    }

    private fun assertPatch(
        code: Int,
        path: String,
        body: Map<String, Any?>? = null,
    ) {
        val response = body?.let { testServer.client.patch(path, it) } ?: testServer.client.patch(path)
        assertEquals(code, response.code, "PATCH $path $body")
    }

    private fun assertDelete(
        code: Int,
        path: String,
        body: Map<String, Any?>? = null,
    ) {
        val response = body?.let { testServer.client.delete(path, it) } ?: testServer.client.delete(path)
        assertEquals(code, response.code, "DELETE $path $body")
    }

    private fun assertGet(
        code: Int,
        path: String,
    ) = assertEquals(code, testServer.client.get(path).code, "GET $path")

    private fun allowance(
        amount: String = "100.00",
        branchDayId: String = testBranchDayId.toString(),
    ) = mapOf(
        "id" to uuid(),
        "branchDayId" to branchDayId,
        "userId" to uuid(),
        "amount" to amount,
    )

    private fun compensation(amount: String = "100.00") =
        mapOf(
            "id" to uuid(),
            "workBranchDayId" to testBranchDayId.toString(),
            "payingBranchDayId" to testBranchDayId.toString(),
            "userId" to uuid(),
            "amount" to amount,
        )

    private fun sessionBody(
        finalPrice: String = "100.00",
        clientId: String = uuid(),
    ) = mapOf(
        "id" to uuid(),
        "clientId" to clientId,
        "branchId" to testBranchId.toString(),
        "isWalkIn" to false,
        "finalPrice" to finalPrice,
    )

    private fun clientBody(vararg overrides: Pair<String, Any?>) =
        mapOf(
            "id" to uuid(),
            "firstName" to "John",
            "lastName" to "Doe",
            "gender" to "M",
            "age" to 25,
        ) + overrides

    private fun product(
        name: String = "Test Product",
        unitPrice: String = "100.00",
        commissionAmount: String = "10.00",
    ) = mapOf(
        "id" to uuid(),
        "name" to name,
        "productCategoryId" to testCategoryId.toString(),
        "unitPrice" to unitPrice,
        "commissionAmount" to commissionAmount,
    )

    private fun restock(quantity: Int) =
        mapOf("id" to uuid(), "quantity" to quantity, "branchDayId" to testBranchDayId.toString())

    private fun movement(
        reason: String,
        quantityChange: Int = -1,
        notes: String? = null,
    ) = buildMap<String, Any?> {
        put("movementId", uuid())
        put("reason", reason)
        put("quantityChange", quantityChange)
        notes?.let { put("notes", it) }
        put("branchDayId", testBranchDayId.toString())
        put("expectedVersion", 0)
    }

    private fun productSale(
        quantity: Int = 1,
        isWalkIn: Boolean = true,
        sessionId: String? = null,
        clientId: String? = null,
    ) = buildMap<String, Any?> {
        put("id", uuid())
        put("branchDayId", testBranchDayId.toString())
        sessionId?.let { put("sessionId", it) }
        clientId?.let { put("clientId", it) }
        put("isWalkIn", isWalkIn)
        put("productId", testProductId.toString())
        put("quantity", quantity)
        put("expectedVersion", 0)
    }

    private fun remittance(remittanceId: UUID = TestFixtures.uuid()): UUID {
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
        return remittanceId
    }

    private fun remittanceLine(
        type: String,
        amount: String = "100.00",
        sessionId: String? = null,
    ) = buildMap<String, Any?> {
        put("id", uuid())
        put("type", type)
        sessionId?.let { put("sessionId", it) }
        put("amount", amount)
    }

    private fun expense(
        amount: String = "100.00",
        category: String = "MISCELLANEOUS",
    ) = mapOf(
        "id" to uuid(),
        "branchDayId" to testBranchDayId.toString(),
        "amount" to amount,
        "category" to category,
    )

    private fun notification(): UUID {
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
        return notificationId
    }

    // ──────────────────────────────────────────────
    // AllowanceRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST allowance negative amount returns 400`() = assertPost(400, "/api/allowances", allowance(amount = "-1.00"))

    @Test
    fun `POST allowance invalid amount string returns 400`() =
        assertPost(400, "/api/allowances", allowance(amount = "not-a-number"))

    @Test
    fun `POST allowance overflowing amount returns 400`() =
        assertPost(400, "/api/allowances", allowance(amount = "9999999999.99"))

    @Test
    fun `POST allowance invalid branchDayId UUID returns 400`() =
        assertPost(400, "/api/allowances", allowance(branchDayId = "not-a-uuid"))

    // ──────────────────────────────────────────────
    // CompensationRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST compensation negative amount returns 400`() =
        assertPost(400, "/api/compensation", compensation(amount = "-50.00"))

    @Test
    fun `POST compensation invalid amount returns 400`() =
        assertPost(400, "/api/compensation", compensation(amount = "abc"))

    @Test
    fun `POST compensation overflowing amount returns 400`() =
        assertPost(400, "/api/compensation", compensation(amount = "9999999999.99"))

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
        assertPatch(400, "/api/compensation/$compId", mapOf("amount" to "-10.00", "expectedVersion" to 0))
    }

    // ──────────────────────────────────────────────
    // SessionRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST session negative finalPrice returns 400`() =
        assertPost(400, "/api/sessions", sessionBody(finalPrice = "-100.00"))

    @Test
    fun `POST session invalid finalPrice string returns 400`() =
        assertPost(400, "/api/sessions", sessionBody(finalPrice = "abc"))

    @Test
    fun `POST session overflowing finalPrice returns 400`() {
        // Seed a base rate so an unguarded request reaches the write (pre-fix 500)
        // instead of failing earlier on the missing rate; the route pre-gate 400s first.
        SessionClientFixtures.insertTestBaseRate(TestFixtures.uuid(), testBranchId, testUserId)
        val freshClientId = SessionClientFixtures.insertTestClient()
        assertPost(
            400,
            "/api/sessions",
            sessionBody(finalPrice = "9999999999.99", clientId = freshClientId.toString()),
        )
    }

    @Test
    fun `PATCH session final-price overflowing returns 400`() =
        assertPatch(
            400,
            "/api/sessions/$testSessionId/final-price",
            mapOf("finalPrice" to "9999999999.99", "version" to 1),
        )

    @Test
    fun `POST void session blank voidReason returns 400`() =
        assertPost(400, "/api/sessions/$testSessionId/void", mapOf("id" to uuid(), "voidReason" to "  "))

    @Test
    fun `POST unvoid session blank unvoidedReason returns 400`() =
        assertPost(400, "/api/sessions/$testSessionId/unvoid", mapOf("unvoidedReason" to "  "))

    @Test
    fun `POST promote concern blank label returns 400`() =
        assertPost(400, "/api/sessions/$testSessionId/promote-concern", mapOf("id" to uuid(), "label" to "  "))

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
        assertGet(200, "/api/sessions/$remittedSessionId/concerns")
    }

    // ──────────────────────────────────────────────
    // ClientRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST client blank firstName returns 400`() = assertPost(400, "/api/clients", clientBody("firstName" to "  "))

    @Test
    fun `POST client blank lastName returns 400`() = assertPost(400, "/api/clients", clientBody("lastName" to "  "))

    @Test
    fun `POST client partial BP systolic only returns 400`() =
        assertPost(400, "/api/clients", clientBody("systolicBp" to 120))

    @Test
    fun `POST client partial BP diastolic only returns 400`() =
        assertPost(400, "/api/clients", clientBody("diastolicBp" to 80))

    @Test
    fun `POST client out-of-range age returns 400`() = assertPost(400, "/api/clients", clientBody("age" to 999))

    @Test
    fun `POST client out-of-range BP returns 400`() =
        assertPost(400, "/api/clients", clientBody("systolicBp" to 10, "diastolicBp" to 80))

    @Test
    fun `POST client overlong phone returns 400`() =
        assertPost(400, "/api/clients", clientBody("phoneNumber" to "123456789012345678901"))

    @Test
    fun `GET clients blank search query returns 400`() = assertGet(400, "/api/clients?q=%20%20")

    @Test
    fun `GET clients missing search query returns 400`() = assertGet(400, "/api/clients")

    @Test
    fun `GET clients includes authoritative session count`() {
        val response = testServer.client.get("/api/clients?q=Test")
        val clients = json.decodeFromString<List<ClientResponse>>(response.body.string())

        assertEquals(200, response.code)
        assertEquals(1, clients.single { it.id == testClientId.toString() }.sessionCount)
        assertEquals(0, clients.single { it.id == testClientNoSessionsId.toString() }.sessionCount)
    }

    @Test
    fun `PATCH client blank firstName returns 400`() =
        assertPatch(400, "/api/clients/$testClientId", mapOf("firstName" to "  "))

    @Test
    fun `PATCH client blank lastName returns 400`() =
        assertPatch(400, "/api/clients/$testClientId", mapOf("lastName" to "  "))

    @Test
    fun `PATCH client partial BP returns 400`() =
        assertPatch(400, "/api/clients/$testClientId", mapOf("systolicBp" to 120))

    @Test
    fun `PATCH client out-of-range age returns 400`() =
        assertPatch(400, "/api/clients/$testClientId", mapOf("age" to 999))

    @Test
    fun `PATCH client out-of-range BP returns 400`() =
        assertPatch(400, "/api/clients/$testClientId", mapOf("systolicBp" to 120, "diastolicBp" to 10))

    @Test
    fun `PATCH client overlong phone returns 400`() =
        assertPatch(400, "/api/clients/$testClientId", mapOf("phoneNumber" to "123456789012345678901"))

    @Test
    fun `PATCH client clear phone persists null`() {
        assertEquals(
            200,
            testServer.client.patch("/api/clients/$testClientId", mapOf("phoneNumber" to "09171234567")).code,
        )
        val response =
            testServer.client.patch(
                "/api/clients/$testClientId",
                mapOf("clearFields" to listOf("phoneNumber")),
            )
        assertEquals(200, response.code)
        val updated = json.decodeFromString<ClientResponse>(response.body.string())
        assertNull(updated.phoneNumber)
        assertEquals("Test", updated.firstName)
    }

    @Test
    fun `PATCH client clear BP pair persists nulls`() {
        val seeded =
            testServer.client.patch(
                "/api/clients/$testClientId",
                mapOf("systolicBp" to 120, "diastolicBp" to 80),
            )
        assertEquals(200, seeded.code)
        val response =
            testServer.client.patch(
                "/api/clients/$testClientId",
                mapOf("clearFields" to listOf("systolicBp", "diastolicBp")),
            )
        assertEquals(200, response.code)
        val updated = json.decodeFromString<ClientResponse>(response.body.string())
        assertNull(updated.systolicBp)
        assertNull(updated.diastolicBp)
    }

    @Test
    fun `PATCH client unknown clear field returns 400`() =
        assertPatch(400, "/api/clients/$testClientId", mapOf("clearFields" to listOf("nickname")))

    @Test
    fun `PATCH client clear required field returns 400`() {
        assertPatch(400, "/api/clients/$testClientId", mapOf("clearFields" to listOf("firstName")))
        assertPatch(400, "/api/clients/$testClientId", mapOf("clearFields" to listOf("age")))
    }

    @Test
    fun `PATCH client half BP clear returns 400`() =
        assertPatch(400, "/api/clients/$testClientId", mapOf("clearFields" to listOf("systolicBp")))

    @Test
    fun `PATCH client set and clear on the same field returns 400`() =
        assertPatch(
            400,
            "/api/clients/$testClientId",
            mapOf("phoneNumber" to "0917", "clearFields" to listOf("phoneNumber")),
        )

    @Test
    fun `PATCH client blank optional returns 400 without storing whitespace`() =
        assertPatch(400, "/api/clients/$testClientId", mapOf("phoneNumber" to "   "))

    @Test
    fun `POST anonymize client with pending session returns 409 and keeps PII`() {
        val response = testServer.client.post("/api/clients/$testClientId/anonymize")

        assertEquals(409, response.code)
        transaction {
            val row = ClientTable.selectAll().where { ClientTable.id eq testClientId }.single()
            assertEquals("Test", row[ClientTable.firstName])
            assertEquals("Client", row[ClientTable.lastName])
            assertNull(row[ClientTable.deletedAt])
        }
    }

    @Test
    fun `POST anonymize client without sessions returns 204`() {
        val response = testServer.client.post("/api/clients/$testClientNoSessionsId/anonymize")

        assertEquals(204, response.code)
        transaction {
            val row =
                ClientTable.selectAll().where { ClientTable.id eq testClientNoSessionsId }.single()
            assertNull(row[ClientTable.firstName])
            assertNull(row[ClientTable.lastName])
        }
    }

    // ──────────────────────────────────────────────
    // ProductRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST product blank name returns 400`() = assertPost(400, "/api/products", product(name = "  "))

    @Test
    fun `POST product negative price returns 400`() = assertPost(400, "/api/products", product(unitPrice = "-1.00"))

    @Test
    fun `POST product negative commission returns 400`() =
        assertPost(400, "/api/products", product(commissionAmount = "-5.00"))

    @Test
    fun `POST product overflowing price returns 400`() =
        assertPost(400, "/api/products", product(unitPrice = "9999999999.99"))

    // ──────────────────────────────────────────────
    // BranchInventoryRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST restock zero quantity returns 400`() =
        assertPost(400, "/api/branches/$testBranchId/inventory/$testProductId/restock", restock(quantity = 0))

    @Test
    fun `POST restock negative quantity returns 400`() =
        assertPost(400, "/api/branches/$testBranchId/inventory/$testProductId/restock", restock(quantity = -1))

    @Test
    fun `POST movement invalid reason returns 400`() =
        assertPost(
            400,
            "/api/branches/$testBranchId/inventory/$testProductId/movement",
            movement(reason = "INVALID_REASON"),
        )

    @Test
    fun `POST movement RESTOCK reason is rejected by endpoint restriction`() =
        assertPost(
            400,
            "/api/branches/$testBranchId/inventory/$testProductId/movement",
            movement(reason = "RESTOCK", quantityChange = 1),
        )

    @Test
    fun `POST movement MISSING without notes returns 400`() =
        assertPost(
            400,
            "/api/branches/$testBranchId/inventory/$testProductId/movement",
            movement(reason = "MISSING"),
        )

    @Test
    fun `POST movement MISSING with blank notes returns 400`() =
        assertPost(
            400,
            "/api/branches/$testBranchId/inventory/$testProductId/movement",
            movement(reason = "MISSING", notes = "  "),
        )

    // ──────────────────────────────────────────────
    // ProductSaleRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST product sale zero quantity returns 400`() =
        assertPost(400, "/api/product-sales", productSale(quantity = 0))

    @Test
    fun `POST product sale quantity over smallint range returns 400`() =
        assertPost(400, "/api/product-sales", productSale(quantity = 40000))

    @Test
    fun `POST product sale sessionId plus clientId returns 400`() =
        assertPost(
            400,
            "/api/product-sales",
            productSale(sessionId = uuid(), clientId = testClientId.toString(), isWalkIn = false),
        )

    @Test
    fun `POST product sale sessionId with walkIn true returns 400`() =
        assertPost(400, "/api/product-sales", productSale(sessionId = uuid(), isWalkIn = true))

    @Test
    fun `POST product sale known client without walkIn returns 400`() =
        assertPost(
            400,
            "/api/product-sales",
            productSale(clientId = testClientId.toString(), isWalkIn = false),
        )

    @Test
    fun `POST product sale anonymous without walkIn returns 400`() =
        assertPost(400, "/api/product-sales", productSale(isWalkIn = false))

    // ──────────────────────────────────────────────
    // UserBranchAssignmentRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST assignment slot less than 1 returns 400`() =
        assertPost(
            400,
            "/api/branches/$testBranchId/assignments",
            mapOf("id" to uuid(), "userId" to uuid(), "slot" to 0),
        )

    @Test
    fun `PATCH assignment slot less than 1 returns 400`() =
        assertPatch(
            400,
            "/api/branches/$testBranchId/assignments/${TestFixtures.uuid()}/slot",
            mapOf("slot" to 0),
        )

    // ──────────────────────────────────────────────
    // RemittanceRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST remittance draft reversed date range returns 400`() =
        assertPost(
            400,
            "/api/remittances",
            mapOf(
                "id" to uuid(),
                "type" to "SESSION",
                "branchId" to testBranchId.toString(),
                "method" to "BANK_TRANSFER",
                "dateRangeStart" to "2024-12-31",
                "dateRangeEnd" to "2024-01-01",
            ),
        )

    @Test
    fun `POST remittance SESSION line without sessionId returns 400`() {
        val remittanceId = remittance()
        assertPost(400, "/api/remittances/$remittanceId/lines", remittanceLine(type = "SESSION"))
    }

    @Test
    fun `POST remittance SESSION line overflowing amount returns 400`() {
        val remittanceId = remittance()
        assertPost(
            400,
            "/api/remittances/$remittanceId/lines",
            remittanceLine(type = "SESSION", amount = "9999999999.99", sessionId = testSessionId.toString()),
        )
    }

    @Test
    fun `POST remittance PRODUCT_SALE line without productSaleId returns 400`() {
        val remittanceId = remittance()
        assertPost(400, "/api/remittances/$remittanceId/lines", remittanceLine(type = "PRODUCT_SALE"))
    }

    // ──────────────────────────────────────────────
    // ExportRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `GET export report missing format returns 400`() =
        assertGet(400, "/api/branches/$testBranchId/export/report?from=2024-01-15&to=2024-01-16")

    @Test
    fun `GET export report invalid format returns 400`() =
        assertGet(400, "/api/branches/$testBranchId/export/report?from=2024-01-15&to=2024-01-16&format=csv")

    @Test
    fun `GET export report missing from returns 400`() =
        assertGet(400, "/api/branches/$testBranchId/export/report?to=2024-01-15&format=xlsx")

    @Test
    fun `GET export report missing to returns 400`() =
        assertGet(400, "/api/branches/$testBranchId/export/report?from=2024-01-15&format=xlsx")

    @Test
    fun `GET export report invalid date returns 400`() =
        assertGet(400, "/api/branches/$testBranchId/export/report?from=not-a-date&to=2024-01-15&format=xlsx")

    @Test
    fun `GET export report from after to returns 400`() =
        assertGet(400, "/api/branches/$testBranchId/export/report?from=2024-01-16&to=2024-01-15&format=xlsx")

    @Test
    fun `GET export report multi-branch unknown type returns 400`() =
        assertGet(400, "/api/branches/export/report?from=2024-01-15&to=2024-01-16&format=xlsx&type=SPA")

    @Test
    fun `GET export report multi-branch missing format returns 400`() =
        assertGet(400, "/api/branches/export/report?from=2024-01-15&to=2024-01-16")

    // ──────────────────────────────────────────────
    // DailySalesSummaryRoutes — paged daily-summaries feed
    // ──────────────────────────────────────────────

    @Test
    fun `GET daily-summaries returns 200 with seeded day`() {
        val response = testServer.client.get("/api/branches/$testBranchId/daily-summaries")
        assertEquals(200, response.code)
        val body = json.decodeFromString<DailySalesSummaryBrowseResponse>(response.body.string())
        assertEquals(1, body.entries.size)
        assertEquals(testBranchDayId.toString(), body.entries.single().branchDayId)
        assertEquals(TestFixtures.today.toString(), body.entries.single().date)
        assertNull(body.nextCursor)
    }

    @Test
    fun `GET daily-summaries returns 200 empty feed for branch without days`() {
        val noDaysBranchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(noDaysBranchId, "No-Days Branch")
        val response = testServer.client.get("/api/branches/$noDaysBranchId/daily-summaries")
        assertEquals(200, response.code, "a feed is 200 not 404 when the branch has no days")
        val body = json.decodeFromString<DailySalesSummaryBrowseResponse>(response.body.string())
        assertTrue(body.entries.isEmpty())
        assertNull(body.nextCursor)
    }

    @Test
    fun `GET daily-summaries rejects limit zero`() =
        assertGet(400, "/api/branches/$testBranchId/daily-summaries?limit=0")

    @Test
    fun `GET daily-summaries rejects limit above max`() =
        assertGet(400, "/api/branches/$testBranchId/daily-summaries?limit=101")

    @Test
    fun `GET daily-summaries rejects non-numeric limit`() =
        assertGet(400, "/api/branches/$testBranchId/daily-summaries?limit=abc")

    @Test
    fun `GET daily-summaries rejects malformed cursor`() =
        assertGet(400, "/api/branches/$testBranchId/daily-summaries?cursor=not-a-cursor")

    @Test
    fun `GET daily-summaries rejects malformed from date`() =
        assertGet(400, "/api/branches/$testBranchId/daily-summaries?from=2026-13-99")

    @Test
    fun `GET daily-summaries rejects malformed to date`() =
        assertGet(400, "/api/branches/$testBranchId/daily-summaries?to=not-a-date")

    @Test
    fun `GET daily-summaries rejects from after to`() {
        val tomorrow = TestFixtures.today.plusDays(1)
        val yesterday = TestFixtures.today.minusDays(1)
        assertGet(400, "/api/branches/$testBranchId/daily-summaries?from=$tomorrow&to=$yesterday")
    }

    @Test
    fun `GET daily-summaries window filters the feed`() {
        val today = TestFixtures.today
        val yesterday = today.minusDays(1)
        val response =
            testServer.client.get(
                "/api/branches/$testBranchId/daily-summaries?from=$yesterday&to=$today",
            )
        assertEquals(200, response.code)
        val body = json.decodeFromString<DailySalesSummaryBrowseResponse>(response.body.string())
        assertEquals(1, body.entries.size, "the window admits only the seeded day")
        assertEquals(today.toString(), body.entries.single().date)
    }

    // ──────────────────────────────────────────────
    // NotificationRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST notifications read-all returns 200 and marks all unread as read`() {
        val notificationId = notification()
        val response = testServer.client.post("/api/notifications/read-all")
        assertEquals(200, response.code)
        assertTrue(response.body.string().contains("\"unreadCount\":0"))
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
        val notificationId = notification()
        assertPatch(200, "/api/notifications/$notificationId/read")
    }

    @Test
    fun `GET notifications unread-count returns count without rows`() {
        val response = testServer.client.get("/api/notifications/unread-count")
        assertEquals(200, response.code)
        val body = json.decodeFromString<NotificationUnreadCountResponse>(response.body.string())
        assertEquals(0, body.unreadCount)
    }

    @Test
    fun `GET notifications history pages bounded entries with cursor`() {
        val first = testServer.client.get("/api/notifications/history?limit=1")
        assertEquals(200, first.code)
        val firstBody = json.decodeFromString<NotificationHistoryResponse>(first.body.string())
        assertTrue(firstBody.entries.size <= 1)

        val second = testServer.client.get("/api/notifications/history?cursor=bad-cursor&limit=1")
        assertEquals(400, second.code)
    }

    // ──────────────────────────────────────────────
    // ExpenseRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST expense non-positive amount returns 400`() = assertPost(400, "/api/expenses", expense(amount = "-0.01"))

    @Test
    fun `POST expense zero amount returns 400`() = assertPost(400, "/api/expenses", expense(amount = "0.00"))

    @Test
    fun `POST expense overflowing amount returns 400`() =
        assertPost(400, "/api/expenses", expense(amount = "9999999999.99"))

    @Test
    fun `POST expense invalid category returns 400`() = assertPost(400, "/api/expenses", expense(category = "INVALID"))

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
        assertDelete(400, "/api/expenses/$expenseId", mapOf("reason" to "  ", "expectedVersion" to 1))
    }
}
