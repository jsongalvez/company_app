package com.companyb.companyapp.api.routes

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.authorization.Role
import com.companyb.companyapp.client.ClientRoutes
import com.companyb.companyapp.commerce.BranchInventoryRoutes
import com.companyb.companyapp.commerce.ProductRoutes
import com.companyb.companyapp.commerce.ProductSaleRoutes
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.finance.AllowanceRoutes
import com.companyb.companyapp.finance.CompensationRoutes
import com.companyb.companyapp.finance.ExpenseRoutes
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.notification.NotificationRoutes
import com.companyb.companyapp.remittance.RemittanceRoutes
import com.companyb.companyapp.reporting.DailySalesSummaryRoutes
import com.companyb.companyapp.reporting.ExportRoutes
import com.companyb.companyapp.session.SessionRoutes
import com.companyb.companyapp.session.dashboard.DashboardRoutes
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import com.companyb.companyapp.workforce.UserBranchAssignmentRoutes
import io.javalin.Javalin
import io.javalin.config.JavalinConfig
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The test server, seed rows and request helpers shared by the route validation suites: each route refuses a
 * malformed request with 400 before it reaches a service.
 */
abstract class RouteValidationBase : BasePostgresTest() {
    protected val json = Json { ignoreUnknownKeys = true }
    protected val testUserId = TEST_USER_ID
    protected val testBranchId = TestFixtures.uuid()
    protected var testBranchDayId: UUID = TestFixtures.uuid()
    protected val testCategoryId = TestFixtures.uuid()
    protected val testProductId = TestFixtures.uuid()
    protected val testClientId = TestFixtures.uuid()
    protected val testClientNoSessionsId = TestFixtures.uuid()
    protected val testSessionId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(testUserId, "route-test")
        BranchWorkforceFixtures.insertTestBranch(testBranchId, "Route Test Branch $testBranchId")
        IdentityFixtures.member(testUserId, testBranchId, Role.SUPERUSER)
        testBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(testBranchId)
        CommerceFinanceFixtures.insertTestCategory(testCategoryId)
        CommerceFinanceFixtures.insertTestProduct(testProductId, categoryId = testCategoryId)
        SessionClientFixtures.insertTestClient(testClientId)
        SessionClientFixtures.insertTestClient(testClientNoSessionsId)
        SessionClientFixtures.insertTestSession(
            id = testSessionId,
            clientId = testClientId,
            branchDayId = testBranchDayId,
        )
    }

    companion object {
        @JvmField
        @ClassRule
        val testServer = JavalinTestServerRule(::createApp)

        val TEST_USER_ID = TestFixtures.uuid()

        fun createApp(): Javalin {
            val config = AppConfig.parse()
            JwtService.init(config)
            Password.init(config.authDummyPassword)

            return Javalin.create { cfg ->
                cfg.jsonMapper(KotlinxSerializationMapper())
                cfg.routes.before { ctx ->
                    Database.connect(TestDatabaseLifecycle.requireTestDataSource())
                    ctx.attribute("userId", TEST_USER_ID.toString())
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
                registerAllRoutes(cfg)
            }
        }

        private fun registerAllRoutes(config: JavalinConfig) {
            AllowanceRoutes.register(config)
            CompensationRoutes.register(config)
            DashboardRoutes.register(config)
            SessionRoutes.register(config)
            ClientRoutes.register(config)
            ProductRoutes.register(config)
            BranchInventoryRoutes.register(config)
            ProductSaleRoutes.register(config)
            UserBranchAssignmentRoutes.register(config)
            RemittanceRoutes.register(config)
            ExportRoutes.register(config)
            DailySalesSummaryRoutes.register(config)
            ExpenseRoutes.register(config)
            NotificationRoutes.register(config)
        }
    }

    protected fun uuid() = TestFixtures.uuid().toString()

    protected fun assertPost(
        code: Int,
        path: String,
        body: Map<String, Any?>? = null,
    ) {
        val response = body?.let { testServer.client.post(path, it) } ?: testServer.client.post(path)
        assertEquals(code, response.code, "POST $path $body")
    }

    protected fun assertPatch(
        code: Int,
        path: String,
        body: Map<String, Any?>? = null,
    ) {
        val response = body?.let { testServer.client.patch(path, it) } ?: testServer.client.patch(path)
        assertEquals(code, response.code, "PATCH $path $body")
    }

    protected fun assertGet(
        code: Int,
        path: String,
    ) = assertEquals(code, testServer.client.get(path).code, "GET $path")
}
