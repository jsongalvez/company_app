@file:Suppress("LargeClass")

package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.ReliefInviteStatus
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.CapabilityRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ReliefInviteTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.JavalinTestServerRule
import io.javalin.Javalin
import io.javalin.http.UnauthorizedResponse
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.ClassRule
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #160 — the relief invite flow route surface (#159 decisions, locked — do not re-litigate).
 *
 * Inviter surface: POST/GET /api/branches/{branchId}/relief-invites + GET .../relief-candidates —
 * gate = active `user_branch_assignment` at the branch (service-level; anyone assigned can
 * invite, symmetric with anyone-can-request). Invitee surface: GET /api/relief-invites +
 * POST .../accept|decline — bearer, caller must be the invitee; retract = inviter + the same
 * assignment gate. Create = resolve-or-create day (the invite is a write; future-day planning
 * is the use case), day-state editable; accept writes the day-scoped grant immediately and the
 * day-state is the expiry (past/REMITTED → 400). Per-person guard: one PENDING/ACCEPTED invite
 * or active grant per (invitee, day) — 409; multiple invitees may hold invites for the same day.
 */
class ReliefInviteAuthzTest : BasePostgresTest() {
    private val inviter = UUID.randomUUID()
    private val otherInviter = UUID.randomUUID()
    private val nonAssigned = UUID.randomUUID()
    private val invitee = UUID.randomUUID()
    private val otherInvitee = UUID.randomUUID()
    private val inactiveUser = UUID.randomUUID()
    private val aliceUser = UUID.randomUUID()
    private val bobUser = UUID.randomUUID()
    private val branchA = UUID.randomUUID()
    private val branchB = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()

    private lateinit var tomorrow: LocalDate
    private lateinit var yesterday: LocalDate
    private lateinit var tomorrowDayId: UUID

