package com.companyb.companyapp.workforce.relief
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.ReliefAccessStatus
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
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
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
 * #357 — the relief-access discovery surface under the broadcast model:
 * `GET /api/relief-access?branchDayId=` (branch members see every row on the day; outsiders
 * only their own), `GET /api/relief-access/mine` (the caller's asks with branch context),
 * and `GET /api/relief-access/branch-options` (bearer-only picker for the pre-clock-in
 * request flow). The #351 candidates endpoint is retired with targeting.
 */
class ReliefAccessRoutesTest : BasePostgresTest() {
    private val requester = TestFixtures.uuid()
    private val otherRequester = TestFixtures.uuid()
    private val memberId = TestFixtures.uuid()
    private val outsider = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val branchName = "ReliefRoutes-${branchId.toString().take(8)}"
    private val branchDayId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(requester, "requester")
        IdentityFixtures.insertTestUser(otherRequester, "requester-2")
        IdentityFixtures.insertTestUser(memberId, "member")
        IdentityFixtures.insertTestUser(outsider, "outsider")
        BranchWorkforceFixtures.insertTestBranch(branchId, branchName)
        insertBranchDay(branchDayId, branchId)
        BranchWorkforceFixtures.insertTestAssignment(
            userId = memberId,
            branchId = branchId,
            slot = 1,
            assignedBy = memberId,
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
                ReliefAccessRoutes.listMine(cfg)
                ReliefAccessRoutes.listBranchOptions(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    @Test
    fun `member sees every request on the day - requesters see only their own`() {
        val firstId = TestFixtures.uuid()
        val secondId = TestFixtures.uuid()
        insertRequest(firstId, requester, branchDayId)
        insertRequest(secondId, otherRequester, branchDayId)

        testServer.client.let { client ->
            val asMember = client.get("${ApiRoutes.RELIEF_ACCESS}?branchDayId=$branchDayId", asUser(memberId))
            assertEquals(200, asMember.code)
            val memberBody = asMember.body.string()
            assertTrue(memberBody.contains("\"id\":\"$firstId\""), memberBody)
            assertTrue(memberBody.contains("\"id\":\"$secondId\""), memberBody)

            val asRequester =
                client.get("${ApiRoutes.RELIEF_ACCESS}?branchDayId=$branchDayId", asUser(requester))
            assertEquals(200, asRequester.code)
            val body = asRequester.body.string()
            assertTrue(body.contains("\"id\":\"$firstId\""))
            assertTrue(!body.contains("\"id\":\"$secondId\""), body)
        }
    }

    @Test
    fun `list is scoped to one branch day and requires the query param`() {
        val requestId = TestFixtures.uuid()
        insertRequest(requestId, requester, branchDayId)

        testServer.client.let { client ->
            val otherDay = client.get("${ApiRoutes.RELIEF_ACCESS}?branchDayId=${TestFixtures.uuid()}", asUser(memberId))
            assertEquals(200, otherDay.code)
            assertEquals(
                "[]",
                otherDay.body
                    .string()
                    .orEmpty()
                    .trim(),
            )

            val missing = client.get(ApiRoutes.RELIEF_ACCESS, asUser(memberId))
            assertEquals(400, missing.code)
        }
    }

    @Test
    fun `mine returns only the caller's rows with branch context`() {
        val firstId = TestFixtures.uuid()
        val secondId = TestFixtures.uuid()
        insertRequest(firstId, requester, branchDayId)
        insertRequest(secondId, otherRequester, branchDayId)

        testServer.client.let { client ->
            val response = client.get(ApiRoutes.RELIEF_ACCESS_MINE, asUser(requester))
            assertEquals(200, response.code)
            val body = response.body.string()
            assertTrue(body.contains("\"id\":\"$firstId\""), body)
            assertTrue(!body.contains("\"id\":\"$secondId\""), body)
            assertTrue(body.contains("\"branchId\":\"$branchId\""), body)
            assertTrue(body.contains(branchName), body)
        }
    }

    @Test
    fun `branch options list every branch bearer-authenticated`() {
        testServer.client.let { client ->
            val response = client.get(ApiRoutes.RELIEF_ACCESS_BRANCH_OPTIONS, asUser(requester))
            assertEquals(200, response.code)
            val body = response.body.string()
            assertTrue(body.contains("\"branchId\":\"$branchId\""), body)
            assertTrue(body.contains(branchName), body)
        }
    }

    // Fixture inserts take the row values as PARAMETERS (the service-test convention): Exposed's
    // insert body is a `T.(...)` extension, so inside it a bare name colliding with a table column
    // (branchId, branchDayId...) resolves to the TABLE'S column — binding a column reference where
    // a value belongs ("invalid reference to FROM-clause entry"). Parameters win resolution over
    // implicit receivers.
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
        requestBranchDayId: UUID,
    ) {
        transaction {
            GrantReliefAccessTable.insert {
                it[GrantReliefAccessTable.id] = requestId
                it[GrantReliefAccessTable.branchDayId] = requestBranchDayId
                it[GrantReliefAccessTable.requestedBy] = requestRequester
                it[GrantReliefAccessTable.requestStatus] = ReliefAccessStatus.PENDING
            }
        }
    }
}
