package com.companyb.companyapp.api.routes

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.client.ClientRoutes
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.commerce.BranchInventoryTable
import com.companyb.companyapp.commerce.ProductSaleRoutes
import com.companyb.companyapp.commerce.ProductSaleTable
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.finance.AllowanceRoutes
import com.companyb.companyapp.finance.AllowanceTable
import com.companyb.companyapp.finance.ExpenseRoutes
import com.companyb.companyapp.finance.ExpenseTable
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.session.SessionBaseRateService
import com.companyb.companyapp.session.SessionPractitionerTable
import com.companyb.companyapp.session.SessionRoutes
import com.companyb.companyapp.session.SessionTable
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import com.companyb.companyapp.workforce.AttendanceRoutes
import com.companyb.companyapp.workforce.AttendanceTable
import io.javalin.Javalin
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.ClassRule
import java.io.File
import java.math.BigDecimal
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The offline queue (`web/src/lib/outbox.svelte.ts`) re-sends a save whose answer it never saw and
 * judges nothing about it, so the server has to make every queued save safe to send twice: the same
 * request, sent again, gets a 2xx and leaves no second row. One test per entry in the queue's RULES;
 * [every queued save has a replay test] fails when the queue gains a rule that has none.
 */
class OutboxReplayContractTest : BasePostgresTest() {
    private val staff = TestFixtures.uuid()
    private val mate = TestFixtures.uuid()
    private val clerk = TestFixtures.uuid()
    private val finance = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    private lateinit var branchDayId: UUID

    override fun initTestData() {
        listOf(staff to "staff", mate to "mate", clerk to "clerk", finance to "finance").forEach { (id, name) ->
            IdentityFixtures.insertTestUser(id, name)
        }
        BranchWorkforceFixtures.insertTestBranch(branchId)
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        listOf(staff, mate, clerk).forEachIndexed { i, u ->
            BranchWorkforceFixtures.insertTestAssignment(
                userId = u,
                branchId = branchId,
                slot = (i + 1).toShort(),
                assignedBy = staff,
            )
        }
        SessionClientFixtures.insertTestClient(clientId)
        CommerceFinanceFixtures.insertTestCategory(categoryId)
        CommerceFinanceFixtures.insertTestProduct(productId, categoryId = categoryId)
        transaction {
            CommerceFinanceFixtures.insertTestCard(
                this@OutboxReplayContractTest.branchId,
                this@OutboxReplayContractTest.productId,
                STOCK,
            )
        }
        grant(staff, CapabilityCodes.EDIT_BRANCH_DATA)
        grant(finance, CapabilityCodes.ASSIGN_COMPENSATION)
        grant(finance, CapabilityCodes.EDIT_BRANCH_DATA)
        SessionBaseRateService.setRate(staff, TestFixtures.uuid(), branchId, SessionType.REGULAR, BigDecimal("2500.00"))
    }

    private fun grant(
        user: UUID,
        code: String,
    ) = IdentityFixtures.grantCapability(user, code, CapabilityContextType.BRANCH, branchId, sourceId)

    private fun clockStaffIn() = BranchWorkforceFixtures.insertTestAttendance(branchDayId, staff)

    private fun <T : Table> count(
        table: T,
        where: (T) -> org.jetbrains.exposed.v1.core.Op<Boolean>,
    ): Long = transaction { table.selectAll().where { where(table) }.count() }

    /** Sends the same request twice. Both answers must be 2xx; returns the second. */
    private fun replay(
        user: UUID,
        method: String,
        path: String,
        body: Map<String, Any?>,
    ) = run {
        val clean = body.filterValues { it != null }
        val send = { call(user, method, path, clean) }
        val first = send()
        assertTrue(first.code in 200..299, "first $method $path -> ${first.code} ${first.body.string()}")
        val second = send()
        assertTrue(second.code in 200..299, "replayed $method $path -> ${second.code} ${second.body.string()}")
        second
    }

