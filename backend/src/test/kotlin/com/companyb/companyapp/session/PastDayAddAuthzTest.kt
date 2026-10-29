package com.companyb.companyapp.session

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.commerce.ProductSaleRoutes
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
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
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import io.javalin.Javalin
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.ClassRule
import java.math.BigDecimal
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Owed #8: staff enter yesterday's sessions and sales the next day. Any branch member may ADD to
 * a past day that is not remitted, with no reason; changing what is already there stays with
 * EDIT_PAST_DAY, and a remitted day stays locked. Marking a session there (done, no-show, back to
 * waiting) is open to any branch member with a reason.
 */
class PastDayAddAuthzTest : BasePostgresTest() {
    private val staff = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val existingSession = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val yesterday = TestFixtures.today.minusDays(1)
    private lateinit var yesterdayId: UUID
    private lateinit var remittedId: UUID

    override fun initTestData() {
        IdentityFixtures.insertTestUser(staff, "past-day-staff")
        BranchWorkforceFixtures.insertTestBranch(branchId)
        BranchWorkforceFixtures.insertTestAssignment(userId = staff, branchId = branchId, slot = 1, assignedBy = staff)
        SessionClientFixtures.insertTestClient(clientId)
        yesterdayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, yesterday)
        remittedId = BranchWorkforceFixtures.createRemittedBranchDay(branchId, TestFixtures.today.minusDays(2))
        SessionClientFixtures.insertTestSession(existingSession, clientId, yesterdayId)
        for (code in listOf(CapabilityCodes.VIEW_BRANCH_DATA, CapabilityCodes.EDIT_BRANCH_DATA)) {
            IdentityFixtures.grantCapability(staff, code, CapabilityContextType.BRANCH, branchId, sourceId)
        }
        SessionBaseRateService.setRate(staff, TestFixtures.uuid(), branchId, SessionType.REGULAR, BigDecimal("2500.00"))
        CommerceFinanceFixtures.insertTestCategory(categoryId)
        CommerceFinanceFixtures.insertTestProduct(productId, categoryId = categoryId)
        CommerceFinanceFixtures.insertTestCard(branchId, productId, stock = 10)
    }

    @Test
    fun `staff adds a session to yesterday with its practitioner, and it lands on yesterday`() {
        val id = TestFixtures.uuid()
        val response = post(ApiRoutes.SESSIONS, sessionBody(id, yesterday.toString()))

        assertEquals(201, response.code, response.body.string())
        assertEquals(yesterdayId, dayOf(id))
        transaction {
            val rows = SessionPractitionerTable.selectAll().where { SessionPractitionerTable.sessionId eq id }.toList()
            assertEquals(listOf(staff), rows.map { it[SessionPractitionerTable.practitionerId] })
        }
    }

    @Test
    fun `staff adds a product sale to yesterday`() {
        val response = post(ApiRoutes.PRODUCT_SALES, saleBody(yesterdayId))
        assertEquals(201, response.code, response.body.string())
    }

    @Test
    fun `staff still may not change yesterday's price or correct a mark`() {
        val price = patch("final-price", mapOf("finalPrice" to "1.00", "version" to 1, "reason" to "typo"))
        assertEquals(403, price.code)
        markYesterday("NO_SHOW", "came late")
        assertEquals(403, patch("status", mapOf("status" to "CANCELLED", "version" to 2, "reason" to "wrong")).code)
    }

    @Test
    fun `staff marks yesterday's session, and must give a reason`() {
        val bare = patch("status", mapOf("status" to "COMPLETED", "version" to 1))
        assertEquals(400, bare.code, bare.body.string())
        markYesterday("COMPLETED", "forgot to tap Done")
        val undo = patch("status", mapOf("status" to "PENDING", "version" to 2, "reason" to "wrong client"))
        assertEquals(200, undo.code, undo.body.string())
    }

    @Test
    fun `a remitted day stays locked for marks`() {
        val remitted = TestFixtures.uuid()
        val client = TestFixtures.uuid()
        SessionClientFixtures.insertTestClient(client)
        SessionClientFixtures.insertTestSession(remitted, client, remittedId)
        val body = mapOf("status" to "COMPLETED", "version" to 1, "reason" to "late")
        assertEquals(403, testServer.client.patch("/api/sessions/$remitted/status", body, asStaff).code)
    }

    private fun markYesterday(
        status: String,
        reason: String,
    ) {
        val r = patch("status", mapOf("status" to status, "version" to 1, "reason" to reason))
        assertEquals(200, r.code, r.body.string())
    }

    private fun patch(
        what: String,
        body: Any,
    ) = testServer.client.patch("/api/sessions/$existingSession/$what", body, asStaff)

    @Test
    fun `a remitted day stays locked for adds`() {
        assertEquals(403, post(ApiRoutes.PRODUCT_SALES, saleBody(remittedId)).code)
        val remittedDate = TestFixtures.today.minusDays(2).toString()
        assertEquals(403, post(ApiRoutes.SESSIONS, sessionBody(TestFixtures.uuid(), remittedDate)).code)
    }

    @Test
    fun `a session can't be added to a later day`() {
        val tomorrow = TestFixtures.today.plusDays(1).toString()
        assertEquals(400, post(ApiRoutes.SESSIONS, sessionBody(TestFixtures.uuid(), tomorrow)).code)
    }

    private val asStaff = Consumer<Request.Builder> { it.header("X-Test-User", staff.toString()) }

    private fun post(
        path: String,
        body: Any,
    ) = testServer.client.post(path, body, asStaff)

    private fun dayOf(sessionId: UUID): UUID =
        transaction {
            SessionTable.selectAll().where { SessionTable.id eq sessionId }.single()[SessionTable.branchDayId]
        }

    private fun sessionBody(
        id: UUID,
        date: String,
    ) = mapOf(
        "id" to id.toString(),
        "clientId" to TestFixtures.uuid().also { SessionClientFixtures.insertTestClient(it) }.toString(),
        "branchId" to branchId.toString(),
        "isWalkIn" to false,
        "finalPrice" to "2500.00",
        "date" to date,
        "practitioners" to listOf(mapOf("id" to TestFixtures.uuid().toString(), "practitionerId" to staff.toString())),
    )

    private fun saleBody(dayId: UUID) =
        mapOf(
            "id" to TestFixtures.uuid().toString(),
            "branchDayId" to dayId.toString(),
            "isWalkIn" to true,
            "productId" to productId.toString(),
            "quantity" to 1,
        )

    companion object {
        private val defaultUser = TestFixtures.uuid()

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
                    ctx.attribute("userId", ctx.header("X-Test-User") ?: defaultUser.toString())
                }
                cfg.routes.exception(ValidationException::class.java) { e, ctx ->
                    ctx.status(400).json(
                        mapOf("error" to e.message),
                    )
                }
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(
                        mapOf("error" to e.message),
                    )
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(
                        mapOf("error" to e.message),
                    )
                }
                cfg.routes.exception(ConflictException::class.java) { e, ctx ->
                    ctx.status(409).json(
                        mapOf("error" to e.message),
                    )
                }
                SessionRoutes.register(cfg)
                ProductSaleRoutes.register(cfg)
            }
        }
    }
}
