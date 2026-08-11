package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayAssignmentTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.service.attendance.AttendanceService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.Javalin
import io.javalin.http.UnauthorizedResponse
import io.javalin.testtools.JavalinTest
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardAuthzTest : BasePostgresTest() {
    private val clockedInUser = UUID.randomUUID()
    private val otherBranchUser = UUID.randomUUID()
    private val notClockedInUser = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val otherBranchId = UUID.randomUUID()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(clockedInUser, "clocked-in")
        DatabaseTestHelper.insertTestUser(otherBranchUser, "other-branch")
        DatabaseTestHelper.insertTestUser(notClockedInUser, "not-clocked")
        DatabaseTestHelper.insertTestBranch(branchId, "Branch A")
        DatabaseTestHelper.insertTestBranch(otherBranchId, "Branch B")

        trackOwned(AppUserTable, AppUserTable.id, clockedInUser)
        trackOwned(AppUserTable, AppUserTable.id, otherBranchUser)
        trackOwned(AppUserTable, AppUserTable.id, notClockedInUser)
        trackOwned(BranchTable, BranchTable.id, branchId)
        trackOwned(BranchTable, BranchTable.id, otherBranchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, otherBranchId)
        trackOwned(AttendanceTable, AttendanceTable.userId, clockedInUser)
        trackOwned(AttendanceTable, AttendanceTable.userId, otherBranchUser)
        trackOwned(AttendanceTable, AttendanceTable.userId, notClockedInUser)
        trackOwned(BranchDayAssignmentTable, BranchDayAssignmentTable.userId, clockedInUser)
        trackOwned(BranchDayAssignmentTable, BranchDayAssignmentTable.userId, otherBranchUser)
        trackOwned(BranchDayAssignmentTable, BranchDayAssignmentTable.userId, notClockedInUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, clockedInUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, otherBranchUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, notClockedInUser)

        AttendanceService.clockIn(UUID.randomUUID(), branchId, clockedInUser)
        AttendanceService.clockIn(UUID.randomUUID(), otherBranchId, otherBranchUser)
    }

    private fun createApp(): Javalin {
        val config = AppConfig.parse()
        JwtService.init(config)
        Password.init(config.authDummyPassword)
        return Javalin.create { cfg ->
            cfg.jsonMapper(KotlinxSerializationMapper())
            cfg.routes.before { ctx ->
                Database.connect(DatabaseTestHelper.requireTestDataSource())
                ctx.attribute("userId", ctx.header("X-Test-User") ?: notClockedInUser.toString())
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

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    @Test
    fun `clocked in user reads dashboard`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(200, client.get("/api/branches/$branchId/dashboard/today", asUser(clockedInUser)).code)
        }
    }

    @Test
    fun `user not clocked in gets 403`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(403, client.get("/api/branches/$branchId/dashboard/today", asUser(notClockedInUser)).code)
        }
    }

    @Test
    fun `user clocked in at another branch gets 403`() {
        JavalinTest.test(createApp()) { _, client ->
            assertEquals(403, client.get("/api/branches/$branchId/dashboard/today", asUser(otherBranchUser)).code)
        }
    }

    @Test
    fun `missing branch with clocked in user gets 404`() {
        JavalinTest.test(createApp()) { _, client ->
            val missingBranch = UUID.randomUUID()
            assertEquals(404, client.get("/api/branches/$missingBranch/dashboard/today", asUser(clockedInUser)).code)
        }
    }

    // #128 lesson — the X-Test-User bypass harness can't exercise the real auth filter;
    // the #147 ticket's spec lists "unauthenticated 401", and the in-repo precedent
    // (ReportsReadScopeAuthzTest.createAppWithJwt) is to add the JWT harness when 401
    // verification matters, not to skip it (pass-2 re-rate).
    @Test
    fun `unauthenticated request gets 401`() {
        JavalinTest.test(createAppWithJwt()) { _, client ->
            assertEquals(401, client.get("/api/branches/$branchId/dashboard/today").code)
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
            DashboardRoutes.register(cfg)
        }
    }
}