    private fun call(
        user: UUID,
        method: String,
        path: String,
        body: Map<String, Any?>,
    ) = run {
        val who = Consumer<Request.Builder> { it.header("X-Test-User", user.toString()) }
        val c = testServer.client
        if (method == "PATCH") c.patch(path, body, who) else c.post(path, body, who)
    }

    @Test
    fun `POST clients`() {
        val id = TestFixtures.uuid()
        replay(
            staff,
            "POST",
            "/api/clients",
            mapOf("id" to id.toString(), "firstName" to "Ana", "lastName" to "Cruz", "gender" to "F", "age" to 30),
        )
        assertEquals(1, count(ClientTable) { it.id eq id })
    }

    @Test
    fun `POST sessions`() {
        clockStaffIn()
        val id = TestFixtures.uuid()
        replay(
            staff,
            "POST",
            "/api/sessions",
            mapOf(
                "id" to id.toString(),
                "clientId" to clientId.toString(),
                "branchId" to branchId.toString(),
                "isWalkIn" to false,
                "finalPrice" to "2500.00",
            ),
        )
        assertEquals(1, count(SessionTable) { it.id eq id })
    }

    @Test
    fun `POST sessions slash id slash practitioners`() {
        val sessionId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(sessionId, clientId, branchDayId)
        val rowId = TestFixtures.uuid()
        replay(
            staff,
            "POST",
            "/api/sessions/$sessionId/practitioners",
            mapOf("id" to rowId.toString(), "practitionerId" to mate.toString()),
        )
        assertEquals(1, count(SessionPractitionerTable) { (it.sessionId eq sessionId) and (it.practitionerId eq mate) })
    }

    @Test
    fun `PATCH sessions slash id slash status`() {
        val sessionId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(sessionId, clientId, branchDayId)
        val version =
            transaction {
                SessionTable.selectAll().where { SessionTable.id eq sessionId }.single()[SessionTable.version]
            }
        replay(staff, "PATCH", "/api/sessions/$sessionId/status", mapOf("status" to "COMPLETED", "version" to version))
        val after = transaction { SessionTable.selectAll().where { SessionTable.id eq sessionId }.single() }
        assertEquals("COMPLETED", after[SessionTable.sessionStatus].name)
        assertEquals(version + 1, after[SessionTable.version], "the mark must land once")
    }

    @Test
    fun `POST product-sales`() {
        val id = TestFixtures.uuid()
        replay(
            staff,
            "POST",
            "/api/product-sales",
            mapOf(
                "id" to id.toString(),
                "branchDayId" to branchDayId.toString(),
                "isWalkIn" to true,
                "productId" to productId.toString(),
                "quantity" to 1,
            ),
        )
        assertEquals(1, count(ProductSaleTable) { it.id eq id })
        val stock =
            transaction {
                BranchInventoryTable
                    .selectAll()
                    .where {
                        BranchInventoryTable.productId eq productId
                    }.single()[BranchInventoryTable.currentStock]
            }
        assertEquals(STOCK - 1, stock, "stock must drop once")
    }

    @Test
    fun `POST expenses`() {
        val id = TestFixtures.uuid()
        replay(
            staff,
            "POST",
            "/api/expenses",
            mapOf(
                "id" to id.toString(),
                "branchDayId" to branchDayId.toString(),
                "amount" to "120.00",
                "category" to "PANTRY",
            ),
        )
        assertEquals(1, count(ExpenseTable) { it.id eq id })
    }

    @Test
    fun `POST allowances`() {
        val id = TestFixtures.uuid()
        replay(
            finance,
            "POST",
            "/api/allowances",
            mapOf(
                "id" to id.toString(),
                "branchDayId" to branchDayId.toString(),
                "userId" to mate.toString(),
                "amount" to "50.00",
            ),
        )
        assertEquals(1, count(AllowanceTable) { it.id eq id })
    }

