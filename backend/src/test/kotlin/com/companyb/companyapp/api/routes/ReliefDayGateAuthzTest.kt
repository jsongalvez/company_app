@file:Suppress("LargeClass")

package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.ExpenseCategory
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.ExpenseTable
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.SessionBaseRateTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.CapabilityService
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.service.inventory.InventoryService
import com.companyb.companyapp.service.inventory.MovementType
import com.companyb.companyapp.service.session.SessionService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
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
        trackOwned(SessionTable, SessionTable.id, sessionOnGrantedDay)
        trackOwned(SessionTable, SessionTable.id, sessionOnOtherDay)

        val today = TestFixtures.today
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
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, branchCUser)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, globalUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, reliefUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, wrongDayUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, expiredUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, branchUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, branchCUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, globalUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, noGrantUser)
        trackOwned(ProductSaleTable, ProductSaleTable.branchDayId, grantedDay)
        trackOwned(ProductSaleTable, ProductSaleTable.branchDayId, otherDaySameBranch)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchA)
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, categoryId)
        trackOwned(ProductTable, ProductTable.id, productId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.branchDayId, grantedDay)
        trackOwned(InventoryMovementTable, InventoryMovementTable.branchDayId, otherDaySameBranch)

        seedAuxiliaryData()
        seedGrants()
        seedReadUsers()
    }

    private fun seedUsersAndBranches() {
        DatabaseTestHelper.insertTestUser(reliefUser, "relief")
        DatabaseTestHelper.insertTestUser(wrongDayUser, "relief-wrong-day")
        DatabaseTestHelper.insertTestUser(expiredUser, "relief-expired")
        DatabaseTestHelper.insertTestUser(branchUser, "branch-editor")
        DatabaseTestHelper.insertTestUser(branchCUser, "branch-c-editor")
        DatabaseTestHelper.insertTestUser(globalUser, "global-editor")
        DatabaseTestHelper.insertTestUser(noGrantUser, "no-grant")
        DatabaseTestHelper.insertTestBranch(branchA, "Branch A")
        DatabaseTestHelper.insertTestBranch(branchB, "Branch B")
        DatabaseTestHelper.insertTestBranch(branchC, "Branch C (no day row)")

        trackOwned(AppUserTable, AppUserTable.id, reliefUser)
        trackOwned(AppUserTable, AppUserTable.id, wrongDayUser)
        trackOwned(AppUserTable, AppUserTable.id, expiredUser)
        trackOwned(AppUserTable, AppUserTable.id, branchUser)
        trackOwned(AppUserTable, AppUserTable.id, branchCUser)
        trackOwned(AppUserTable, AppUserTable.id, globalUser)
        trackOwned(AppUserTable, AppUserTable.id, noGrantUser)
        trackOwned(BranchTable, BranchTable.id, branchA)
        trackOwned(BranchTable, BranchTable.id, branchB)
        trackOwned(BranchTable, BranchTable.id, branchC)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchA)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchB)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchC)
    }

    private fun seedClients() {
        DatabaseTestHelper.insertTestClient(clientId)
        DatabaseTestHelper.insertTestClient(otherClientId)
        DatabaseTestHelper.insertTestClient(createClientId)
        trackOwned(ClientTable, ClientTable.id, clientId)
        trackOwned(ClientTable, ClientTable.id, otherClientId)
        trackOwned(ClientTable, ClientTable.id, createClientId)
    }

    private fun seedAuxiliaryData() {
        DatabaseTestHelper.insertTestSession(sessionOnGrantedDay, clientId, grantedDay)
        DatabaseTestHelper.insertTestSession(sessionOnOtherDay, otherClientId, otherDaySameBranch)

        // Session-create base rates (the create resolves today's branch day).
        seedBaseRate(branchA)
        seedBaseRate(branchC)

        // Inventory card + stock for the product-sale tests (a sale needs stock so nothing
        // trips branch_inventory_current_stock_check).
        DatabaseTestHelper.insertTestCategory(categoryId)
        DatabaseTestHelper.insertTestProduct(productId, categoryId = categoryId)
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
        SessionService.setRate(
            callerId = reliefUser,
            id = rateId,
            branchId = branchId,
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
        // expiredUser: BRANCH_DAY grant for the granted day whose window has closed. Seeded
        // from the JVM clock against the view's DB now() — the −12h/−10h margins absorb any
        // realistic same-host clock skew (the grant must stay expired regardless).
        val now = TestFixtures.now
        DatabaseTestHelper.grantCapability(
            userId = expiredUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH_DAY,
            contextId = grantedDay,
            sourceId = sourceId,
            validFrom = now.minusHours(12),
            validTo = now.minusHours(10),
        )
        // branchUser: ordinary BRANCH grant (the branch leg regression).
        DatabaseTestHelper.grantCapability(
            userId = branchUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchA,
            sourceId = sourceId,
        )
        // branchCUser: BRANCH grant at the no-day-row branch (the null-day fallback success path).
        DatabaseTestHelper.grantCapability(
            userId = branchCUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchC,
            sourceId = sourceId,
        )
        // globalUser: GLOBAL EDIT_BRANCH_DATA — must NOT satisfy the day gates (pre-change
        // strictness preserved; the #131 class).
        DatabaseTestHelper.grantCapability(
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
                    Database.connect(DatabaseTestHelper.requireTestDataSource())
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
            assertEquals(201, response.code, response.body.string().orEmpty())
        }
    }

    @Test
    fun `relief user session create at an ungranted branch is forbidden`() {
        testServer.client.let { client ->
            val sessionId = TestFixtures.uuid()
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

    // --- Product-sale create (day-scoped write via body branchDayId) ---

    @Test
    fun `relief user creates a product sale on the granted day`() {
        testServer.client.let { client ->
            val saleId = TestFixtures.uuid()
            trackOwned(ProductSaleTable, ProductSaleTable.id, saleId)
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
            trackOwned(ProductSaleTable, ProductSaleTable.id, saleId)
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
            trackOwned(SessionTable, SessionTable.id, sessionId)
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
            trackOwned(SessionTable, SessionTable.id, sessionId)
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
                    bookedAt = null,
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
        DatabaseTestHelper.insertTestUser(viewUser, "view-user")
        DatabaseTestHelper.insertTestUser(globalViewUser, "global-view-user")
        trackOwned(AppUserTable, AppUserTable.id, viewUser)
        trackOwned(AppUserTable, AppUserTable.id, globalViewUser)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, viewUser)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, globalViewUser)
        DatabaseTestHelper.grantCapability(
            userId = viewUser,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchA,
            sourceId = sourceId,
        )
        DatabaseTestHelper.grantCapability(
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
