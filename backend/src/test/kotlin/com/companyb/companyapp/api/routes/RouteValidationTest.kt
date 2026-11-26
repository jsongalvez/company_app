package com.companyb.companyapp.api.routes

import com.companyb.companyapp.commerce.BranchInventoryRoutes
import com.companyb.companyapp.commerce.ProductRoutes
import com.companyb.companyapp.commerce.ProductSaleRoutes
import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.finance.AllowanceRoutes
import com.companyb.companyapp.finance.CompensationRoutes
import com.companyb.companyapp.finance.CompensationTable
import com.companyb.companyapp.finance.ExpenseRoutes
import com.companyb.companyapp.finance.ExpenseTable
import com.companyb.companyapp.remittance.RemittanceRoutes
import com.companyb.companyapp.remittance.RemittanceTable
import com.companyb.companyapp.session.SessionRoutes
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import com.companyb.companyapp.workforce.UserBranchAssignmentRoutes
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Route validation for allowances, compensation, sessions, products, stock, product sales, assignments, remittances
 * and expenses.
 */
class RouteValidationTest : RouteValidationBase() {
    private fun assertDelete(
        code: Int,
        path: String,
        body: Map<String, Any?>? = null,
    ) {
        val response = body?.let { testServer.client.delete(path, it) } ?: testServer.client.delete(path)
        assertEquals(code, response.code, "DELETE $path $body")
    }

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
