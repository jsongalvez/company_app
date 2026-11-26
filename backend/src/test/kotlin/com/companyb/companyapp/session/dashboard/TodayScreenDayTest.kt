package com.companyb.companyapp.session.dashboard

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.authorization.Role
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.TodayScreenResponse
import com.companyb.companyapp.database.JdbcTiming
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.logging.RequestQueryTally
import com.companyb.companyapp.session.SessionService
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import com.companyb.companyapp.workforce.AttendanceService
import io.javalin.Javalin
import io.javalin.testtools.Request
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.time.OffsetDateTime
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The day document: clock-in and done times, and a past day through the same endpoint. */
class TodayScreenDayTest : BasePostgresTest() {
    private val member = TestFixtures.uuid()
    private val outsider = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()
    private val pastSession = TestFixtures.uuid()
    private val yesterday = TestFixtures.today.minusDays(1)

    override fun initTestData() {
        IdentityFixtures.insertTestUser(member, "member")
        IdentityFixtures.insertTestUser(outsider, "outsider")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Branch A")
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Branch B")
        IdentityFixtures.member(member, branchId, Role.COORDINATOR)
        IdentityFixtures.member(outsider, otherBranchId, Role.COORDINATOR)
        SessionClientFixtures.insertTestBaseRate(TestFixtures.uuid(), branchId, member)
        val past = BranchWorkforceFixtures.createBranchDayForDate(branchId, yesterday)
        SessionClientFixtures.insertTestSession(pastSession, SessionClientFixtures.insertTestClient(), past)
        AttendanceService.clockIn(TestFixtures.uuid(), branchId, member)
    }

    companion object {
        @JvmField
        @ClassRule
        val testServer = JavalinTestServerRule(::createApp)

        private val json = Json { ignoreUnknownKeys = true }

        /** The on-duty Today document's budget (`TodayScreenServicePostgresTest`). */
        private const val QUERY_BUDGET = 13

        private fun createApp(): Javalin {
            val config = AppConfig.parse()
            JwtService.init(config)
            Password.init(config.authDummyPassword)
            return Javalin.create { cfg ->
                cfg.jsonMapper(KotlinxSerializationMapper())
                cfg.routes.before("${ApiRoutes.API_PREFIX}*") { ctx ->
                    Database.connect(TestDatabaseLifecycle.requireTestDataSource())
                    ctx.attribute("userId", ctx.header("X-Test-User")!!)
                }
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(mapOf("error" to (e.message ?: "Forbidden")))
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(mapOf("error" to (e.message ?: "Not Found")))
                }
                TodayScreenRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun get(
        user: UUID,
        query: String = "",
    ) = testServer.client.get("/api/branches/$branchId/today-screen$query", asUser(user))

    private fun screen(
        user: UUID,
        query: String = "",
    ): TodayScreenResponse {
        val response = get(user, query)
        val body = response.body.string()
        assertEquals(200, response.code, body)
        return json.decodeFromString(body)
    }

    @Test
    fun `the roster says since when each person on duty is clocked in`() {
        val row = screen(member).roster!!.single { it.userId == member.toString() }
        assertTrue(row.present)
        assertNotNull(row.clockedInAt, "an on-duty row carries its clock-in time")
        OffsetDateTime.parse(row.clockedInAt)
    }

    @Test
    fun `a session marked done carries when it was done, and loses it when undone`() {
        val day = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val id = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(id, SessionClientFixtures.insertTestClient(), day)
        SessionService.updateStatus(member, id, SessionStatus.COMPLETED, 1)
        val done = screen(member).dashboard!!.sessions.single { it.id == id.toString() }
        assertNotNull(done.completedAt, "a done session names its done time")
        OffsetDateTime.parse(done.completedAt)
        SessionService.updateStatus(member, id, SessionStatus.PENDING, 2)
        assertNull(
            screen(member)
                .dashboard!!
                .sessions
                .single { it.id == id.toString() }
                .completedAt,
        )
    }

    @Test
    fun `with no date the document is today's, as before`() {
        val today = screen(member)
        assertEquals(TestFixtures.today.toString(), today.date)
        assertNotNull(today.bookings)
        assertNotNull(today.earlierWaitingDays)
        assertEquals(screen(member, "?date=${TestFixtures.today}"), today)
    }

    @Test
    fun `a date gives that past day's document to whoever may see the day`() {
        val past = screen(member, "?date=$yesterday")
        assertEquals(yesterday.toString(), past.date)
        assertEquals(listOf(pastSession.toString()), past.dashboard!!.sessions.map { it.id })
        assertNull(past.dashboardRefusal, "a past day needs no clock-in")
        assertNull(past.siteHub, "a site hub shares only today's work")
    }

    @Test
    fun `a past day's document stays in the Today screen's statement budget and names what it read`() {
        val source = JdbcTiming.wrap(TestDatabaseLifecycle.requireTestDataSource())
        Database.connect(source)
        val (queries, reads, changed) =
            try {
                RequestQueryTally.startRequest()
                TodayScreenService.get(member, branchId, yesterday)
                Triple(RequestQueryTally.snapshot().queries, RequestQueryTally.reads(), RequestQueryTally.changed())
            } finally {
                RequestQueryTally.endRequest()
                Database.connect(TestDatabaseLifecycle.requireTestDataSource())
            }
        assertTrue(queries <= QUERY_BUDGET, "a past day's document ran $queries queries, budget $QUERY_BUDGET")
        assertTrue("*" !in reads, "reads $reads")
        assertTrue(setOf("branch_day", "session", "attendance").all { it in reads }, "reads $reads")
        assertEquals(emptySet(), changed, "a past day's read makes no row")
    }

    @Test
    fun `a past day is refused to someone who may not see it`() {
        assertEquals(403, get(outsider, "?date=$yesterday").code)
    }

    @Test
    fun `a future or malformed date is refused`() {
        assertEquals(400, get(member, "?date=${TestFixtures.today.plusDays(1)}").code)
        assertEquals(400, get(member, "?date=yesterday").code)
    }
}
