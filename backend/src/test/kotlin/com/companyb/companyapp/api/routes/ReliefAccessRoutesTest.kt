package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.ReliefAccessStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.GrantReliefAccessTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import io.javalin.Javalin
import io.javalin.http.UnauthorizedResponse
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #351 — the relief-access discovery surface: `GET /api/relief-access?branchDayId=` (caller-relative:
 * rows targeting or requested by the caller — ownership is the authorization, no capability gate)
 * and `GET /api/relief-access/candidates?branchDayId=` (checked-in users minus caller, gated on the
 * caller's own active clock-in — the dashboard universal-post-clock-in precedent; pre-grant relief
 * users hold no capabilities).
 */
class ReliefAccessRoutesTest : BasePostgresTest() {
    private val requester = TestFixtures.uuid()
    private val targetUser = TestFixtures.uuid()
    private val outsider = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val branchDayId = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(requester, "requester")
        trackOwned(AppUserTable, AppUserTable.id, requester)
        DatabaseTestHelper.insertTestUser(targetUser, "target")
        trackOwned(AppUserTable, AppUserTable.id, targetUser)
        DatabaseTestHelper.insertTestUser(outsider, "outsider")
        trackOwned(AppUserTable, AppUserTable.id, outsider)
        DatabaseTestHelper.insertTestBranch(branchId, "ReliefRoutes-${branchId.toString().take(8)}")
        trackOwned(BranchTable, BranchTable.id, branchId)
        insertBranchDay(branchDayId, branchId)
        trackOwned(BranchDayTable, BranchDayTable.id, branchDayId)
        insertAttendance(TestFixtures.uuid(), requester, branchDayId)
        insertAttendance(TestFixtures.uuid(), targetUser, branchDayId)
        trackOwned(AttendanceTable, AttendanceTable.branchDayId, branchDayId)
        trackOwned(GrantReliefAccessTable, GrantReliefAccessTable.branchDayId, branchDayId)
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
                cfg.routes.exception(ValidationException::class.java) { e, ctx ->
                    ctx.status(400).json(mapOf("error" to (e.message ?: "Bad Request")))
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
                ReliefAccessRoutes.listReliefAccess(cfg)
                ReliefAccessRoutes.listReliefCandidates(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    @Test
    fun `target user and requester each see the pending request - outsider sees none`() {
        val requestId = TestFixtures.uuid()
        insertRequest(requestId, requester, targetUser, branchDayId)

        testServer.client.let { client ->
            val asTarget =
                client.get("${ApiRoutes.RELIEF_ACCESS}?branchDayId=$branchDayId", asUser(targetUser))
            assertEquals(200, asTarget.code)
            val targetBody = asTarget.body.string()
            assertTrue(targetBody.contains("\"id\":\"$requestId\""), targetBody)
            assertTrue(targetBody.contains("\"requestStatus\":\"PENDING\""), targetBody)

            val asRequester =
                client.get("${ApiRoutes.RELIEF_ACCESS}?branchDayId=$branchDayId", asUser(requester))
            assertEquals(200, asRequester.code)
            assertTrue(asRequester.body.string().contains("\"id\":\"$requestId\""))

            val asOutsider =
                client.get("${ApiRoutes.RELIEF_ACCESS}?branchDayId=$branchDayId", asUser(outsider))
            assertEquals(200, asOutsider.code)
            assertEquals(
                "[]",
                asOutsider.body
                    .string()
                    .orEmpty()
                    .trim(),
            )
        }
    }

    @Test
    fun `list is scoped to one branch day`() {
        val requestId = TestFixtures.uuid()
        insertRequest(requestId, requester, targetUser, branchDayId)
        val otherDayId = TestFixtures.uuid()

        testServer.client.let { client ->
            val response =
                client.get("${ApiRoutes.RELIEF_ACCESS}?branchDayId=$otherDayId", asUser(targetUser))
            assertEquals(200, response.code)
            assertEquals(
                "[]",
                response.body
                    .string()
                    .orEmpty()
                    .trim(),
            )
        }
    }

    @Test
    fun `list without branchDayId fails with 400`() {
        testServer.client.let { client ->
            val response = client.get(ApiRoutes.RELIEF_ACCESS, asUser(targetUser))
            assertEquals(400, response.code)
        }
    }

    @Test
    fun `candidates exclude the caller and include other clocked-in users`() {
        testServer.client.let { client ->
            val response =
                client.get(
                    "${ApiRoutes.RELIEF_ACCESS_CANDIDATES}?branchDayId=$branchDayId",
                    asUser(requester),
                )
            assertEquals(200, response.code)
            val body = response.body.string()
            assertTrue(body.contains("\"userId\":\"$targetUser\""), body)
            assertTrue(!body.contains("\"userId\":\"$requester\""), body)
        }
    }

    @Test
    fun `candidates require an active clock-in for the caller`() {
        testServer.client.let { client ->
            val response =
                client.get(
                    "${ApiRoutes.RELIEF_ACCESS_CANDIDATES}?branchDayId=$branchDayId",
                    asUser(outsider),
                )
            assertEquals(403, response.code)
        }
    }

    // Fixture inserts take the row values as PARAMETERS (the service-test convention): Exposed's
    // insert body is a `T.(...)` extension, so inside it a bare name colliding with a table column
    // (branchId, branchDayId, targetUser...) resolves to the TABLE'S column — binding a column
    // reference where a value belongs ("invalid reference to FROM-clause entry"). Parameters win
    // resolution over implicit receivers.
    private fun insertBranchDay(
        id: UUID,
        dayBranchId: UUID,
    ) {
        transaction {
            BranchDayTable.insert {
                it[BranchDayTable.id] = id
                it[BranchDayTable.branchId] = dayBranchId
                it[BranchDayTable.date] = TestFixtures.today
                it[BranchDayTable.status] = DayStatus.OPEN
            }
        }
    }

    private fun insertRequest(
        requestId: UUID,
        requestRequester: UUID,
        requestTargetUser: UUID,
        requestBranchDayId: UUID,
    ) {
        transaction {
            GrantReliefAccessTable.insert {
                it[GrantReliefAccessTable.id] = requestId
                it[GrantReliefAccessTable.branchDayId] = requestBranchDayId
                it[GrantReliefAccessTable.requestedBy] = requestRequester
                it[GrantReliefAccessTable.targetUser] = requestTargetUser
                it[GrantReliefAccessTable.requestStatus] = ReliefAccessStatus.PENDING
            }
        }
    }

    private fun insertAttendance(
        id: UUID,
        attendanceUserId: UUID,
        attendanceBranchDayId: UUID,
    ) {
        transaction {
            AttendanceTable.insert {
                it[AttendanceTable.id] = id
                it[AttendanceTable.branchDayId] = attendanceBranchDayId
                it[AttendanceTable.userId] = attendanceUserId
                it[AttendanceTable.markedBy] = attendanceUserId
                it[AttendanceTable.clockIn] = TestFixtures.now
            }
        }
    }
}
