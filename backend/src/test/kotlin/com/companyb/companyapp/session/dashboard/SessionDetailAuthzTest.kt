package com.companyb.companyapp.session.dashboard
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.notification.NotificationService
import com.companyb.companyapp.session.SessionRoutes
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import io.javalin.Javalin
import io.javalin.http.UnauthorizedResponse
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #152 session-detail GET authz: bearer-only read gate (#151 Q1/Q5) — the caller may fetch
 * iff a notification row exists for (sessionId, caller), any read state; 404 for both
 * non-bearer and missing sessions. No capability filters — the notification IS the
 * authorization, so a bearer needs zero grants (the primary case: an alerted coordinator
 * without VIEW_BRANCH_DATA must still open the pushed session).
 */
class SessionDetailAuthzTest : BasePostgresTest() {
    private val bearerUser = TestFixtures.uuid()
    private val otherUser = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val sessionId = TestFixtures.uuid()
    private lateinit var branchDayId: UUID

    override fun initTestData() {
        IdentityFixtures.insertTestUser(bearerUser, "detail-bearer")
        IdentityFixtures.insertTestUser(otherUser, "detail-other")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Branch ${branchId.toString().take(8)}")
        SessionClientFixtures.insertTestClient(clientId)
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        SessionClientFixtures.insertTestSession(
            id = sessionId,
            clientId = clientId,
            branchDayId = branchDayId,
            sessionStatus = SessionStatus.COMPLETED,
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
                SessionRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    @Test
    fun `bearer with unread notification gets 200 with dashboard-shaped response`() {
        seedNotification(sessionId, bearerUser, branchId)
        testServer.client.let { client ->
            val response = client.get("/api/sessions/$sessionId", asUser(bearerUser))

            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.contains("\"id\":\"$sessionId\""))
            assertTrue(body.contains("\"clientName\":\"Test Client\""))
            assertTrue(body.contains("\"isVoided\":false"))
        }
    }

    @Test
    fun `bearer with read notification gets 200`() {
        val notification = seedNotification(sessionId, bearerUser, branchId)
        NotificationService.markRead(bearerUser, notification.id)
        testServer.client.let { client ->
            assertEquals(200, client.get("/api/sessions/$sessionId", asUser(bearerUser)).code)
        }
    }

    @Test
    fun `user without notification gets 404`() {
        seedNotification(sessionId, otherUser, branchId)
        testServer.client.let { client ->
            assertEquals(404, client.get("/api/sessions/$sessionId", asUser(bearerUser)).code)
        }
    }

    @Test
    fun `missing session gets 404`() {
        testServer.client.let { client ->
            assertEquals(404, client.get("/api/sessions/${TestFixtures.uuid()}", asUser(bearerUser)).code)
        }
    }

    // #128 lesson — the X-Test-User harness can't exercise the real auth filter; 401 needs the
    // real-JWT harness (the #147/ReportsReadScopeAuthzTest precedent).
    @Test
    fun `unauthenticated request gets 401`() {
        testServer.client.let { client ->
            assertEquals(401, client.get("/api/sessions/$sessionId").code)
        }
    }

    private fun seedNotification(
        sessionId: UUID,
        userId: UUID,
        branchId: UUID,
    ): com.companyb.companyapp.notification.Notification {
        val notification =
            SessionClientFixtures.insertTestNotification(
                sessionId = sessionId,
                userId = userId,
                branchId = branchId,
            )
        return notification
    }
}
