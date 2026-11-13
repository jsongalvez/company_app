@file:Suppress("LargeClass") // #596

package com.companyb.companyapp.api.routes

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.authorization.Role
import com.companyb.companyapp.branchday.BranchDayRoutes
import com.companyb.companyapp.contracts.finance.ExpenseCategory
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
    private val globalOnlyUser = TestFixtures.uuid()
    private val targetUser1 = TestFixtures.uuid()
    private val targetUser2 = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()

    private lateinit var branchDayId: UUID
    private lateinit var otherBranchDayId: UUID
    private lateinit var expenseId: UUID

    override fun initTestData() {
        seedUsersAndBranches()
        seedDaysAndGrants()
        seedCompensationsAndExpense()
    }

    private fun seedUsersAndBranches() {
        listOf(
            assignUser to "assign",
            editOnlyUser to "edit",
            noneUser to "no-caps",
            globalOnlyUser to "global-only",
            targetUser1 to "target-1",
            targetUser2 to "target-2",
        ).forEach { (id, prefix) ->
            IdentityFixtures.insertTestUser(id, prefix)
        }

        BranchWorkforceFixtures.insertTestBranch(branchId, "Finance Branch $branchId")
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Other Finance Branch $otherBranchId")
    }

    private fun seedDaysAndGrants() {
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        otherBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(otherBranchId)

        IdentityFixtures.member(assignUser, branchId, Role.COORDINATOR)
        // An owner with no assignment pays everywhere but edits no branch day.
        IdentityFixtures.hold(globalOnlyUser, Role.OWNER)
        IdentityFixtures.member(editOnlyUser, branchId)
    }

    private fun seedCompensationsAndExpense() {
        CommerceFinanceFixtures.insertTestCompensation(branchDayId, targetUser1, BigDecimal("1500.00"), assignUser)
        CommerceFinanceFixtures.insertTestCompensation(branchDayId, targetUser2, BigDecimal("1200.00"), assignUser)

        BranchWorkforceFixtures.insertTestAttendance(branchDayId, targetUser1)
        BranchWorkforceFixtures.insertTestAttendance(branchDayId, targetUser2)
        BranchWorkforceFixtures.insertTestAttendance(otherBranchDayId, targetUser1)

        expenseId = newExpense(notes = null)
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

    private fun assertGet(
        code: Int,
        path: String,
        user: UUID,
    ) {
        val response = testServer.client.get(path, asUser(user))
        assertEquals(code, response.code, "GET $path as $user")
    }

    private fun assertPost(
        code: Int,
        path: String,
        body: Any,
        user: UUID,
    ) {
        val response = testServer.client.post(path, body, asUser(user))
        assertEquals(code, response.code, "POST $path as $user")
    }

    private fun assertPatch(
        code: Int,
        path: String,
        body: Any,
        user: UUID,
    ) {
        val response = testServer.client.patch(path, body, asUser(user))
        assertEquals(code, response.code, "PATCH $path as $user")
    }

    private fun assertDelete(
        code: Int,
        path: String,
        body: Any,
        user: UUID,
    ) {
        val response = testServer.client.delete(path, body, asUser(user))
        assertEquals(code, response.code, "DELETE $path as $user")
    }

    private fun newExpense(
        branchDayId: UUID = this.branchDayId,
        amount: String = "200.00",
        notes: String? = null,
    ): UUID {
        val id = TestFixtures.uuid()
        ExpenseService.create(
            callerId = editOnlyUser,
            id = id,
            branchDayId = branchDayId,
            amount = BigDecimal(amount),
            category = ExpenseCategory.WATER,
            notes = notes,
        )
        return id
    }

    private fun deletedExpense(branchDayId: UUID = this.branchDayId): UUID {
        val id = newExpense(branchDayId = branchDayId)
        ExpenseService.softDelete(
            callerId = editOnlyUser,
            expenseId = id,
            reason = "Incorrect entry",
            expectedVersion = 1,
        )
        return id
    }

    private fun patchBody(
        amount: String = "750.00",
        category: String = "WATER",
        expectedVersion: Int = 1,
    ) = mapOf(
        "amount" to amount,
        "category" to category,
        "notes" to "Updated",
        "expectedVersion" to expectedVersion,
    )

    private val noBody = emptyMap<String, String>()

    // ──────────────────────────────────────────────
    // GET /api/compensations?branchDayId= → ASSIGN_COMPENSATION
    // ──────────────────────────────────────────────

    @Test
    fun `GET compensations allowed for ASSIGN_COMPENSATION user`() {
        val response =
            testServer.client.get("/api/compensations?branchDayId=$branchDayId", asUser(assignUser))
        assertEquals(200, response.code)
        val body = response.body.string().orEmpty()
        assertTrue(body.contains("\"userName\":\"Test target-1\""))
        assertTrue(body.contains("\"userName\":\"Test target-2\""))
    }

    @Test
    fun `GET compensations returns only rows for requested branch day`() {
        val yesterday = TestFixtures.today.minusDays(1)
        val otherDayBranchDayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, yesterday)
        val response =
            testServer.client.get("/api/compensations?branchDayId=$otherDayBranchDayId", asUser(assignUser))
        assertEquals(200, response.code)
        assertTrue(
            response.body
                .string()
                .orEmpty()
                .startsWith("[]"),
        )
    }

    @Test
    fun `GET compensations forbidden for EDIT_BRANCH_DATA-only user`() =
        assertGet(403, "/api/compensations?branchDayId=$branchDayId", editOnlyUser)

    // Owed #25: the Owner pays members at every branch, so a GLOBAL pay grant counts everywhere.
    // The web's pay controls look this answer up (web/tests/unit/rights.test.ts).
    @Test
    fun `GET compensations and branch-day users allowed for a GLOBAL-only ASSIGN_COMPENSATION holder`() {
        assertGet(200, "/api/compensations?branchDayId=$branchDayId", globalOnlyUser)
        assertGet(200, "/api/branch-days/$branchDayId/users", globalOnlyUser)
    }

    @Test
    fun `GET compensations forbidden for no-capability user`() =
        assertGet(403, "/api/compensations?branchDayId=$branchDayId", noneUser)

    @Test
    fun `GET compensations forbidden for ASSIGN_COMPENSATION user on other branch`() =
        assertGet(403, "/api/compensations?branchDayId=$otherBranchDayId", assignUser)

    @Test
    fun `GET compensations returns 404 for missing branch day with grant`() {
        val missingBranchId = TestFixtures.uuid()
        assertGet(404, "/api/compensations?branchDayId=$missingBranchId", assignUser)
    }

    // ──────────────────────────────────────────────
    // GET /api/branch-days/{branchDayId}/users → ASSIGN_COMPENSATION
    // ──────────────────────────────────────────────

    @Test
    fun `GET branch-day users allowed for ASSIGN_COMPENSATION user with names`() {
        val response =
            testServer.client.get("/api/branch-days/$branchDayId/users", asUser(assignUser))
        assertEquals(200, response.code)
        val body = response.body.string().orEmpty()
        assertTrue(body.contains("\"displayName\":\"Test target-1\""))
        assertTrue(body.contains("\"displayName\":\"Test target-2\""))
    }

    @Test
    fun `GET branch-day users returns only users of requested day`() {
        val yesterday = TestFixtures.today.minusDays(1)
        val otherDayBranchDayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, yesterday)
        BranchWorkforceFixtures.insertTestAttendance(otherDayBranchDayId, targetUser1)
        val response =
            testServer.client.get("/api/branch-days/$otherDayBranchDayId/users", asUser(assignUser))
        assertEquals(200, response.code)
        val body = response.body.string().orEmpty()
        assertTrue(body.contains("\"displayName\":\"Test target-1\""))
        assertTrue(!body.contains("\"displayName\":\"Test target-2\""))
    }

    @Test
    fun `GET branch-day users forbidden for EDIT_BRANCH_DATA-only user`() =
        assertGet(403, "/api/branch-days/$branchDayId/users", editOnlyUser)

    @Test
    fun `GET branch-day users forbidden for no-capability user`() =
        assertGet(403, "/api/branch-days/$branchDayId/users", noneUser)

    @Test
    fun `GET branch-day users forbidden on other branch`() =
        assertGet(403, "/api/branch-days/$otherBranchDayId/users", assignUser)

    @Test
    fun `GET branch-day users returns 404 for missing branch day with grant`() {
        val missingBranchId = TestFixtures.uuid()
        assertGet(404, "/api/branch-days/$missingBranchId/users", assignUser)
    }

    // ──────────────────────────────────────────────
    // PATCH /api/expenses/{expenseId} → EDIT_BRANCH_DATA
    // ──────────────────────────────────────────────

    @Test
    fun `PATCH expense allowed for EDIT_BRANCH_DATA user with version bump`() {
        val response =
            testServer.client.patch("/api/expenses/$expenseId", patchBody(), asUser(editOnlyUser))
        assertEquals(200, response.code)
        val body = response.body.string().orEmpty()
        assertTrue(body.contains("\"version\":2"))
        assertTrue(body.contains("\"amount\":\"750.00\""))
        assertTrue(body.contains("\"category\":\"WATER\""))
    }

    @Test
    fun `PATCH expense forbidden for an owner with no assignment here`() =
        assertPatch(403, "/api/expenses/$expenseId", patchBody(), globalOnlyUser)

    @Test
    fun `PATCH expense forbidden for no-capability user`() =
        assertPatch(403, "/api/expenses/$expenseId", patchBody(), noneUser)

    @Test
    fun `PATCH expense forbidden on other branch`() {
        val otherExpenseId = newExpense(branchDayId = otherBranchDayId, amount = "300.00")
        assertPatch(403, "/api/expenses/$otherExpenseId", patchBody(amount = "400.00"), editOnlyUser)
    }

    @Test
    fun `PATCH expense returns 409 on version mismatch`() =
        assertPatch(409, "/api/expenses/$expenseId", patchBody(expectedVersion = 999), editOnlyUser)

    @Test
    fun `DELETE expense returns 409 on stale version`() =
        assertDelete(
            409,
            "/api/expenses/$expenseId",
            mapOf("reason" to "Stale delete", "expectedVersion" to 999),
            editOnlyUser,
        )

    @Test
    fun `DELETE expense succeeds with fresh version and bumps version`() {
        val deleteId = newExpense(amount = "120.00")
        val response =
            testServer.client.delete(
                "/api/expenses/$deleteId",
                mapOf("reason" to "Incorrect entry", "expectedVersion" to 1),
                asUser(editOnlyUser),
            )
        assertEquals(200, response.code)
        val body = response.body.string().orEmpty()
        assertTrue(body.contains("\"version\":2"), "#663 — delete bumps version 1→2")
        assertTrue(body.contains("\"deletedReason\":\"Incorrect entry\""))
    }

    @Test
    fun `PATCH expense returns 400 on invalid amount`() =
        assertPatch(400, "/api/expenses/$expenseId", patchBody(amount = "-5.00"), editOnlyUser)

    @Test
    fun `PATCH expense returns 400 on invalid category`() =
        assertPatch(400, "/api/expenses/$expenseId", patchBody(category = "NOT_A_CATEGORY"), editOnlyUser)

    // ──────────────────────────────────────────────
    // POST /api/expenses/{expenseId}/restore → EDIT_BRANCH_DATA (#153)
    // ──────────────────────────────────────────────

    @Test
    fun `POST restore soft-deleted expense succeeds and clears deletion fields`() {
        val deletedId = deletedExpense()
        val response =
            testServer.client.post(
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
        assertTrue(
            body.contains("\"version\":2"),
            "#153 Q6 — no version bump on restore (#663: delete bumps 1→2, restore keeps 2)",
        )
    }

    @Test
    fun `POST restore forbidden for an owner with no assignment here`() {
        val deletedId = deletedExpense()
        assertPost(403, "/api/expenses/$deletedId/restore", noBody, globalOnlyUser)
    }

    @Test
    fun `POST restore forbidden for no-capability user`() {
        val deletedId = deletedExpense()
        assertPost(403, "/api/expenses/$deletedId/restore", noBody, noneUser)
    }

    @Test
    fun `POST restore forbidden on other branch`() {
        val otherDeletedId = deletedExpense(branchDayId = otherBranchDayId)
        assertPost(403, "/api/expenses/$otherDeletedId/restore", noBody, editOnlyUser)
    }

    @Test
    fun `POST restore returns 404 for missing expense`() =
        assertPost(404, "/api/expenses/${TestFixtures.uuid()}/restore", noBody, editOnlyUser)

    @Test
    fun `POST restore returns 400 for already-live expense`() =
        assertPost(400, "/api/expenses/$expenseId/restore", noBody, editOnlyUser)

    @Test
    fun `POST restore returns 400 on second restore - double-restore race window`() {
        val deletedId = deletedExpense()
        assertPost(200, "/api/expenses/$deletedId/restore", noBody, editOnlyUser)
        assertPost(400, "/api/expenses/$deletedId/restore", noBody, editOnlyUser)
    }

    @Test
    fun `GET expenses includes soft-deleted rows with reason`() {
        deletedExpense()
        val response = testServer.client.get("/api/expenses?branchDayId=$branchDayId", asUser(editOnlyUser))
        assertEquals(200, response.code)
        assertTrue(
            response.body
                .string()
                .orEmpty()
                .contains("\"deletedReason\":\"Incorrect entry\""),
        )
    }
}