    override fun initTestData() {
        val today = LocalDate.now(ZoneId.of("Asia/Manila"))
        tomorrow = today.plusDays(1)
        yesterday = today.minusDays(1)

        DatabaseTestHelper.insertTestUser(inviter, "inviter")
        DatabaseTestHelper.insertTestUser(otherInviter, "other-inviter")
        DatabaseTestHelper.insertTestUser(nonAssigned, "non-assigned")
        DatabaseTestHelper.insertTestUser(invitee, "invitee")
        DatabaseTestHelper.insertTestUser(otherInvitee, "other-invitee")
        DatabaseTestHelper.insertTestUser(inactiveUser, "inactive")
        DatabaseTestHelper.insertTestUser(aliceUser, "alice")
        DatabaseTestHelper.insertTestUser(bobUser, "bob")
        DatabaseTestHelper.insertTestBranch(branchA, "Branch A")
        DatabaseTestHelper.insertTestBranch(branchB, "Branch B")

        DatabaseTestHelper.insertTestAssignment(
            userId = inviter,
            branchId = branchA,
            slot = 1,
            assignedBy = inviter,
        )
        DatabaseTestHelper.insertTestAssignment(
            userId = otherInviter,
            branchId = branchA,
            slot = 2,
            assignedBy = inviter,
        )

        // An INACTIVE user can never be invited (the ACTIVE check).
        transaction {
            AppUserTable.update({ AppUserTable.id eq inactiveUser }) {
                it[AppUserTable.status] = UserStatus.INACTIVE
            }
        }

        tomorrowDayId = BranchDayService.resolveOrCreate(branchA, tomorrow).id

        trackOwned(AppUserTable, AppUserTable.id, inviter)
        trackOwned(AppUserTable, AppUserTable.id, otherInviter)
        trackOwned(AppUserTable, AppUserTable.id, nonAssigned)
        trackOwned(AppUserTable, AppUserTable.id, invitee)
        trackOwned(AppUserTable, AppUserTable.id, otherInvitee)
        trackOwned(AppUserTable, AppUserTable.id, inactiveUser)
        trackOwned(AppUserTable, AppUserTable.id, aliceUser)
        trackOwned(AppUserTable, AppUserTable.id, bobUser)
        trackOwned(BranchTable, BranchTable.id, branchA)
        trackOwned(BranchTable, BranchTable.id, branchB)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchA)
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.userId, inviter)
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.userId, otherInviter)
        trackOwned(ReliefInviteTable, ReliefInviteTable.invitee, invitee)
        trackOwned(ReliefInviteTable, ReliefInviteTable.invitee, otherInvitee)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, invitee)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, otherInvitee)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, inviter)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, otherInviter)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, invitee)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, otherInvitee)
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
                ReliefInviteRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun createBody(
        inviteeUserId: UUID,
        date: LocalDate,
    ): Map<String, String> =
        mapOf(
            "inviteeUserId" to inviteeUserId.toString(),
            "date" to date.toString(),
        )

    // ─────────────────────────── create ───────────────────────────

    @Test
    fun `assigned inviter creates an invite for a future day`() {
        testServer.client.let { client ->
            val response =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(invitee, tomorrow),
                    asUser(inviter),
                )
            val body = response.body.string().orEmpty()
            assertEquals(201, response.code, body)
            assertTrue(body.contains("\"status\":\"PENDING\""), body)
            assertTrue(body.contains(tomorrow.toString()), body)
            assertTrue(body.contains("Branch A"), body)
        }
    }

    @Test
    fun `assigned inviter invite resolves the day row when missing`() {
        val noDayDate = tomorrow.plusDays(2)
        testServer.client.let { client ->
            val response =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(invitee, noDayDate),
                    asUser(inviter),
                )
            assertEquals(201, response.code, response.body.string().orEmpty())
            val dayCount =
                transaction {
                    BranchDayTable
                        .selectAll()
                        .where { (BranchDayTable.branchId eq branchA) and (BranchDayTable.date eq noDayDate) }
                        .count()
                }
            assertEquals(1L, dayCount, "create must resolve-or-create the day (future-day planning)")
        }
    }

    @Test
    fun `non-assigned user cannot create an invite`() {
        testServer.client.let { client ->
            val response =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(invitee, tomorrow),
                    asUser(nonAssigned),
                )
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `inviter cannot invite themselves`() {
        testServer.client.let { client ->
            val response =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(inviter, tomorrow),
                    asUser(inviter),
                )
            assertEquals(400, response.code)
        }
    }

    @Test
    fun `inactive invitee is rejected`() {
        testServer.client.let { client ->
            val response =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(inactiveUser, tomorrow),
                    asUser(inviter),
                )
            assertEquals(400, response.code)
        }
    }

    @Test
    fun `create on a past day is forbidden`() {
        testServer.client.let { client ->
            val response =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(invitee, yesterday),
                    asUser(inviter),
                )
            assertEquals(403, response.code, response.body.string().orEmpty())
        }
    }

    @Test
    fun `duplicate invite for the same invitee and day conflicts`() {
        testServer.client.let { client ->
            val first =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(invitee, tomorrow),
                    asUser(inviter),
                )
            assertEquals(201, first.code)
            val second =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(invitee, tomorrow),
                    asUser(inviter),
                )
            assertEquals(409, second.code, second.body.string().orEmpty())
        }
    }

    @Test
    fun `invitee with an active grant for the day conflicts`() {
        DatabaseTestHelper.grantCapability(
            userId = invitee,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH_DAY,
            contextId = tomorrowDayId,
            sourceId = sourceId,
        )
        testServer.client.let { client ->
            val response =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(invitee, tomorrow),
                    asUser(inviter),
                )
            assertEquals(409, response.code)
        }
    }

    @Test
    fun `two invitees can hold invites for the same day`() {
        testServer.client.let { client ->
            val first =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(invitee, tomorrow),
                    asUser(inviter),
                )
            assertEquals(201, first.code)
            val second =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(otherInvitee, tomorrow),
                    asUser(inviter),
                )
            assertEquals(201, second.code, second.body.string().orEmpty())
        }
    }

    @Test
    fun `re-invite after decline succeeds`() {
        testServer.client.let { client ->
            val created =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(invitee, tomorrow),
                    asUser(inviter),
                )
            assertEquals(201, created.code)
            val inviteId = DatabaseTestHelper.extractJsonField(created.body.string(), "id")
            val declined =
                client.post(
                    "/api/relief-invites/$inviteId/decline",
                    emptyMap<String, String>(),
                    asUser(invitee),
                )
            assertEquals(200, declined.code)

            val again =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(invitee, tomorrow),
                    asUser(inviter),
                )
            assertEquals(201, again.code, again.body.string().orEmpty())
        }
    }

    // ─────────────────────────── sent list ───────────────────────────

    @Test
    fun `sent list is scoped to the caller and branch`() {
        testServer.client.let { client ->
            client.post("/api/branches/$branchA/relief-invites", createBody(invitee, tomorrow), asUser(inviter))
            client.post(
                "/api/branches/$branchA/relief-invites",
                createBody(otherInvitee, tomorrow),
                asUser(otherInviter),
            )

            val response = client.get("/api/branches/$branchA/relief-invites", asUser(inviter))
            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.contains(invitee.toString()), body)
            assertTrue(!body.contains(otherInvitee.toString()), "other inviter's invites must not appear")
        }
    }

    @Test
    fun `sent list requires the assignment gate`() {
        testServer.client.let { client ->
            val response = client.get("/api/branches/$branchA/relief-invites", asUser(nonAssigned))
            assertEquals(403, response.code)
        }
    }

    // ─────────────────────────── received list ───────────────────────────

    @Test
    fun `received list serves only the caller's invites`() {
        testServer.client.let { client ->
            client.post("/api/branches/$branchA/relief-invites", createBody(invitee, tomorrow), asUser(inviter))
            client.post("/api/branches/$branchA/relief-invites", createBody(otherInvitee, tomorrow), asUser(inviter))

            val response = client.get("/api/relief-invites", asUser(invitee))
            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.contains(invitee.toString()), body)
            assertTrue(!body.contains(otherInvitee.toString()), body)
            assertTrue(body.contains("Branch A"), body)
            assertTrue(body.contains(tomorrow.toString()), body)
        }
    }

    @Test
    fun `received list serves only pending invites`() {
        testServer.client.let { client ->
            val first =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(invitee, tomorrow.plusDays(3)),
                    asUser(inviter),
                )
            val firstId = DatabaseTestHelper.extractJsonField(first.body.string(), "id")
            client.post("/api/relief-invites/$firstId/decline", emptyMap<String, String>(), asUser(invitee))
            client.post("/api/branches/$branchA/relief-invites", createBody(invitee, tomorrow), asUser(inviter))

            val response = client.get("/api/relief-invites", asUser(invitee))
            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.contains(tomorrow.toString()), "the pending invite must be served: $body")
            assertTrue(!body.contains(tomorrow.plusDays(3).toString()), "resolved rows must leave the list: $body")
        }
    }

    // ─────────────────────────── accept ───────────────────────────

    @Test
    fun `accept writes the day grant immediately`() {
        var inviteId = ""
        testServer.client.let { client ->
            val created =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(invitee, tomorrow),
                    asUser(inviter),
                )
            assertEquals(201, created.code)
            inviteId = DatabaseTestHelper.extractJsonField(created.body.string(), "id")

            val accepted =
                client.post(
                    "/api/relief-invites/$inviteId/accept",
                    emptyMap<String, String>(),
                    asUser(invitee),
                )
            val body = accepted.body.string()
            assertEquals(200, accepted.code, body)
            assertTrue(body.contains("\"status\":\"ACCEPTED\""))
        }
        val granted =
            CapabilityRepository.hasCapabilityForBranchDay(
                userId = invitee,
                capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
                branchId = branchA,
                branchDayId = tomorrowDayId,
            )
        assertTrue(granted, "accept must write the day-scoped grant (the #157 enforcement surface)")
    }

    @Test
    fun `accept by a non-invitee is forbidden`() {
        testServer.client.let { client ->
            val created =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(invitee, tomorrow),
                    asUser(inviter),
                )
            val inviteId = DatabaseTestHelper.extractJsonField(created.body.string(), "id")
            val response =
                client.post(
                    "/api/relief-invites/$inviteId/accept",
                    emptyMap<String, String>(),
                    asUser(otherInvitee),
                )
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `accept on a past day is rejected as expired`() {
        testServer.client.let { client ->
            // A past-day invite can only exist via a direct row (create 403s on past days).
            val pastDayId = BranchDayService.resolveOrCreate(branchA, yesterday).id
            trackOwned(BranchDayTable, BranchDayTable.branchId, branchA)
            val inviteId = UUID.randomUUID()
            trackOwned(ReliefInviteTable, ReliefInviteTable.invitee, invitee)
            // Locals only — inside `insert {}` the receiver is the table, so unqualified
            // class fields would resolve to columns (the #118/#152 insert-lambda trap).
            val invitedBy = inviter
            val inviteeId = invitee
            transaction {
                ReliefInviteTable.insert {
                    it[ReliefInviteTable.id] = inviteId
                    it[ReliefInviteTable.branchDayId] = pastDayId
                    it[ReliefInviteTable.invitedBy] = invitedBy
                    it[ReliefInviteTable.invitee] = inviteeId
                }
            }
            val response =
                client.post(
                    "/api/relief-invites/$inviteId/accept",
                    emptyMap<String, String>(),
                    asUser(invitee),
                )
            assertEquals(400, response.code, response.body.string().orEmpty())
        }
    }

    @Test
    fun `double accept conflicts on the second attempt`() {
        testServer.client.let { client ->
            val created =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(invitee, tomorrow),
                    asUser(inviter),
                )
            val inviteId = DatabaseTestHelper.extractJsonField(created.body.string(), "id")
            val first = client.post("/api/relief-invites/$inviteId/accept", emptyMap<String, String>(), asUser(invitee))
            assertEquals(200, first.code)
            val second =
                client.post(
                    "/api/relief-invites/$inviteId/accept",
                    emptyMap<String, String>(),
                    asUser(invitee),
                )
            assertEquals(409, second.code)
        }
    }

    // ─────────────────────────── decline / retract ───────────────────────────

    @Test
    fun `decline sets DECLINED and frees the slot`() {
        testServer.client.let { client ->
            val created =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(invitee, tomorrow),
                    asUser(inviter),
                )
            val inviteId = DatabaseTestHelper.extractJsonField(created.body.string(), "id")
            val declined =
                client.post(
                    "/api/relief-invites/$inviteId/decline",
                    emptyMap<String, String>(),
                    asUser(invitee),
                )
            assertEquals(200, declined.code)
            assertTrue(declined.body.string().contains("\"status\":\"DECLINED\""))
            val again =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(invitee, tomorrow),
                    asUser(inviter),
                )
            assertEquals(201, again.code, "declined invites free the per-person slot")
        }
    }

    @Test
    fun `retract by the inviter sets RETRACTED`() {
        testServer.client.let { client ->
            val created =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(invitee, tomorrow),
                    asUser(inviter),
                )
            val inviteId = DatabaseTestHelper.extractJsonField(created.body.string(), "id")
            val retracted =
                client.post(
                    "/api/relief-invites/$inviteId/retract",
                    emptyMap<String, String>(),
                    asUser(inviter),
                )
            assertEquals(200, retracted.code)
            assertTrue(retracted.body.string().contains("\"status\":\"RETRACTED\""))
        }
    }

    @Test
    fun `retract by a non-inviter is forbidden`() {
        testServer.client.let { client ->
            val created =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(invitee, tomorrow),
                    asUser(inviter),
                )
            val inviteId = DatabaseTestHelper.extractJsonField(created.body.string(), "id")
            val response =
                client.post(
                    "/api/relief-invites/$inviteId/retract",
                    emptyMap<String, String>(),
                    asUser(otherInviter),
                )
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `retract after accept conflicts`() {
        testServer.client.let { client ->
            val created =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(invitee, tomorrow),
                    asUser(inviter),
                )
            val inviteId = DatabaseTestHelper.extractJsonField(created.body.string(), "id")
            client.post("/api/relief-invites/$inviteId/accept", emptyMap<String, String>(), asUser(invitee))
            val response =
                client.post(
                    "/api/relief-invites/$inviteId/retract",
                    emptyMap<String, String>(),
                    asUser(inviter),
                )
            assertEquals(409, response.code)
        }
    }

    // ─────────────────────────── candidates ───────────────────────────

    @Test
    fun `candidates exclude self, live invitees and granted users`() {
        DatabaseTestHelper.grantCapability(
            userId = otherInvitee,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH_DAY,
            contextId = tomorrowDayId,
            sourceId = sourceId,
        )
        testServer.client.let { client ->
            client.post("/api/branches/$branchA/relief-invites", createBody(invitee, tomorrow), asUser(inviter))

            val response = client.get("/api/branches/$branchA/relief-candidates?date=$tomorrow", asUser(inviter))
            val body = response.body.string().orEmpty()
            assertEquals(200, response.code, body)
            assertTrue(!body.contains(inviter.toString()), "self must be excluded: $body")
            assertTrue(!body.contains(invitee.toString()), "live invitee must be excluded: $body")
            assertTrue(!body.contains(otherInvitee.toString()), "granted user must be excluded: $body")
            assertTrue(body.contains(aliceUser.toString()), body)
            assertTrue(body.contains(bobUser.toString()), body)
        }
    }

    @Test
    fun `candidates filter by username or display name prefix`() {
        testServer.client.let { client ->
            val response = client.get("/api/branches/$branchA/relief-candidates?q=ali&date=$tomorrow", asUser(inviter))
            val body = response.body.string().orEmpty()
            assertEquals(200, response.code, body)
            assertTrue(body.contains(aliceUser.toString()), body)
            assertTrue(!body.contains(bobUser.toString()), body)
        }
    }

    @Test
    fun `candidate search with no day row applies no exclusions and creates nothing`() {
        val noDayDate = tomorrow.plusDays(5)
        testServer.client.let { client ->
            val response =
                client.get(
                    "/api/branches/$branchA/relief-candidates?date=$noDayDate",
                    asUser(inviter),
                )
            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.contains(aliceUser.toString()), body)
            assertTrue(!body.contains(inviter.toString()), body)
            val dayCount =
                transaction {
                    BranchDayTable
                        .selectAll()
                        .where { (BranchDayTable.branchId eq branchA) and (BranchDayTable.date eq noDayDate) }
                        .count()
                }
            assertEquals(0L, dayCount, "a search must never create a branch day row (the #158 find-only discipline)")
        }
    }

    @Test
    fun `candidates require the assignment gate`() {
        testServer.client.let { client ->
            val response = client.get("/api/branches/$branchA/relief-candidates?date=$tomorrow", asUser(nonAssigned))
            assertEquals(403, response.code)
        }
    }

    @Test
    fun `invalid date is a 400`() {
        testServer.client.let { client ->
            val response =
                client.post(
                    "/api/branches/$branchA/relief-invites",
                    createBody(invitee, tomorrow).plus("date" to "not-a-date"),
                    asUser(inviter),
                )
            assertEquals(400, response.code)
        }
    }

    // ─────────────────────────── real-JWT 401 (the #128 harness) ───────────────────────────

    @Test
    fun `unauthenticated requests get 401`() {
        testServer.client.let { client ->
            assertEquals(401, client.get("/api/relief-invites").code)
            assertEquals(401, client.post("/api/branches/$branchA/relief-invites", createBody(invitee, tomorrow)).code)
            assertEquals(401, client.get("/api/branches/$branchA/relief-candidates?date=$tomorrow").code)
        }
    }
}
