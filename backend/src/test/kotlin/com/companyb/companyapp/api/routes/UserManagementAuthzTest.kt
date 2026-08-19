package com.companyb.companyapp.api.routes

import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.dto.UserSummaryResponse
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.UserService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.JavalinTestServerRule
import io.javalin.Javalin
import io.javalin.testtools.Request
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #133 route-level proof: every /api/users sub-path carries its own GLOBAL
 * MANAGE_USERS before-filter (the #114 exact-path lesson — a filter on one
 * sub-path must never be trusted to cover siblings), and the self-deactivate
 * guard surfaces as HTTP 400 through the stack.
 *
 * Grants are direct via DatabaseTestHelper (the #132 dead-grant warning: role
 * derivation exists but has no production seed — direct grants are the
 * realistic path today).
 */
class UserManagementAuthzTest : BasePostgresTest() {
    private val managerUser = UUID.randomUUID()
    private val noGrantUser = UUID.randomUUID()
    private val targetUser = UUID.randomUUID()
    private val branchId = UUID.randomUUID()

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    override fun initTestData() {
        listOf(managerUser, noGrantUser, targetUser).forEach { id ->
            DatabaseTestHelper.insertTestUser(id, id.toString().take(6))
            trackOwned(AppUserTable, AppUserTable.id, id)
        }
        DatabaseTestHelper.grantManageUsers(managerUser, managerUser)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, managerUser)
        DatabaseTestHelper.insertTestBranch(branchId, "Authz Branch")
        trackOwned(BranchTable, BranchTable.id, branchId)
        val targetUserId = this@UserManagementAuthzTest.targetUser
        val assignmentId = UUID.randomUUID()
        DatabaseTestHelper.insertTestAssignment(
            id = assignmentId,
            userId = targetUserId,
            branchId = branchId,
            slot = 1,
            assignedBy = managerUser,
        )
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.id, assignmentId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, managerUser)
    }

    companion object {
        private val DEFAULT_USER = UUID.randomUUID()

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
                UserRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    @Test
    fun `GET users is forbidden without MANAGE_USERS`() {
        var status = 0
        testServer.client.let { client ->
            status = client.get("/api/users", asUser(noGrantUser)).code
        }
        assertEquals(403, status)
    }

    @Test
    fun `GET users returns the full user list with active assignments`() {
        var users: List<UserSummaryResponse> = emptyList()
        testServer.client.let { client ->
            val response = client.get("/api/users", asUser(managerUser))
            assertEquals(200, response.code)
            users =
                json.decodeFromString<List<UserSummaryResponse>>(
                    response.body!!.string(),
                )
        }
        assertEquals(3, users.size)
        val target = users.single { it.id == targetUser.toString() }
        assertEquals(com.companyb.companyapp.domain.UserStatus.ACTIVE, target.status)
        assertEquals(1, target.assignments.size)
        assertEquals("Authz Branch", target.assignments[0].branchName)
        assertEquals(1, target.assignments[0].slot)
    }

    @Test
    fun `reactivate is forbidden without MANAGE_USERS`() {
        var status = 0
        testServer.client.let { client ->
            status = client.patch("/api/users/$targetUser/reactivate", null, asUser(noGrantUser)).code
        }
        assertEquals(403, status)
    }

    @Test
    fun `reactivate flips an inactive user back to ACTIVE with 204`() {
        UserService.deactivate(managerUser, targetUser)

        var status = 0
        testServer.client.let { client ->
            status = client.patch("/api/users/$targetUser/reactivate", null, asUser(managerUser)).code
        }
        assertEquals(204, status)
        val statusAfter =
            transaction {
                AppUserTable
                    .selectAll()
                    .where { AppUserTable.id eq targetUser }
                    .single()[AppUserTable.status]
            }
        assertEquals(UserStatus.ACTIVE, statusAfter)
    }

    @Test
    fun `self-deactivate returns 400 through HTTP`() {
        var status = 0
        var body = ""
        testServer.client.let { client ->
            val response = client.patch("/api/users/$managerUser/deactivate", null, asUser(managerUser))
            status = response.code
            body = response.body?.string().orEmpty()
        }
        assertEquals(400, status)
        assertTrue(body.contains("Cannot deactivate yourself"), "body must carry the self-guard message: $body")
    }

    @Test
    fun `deactivate without MANAGE_USERS is forbidden`() {
        var status = 0
        testServer.client.let { client ->
            status = client.patch("/api/users/$targetUser/deactivate", null, asUser(noGrantUser)).code
        }
        assertEquals(403, status)
    }
}
