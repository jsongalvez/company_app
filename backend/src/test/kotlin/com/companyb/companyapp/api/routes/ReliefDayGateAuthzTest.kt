@file:Suppress("LargeClass")

package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.ExpenseCategory
import com.companyb.companyapp.repository.model.ExpenseTable
import com.companyb.companyapp.repository.model.SessionBaseRateTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.service.session.SessionService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.Javalin
import io.javalin.http.UnauthorizedResponse
import io.javalin.testtools.JavalinTest
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #157 — BRANCH_DAY relief-grant enforcement.
 *
 * Day-scoped gates must accept a BRANCH grant at the day's branch OR a BRANCH_DAY grant for
 * the specific branch day. The relief grant is written as (EDIT_BRANCH_DATA, BRANCH_DAY,
 * branchDayId) with a validFrom/validTo window (`ReliefAccessRepository.grantWithCapability`),
 * and the window is enforced by the `active_user_capabilities` view. GLOBAL grants
 * deliberately do NOT satisfy these gates (the #131 strictness).
 *
 * Covered surface (the #157 decision): expenses (create/read/update/delete/restore),
 * product-sale create, session create + mutations. Not covered: inventory movements
 * (branch-scoped — the day comes from the body while the route is branch-scoped via the
 * path; the parent-child trap), allowance/compensation/commission/remittance (other
 * capability codes), session void/unvoid (VOID_SESSION).
 */
class ReliefDayGateAuthzTest : BasePostgresTest() {
    private val reliefUser = UUID.randomUUID()
    private val wrongDayUser = UUID.randomUUID()
    private val expiredUser = UUID.randomUUID()
    private val branchUser = UUID.randomUUID()
    private val noGrantUser = UUID.randomUUID()
    private val branchA = UUID.randomUUID()
    private val branchB = UUID.randomUUID()
    private val clientId = UUID.randomUUID()
    private val otherClientId = UUID.randomUUID()
    private val createClientId = UUID.randomUUID()
    private val sessionOnGrantedDay = UUID.randomUUID()
    private val sessionOnOtherDay = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()

    private lateinit var grantedDay: UUID
    private lateinit var otherDaySameBranch: UUID
    private lateinit var dayOtherBranch: UUID

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(reliefUser, "relief")
        DatabaseTestHelper.insertTestUser(wrongDayUser, "relief-wrong-day")
        DatabaseTestHelper.insertTestUser(expiredUser, "relief-expired")
        DatabaseTestHelper.insertTestUser(branchUser, "branch-editor")
        DatabaseTestHelper.insertTestUser(noGrantUser, "no-grant")
        DatabaseTestHelper.insertTestBranch(branchA, "Branch A")
        DatabaseTestHelper.insertTestBranch(branchB, "Branch B")
        DatabaseTestHelper.insertTestClient(clientId)
        DatabaseTestHelper.insertTestClient(otherClientId)
        DatabaseTestHelper.insertTestClient(createClientId)

        trackOwned(AppUserTable, AppUserTable.id, reliefUser)
        trackOwned(AppUserTable, AppUserTable.id, wrongDayUser)
        trackOwned(AppUserTable, AppUserTable.id, expiredUser)
        trackOwned(AppUserTable, AppUserTable.id, branchUser)
        trackOwned(AppUserTable, AppUserTable.id, noGrantUser)
        trackOwned(BranchTable, BranchTable.id, branchA)
        trackOwned(BranchTable, BranchTable.id, branchB)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchA)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchB)
        trackOwned(ClientTable, ClientTable.id, clientId)
        trackOwned(ClientTable, ClientTable.id, otherClientId)
        trackOwned(ClientTable, ClientTable.id, createClientId)
        trackOwned(SessionTable, SessionTable.id, sessionOnGrantedDay)
        trackOwned(SessionTable, SessionTable.id, sessionOnOtherDay)

        val today = LocalDate.now(ZoneId.of("Asia/Manila"))
        grantedDay = BranchDayService.resolveOrCreate(branchA, today).id
        otherDaySameBranch = BranchDayService.resolveOrCreate(branchA, today.plusDays(1)).id
        dayOtherBranch = BranchDayService.resolveOrCreate(branchB, today).id

        trackOwned(ExpenseTable, ExpenseTable.branchDayId, grantedDay)
        trackOwned(ExpenseTable, ExpenseTable.branchDayId, otherDaySameBranch)
        trackOwned(ExpenseTable, ExpenseTable.branchDayId, dayOtherBranch)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, reliefUser)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, wrongDayUser)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, expiredUser)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, branchUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, reliefUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, wrongDayUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, expiredUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, branchUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, noGrantUser)

        seedAuxiliaryData()
        seedGrants()
    }

    private fun seedAuxiliaryData() {
        DatabaseTestHelper.insertTestSession(sessionOnGrantedDay, clientId, grantedDay)
        DatabaseTestHelper.insertTestSession(sessionOnOtherDay, otherClientId, otherDaySameBranch)

        // Session-create base rate for branch A (the create resolves today's branch day).
        val rateId = UUID.randomUUID()
        SessionService.setRate(
            callerId = reliefUser,
            id = rateId,
            branchId = branchA,
            sessionType = SessionType.REGULAR,
            rate = BigDecimal("2500.00"),
        )
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, rateId)
    }

    private fun seedGrants() {
        // reliefUser: BRANCH_DAY grant for the granted day (open window).
        DatabaseTestHelper.grantCapability(
            userId = reliefUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH_DAY,
            contextId = grantedDay,
            sourceId = sourceId,
        )
        // wrongDayUser: BRANCH_DAY grant for a DIFFERENT day at the same branch.
        DatabaseTestHelper.grantCapability(
            userId = wrongDayUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH_DAY,
            contextId = otherDaySameBranch,
            sourceId = sourceId,
        )
        // expiredUser: BRANCH_DAY grant for the granted day whose window has closed.
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        DatabaseTestHelper.grantCapability(
            userId = expiredUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH_DAY,
            contextId = grantedDay,
            sourceId = sourceId,
            validFrom = now.minusHours(2),
            validTo = now.minusHours(1),
        )
        // branchUser: ordinary BRANCH grant (the branch leg regression).
        DatabaseTestHelper.grantCapability(
            userId = branchUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchA,
            sourceId = sourceId,
        )
    }

    private fun createApp(): Javalin {
        val config = AppConfig.parse()
        JwtService.init(config)
        Password.init(config.authDummyPassword)
        return Javalin.create { cfg ->
            cfg.jsonMapper(KotlinxSerializationMapper())
            cfg.routes.before { ctx ->
                Database.connect(DatabaseTestHelper.requireTestDataSource())
                ctx.attribute("userId", ctx.header("X-Test-User") ?: noGrantUser.toString())
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
            ExpenseRoutes.register(cfg)
            SessionRoutes.register(cfg)
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun expenseBody(branchDayId: UUID): Map<String, String> =
        mapOf(
            "id" to UUID.randomUUID().toString(),
            "branchDayId" to branchDayId.toString(),
            "amount" to "100.00",
            "category" to "MISCELLANEOUS",
        )

    // --- Expense create (the core day-scoped write) ---

    @Test
    fun `relief user creates expense on the granted day`() {
        JavalinTest.test(createApp()) { _, client ->
            val response = client.post("/api/expenses", expenseBody(grantedDay), asUser(reliefUser))
            assertEquals(201, response.code)
        }
    }

    @Test
    fun `relief user expense on a non-granted day at the same branch is forbidden`() {
        JavalinTest.test(createApp()) { _, client ->
            val response = client.post("/api/expenses", expenseBody(otherDaySameBranch), asUser(reliefUser))
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `relief user expense on another branch day is forbidden`() {
        JavalinTest.test(createApp()) { _, client ->
            val response = client.post("/api/expenses", expenseBody(dayOtherBranch), asUser(reliefUser))
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `day-scoped grant for another day does not satisfy the gate`() {
        JavalinTest.test(createApp()) { _, client ->
            val response = client.post("/api/expenses", expenseBody(grantedDay), asUser(wrongDayUser))
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `expired relief grant is forbidden`() {
        JavalinTest.test(createApp()) { _, client ->
            val response = client.post("/api/expenses", expenseBody(grantedDay), asUser(expiredUser))
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `no-grant user is forbidden`() {
        JavalinTest.test(createApp()) { _, client ->
            val response = client.post("/api/expenses", expenseBody(grantedDay), asUser(noGrantUser))
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `branch-scoped grant still satisfies the gate`() {
        JavalinTest.test(createApp()) { _, client ->
            val response = client.post("/api/expenses", expenseBody(grantedDay), asUser(branchUser))
            assertEquals(201, response.code)
        }
    }

    // --- Expense read mirror ---

    @Test
    fun `relief user reads expenses on the granted day`() {
        JavalinTest.test(createApp()) { _, client ->
            val response = client.get("/api/expenses?branchDayId=$grantedDay", asUser(reliefUser))
            assertEquals(200, response.code)
        }
    }

    @Test
    fun `relief user expense read on a non-granted day is forbidden`() {
        JavalinTest.test(createApp()) { _, client ->
            val response = client.get("/api/expenses?branchDayId=$otherDaySameBranch", asUser(reliefUser))
            assertEquals(403, response.code)
        }
    }

    // --- Expense record-scoped variant (ForExpense) ---

    @Test
    fun `relief user patches an expense on the granted day`() {
        JavalinTest.test(createApp()) { _, client ->
            val expenseId = UUID.randomUUID()
            trackOwned(ExpenseTable, ExpenseTable.id, expenseId)
            transaction {
                ExpenseTable.insert {
                    it[ExpenseTable.id] = expenseId
                    it[ExpenseTable.branchDayId] = grantedDay
                    it[ExpenseTable.amount] = BigDecimal("100.00")
                    it[ExpenseTable.category] = ExpenseCategory.MISCELLANEOUS
                    it[ExpenseTable.createdBy] = reliefUser
                    it[ExpenseTable.notes] = "Test expense"
                }
            }

            val body = mapOf("amount" to "150.00", "category" to "MISCELLANEOUS", "expectedVersion" to 1)
            val response = client.patch("/api/expenses/$expenseId", body, asUser(reliefUser))
            assertEquals(200, response.code)
            assertTrue(
                response.body
                    ?.string()
                    .orEmpty()
                    .contains("\"amount\":\"150.00\""),
            )
        }
    }

    // --- Session mutations (ForSession variant) ---

    @Test
    fun `relief user patches a session on the granted day`() {
        JavalinTest.test(createApp()) { _, client ->
            val body = mapOf("sessionType" to "SECOND_SESSION", "version" to 1)
            val response = client.patch("/api/sessions/$sessionOnGrantedDay/type", body, asUser(reliefUser))
            assertEquals(200, response.code)
        }
    }

    @Test
    fun `relief user session patch on a non-granted day is forbidden`() {
        JavalinTest.test(createApp()) { _, client ->
            val body = mapOf("sessionType" to "SECOND_SESSION", "version" to 1)
            val response = client.patch("/api/sessions/$sessionOnOtherDay/type", body, asUser(reliefUser))
            assertEquals(403, response.code, response.body?.string().orEmpty())
        }
    }

    // --- Session create (the getToday-resolution path) ---

    @Test
    fun `relief user creates a session on the granted day`() {
        JavalinTest.test(createApp()) { _, client ->
            val sessionId = UUID.randomUUID()
            trackOwned(SessionTable, SessionTable.id, sessionId)
            val body =
                mapOf(
                    "id" to sessionId.toString(),
                    "clientId" to createClientId.toString(),
                    "branchId" to branchA.toString(),
                    "isWalkIn" to false,
                    "finalPrice" to "2500.00",
                )
            val response = client.post("/api/sessions", body, asUser(reliefUser))
            assertEquals(201, response.code, response.body?.string().orEmpty())
        }
    }

    @Test
    fun `relief user session create at an ungranted branch is forbidden`() {
        JavalinTest.test(createApp()) { _, client ->
            val sessionId = UUID.randomUUID()
            trackOwned(SessionTable, SessionTable.id, sessionId)
            val body =
                mapOf(
                    "id" to sessionId.toString(),
                    "clientId" to createClientId.toString(),
                    "branchId" to branchB.toString(),
                    "isWalkIn" to false,
                    "finalPrice" to "2500.00",
                )
            val response = client.post("/api/sessions", body, asUser(reliefUser))
            assertEquals(403, response.code)
        }
    }

    // #128 lesson — the X-Test-User harness can't exercise the real auth filter; 401 needs
    // the real-JWT harness (the #147/ReportsReadScopeAuthzTest precedent).
    @Test
    fun `unauthenticated request gets 401`() {
        JavalinTest.test(createAppWithJwt()) { _, client ->
            val response = client.post("/api/expenses", expenseBody(grantedDay))
            assertEquals(401, response.code)
        }
    }

    private fun createAppWithJwt(): Javalin {
        val config = AppConfig.parse()
        JwtService.init(config)
        Password.init(config.authDummyPassword)
        return Javalin.create { cfg ->
            cfg.jsonMapper(KotlinxSerializationMapper())
            cfg.routes.before { ctx ->
                Database.connect(DatabaseTestHelper.requireTestDataSource())
            }
            cfg.routes.before("${ApiRoutes.API_PREFIX}*") { ctx ->
                val token = ctx.header("Authorization")?.removePrefix("Bearer ") ?: throw UnauthorizedResponse()
                val userId = JwtService.verifyToken(token) ?: throw UnauthorizedResponse()
                ctx.attribute("userId", userId)
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
            ExpenseRoutes.register(cfg)
        }
    }
}
