package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.dto.SessionResponse
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.service.session.SessionBaseRateService
import com.companyb.companyapp.service.session.SessionService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import io.javalin.Javalin
import io.javalin.http.UnauthorizedResponse
import io.javalin.testtools.Request
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
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

class SessionCreateServerTimeTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val bookedClientId = TestFixtures.uuid()
    private val walkInClientId = TestFixtures.uuid()
    private val rateId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "session-server-time")
        DatabaseTestHelper.insertTestBranch(branchId)
        DatabaseTestHelper.insertTestClient(bookedClientId)
        DatabaseTestHelper.insertTestClient(walkInClientId)
        DatabaseTestHelper.createBranchDayForToday(branchId)
        DatabaseTestHelper.grantCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
        SessionBaseRateService.setRate(callerId, rateId, branchId, SessionType.REGULAR, BigDecimal("2500.00"))
    }

    @Test
    fun `booked create ignores hostile timestamps and retry keeps original server timestamp`() {
        val sessionId = TestFixtures.uuid()
        val beforeCreate = databaseNow()
        val createdResponse =
            testServer.client.post(
                ApiRoutes.SESSIONS,
                sessionBody(sessionId, bookedClientId, isWalkIn = false, bookedAt = "2000-01-01T00:00:00Z"),
                asUser(callerId),
            )
        val afterCreate = databaseNow()

        assertEquals(201, createdResponse.code)
        val created = json.decodeFromString<SessionResponse>(createdResponse.body.string())
        val createdBookedAt = OffsetDateTime.parse(assertNotNull(created.bookedAt))
        assertTrue(createdBookedAt >= beforeCreate)
        assertTrue(createdBookedAt <= afterCreate)

        val retryResponse =
            testServer.client.post(
                ApiRoutes.SESSIONS,
                sessionBody(sessionId, bookedClientId, isWalkIn = false, bookedAt = "2099-12-31T23:59:59Z"),
                asUser(callerId),
            )
        val retry = json.decodeFromString<SessionResponse>(retryResponse.body.string())

        assertEquals(200, retryResponse.code)
        assertEquals(created.bookedAt, retry.bookedAt)
    }

    @Test
    fun `booked create without timestamp stamps server time and walk-in remains null`() {
        val bookedSessionId = TestFixtures.uuid()
        val walkInSessionId = TestFixtures.uuid()

        val bookedResponse =
            testServer.client.post(
                ApiRoutes.SESSIONS,
                sessionBody(bookedSessionId, bookedClientId, isWalkIn = false),
                asUser(callerId),
            )
        val booked = json.decodeFromString<SessionResponse>(bookedResponse.body.string())

        val walkInResponse =
            testServer.client.post(
                ApiRoutes.SESSIONS,
                sessionBody(
                    walkInSessionId,
                    walkInClientId,
                    isWalkIn = true,
                    bookedAt = "2099-12-31T23:59:59Z",
                    nextAppointmentDate = databaseNow().toLocalDate().minusDays(1).toString(),
                ),
                asUser(callerId),
            )
        val walkIn = json.decodeFromString<SessionResponse>(walkInResponse.body.string())

        assertEquals(201, bookedResponse.code)
        assertNotNull(booked.bookedAt)
        assertEquals(201, walkInResponse.code)
        assertNull(walkIn.bookedAt)
    }

    @Test
    fun `booked create with past next appointment returns 400 without persistence`() {
        val sessionId = TestFixtures.uuid()

        val response =
            testServer.client.post(
                ApiRoutes.SESSIONS,
                sessionBody(
                    sessionId,
                    bookedClientId,
                    isWalkIn = false,
                    nextAppointmentDate = databaseNow().toLocalDate().minusDays(1).toString(),
                ),
                asUser(callerId),
            )
        val error = json.decodeFromString<Map<String, String>>(response.body.string())

        assertEquals(400, response.code)
        assertEquals("Next appointment date cannot be before booking date", error["error"])
        transaction {
            assertTrue(SessionTable.selectAll().where { SessionTable.id eq sessionId }.empty())
            assertTrue(AuditLogTable.selectAll().where { AuditLogTable.recordId eq sessionId }.empty())
        }
    }

    private fun sessionBody(
        sessionId: UUID,
        clientId: UUID,
        isWalkIn: Boolean,
        bookedAt: String? = null,
        nextAppointmentDate: String? = null,
    ): Map<String, Any> =
        buildMap {
            put("id", sessionId.toString())
            put("clientId", clientId.toString())
            put("branchId", branchId.toString())
            put("isWalkIn", isWalkIn)
            put("finalPrice", "2500.00")
            if (bookedAt != null) put("bookedAt", bookedAt)
            if (nextAppointmentDate != null) put("nextAppointmentDate", nextAppointmentDate)
        }

    private fun databaseNow(): OffsetDateTime =
        transaction {
            BranchTable
                .select(CurrentTimestampWithTimeZone)
                .first()[CurrentTimestampWithTimeZone]
        }

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
                    Database.connect(DatabaseTestHelper.requireTestDataSource())
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
