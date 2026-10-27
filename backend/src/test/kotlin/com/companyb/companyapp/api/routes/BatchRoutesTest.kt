package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.middleware.TraceIdFilter
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.contracts.batch.BatchRequest
import com.companyb.companyapp.contracts.batch.BatchResponse
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.session.dashboard.TodayScreenRoutes
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.workforce.AttendanceService
import io.javalin.Javalin
import io.javalin.http.UnauthorizedResponse
import io.javalin.testtools.Request
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A batch answers each read as that read answers alone, for the person asking, and takes only reads of the API. */
class BatchRoutesTest : BasePostgresTest() {
    private val onDuty = TestFixtures.uuid()
    private val offDuty = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(onDuty, "on-duty")
        IdentityFixtures.insertTestUser(offDuty, "off-duty")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Branch A")
        AttendanceService.clockIn(TestFixtures.uuid(), branchId, onDuty)
    }

    companion object {
        @JvmField
        @ClassRule
        val testServer = JavalinTestServerRule(::createApp)

        private fun createApp(): Javalin {
            val config = AppConfig.parse()
            JwtService.init(config)
            Password.init(config.authDummyPassword)
            return Javalin.create { cfg ->
                cfg.jsonMapper(KotlinxSerializationMapper())
                cfg.routes.before {
                    Database.connect(TestDatabaseLifecycle.requireTestDataSource())
                    TraceIdFilter.before(it)
                }
                cfg.routes.before("${ApiRoutes.API_PREFIX}*") { ctx ->
                    val token = ctx.header("Authorization")?.removePrefix("Bearer ") ?: throw UnauthorizedResponse()
                    ctx.attribute("userId", JwtService.verifyToken(token) ?: throw UnauthorizedResponse())
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
                TodayScreenRoutes.register(cfg)
                BatchRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> =
        Consumer { it.header("Authorization", "Bearer ${JwtService.generateToken(user.toString())}") }

    private fun batch(
        user: UUID?,
        vararg paths: String,
    ) = testServer.client.post(ApiRoutes.BATCH, BatchRequest(paths.toList()), user?.let { asUser(it) })

    private fun results(
        user: UUID,
        vararg paths: String,
    ): BatchResponse {
        val response = batch(user, *paths)
        val text = response.body.string()
        assertEquals(200, response.code, text)
        return Json.decodeFromString(BatchResponse.serializer(), text)
    }

    private fun alone(
        user: UUID,
        path: String,
    ) = testServer.client.get(path, asUser(user)).let { it.code to it.body.string() }

    private val today get() = "/api/branches/$branchId/today-screen"
    private val unknown get() = "/api/branches/${TestFixtures.uuid()}/today-screen"

    @Test
    fun `each read answers as it does alone, in order, with its own status`() {
        val r = results(onDuty, today, unknown, today).results
        assertEquals(listOf(200, 404, 200), r.map { it.status })
        assertEquals(alone(onDuty, today).second, r[0].body)
        assertEquals(alone(onDuty, unknown).second, r[1].body)
        assertTrue(r.all { !it.traceId.isNullOrBlank() })
    }

    @Test
    fun `a read is answered for the person asking, not for anyone else`() {
        assertTrue(
            results(onDuty, today)
                .results
                .single()
                .body
                .contains("\"dashboardRefusal\":null"),
        )
        assertTrue(
            results(offDuty, today)
                .results
                .single()
                .body
                .contains("\"dashboard\":null"),
        )
    }

    @Test
    fun `takes only reads of the API`() {
        val bad =
            listOf(
                "/health",
                "/api/batch",
                "/api/../auth/login",
                "http://example.com/api/me",
                "//example.com/api/me",
                "/api/x//y",
                "/api/me\r\nX-Evil: 1",
                "api/me",
                "/api/" + "a".repeat(2100),
            )
        bad.forEach { assertEquals(400, batch(onDuty, it).code, it.take(40)) }
        assertEquals(400, batch(onDuty).code)
        assertEquals(400, batch(onDuty, *Array(BatchRoutes.MAX_PATHS + 1) { today }).code)
    }

    @Test
    fun `a batch without a sign-in is refused`() {
        assertEquals(401, batch(null, today).code)
    }
}
