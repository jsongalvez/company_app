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
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.CompensationTable
import com.companyb.companyapp.repository.model.ExpenseCategory
import com.companyb.companyapp.repository.model.ExpenseTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.ExpenseService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.Javalin
import io.javalin.testtools.JavalinTest
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.jdbc.Database
import java.math.BigDecimal
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FinanceReadBackAuthzTest : BasePostgresTest() {
    private val assignUser = UUID.randomUUID()
    private val editOnlyUser = UUID.randomUUID()
    private val noneUser = UUID.randomUUID()
    private val targetUser1 = UUID.randomUUID()
    private val targetUser2 = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val otherBranchId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()

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
            DatabaseTestHelper.insertTestUser(id, prefix)
            trackOwned(AppUserTable, AppUserTable.id, id)
        }

        trackOwned(BranchTable, BranchTable.id, branchId)
        DatabaseTestHelper.insertTestBranch(branchId, "Finance Branch $branchId")
        trackOwned(BranchTable, BranchTable.id, otherBranchId)
        DatabaseTestHelper.insertTestBranch(otherBranchId, "Other Finance Branch $otherBranchId")

        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        otherBranchDayId = DatabaseTestHelper.createBranchDayForToday(otherBranchId)
        trackOwned(BranchDayTable, BranchDayTable.id, branchDayId)
        trackOwned(BranchDayTable, BranchDayTable.id, otherBranchDayId)

        DatabaseTestHelper.grantCapability(
            userId = assignUser,
            capabilityCode = CapabilityCodes.ASSIGN_COMPENSATION,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
        DatabaseTestHelper.grantCapability(
            userId = editOnlyUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, assignUser)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, editOnlyUser)

        DatabaseTestHelper.insertTestCompensation(branchDayId, targetUser1, BigDecimal("1500.00"), assignUser)
        DatabaseTestHelper.insertTestCompensation(branchDayId, targetUser2, BigDecimal("1200.00"), assignUser)
        trackOwned(CompensationTable, CompensationTable.userId, targetUser1)
        trackOwned(CompensationTable, CompensationTable.userId, targetUser2)
        trackOwned(CompensationTable, CompensationTable.assignedBy, assignUser)

        DatabaseTestHelper.insertTestAttendance(branchDayId, targetUser1)
        DatabaseTestHelper.insertTestAttendance(branchDayId, targetUser2)
        DatabaseTestHelper.insertTestAttendance(otherBranchDayId, targetUser1)
        trackOwned(AttendanceTable, AttendanceTable.userId, targetUser1)
        trackOwned(AttendanceTable, AttendanceTable.userId, targetUser2)
        trackOwned(AttendanceTable, AttendanceTable.branchDayId, branchDayId)
        trackOwned(AttendanceTable, AttendanceTable.branchDayId, otherBranchDayId)

        expenseId = UUID.randomUUID()
        ExpenseService.create(
            callerId = editOnlyUser,
            id = expenseId,
            branchDayId = branchDayId,
            amount = BigDecimal("500.00"),
            category = ExpenseCategory.PANTRY,
            notes = null,
        )
        trackOwned(ExpenseTable, ExpenseTable.createdBy, editOnlyUser)
        trackOwned(ExpenseTable, ExpenseTable.branchDayId, branchDayId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, editOnlyUser)
    }

    private fun createApp(): Javalin {
        val config = AppConfig.parse()
        JwtService.init(config)
        Password.init(config.authDummyPassword)
        return Javalin.create { cfg ->
            cfg.jsonMapper(KotlinxSerializationMapper())
            cfg.routes.before { ctx ->
                Database.connect(DatabaseTestHelper.requireTestDataSource())
                ctx.attribute("userId", ctx.header("X-Test-User") ?: noneUser.toString())
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
            CompensationRoutes.register(cfg)
            BranchDayRoutes.register(cfg)
            ExpenseRoutes.register(cfg)
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun grantAssignOnBranch(
        userId: UUID,
        branchId: UUID,
    ) {
        DatabaseTestHelper.grantCapability(
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
        JavalinTest.test(createApp()) { _, client ->
            val response =
                client.get(
                    "/api/compensations?branchDayId=$branchDayId",
                    asUser(assignUser),
                )
            assertEquals(200, response.code)
            val body = response.body?.string().orEmpty()
            assertTrue(body.contains("\"userName\":\"Test target-1\""))
            assertTrue(body.contains("\"userName\":\"Test target-2\""))
        }
    }

    @Test
    fun `GET compensations returns only rows for requested branch day`() {
        val yesterday =
            java.time.LocalDate
                .now()
                .minusDays(1)
        val otherDayBranchDayId = DatabaseTestHelper.createBranchDayForDate(branchId, yesterday)
        trackOwned(BranchDayTable, BranchDayTable.id, otherDayBranchDayId)
        JavalinTest.test(createApp()) { _, client ->
            val response =
                client.get(
                    "/api/compensations?branchDayId=$otherDayBranchDayId",
                    asUser(assignUser),
                )
            assertEquals(200, response.code)
            val body = response.body?.string().orEmpty()
            assertTrue(body.startsWith("[]"))
        }
    }

    @Test
    fun `GET compensations forbidden for EDIT_BRANCH_DATA-only user`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(
                403,
                client.get("/api/compensations?branchDayId=$branchDayId", asUser(editOnlyUser)).code,
            )
        }
    }

    @Test
    fun `GET compensations forbidden for no-capability user`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(
                403,
                client.get("/api/compensations?branchDayId=$branchDayId", asUser(noneUser)).code,
            )
        }
    }

    @Test
    fun `GET compensations forbidden for ASSIGN_COMPENSATION user on other branch`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(
                403,
                client.get("/api/compensations?branchDayId=$otherBranchDayId", asUser(assignUser)).code,
            )
        }
    }

    @Test
    fun `GET compensations returns 404 for missing branch day with grant`() {
        val missingBranchId = UUID.randomUUID()
        grantAssignOnBranch(assignUser, missingBranchId)
        JavalinTest.test(createApp()) { _, client ->
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
        JavalinTest.test(createApp()) { _, client ->
            val response =
                client.get(
                    "/api/branch-days/$branchDayId/users",
                    asUser(assignUser),
                )
            assertEquals(200, response.code)
            val body = response.body?.string().orEmpty()
            assertTrue(body.contains("\"displayName\":\"Test target-1\""))
            assertTrue(body.contains("\"displayName\":\"Test target-2\""))
        }
    }

    @Test
    fun `GET branch-day users returns only users of requested day`() {
        val yesterday =
            java.time.LocalDate
                .now()
                .minusDays(1)
        val otherDayBranchDayId = DatabaseTestHelper.createBranchDayForDate(branchId, yesterday)
        trackOwned(BranchDayTable, BranchDayTable.id, otherDayBranchDayId)
        DatabaseTestHelper.insertTestAttendance(otherDayBranchDayId, targetUser1)
        trackOwned(AttendanceTable, AttendanceTable.branchDayId, otherDayBranchDayId)
        JavalinTest.test(createApp()) { _, client ->
            val response =
                client.get(
                    "/api/branch-days/$otherDayBranchDayId/users",
                    asUser(assignUser),
                )
            assertEquals(200, response.code)
            val body = response.body?.string().orEmpty()
            assertTrue(body.contains("\"displayName\":\"Test target-1\""))
            assertTrue(!body.contains("\"displayName\":\"Test target-2\""))
        }
    }

    @Test
    fun `GET branch-day users forbidden for EDIT_BRANCH_DATA-only user`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(
                403,
                client.get("/api/branch-days/$branchDayId/users", asUser(editOnlyUser)).code,
            )
        }
    }

    @Test
    fun `GET branch-day users forbidden for no-capability user`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(
                403,
                client.get("/api/branch-days/$branchDayId/users", asUser(noneUser)).code,
            )
        }
    }

    @Test
    fun `GET branch-day users forbidden on other branch`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(
                403,
                client.get("/api/branch-days/$otherBranchDayId/users", asUser(assignUser)).code,
            )
        }
    }

    @Test
    fun `GET branch-day users returns 404 for missing branch day with grant`() {
        val missingBranchId = UUID.randomUUID()
        grantAssignOnBranch(assignUser, missingBranchId)
        JavalinTest.test(createApp()) { _, client ->
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
        JavalinTest.test(createApp()) { _, client ->
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
            val responseBody = response.body?.string().orEmpty()
            assertTrue(responseBody.contains("\"version\":2"))
            assertTrue(responseBody.contains("\"amount\":\"750.00\""))
            assertTrue(responseBody.contains("\"category\":\"WATER\""))
        }
    }

    @Test
    fun `PATCH expense forbidden for ASSIGN_COMPENSATION-only user`() {
        JavalinTest.test(createApp()) { _, client ->
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
        JavalinTest.test(createApp()) { _, client ->
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
        val otherExpenseId = UUID.randomUUID()
        ExpenseService.create(
            callerId = editOnlyUser,
            id = otherExpenseId,
            branchDayId = otherBranchDayId,
            amount = BigDecimal("300.00"),
            category = ExpenseCategory.WATER,
            notes = null,
        )
        trackOwned(ExpenseTable, ExpenseTable.branchDayId, otherBranchDayId)
        trackOwned(ExpenseTable, ExpenseTable.createdBy, editOnlyUser)
        JavalinTest.test(createApp()) { _, client ->
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
        JavalinTest.test(createApp()) { _, client ->
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
        JavalinTest.test(createApp()) { _, client ->
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
        JavalinTest.test(createApp()) { _, client ->
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
}
