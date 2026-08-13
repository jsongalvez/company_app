package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.NotificationTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.service.NotificationService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.Javalin
import io.javalin.http.UnauthorizedResponse
import io.javalin.testtools.JavalinTest
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.jdbc.Database
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
    private val bearerUser = UUID.randomUUID()
    private val otherUser = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val clientId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()
    private lateinit var branchDayId: UUID

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(bearerUser, "detail-bearer")
        DatabaseTestHelper.insertTestUser(otherUser, "detail-other")
        DatabaseTestHelper.insertTestBranch(branchId, "Branch ${branchId.toString().take(8)}")
        DatabaseTestHelper.insertTestClient(clientId)
        branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        DatabaseTestHelper.insertTestSession(
            id = sessionId,
            clientId = clientId,
            branchDayId = branchDayId,
            sessionStatus = SessionStatus.COMPLETED,
        )

        trackOwned(AppUserTable, AppUserTable.id, bearerUser)
        trackOwned(AppUserTable, AppUserTable.id, otherUser)
        trackOwned(BranchTable, BranchTable.id, branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        trackOwned(ClientTable, ClientTable.id, clientId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
    }

    private fun createApp(): Javalin {
        val config = AppConfig.parse()
        JwtService.init(config)
        Password.init(config.authDummyPassword)
        return Javalin.create { cfg ->
            cfg.jsonMapper(KotlinxSerializationMapper())
            cfg.routes.before { ctx ->
                Database.connect(DatabaseTestHelper.requireTestDataSource())
                ctx.attribute("userId", ctx.header("X-Test-User") ?: otherUser.toString())
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

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    @Test
    fun `bearer with unread notification gets 200 with dashboard-shaped response`() {
        seedNotification(sessionId, bearerUser, branchId)
        JavalinTest.test(createApp()) { _, client ->
            val response = client.get("/api/sessions/$sessionId", asUser(bearerUser))

            assertEquals(200, response.code)
            val body = response.body?.string().orEmpty()
            assertTrue(body.contains("\"id\":\"$sessionId\""))
            assertTrue(body.contains("\"clientName\":\"Test Client\""))
            assertTrue(body.contains("\"isVoided\":false"))
        }
    }

    @Test
    fun `bearer with read notification gets 200`() {
        val notification = seedNotification(sessionId, bearerUser, branchId)
        NotificationService.markRead(bearerUser, notification.id)
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(200, client.get("/api/sessions/$sessionId", asUser(bearerUser)).code)
        }
    }

    @Test
    fun `user without notification gets 404`() {
        seedNotification(sessionId, otherUser, branchId)
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(404, client.get("/api/sessions/$sessionId", asUser(bearerUser)).code)
        }
    }

    @Test
    fun `missing session gets 404`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(404, client.get("/api/sessions/${UUID.randomUUID()}", asUser(bearerUser)).code)
        }
    }

    // #128 lesson — the X-Test-User harness can't exercise the real auth filter; 401 needs the
    // real-JWT harness (the #147/ReportsReadScopeAuthzTest precedent).
    @Test
    fun `unauthenticated request gets 401`() {
        JavalinTest.test(createAppWithJwt()) { _, client ->
            assertEquals(401, client.get("/api/sessions/$sessionId").code)
        }
    }

    private fun createAppWithJwt(): Javalin {
        val config = AppConfig.parse()
        JwtService.init(config)
        Password.init(config.authDummyPassword)
        return Javalin.create { cfg ->
            cfg.jsonMapper(KotlinxSerializationMapper())
            cfg.routes.before { ctx ->
                Database.connect(DatabaseTestHelper.requireTestDataSource())
            }
            cfg.routes.before("${ApiRoutes.API_PREFIX}*") { ctx ->
                val token = ctx.header("Authorization")?.removePrefix("Bearer ") ?: throw UnauthorizedResponse()
                val userId = JwtService.verifyToken(token) ?: throw UnauthorizedResponse()
                ctx.attribute("userId", userId)
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

    private fun seedNotification(
        sessionId: UUID,
        userId: UUID,
        branchId: UUID,
    ): com.companyb.companyapp.repository.model.Notification {
        val notification =
            DatabaseTestHelper.insertTestNotification(
                sessionId = sessionId,
                userId = userId,
                branchId = branchId,
            )
        trackOwned(NotificationTable, NotificationTable.id, notification.id)
        return notification
    }
}
