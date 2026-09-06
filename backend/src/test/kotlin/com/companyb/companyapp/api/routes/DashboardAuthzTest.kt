package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.service.attendance.AttendanceService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import io.javalin.Javalin
import io.javalin.http.UnauthorizedResponse
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardAuthzTest : BasePostgresTest() {
    private val clockedInUser = TestFixtures.uuid()
    private val otherBranchUser = TestFixtures.uuid()
    private val notClockedInUser = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(clockedInUser, "clocked-in")
        DatabaseTestHelper.insertTestUser(otherBranchUser, "other-branch")
        DatabaseTestHelper.insertTestUser(notClockedInUser, "not-clocked")
        DatabaseTestHelper.insertTestBranch(branchId, "Branch A")
        DatabaseTestHelper.insertTestBranch(otherBranchId, "Branch B")

        AttendanceService.clockIn(TestFixtures.uuid(), branchId, clockedInUser)
        AttendanceService.clockIn(TestFixtures.uuid(), otherBranchId, otherBranchUser)
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
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(
                        mapOf("error" to (e.message ?: "Forbidden")),
                    )
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(
                        mapOf("error" to (e.message ?: "Not Found")),
                    )
                }
                DashboardRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    @Test
    fun `clocked in user reads dashboard`() {
        testServer.client.let { client ->
            assertEquals(200, client.get("/api/branches/$branchId/dashboard/today", asUser(clockedInUser)).code)
        }
    }

    @Test
    fun `user not clocked in gets 403`() {
        testServer.client.let { client ->
            assertEquals(403, client.get("/api/branches/$branchId/dashboard/today", asUser(notClockedInUser)).code)
        }
    }

    @Test
    fun `user clocked in at another branch gets 403`() {
        testServer.client.let { client ->
            assertEquals(403, client.get("/api/branches/$branchId/dashboard/today", asUser(otherBranchUser)).code)
        }
    }

    @Test
    fun `missing branch with clocked in user gets 404`() {
        testServer.client.let { client ->
            val missingBranch = TestFixtures.uuid()
            assertEquals(404, client.get("/api/branches/$missingBranch/dashboard/today", asUser(clockedInUser)).code)
        }
    }

    @Test
    fun `unauthenticated request gets 401`() {
        testServer.client.let { client ->
            assertEquals(401, client.get("/api/branches/$branchId/dashboard/today").code)
        }
    }
}
