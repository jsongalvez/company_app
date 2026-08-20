package com.companyb.companyapp.api.routes

import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.dto.AssignmentResponse
import com.companyb.companyapp.dto.CreateAssignmentRequest
import com.companyb.companyapp.dto.SwapSlotsRequest
import com.companyb.companyapp.dto.UpdateSlotRequest
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * #134 route-level proof: every /api/branches/{branchId}/assignments and
 * /api/branches/{branchId}/slots sub-path is authorized independently at the
 * service layer (the #114 exact-path lesson — the old 4-segment before-filters
 * never matched the 5-segment DELETE and /slots/swap paths).
 *
 * Grants are direct via DatabaseTestHelper (ADR-0023 — role derivation has no
 * production seed; direct grants are the realistic path today).
 */
class UserBranchAssignmentAuthzTest : BasePostgresTest() {
    private val managerUser = UUID.randomUUID()
    private val noGrantUser = UUID.randomUUID()
    private val userAId = UUID.randomUUID()
    private val userBId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private lateinit var assignmentAId: UUID
    private lateinit var assignmentBId: UUID

    override fun initTestData() {
        listOf(managerUser, noGrantUser, userAId, userBId).forEach { id ->
            DatabaseTestHelper.insertTestUser(id, id.toString().take(6))
            trackOwned(AppUserTable, AppUserTable.id, id)
        }
        DatabaseTestHelper.grantManageUsers(managerUser, managerUser)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, managerUser)
        DatabaseTestHelper.insertTestBranch(branchId, "Authz Branch 134")
        trackOwned(BranchTable, BranchTable.id, branchId)
        assignmentAId =
            DatabaseTestHelper.insertTestAssignment(
                userId = userAId,
                branchId = branchId,
                slot = 1,
                assignedBy = managerUser,
            )
        assignmentBId =
            DatabaseTestHelper.insertTestAssignment(
                userId = userBId,
                branchId = branchId,
                slot = 2,
                assignedBy = managerUser,
            )
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.id, assignmentAId)
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.id, assignmentBId)
        listOf(managerUser, noGrantUser, userAId, userBId).forEach { id ->
            trackOwned(AuditLogTable, AuditLogTable.changedBy, id)
        }
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
                UserBranchAssignmentRoutes.register(cfg)
            }
        }

        private val json =
            Json {
                ignoreUnknownKeys = true
            }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun assignedSlot(assignmentId: UUID): Short? =
        transaction {
            UserBranchAssignmentTable
                .selectAll()
                .where { UserBranchAssignmentTable.id eq assignmentId }
                .singleOrNull()
                ?.let { it[UserBranchAssignmentTable.slot] }
        }

    private fun hasEnded(assignmentId: UUID): Boolean =
        transaction {
            UserBranchAssignmentTable
                .selectAll()
                .where { UserBranchAssignmentTable.id eq assignmentId }
                .single()[UserBranchAssignmentTable.endedAt] != null
        }

    @Test
    fun `POST assignments is forbidden without MANAGE_USERS`() {
        var status = 0
        testServer.client.let { client ->
            status =
                client
                    .post(
                        "/api/branches/$branchId/assignments",
                        CreateAssignmentRequest(
                            id = UUID.randomUUID().toString(),
                            userId = userAId.toString(),
                            slot = 3,
                        ),
                        asUser(noGrantUser),
                    ).code
        }
        assertEquals(403, status)
    }

    @Test
    fun `POST assignments by manager creates the assignment`() {
        val newId = UUID.randomUUID()
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.id, newId)
        var status = 0
        testServer.client.let { client ->
            status =
                client
                    .post(
                        "/api/branches/$branchId/assignments",
                        CreateAssignmentRequest(id = newId.toString(), userId = noGrantUser.toString(), slot = 3),
                        asUser(managerUser),
                    ).code
        }
        assertEquals(201, status)
        assertEquals(3, assignedSlot(newId))
    }

    @Test
    fun `GET assignments is forbidden without MANAGE_USERS`() {
        var status = 0
        testServer.client.let { client ->
            status = client.get("/api/branches/$branchId/assignments", asUser(noGrantUser)).code
        }
        assertEquals(403, status)
    }

    @Test
    fun `GET assignments by manager lists active assignments`() {
        var status = 0
        var assignments: List<AssignmentResponse> = emptyList()
        testServer.client.let { client ->
            val response = client.get("/api/branches/$branchId/assignments", asUser(managerUser))
            status = response.code
            assignments =
                json.decodeFromString<List<AssignmentResponse>>(
                    response.body.string(),
                )
        }
        assertEquals(200, status)
        assertEquals(2, assignments.size)
        assertEquals(listOf<Short>(1, 2), assignments.map { it.slot })
    }

    @Test
    fun `DELETE assignment is forbidden without MANAGE_USERS`() {
        var status = 0
        testServer.client.let { client ->
            status = client.delete("/api/branches/$branchId/assignments/$userAId", null, asUser(noGrantUser)).code
        }
        assertEquals(403, status)
    }

    @Test
    fun `DELETE own assignment is forbidden without MANAGE_USERS`() {
        var status = 0
        testServer.client.let { client ->
            status = client.delete("/api/branches/$branchId/assignments/$userAId", null, asUser(userAId)).code
        }
        assertEquals(403, status)
    }

    @Test
    fun `DELETE assignment by manager ends it`() {
        var status = 0
        testServer.client.let { client ->
            status = client.delete("/api/branches/$branchId/assignments/$userAId", null, asUser(managerUser)).code
        }
        assertEquals(204, status)
        assertNotNull(assignedSlot(assignmentAId))
        assertTrue(hasEnded(assignmentAId))
    }

    @Test
    fun `DELETE own assignment by manager succeeds`() {
        val managerAssignmentId =
            DatabaseTestHelper.insertTestAssignment(
                userId = managerUser,
                branchId = branchId,
                slot = 3,
                assignedBy = managerUser,
            )
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.id, managerAssignmentId)

        var status = 0
        testServer.client.let { client ->
            status = client.delete("/api/branches/$branchId/assignments/$managerUser", null, asUser(managerUser)).code
        }
        assertEquals(204, status)
        assertTrue(hasEnded(managerAssignmentId))
    }

    @Test
    fun `PATCH slot is forbidden without MANAGE_USERS for another user`() {
        var status = 0
        testServer.client.let { client ->
            status =
                client
                    .patch(
                        "/api/branches/$branchId/assignments/$userAId/slot",
                        UpdateSlotRequest(slot = 4),
                        asUser(noGrantUser),
                    ).code
        }
        assertEquals(403, status)
    }

    @Test
    fun `PATCH own slot is allowed without MANAGE_USERS`() {
        var status = 0
        testServer.client.let { client ->
            status =
                client
                    .patch(
                        "/api/branches/$branchId/assignments/$userAId/slot",
                        UpdateSlotRequest(slot = 4),
                        asUser(userAId),
                    ).code
        }
        assertEquals(204, status)
        assertEquals(4, assignedSlot(assignmentAId))
    }

    @Test
    fun `POST swap is forbidden without MANAGE_USERS`() {
        var status = 0
        testServer.client.let { client ->
            status =
                client
                    .post(
                        "/api/branches/$branchId/slots/swap",
                        SwapSlotsRequest(userIdA = userAId.toString(), userIdB = userBId.toString()),
                        asUser(noGrantUser),
                    ).code
        }
        assertEquals(403, status)
    }

    @Test
    fun `POST swap by manager swaps slots`() {
        var status = 0
        testServer.client.let { client ->
            status =
                client
                    .post(
                        "/api/branches/$branchId/slots/swap",
                        SwapSlotsRequest(userIdA = userAId.toString(), userIdB = userBId.toString()),
                        asUser(managerUser),
                    ).code
        }
        assertEquals(204, status)
        assertEquals(2, assignedSlot(assignmentAId))
        assertEquals(1, assignedSlot(assignmentBId))
    }

    @Test
    fun `POST swap self-service is allowed without MANAGE_USERS`() {
        var status = 0
        testServer.client.let { client ->
            status =
                client
                    .post(
                        "/api/branches/$branchId/slots/swap",
                        SwapSlotsRequest(userIdA = userAId.toString(), userIdB = userBId.toString()),
                        asUser(userAId),
                    ).code
        }
        assertEquals(204, status)
        assertEquals(2, assignedSlot(assignmentAId))
        assertEquals(1, assignedSlot(assignmentBId))
    }
}
