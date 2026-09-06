@file:Suppress("LargeClass")

package com.companyb.companyapp.api.routes
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.branchday.BranchDayRoutes
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.ExpenseCategory
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.finance.CompensationRoutes
import com.companyb.companyapp.finance.ExpenseRoutes
import com.companyb.companyapp.finance.ExpenseService
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
import java.math.BigDecimal
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FinanceReadBackAuthzTest : BasePostgresTest() {
    private val assignUser = TestFixtures.uuid()
    private val editOnlyUser = TestFixtures.uuid()
    private val noneUser = TestFixtures.uuid()
    private val targetUser1 = TestFixtures.uuid()
    private val targetUser2 = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    private lateinit var branchDayId: UUID
    private lateinit var otherBranchDayId: UUID
    private lateinit var expenseId: UUID

    @Suppress("LongMethod")
    override fun initTestData() {
        listOf(
            assignUser to "assign",
            editOnlyUser to "edit",
            noneUser to "no-caps",
            targetUser1 to "target-1",
            targetUser2 to "target-2",
        ).forEach { (id, prefix) ->
            IdentityFixtures.insertTestUser(id, prefix)
        }

        BranchWorkforceFixtures.insertTestBranch(branchId, "Finance Branch $branchId")
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Other Finance Branch $otherBranchId")

        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        otherBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(otherBranchId)

        IdentityFixtures.grantCapability(
            userId = assignUser,
            capabilityCode = CapabilityCodes.ASSIGN_COMPENSATION,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
        IdentityFixtures.grantCapability(
            userId = editOnlyUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )

        CommerceFinanceFixtures.insertTestCompensation(branchDayId, targetUser1, BigDecimal("1500.00"), assignUser)
        CommerceFinanceFixtures.insertTestCompensation(branchDayId, targetUser2, BigDecimal("1200.00"), assignUser)

        BranchWorkforceFixtures.insertTestAttendance(branchDayId, targetUser1)
        BranchWorkforceFixtures.insertTestAttendance(branchDayId, targetUser2)
        BranchWorkforceFixtures.insertTestAttendance(otherBranchDayId, targetUser1)

        expenseId = TestFixtures.uuid()
        ExpenseService.create(
            callerId = editOnlyUser,
            id = expenseId,
            branchDayId = branchDayId,
            amount = BigDecimal("500.00"),
            category = ExpenseCategory.PANTRY,
            notes = null,
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
                cfg.routes.exception(ConflictException::class.java) { e, ctx ->
                    ctx.status(409).json(mapOf("error" to (e.message ?: "Conflict")))
                }
                cfg.routes.exception(ValidationException::class.java) { e, ctx ->
                    ctx.status(400).json(mapOf("error" to (e.message ?: "Bad Request")))
                }
                CompensationRoutes.register(cfg)
                BranchDayRoutes.register(cfg)
                ExpenseRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun grantAssignOnBranch(
        userId: UUID,
        branchId: UUID,
    ) {
        IdentityFixtures.grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.ASSIGN_COMPENSATION,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
    }

    // ──────────────────────────────────────────────
    // GET /api/compensations?branchDayId= → ASSIGN_COMPENSATION
    // ──────────────────────────────────────────────

    @Test
    fun `GET compensations allowed for ASSIGN_COMPENSATION user`() {
        testServer.client.let { client ->
            val response =
                client.get(
                    "/api/compensations?branchDayId=$branchDayId",
                    asUser(assignUser),
                )
            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.contains("\"userName\":\"Test target-1\""))
            assertTrue(body.contains("\"userName\":\"Test target-2\""))
        }
    }

    @Test
    fun `GET compensations returns only rows for requested branch day`() {
        val yesterday = TestFixtures.today.minusDays(1)
        val otherDayBranchDayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, yesterday)
        testServer.client.let { client ->
            val response =
                client.get(
                    "/api/compensations?branchDayId=$otherDayBranchDayId",
                    asUser(assignUser),
                )
            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.startsWith("[]"))
        }
    }

    @Test
    fun `GET compensations forbidden for EDIT_BRANCH_DATA-only user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/compensations?branchDayId=$branchDayId", asUser(editOnlyUser)).code,
            )
        }
    }

    @Test
    fun `GET compensations forbidden for no-capability user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/compensations?branchDayId=$branchDayId", asUser(noneUser)).code,
            )
        }
    }

    @Test
    fun `GET compensations forbidden for ASSIGN_COMPENSATION user on other branch`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/compensations?branchDayId=$otherBranchDayId", asUser(assignUser)).code,
            )
        }
    }

    @Test
    fun `GET compensations returns 404 for missing branch day with grant`() {
        val missingBranchId = TestFixtures.uuid()
        grantAssignOnBranch(assignUser, missingBranchId)
        testServer.client.let { client ->
            assertEquals(
                404,
                client.get("/api/compensations?branchDayId=$missingBranchId", asUser(assignUser)).code,
            )
        }
    }

    // ──────────────────────────────────────────────
    // GET /api/branch-days/{branchDayId}/users → ASSIGN_COMPENSATION
    // ──────────────────────────────────────────────

    @Test
    fun `GET branch-day users allowed for ASSIGN_COMPENSATION user with names`() {
        testServer.client.let { client ->
            val response =
                client.get(
                    "/api/branch-days/$branchDayId/users",
                    asUser(assignUser),
                )
            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.contains("\"displayName\":\"Test target-1\""))
            assertTrue(body.contains("\"displayName\":\"Test target-2\""))
        }
    }

    @Test
    fun `GET branch-day users returns only users of requested day`() {
        val yesterday = TestFixtures.today.minusDays(1)
        val otherDayBranchDayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, yesterday)
        BranchWorkforceFixtures.insertTestAttendance(otherDayBranchDayId, targetUser1)
        testServer.client.let { client ->
            val response =
                client.get(
                    "/api/branch-days/$otherDayBranchDayId/users",
                    asUser(assignUser),
                )
            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.contains("\"displayName\":\"Test target-1\""))
            assertTrue(!body.contains("\"displayName\":\"Test target-2\""))
        }
    }

    @Test
    fun `GET branch-day users forbidden for EDIT_BRANCH_DATA-only user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/branch-days/$branchDayId/users", asUser(editOnlyUser)).code,
            )
        }
    }

    @Test
    fun `GET branch-day users forbidden for no-capability user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/branch-days/$branchDayId/users", asUser(noneUser)).code,
            )
        }
    }

    @Test
    fun `GET branch-day users forbidden on other branch`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/branch-days/$otherBranchDayId/users", asUser(assignUser)).code,
            )
        }
    }

    @Test
    fun `GET branch-day users returns 404 for missing branch day with grant`() {
        val missingBranchId = TestFixtures.uuid()
        grantAssignOnBranch(assignUser, missingBranchId)
        testServer.client.let { client ->
            assertEquals(
                404,
                client.get("/api/branch-days/$missingBranchId/users", asUser(assignUser)).code,
            )
        }
    }

    // ──────────────────────────────────────────────
    // PATCH /api/expenses/{expenseId} → EDIT_BRANCH_DATA
    // ──────────────────────────────────────────────

    @Test
    fun `PATCH expense allowed for EDIT_BRANCH_DATA user with version bump`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "amount" to "750.00",
                    "category" to "WATER",
                    "notes" to "Updated",
                    "expectedVersion" to 1,
                )
            val response =
                client.patch(
                    "/api/expenses/$expenseId",
                    body,
                    asUser(editOnlyUser),
                )
            assertEquals(200, response.code)
            val responseBody = response.body.string().orEmpty()
            assertTrue(responseBody.contains("\"version\":2"))
            assertTrue(responseBody.contains("\"amount\":\"750.00\""))
            assertTrue(responseBody.contains("\"category\":\"WATER\""))
        }
    }

    @Test
    fun `PATCH expense forbidden for ASSIGN_COMPENSATION-only user`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "amount" to "750.00",
                    "category" to "WATER",
                    "notes" to "Updated",
                    "expectedVersion" to 1,
                )
            assertEquals(
                403,
                client.patch("/api/expenses/$expenseId", body, asUser(assignUser)).code,
            )
        }
    }

    @Test
    fun `PATCH expense forbidden for no-capability user`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "amount" to "750.00",
                    "category" to "WATER",
                    "notes" to "Updated",
                    "expectedVersion" to 1,
                )
            assertEquals(
                403,
                client.patch("/api/expenses/$expenseId", body, asUser(noneUser)).code,
            )
        }
    }

    @Test
    fun `PATCH expense forbidden on other branch`() {
        val otherExpenseId = TestFixtures.uuid()
        ExpenseService.create(
            callerId = editOnlyUser,
            id = otherExpenseId,
            branchDayId = otherBranchDayId,
            amount = BigDecimal("300.00"),
            category = ExpenseCategory.WATER,
            notes = null,
        )
        testServer.client.let { client ->
            val body =
                mapOf(
                    "amount" to "400.00",
                    "category" to "WATER",
                    "notes" to "Updated",
                    "expectedVersion" to 1,
                )
            assertEquals(
                403,
                client.patch("/api/expenses/$otherExpenseId", body, asUser(editOnlyUser)).code,
            )
        }
    }

    @Test
    fun `PATCH expense returns 409 on version mismatch`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "amount" to "750.00",
                    "category" to "WATER",
                    "notes" to "Updated",
                    "expectedVersion" to 999,
                )
            assertEquals(
                409,
                client.patch("/api/expenses/$expenseId", body, asUser(editOnlyUser)).code,
            )
        }
    }

    @Test
    fun `PATCH expense returns 400 on invalid amount`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "amount" to "-5.00",
                    "category" to "WATER",
                    "notes" to "Updated",
                    "expectedVersion" to 1,
                )
            assertEquals(
                400,
                client.patch("/api/expenses/$expenseId", body, asUser(editOnlyUser)).code,
            )
        }
    }

    @Test
    fun `PATCH expense returns 400 on invalid category`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "amount" to "750.00",
                    "category" to "NOT_A_CATEGORY",
                    "notes" to "Updated",
                    "expectedVersion" to 1,
                )
            assertEquals(
                400,
                client.patch("/api/expenses/$expenseId", body, asUser(editOnlyUser)).code,
            )
        }
    }

    // ──────────────────────────────────────────────
    // POST /api/expenses/{expenseId}/restore → EDIT_BRANCH_DATA (#153)
    // ──────────────────────────────────────────────

    @Test
    fun `POST restore soft-deleted expense succeeds and clears deletion fields`() {
        val deletedId = TestFixtures.uuid()
        ExpenseService.create(
            callerId = editOnlyUser,
            id = deletedId,
            branchDayId = branchDayId,
            amount = BigDecimal("200.00"),
            category = ExpenseCategory.WATER,
            notes = "To delete",
        )
        ExpenseService.softDelete(
            callerId = editOnlyUser,
            expenseId = deletedId,
            reason = "Incorrect entry",
        )
        testServer.client.let { client ->
            val response =
                client.post(
                    "/api/expenses/$deletedId/restore",
                    mapOf("reason" to "Reversed per review"),
                    asUser(editOnlyUser),
                )
            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.contains("\"deletedAt\":null"))
            // The backend mapper encodes defaults only when non-null — a null defaulted field
            // is omitted from the JSON entirely (assert the field's absence, not `:null`).
            assertTrue(!body.contains("\"deletedReason\""))
            assertTrue(body.contains("\"version\":1"), "#153 Q6 — no version bump on restore")
        }
    }

    @Test
    fun `POST restore forbidden for ASSIGN_COMPENSATION-only user`() {
        val deletedId = TestFixtures.uuid()
        ExpenseService.create(
            callerId = editOnlyUser,
            id = deletedId,
            branchDayId = branchDayId,
            amount = BigDecimal("200.00"),
            category = ExpenseCategory.WATER,
            notes = null,
        )
        ExpenseService.softDelete(
            callerId = editOnlyUser,
            expenseId = deletedId,
            reason = "Incorrect entry",
        )
        testServer.client.let { client ->
            assertEquals(
                403,
                client
                    .post(
                        "/api/expenses/$deletedId/restore",
                        emptyMap<String, String>(),
                        asUser(assignUser),
                    ).code,
            )
        }
    }

    @Test
    fun `POST restore forbidden for no-capability user`() {
        val deletedId = TestFixtures.uuid()
        ExpenseService.create(
            callerId = editOnlyUser,
            id = deletedId,
            branchDayId = branchDayId,
            amount = BigDecimal("200.00"),
            category = ExpenseCategory.WATER,
            notes = null,
        )
        ExpenseService.softDelete(
            callerId = editOnlyUser,
            expenseId = deletedId,
            reason = "Incorrect entry",
        )
        testServer.client.let { client ->
            assertEquals(
                403,
                client
                    .post(
                        "/api/expenses/$deletedId/restore",
                        emptyMap<String, String>(),
                        asUser(noneUser),
                    ).code,
            )
        }
    }

    @Test
    fun `POST restore forbidden on other branch`() {
        val otherDeletedId = TestFixtures.uuid()
        ExpenseService.create(
            callerId = editOnlyUser,
            id = otherDeletedId,
            branchDayId = otherBranchDayId,
            amount = BigDecimal("200.00"),
            category = ExpenseCategory.WATER,
            notes = null,
        )
        ExpenseService.softDelete(
            callerId = editOnlyUser,
            expenseId = otherDeletedId,
            reason = "Incorrect entry",
        )
        testServer.client.let { client ->
            assertEquals(
                403,
                client
                    .post(
                        "/api/expenses/$otherDeletedId/restore",
                        emptyMap<String, String>(),
                        asUser(editOnlyUser),
                    ).code,
            )
        }
    }

    @Test
    fun `POST restore returns 404 for missing expense`() {
        testServer.client.let { client ->
            assertEquals(
                404,
                client
                    .post(
                        "/api/expenses/${TestFixtures.uuid()}/restore",
                        emptyMap<String, String>(),
                        asUser(editOnlyUser),
                    ).code,
            )
        }
    }

    @Test
    fun `POST restore returns 400 for already-live expense`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client
                    .post(
                        "/api/expenses/$expenseId/restore",
                        emptyMap<String, String>(),
                        asUser(editOnlyUser),
                    ).code,
            )
        }
    }

    @Test
    fun `POST restore returns 400 on second restore - double-restore race window`() {
        val deletedId = TestFixtures.uuid()
        ExpenseService.create(
            callerId = editOnlyUser,
            id = deletedId,
            branchDayId = branchDayId,
            amount = BigDecimal("200.00"),
            category = ExpenseCategory.WATER,
            notes = null,
        )
        ExpenseService.softDelete(
            callerId = editOnlyUser,
            expenseId = deletedId,
            reason = "Incorrect entry",
        )
        testServer.client.let { client ->
            val first =
                client.post(
                    "/api/expenses/$deletedId/restore",
                    emptyMap<String, String>(),
                    asUser(editOnlyUser),
                )
            val second =
                client.post(
                    "/api/expenses/$deletedId/restore",
                    emptyMap<String, String>(),
                    asUser(editOnlyUser),
                )
            assertEquals(200, first.code)
            assertEquals(400, second.code, "restored row is already live — 400 not 200")
        }
    }

    @Test
    fun `GET expenses includes soft-deleted rows with reason`() {
        val deletedId = TestFixtures.uuid()
        ExpenseService.create(
            callerId = editOnlyUser,
            id = deletedId,
            branchDayId = branchDayId,
            amount = BigDecimal("200.00"),
            category = ExpenseCategory.WATER,
            notes = null,
        )
        ExpenseService.softDelete(
            callerId = editOnlyUser,
            expenseId = deletedId,
            reason = "Incorrect entry",
        )
        testServer.client.let { client ->
            val response =
                client.get(
                    "/api/expenses?branchDayId=$branchDayId",
                    asUser(editOnlyUser),
                )
            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.contains("\"deletedReason\":\"Incorrect entry\""))
        }
    }
}
