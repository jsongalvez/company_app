package com.companyb.companyapp.session

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.session.SessionResponse
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.session.SessionBaseRateService
import com.companyb.companyapp.session.SessionService
import com.companyb.companyapp.session.SessionTable
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import com.companyb.companyapp.workforce.AttendanceTable
import io.javalin.Javalin
import io.javalin.http.UnauthorizedResponse
import io.javalin.testtools.Request
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.ClassRule
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionCreateWithPractitionersTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val noneUser = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val bookedClientId = TestFixtures.uuid()
    private val walkInClientId = TestFixtures.uuid()
    private val rateId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val practitionerId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "session-create-with-practitioners")
        IdentityFixtures.insertTestUser(noneUser, "session-create-practitioner-none")
        IdentityFixtures.insertTestUser(practitionerId, "session-create-practitioner")
        BranchWorkforceFixtures.insertTestBranch(branchId)
        SessionClientFixtures.insertTestClient(bookedClientId)
        SessionClientFixtures.insertTestClient(walkInClientId)
        val todayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        transaction {
            AttendanceTable.insert {
                it[AttendanceTable.id] = TestFixtures.uuid()
                it[AttendanceTable.branchDayId] = todayId
                it[AttendanceTable.userId] = callerId
                it[AttendanceTable.markedBy] = callerId
            }
        }
        IdentityFixtures.grantCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
        SessionBaseRateService.setRate(callerId, rateId, branchId, SessionType.REGULAR, BigDecimal("2500.00"))
    }

    @Test
    fun `create with practitioners adds them in the same request and a retry adds none twice`() {
        val sessionId = TestFixtures.uuid()
        val body = sessionBody(sessionId, practitionerId)

        val first = testServer.client.post(ApiRoutes.SESSIONS, body, asUser(callerId))
        val retry = testServer.client.post(ApiRoutes.SESSIONS, body, asUser(callerId))

        assertEquals(201, first.code)
        assertEquals(200, retry.code)
        transaction {
            val rows =
                SessionPractitionerTable
                    .selectAll()
                    .where { SessionPractitionerTable.sessionId eq sessionId }
                    .toList()
            assertEquals(listOf(practitionerId), rows.map { it[SessionPractitionerTable.practitionerId] })
        }
    }

    @Test
    fun `create with an unknown practitioner fails and the session is still created`() {
        val sessionId = TestFixtures.uuid()

        val response =
            testServer.client.post(
                ApiRoutes.SESSIONS,
                sessionBody(sessionId, TestFixtures.uuid()),
                asUser(callerId),
            )

        assertEquals(404, response.code)
        transaction { assertTrue(SessionTable.selectAll().where { SessionTable.id eq sessionId }.any()) }
    }

    private fun sessionBody(
        sessionId: UUID,
        practitioner: UUID,
    ): Map<String, Any> =
        mapOf(
            "id" to sessionId.toString(),
            "clientId" to bookedClientId.toString(),
            "branchId" to branchId.toString(),
            "isWalkIn" to false,
            "finalPrice" to "2500.00",
            "practitioners" to
                listOf(
                    mapOf(
                        "id" to UUID.nameUUIDFromBytes("$sessionId$practitioner".toByteArray()).toString(),
                        "practitionerId" to practitioner.toString(),
                    ),
                ),
        )

    private fun asUser(userId: UUID): Consumer<Request.Builder> =
        Consumer { it.header("X-Test-User", userId.toString()) }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
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
                SessionRoutes.register(cfg)
            }
        }
    }
}
