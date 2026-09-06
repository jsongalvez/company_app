@file:Suppress("LargeClass")

package com.companyb.companyapp.workforce.relief
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.branchday.BranchDayRoutes
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.commerce.InventoryService
import com.companyb.companyapp.commerce.MovementType
import com.companyb.companyapp.commerce.ProductSaleRoutes
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.ExpenseCategory
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.finance.ExpenseRoutes
import com.companyb.companyapp.finance.ExpenseTable
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.reporting.DailySalesSummaryRoutes
import com.companyb.companyapp.session.SessionBaseRateService
import com.companyb.companyapp.session.SessionRoutes
import com.companyb.companyapp.session.SessionService
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import io.javalin.Javalin
import io.javalin.http.UnauthorizedResponse
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.ClassRule
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * #157 — BRANCH_DAY relief-grant enforcement.
 *
 * Day-scoped gates must accept a BRANCH grant at the day's branch OR a BRANCH_DAY grant for
 * the specific branch day. The relief grant is written as (EDIT_BRANCH_DATA, BRANCH_DAY,
 * branchDayId) with a validFrom/validTo window (`ReliefAccessRepository.grantInTransaction`),
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
    private val reliefUser = TestFixtures.uuid()
    private val wrongDayUser = TestFixtures.uuid()
    private val expiredUser = TestFixtures.uuid()
    private val branchUser = TestFixtures.uuid()
    private val branchCUser = TestFixtures.uuid()
    private val globalUser = TestFixtures.uuid()
    private val noGrantUser = TestFixtures.uuid()
    private val branchA = TestFixtures.uuid()
    private val branchB = TestFixtures.uuid()
    private val branchC = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val otherClientId = TestFixtures.uuid()
    private val createClientId = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()
    private val sessionOnGrantedDay = TestFixtures.uuid()
    private val sessionOnOtherDay = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    private lateinit var grantedDay: UUID
    private lateinit var otherDaySameBranch: UUID
    private lateinit var dayOtherBranch: UUID

    override fun initTestData() {
        seedUsersAndBranches()
        seedClients()

        val today = TestFixtures.today
        grantedDay = BranchDayService.resolveOrCreate(branchA, today).id
        otherDaySameBranch = BranchDayService.resolveOrCreate(branchA, today.plusDays(1)).id
        dayOtherBranch = BranchDayService.resolveOrCreate(branchB, today).id

        seedAuxiliaryData()
        seedGrants()
        seedReadUsers()
    }

    private fun seedUsersAndBranches() {
        IdentityFixtures.insertTestUser(reliefUser, "relief")
        IdentityFixtures.insertTestUser(wrongDayUser, "relief-wrong-day")
        IdentityFixtures.insertTestUser(expiredUser, "relief-expired")
        IdentityFixtures.insertTestUser(branchUser, "branch-editor")
        IdentityFixtures.insertTestUser(branchCUser, "branch-c-editor")
        IdentityFixtures.insertTestUser(globalUser, "global-editor")
        IdentityFixtures.insertTestUser(noGrantUser, "no-grant")
        BranchWorkforceFixtures.insertTestBranch(branchA, "Branch A")
        BranchWorkforceFixtures.insertTestBranch(branchB, "Branch B")
        BranchWorkforceFixtures.insertTestBranch(branchC, "Branch C (no day row)")
    }

    private fun seedClients() {
        SessionClientFixtures.insertTestClient(clientId)
        SessionClientFixtures.insertTestClient(otherClientId)
        SessionClientFixtures.insertTestClient(createClientId)
    }

    private fun seedAuxiliaryData() {
        SessionClientFixtures.insertTestSession(sessionOnGrantedDay, clientId, grantedDay)
        SessionClientFixtures.insertTestSession(sessionOnOtherDay, otherClientId, otherDaySameBranch)

        // Session-create base rates (the create resolves today's branch day).
        seedBaseRate(branchA)
        seedBaseRate(branchC)

        // Inventory card + stock for the product-sale tests (a sale needs stock so nothing
        // trips branch_inventory_current_stock_check).
        CommerceFinanceFixtures.insertTestCategory(categoryId)
        CommerceFinanceFixtures.insertTestProduct(productId, categoryId = categoryId)
        InventoryService.ensureCard(reliefUser, branchA, productId)
        InventoryService.recordMovement(
            callerId = reliefUser,
            movementId = TestFixtures.uuid(),
            branchId = branchA,
            productId = productId,
            movementType = MovementType.Restock,
            quantityChange = 10,
            notes = null,
            branchDayId = grantedDay,
        )
    }

    private fun seedBaseRate(branchId: UUID) {
        val rateId = TestFixtures.uuid()
        SessionBaseRateService.setRate(
            callerId = reliefUser,
            id = rateId,
            branchId = branchId,
            sessionType = SessionType.REGULAR,
            rate = BigDecimal("2500.00"),
        )
    }

    private fun seedGrants() {
        // reliefUser: BRANCH_DAY grant for the granted day (open window).
        IdentityFixtures.grantCapability(
            userId = reliefUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH_DAY,
            contextId = grantedDay,
            sourceId = sourceId,
        )
        // wrongDayUser: BRANCH_DAY grant for a DIFFERENT day at the same branch.
        IdentityFixtures.grantCapability(
            userId = wrongDayUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH_DAY,
            contextId = otherDaySameBranch,
            sourceId = sourceId,
        )
        // expiredUser: BRANCH_DAY grant for the granted day whose window has closed. Windows
        // derive from the REAL clock, never the frozen operational-day noon (#318): the view
        // compares against the DB's live now(), so a frozen base flips "expired" back active
        // whenever the suite runs inside the seeded window (#412 — daily red span). The wide
        // margins absorb realistic same-host clock skew (the grant must stay expired regardless).
        val seededAt = OffsetDateTime.ofInstant(TestFixtures.realNow(), ZoneOffset.UTC)
        IdentityFixtures.grantCapability(
            userId = expiredUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH_DAY,
            contextId = grantedDay,
            sourceId = sourceId,
            validFrom = seededAt.minusHours(12),
            validTo = seededAt.minusHours(10),
        )
        // branchUser: ordinary BRANCH grant (the branch leg regression).
        IdentityFixtures.grantCapability(
            userId = branchUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchA,
            sourceId = sourceId,
        )
        // branchCUser: BRANCH grant at the no-day-row branch (the null-day fallback success path).
        IdentityFixtures.grantCapability(
            userId = branchCUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchC,
            sourceId = sourceId,
        )
        // globalUser: GLOBAL EDIT_BRANCH_DATA — must NOT satisfy the day gates (pre-change
        // strictness preserved; the #131 class).
        IdentityFixtures.grantCapability(
            userId = globalUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
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
                cfg.routes.before("${ApiRoutes.API_PREFIX}*") { ctx ->
                    if (ctx.header("X-Test-User") == null) {
                        val token = ctx.header("Authorization")?.removePrefix("Bearer ") ?: throw UnauthorizedResponse()
                        ctx.attribute("userId", JwtService.verifyToken(token) ?: throw UnauthorizedResponse())
                    }
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
                ProductSaleRoutes.register(cfg)
                DailySalesSummaryRoutes.register(cfg)
                BranchDayRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun expenseBody(branchDayId: UUID): Map<String, String> =
        mapOf(
            "id" to TestFixtures.uuid().toString(),
            "branchDayId" to branchDayId.toString(),
            "amount" to "100.00",
            "category" to "MISCELLANEOUS",
        )

    // --- Expense create (the core day-scoped write) ---

    @Test
    fun `relief user creates expense on the granted day`() {
        testServer.client.let { client ->
            val response = client.post("/api/expenses", expenseBody(grantedDay), asUser(reliefUser))
            assertEquals(201, response.code)
        }
    }

    @Test
    fun `relief user expense on a non-granted day at the same branch is forbidden`() {
        testServer.client.let { client ->
            val response = client.post("/api/expenses", expenseBody(otherDaySameBranch), asUser(reliefUser))
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `relief user expense on another branch day is forbidden`() {
        testServer.client.let { client ->
            val response = client.post("/api/expenses", expenseBody(dayOtherBranch), asUser(reliefUser))
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `day-scoped grant for another day does not satisfy the gate`() {
        testServer.client.let { client ->
            val response = client.post("/api/expenses", expenseBody(grantedDay), asUser(wrongDayUser))
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `expired relief grant is forbidden`() {
        testServer.client.let { client ->
            val response = client.post("/api/expenses", expenseBody(grantedDay), asUser(expiredUser))
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `no-grant user is forbidden`() {
        testServer.client.let { client ->
            val response = client.post("/api/expenses", expenseBody(grantedDay), asUser(noGrantUser))
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `branch-scoped grant still satisfies the gate`() {
        testServer.client.let { client ->
            val response = client.post("/api/expenses", expenseBody(grantedDay), asUser(branchUser))
            assertEquals(201, response.code)
        }
    }

    // --- Expense read mirror ---

    @Test
    fun `relief user reads expenses on the granted day`() {
        testServer.client.let { client ->
            val response = client.get("/api/expenses?branchDayId=$grantedDay", asUser(reliefUser))
            assertEquals(200, response.code)
        }
    }

    @Test
    fun `relief user expense read on a non-granted day is forbidden`() {
        testServer.client.let { client ->
            val response = client.get("/api/expenses?branchDayId=$otherDaySameBranch", asUser(reliefUser))
            assertEquals(403, response.code)
        }
    }

    // --- Expense record-scoped variant (ForExpense) ---

    @Test
    fun `relief user patches an expense on the granted day`() {
        testServer.client.let { client ->
            val expenseId = TestFixtures.uuid()
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
                    .string()
                    .orEmpty()
                    .contains("\"amount\":\"150.00\""),
            )
        }
    }

    // --- Session create (the getToday-resolution path) ---

    @Test
    fun `relief user creates a session on the granted day`() {
        testServer.client.let { client ->
            val sessionId = TestFixtures.uuid()
            val body =
                mapOf(
                    "id" to sessionId.toString(),
                    "clientId" to createClientId.toString(),
                    "branchId" to branchA.toString(),
                    "isWalkIn" to false,
                    "finalPrice" to "2500.00",
                )
            val response = client.post("/api/sessions", body, asUser(reliefUser))
            assertEquals(201, response.code, response.body.string().orEmpty())
        }
    }

    @Test
    fun `relief user session create at an ungranted branch is forbidden`() {
        testServer.client.let { client ->
            val sessionId = TestFixtures.uuid()
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

    // --- Product-sale create (day-scoped write via body branchDayId) ---

    @Test
    fun `relief user creates a product sale on the granted day`() {
        testServer.client.let { client ->
            val saleId = TestFixtures.uuid()
            val body =
                mapOf(
                    "id" to saleId.toString(),
                    "branchDayId" to grantedDay.toString(),
                    "isWalkIn" to true,
                    "productId" to productId.toString(),
                    "quantity" to 1,
                    "expectedVersion" to cardVersion(),
                )
            val response = client.post("/api/product-sales", body, asUser(reliefUser))
            assertEquals(201, response.code, response.body.string().orEmpty())
        }
    }

    @Test
    fun `relief user product sale on a non-granted day is forbidden`() {
        testServer.client.let { client ->
            val saleId = TestFixtures.uuid()
            val body =
                mapOf(
                    "id" to saleId.toString(),
                    "branchDayId" to otherDaySameBranch.toString(),
                    "isWalkIn" to true,
                    "productId" to productId.toString(),
                    "quantity" to 1,
                    "expectedVersion" to cardVersion(),
                )
            val response = client.post("/api/product-sales", body, asUser(reliefUser))
            assertEquals(403, response.code)
        }
    }

    private fun cardVersion(): Int =
        InventoryService
            .getStock(branchA)
            .first { it.inventory.productId == productId }
            .inventory.version

    // --- The GLOBAL exclusion (the #131 strictness) ---

    @Test
    fun `global grant does not satisfy the day gates`() {
        testServer.client.let { client ->
            val response = client.post("/api/expenses", expenseBody(grantedDay), asUser(globalUser))
            assertEquals(403, response.code)
        }
    }

    // --- Session create at a branch with NO today day row (the find-only fallback) ---

    @Test
    fun `session create at a no-day branch falls back to the branch gate without creating a day`() {
        testServer.client.let { client ->
            val sessionId = TestFixtures.uuid()
            val body =
                mapOf(
                    "id" to sessionId.toString(),
                    "clientId" to createClientId.toString(),
                    "branchId" to branchC.toString(),
                    "isWalkIn" to false,
                    "finalPrice" to "2500.00",
                )
            val response = client.post("/api/sessions", body, asUser(reliefUser))
            assertEquals(403, response.code)
            val dayCount =
                transaction {
                    BranchDayTable
                        .selectAll()
                        .where { BranchDayTable.branchId eq branchC }
                        .count()
                }
            assertEquals(0L, dayCount, "the find-only gate must not create a branch day row")
        }
    }

    @Test
    fun `branch-granted user creates a session at a branch with no day row`() {
        testServer.client.let { client ->
            val sessionId = TestFixtures.uuid()
            val body =
                mapOf(
                    "id" to sessionId.toString(),
                    "clientId" to createClientId.toString(),
                    "branchId" to branchC.toString(),
                    "isWalkIn" to false,
                    "finalPrice" to "2500.00",
                )
            val response = client.post("/api/sessions", body, asUser(branchCUser))
            assertEquals(201, response.code, response.body.string().orEmpty())
        }
    }

    // --- The branch-affiliation guard (parent-child convention, #157) ---

    @Test
    fun `gated branch day of another branch is rejected`() {
        val e =
            assertFailsWith<NotFoundException> {
                SessionService.create(
                    callerId = reliefUser,
                    id = TestFixtures.uuid(),
                    clientId = createClientId,
                    branchId = branchA,
                    isWalkIn = false,
                    requestedPractitionerId = null,
                    finalPrice = BigDecimal("2500.00"),
                    remarks = null,
                    otherConcerns = null,
                    nextAppointmentDate = null,
                    gatedBranchDayId = dayOtherBranch,
                )
            }
        assertTrue(e.message.orEmpty().contains("for this branch"))
    }

    // #128 lesson — the X-Test-User harness can't exercise the real auth filter; 401 needs
    // the real-JWT harness (the #147/ReportsReadScopeAuthzTest precedent).
    @Test
    fun `unauthenticated request gets 401`() {
        testServer.client.let { client ->
            val response = client.post("/api/expenses", expenseBody(grantedDay))
            assertEquals(401, response.code)
        }
    }

    // ─────────────────────────── read legs (#158) ───────────────────────────

    private val today = TestFixtures.today
    private val noDayDate = today.plusDays(10)

    // Seed VIEW_BRANCH_DATA holders for the read-leg regressions (the read's branch/global
    // leg is VIEW_BRANCH_DATA — distinct from the write surface's EDIT_BRANCH_DATA).
    private val viewUser = TestFixtures.uuid()
    private val globalViewUser = TestFixtures.uuid()

    private fun seedReadUsers() {
        IdentityFixtures.insertTestUser(viewUser, "view-user")
        IdentityFixtures.insertTestUser(globalViewUser, "global-view-user")
        IdentityFixtures.grantCapability(
            userId = viewUser,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchA,
            sourceId = sourceId,
        )
        IdentityFixtures.grantCapability(
            userId = globalViewUser,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
    }

    @Test
    fun `relief user reads the single-day summary on the granted day`() {
        testServer.client.let { client ->
            val response = client.get("/api/branches/$branchA/daily-summary?date=$today", asUser(reliefUser))
            assertEquals(200, response.code, response.body.string().orEmpty())
        }
    }

    @Test
    fun `relief user single-day summary on a non-granted day is forbidden`() {
        testServer.client.let { client ->
            val url = "/api/branches/$branchA/daily-summary?date=${today.plusDays(1)}"
            val response = client.get(url, asUser(reliefUser))
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `relief user single-day summary at another branch is forbidden`() {
        testServer.client.let { client ->
            val response = client.get("/api/branches/$branchB/daily-summary?date=$today", asUser(reliefUser))
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `relief user single-day summary with no day row falls back to the branch gate`() {
        testServer.client.let { client ->
            val response = client.get("/api/branches/$branchA/daily-summary?date=$noDayDate", asUser(reliefUser))
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `day-scoped grant for another day does not satisfy the single-day read`() {
        testServer.client.let { client ->
            val response = client.get("/api/branches/$branchA/daily-summary?date=$today", asUser(wrongDayUser))
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `expired relief grant cannot read the single-day summary`() {
        testServer.client.let { client ->
            val response = client.get("/api/branches/$branchA/daily-summary?date=$today", asUser(expiredUser))
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `BRANCH VIEW_BRANCH_DATA holder still reads the single-day summary`() {
        testServer.client.let { client ->
            val response = client.get("/api/branches/$branchA/daily-summary?date=$today", asUser(viewUser))
            assertEquals(200, response.code, response.body.string().orEmpty())
        }
    }

    @Test
    fun `GLOBAL VIEW_BRANCH_DATA holder still reads the single-day summary`() {
        testServer.client.let { client ->
            val response = client.get("/api/branches/$branchA/daily-summary?date=$today", asUser(globalViewUser))
            assertEquals(200, response.code, response.body.string().orEmpty())
        }
    }

    @Test
    fun `GLOBAL EDIT_BRANCH_DATA does not satisfy the single-day read`() {
        testServer.client.let { client ->
            // The read's branch/global leg is VIEW_BRANCH_DATA; a GLOBAL EDIT grant is
            // not a read grant (and the day leg checks the BRANCH_DAY form only).
            val response = client.get("/api/branches/$branchA/daily-summary?date=$today", asUser(globalUser))
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `relief user reads today day-status on the granted day`() {
        testServer.client.let { client ->
            val response = client.get("/api/branches/$branchA/today", asUser(reliefUser))
            assertEquals(200, response.code, response.body.string().orEmpty())
        }
    }

    @Test
    fun `relief user today day-status on a non-granted day is forbidden`() {
        testServer.client.let { client ->
            val response = client.get("/api/branches/$branchA/today", asUser(wrongDayUser))
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `BRANCH EDIT_BRANCH_DATA holder still reads today day-status`() {
        testServer.client.let { client ->
            val response = client.get("/api/branches/$branchA/today", asUser(branchUser))
            assertEquals(200, response.code, response.body.string().orEmpty())
        }
    }

    @Test
    fun `today day-status at a no-day branch falls back to the branch gate`() {
        testServer.client.let { client ->
            val response = client.get("/api/branches/$branchC/today", asUser(reliefUser))
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `GLOBAL EDIT_BRANCH_DATA does not satisfy the today day-status read`() {
        // The /today fallback is the plain BRANCH gate — GLOBAL never passes (the #131
        // strictness; the pass-2 doc amendment pins the asymmetry).
        testServer.client.let { client ->
            val response = client.get("/api/branches/$branchA/today", asUser(globalUser))
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `unauthenticated single-day summary read gets 401`() {
        testServer.client.let { client ->
            val response = client.get("/api/branches/$branchA/daily-summary?date=$today")
            assertEquals(401, response.code)
        }
    }
}
