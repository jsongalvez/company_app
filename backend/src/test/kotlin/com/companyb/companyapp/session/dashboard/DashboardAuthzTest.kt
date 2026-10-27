package com.companyb.companyapp.session.dashboard
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
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
        IdentityFixtures.insertTestUser(clockedInUser, "clocked-in")
        IdentityFixtures.insertTestUser(otherBranchUser, "other-branch")
        IdentityFixtures.insertTestUser(notClockedInUser, "not-clocked")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Branch A")
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Branch B")

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
                    ctx.status(403).json(
                        mapOf("error" to (e.message ?: "Forbidden")),
                    )
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(
                        mapOf("error" to (e.message ?: "Not Found")),
                    )
                }
                TodayScreenRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun screen(
        branch: UUID,
        user: UUID? = null,
    ) = testServer.client.get("/api/branches/$branch/today-screen", user?.let { asUser(it) })

    @Test
    fun `clocked in user reads the dashboard`() {
        val response = screen(branchId, clockedInUser)
        assertEquals(200, response.code)
        assertEquals(false, response.body.string().contains("\"dashboard\":null"))
    }

    @Test
    fun `user not clocked in gets the screen without a dashboard`() {
        val response = screen(branchId, notClockedInUser)
        assertEquals(200, response.code)
        assertEquals(true, response.body.string().contains("\"dashboard\":null"))
    }

    @Test
    fun `user clocked in at another branch gets no dashboard here`() {
        assertEquals(true, screen(branchId, otherBranchUser).body.string().contains("\"dashboard\":null"))
    }

    @Test
    fun `missing branch gets 404`() {
        assertEquals(404, screen(TestFixtures.uuid(), clockedInUser).code)
    }

    @Test
    fun `unauthenticated request gets 401`() {
        assertEquals(401, screen(branchId).code)
    }
}