    @Test
    fun `POST attendance clock-in`() {
        val id = TestFixtures.uuid()
        replay(
            clerk,
            "POST",
            "/api/attendance/clock-in",
            mapOf(
                "attendanceId" to id.toString(),
                "branchId" to branchId.toString(),
            ),
        )
        assertEquals(1, count(AttendanceTable) { it.userId eq clerk })
    }

    @Test
    fun `POST attendance clock-out`() {
        val id = TestFixtures.uuid()
        assertEquals(
            201,
            call(
                clerk,
                "POST",
                "/api/attendance/clock-in",
                mapOf(
                    "attendanceId" to id.toString(),
                    "branchId" to branchId.toString(),
                ),
            ).code,
        )
        replay(clerk, "POST", "/api/attendance/clock-out", mapOf("attendanceId" to id.toString()))
        assertEquals(1, count(AttendanceTable) { it.userId eq clerk })
    }

    @Test
    fun `POST branches slash id slash attendance marks, present`() {
        val id = TestFixtures.uuid()
        replay(
            staff,
            "POST",
            "/api/branches/$branchId/attendance/marks",
            mapOf("userId" to mate.toString(), "present" to true, "attendanceId" to id.toString()),
        )
        assertEquals(1, count(AttendanceTable) { it.userId eq mate })
    }

    @Test
    fun `POST branches slash id slash attendance marks, absent`() {
        val id = TestFixtures.uuid()
        assertEquals(
            200,
            call(
                staff,
                "POST",
                "/api/branches/$branchId/attendance/marks",
                mapOf(
                    "userId" to mate.toString(),
                    "present" to true,
                    "attendanceId" to id.toString(),
                ),
            ).code,
        )
        replay(
            staff,
            "POST",
            "/api/branches/$branchId/attendance/marks",
            mapOf(
                "userId" to mate.toString(),
                "present" to false,
            ),
        )
        assertEquals(1, count(AttendanceTable) { it.userId eq mate })
    }

    @Test
    fun `every queued save has a replay test`() {
        val source = File("web/src/lib/outbox.svelte.ts").readText()
        val queued =
            Regex("""method: "(\w+)", path: /\^(.*?)\$/,""")
                .findAll(source)
                .map { m -> "${m.groupValues[1]} ${m.groupValues[2].replace("\\/", "/").replace("[^/]+", "{}")}" }
                .toSet()
        assertEquals(
            EXPECTED_RULES,
            queued,
            "web/src/lib/outbox.svelte.ts RULES changed: add or remove the matching replay test above",
        )
    }

    companion object {
        private const val STOCK = 20
        private val DEFAULT_USER = TestFixtures.uuid()

        /** One line per RULES entry, in the form the test above derives from the source. */
        private val EXPECTED_RULES =
            setOf(
                "POST /api/clients",
                "POST /api/sessions",
                "POST /api/sessions/{}/practitioners",
                "PATCH /api/sessions/{}/status",
                "POST /api/product-sales",
                "POST /api/expenses",
                "POST /api/allowances",
                "POST /api/attendance/clock-in",
                "POST /api/attendance/clock-out",
                "POST /api/branches/{}/attendance/marks",
            )

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
                    ctx.status(403).json(
                        mapOf("error" to (e.message ?: "")),
                    )
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(
                        mapOf("error" to (e.message ?: "")),
                    )
                }
                cfg.routes.exception(ConflictException::class.java) { e, ctx ->
                    ctx.status(409).json(
                        mapOf("error" to (e.message ?: "")),
                    )
                }
                cfg.routes.exception(ValidationException::class.java) { e, ctx ->
                    ctx.status(400).json(
                        mapOf("error" to (e.message ?: "")),
                    )
                }
                ClientRoutes.register(cfg)
                SessionRoutes.register(cfg)
                ProductSaleRoutes.register(cfg)
                ExpenseRoutes.register(cfg)
                AllowanceRoutes.register(cfg)
                AttendanceRoutes.clockIn(cfg)
                AttendanceRoutes.clockOut(cfg)
                AttendanceRoutes.mark(cfg)
            }
        }
    }
}
