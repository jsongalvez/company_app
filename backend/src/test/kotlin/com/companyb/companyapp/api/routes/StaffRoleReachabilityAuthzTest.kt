package com.companyb.companyapp.api.routes

import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.identity.RoleTable
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import io.javalin.Javalin
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #417 route-level proof: a freshly created staff user whose ONLY grants are the
 * role bundle + one ACTIVE home-branch assignment (zero direct user_capability
 * rows) passes the affected route classes' gates through the HTTP stack — one
 * probe per class: expense create (EDIT_BRANCH_DATA, the same
 * requireBranchOrBranchDayCapability filter session create/mutations use),
 * compensation read (ASSIGN_COMPENSATION), remittance list (SUBMIT_REMITTANCE),
 * base-rate upsert (MANAGE_PRODUCTS). Branch scoping (unrelated branch 403s)
 * and revocation (ended assignment 403s) ride the same view window. The
 * dashboard read is deliberately absent: it is clock-in-gated, not
 * capability-gated.
 *
 * The #421 cross-branch compensation denial rides this server too: the
 * work-branch gate passes while the service rejects the spanning day pair.
 *
 * The derivation matrix itself is pinned by StaffRoleBranchDerivationPostgresTest.
 */
class StaffRoleReachabilityAuthzTest : BasePostgresTest() {
    private val coordinatorUser = TestFixtures.uuid()
    private val practitionerUser = TestFixtures.uuid()
    private val onboardingUser = TestFixtures.uuid()
    private val endedCoordinator = TestFixtures.uuid()

    private val branchA = TestFixtures.uuid()
    private val unrelatedBranch = TestFixtures.uuid()

    private lateinit var todayAtBranchA: UUID
    private lateinit var todayAtUnrelatedBranch: UUID

    override fun initTestData() {
        listOf(
            coordinatorUser to "reach-coord",
            practitionerUser to "reach-pract",
            onboardingUser to "reach-onbrd",
            endedCoordinator to "reach-ended",
        ).forEach { (id, prefix) ->
            DatabaseTestHelper.insertTestUser(id, prefix)
        }

        listOf(branchA to "Reachability Branch A", unrelatedBranch to "Unrelated Branch").forEach { (id, name) ->
            DatabaseTestHelper.insertTestBranch(id, name)
        }

        assignRole(coordinatorUser, "COORDINATOR")
        assignRole(practitionerUser, "PRACTITIONER")
        assignRole(onboardingUser, "ONBOARDING")
        assignRole(endedCoordinator, "COORDINATOR")

        insertAssignment(coordinatorUser, branchA)
        insertAssignment(practitionerUser, branchA)
        insertAssignment(onboardingUser, branchA)
        insertAssignment(endedCoordinator, branchA, ended = true)

        todayAtBranchA = DatabaseTestHelper.createBranchDayForToday(branchA)
        todayAtUnrelatedBranch = DatabaseTestHelper.createBranchDayForToday(unrelatedBranch)
        // Compensations created below are torn down with their owning user (#421 probes).
        // Expenses created below are torn down with their owning day.
        // Audited writes (expense insert, rate set) reference their caller —
        // audit rows must be removed before the users (UserManagementAuthzTest pattern).
    }

    private fun assignRole(
        userId: UUID,
        roleName: String,
    ) {
        val roleId =
            transaction {
                RoleTable.selectAll().where { RoleTable.name eq roleName }.single()[RoleTable.id]
            }
        transaction {
            UserRoleTable.insert {
                it[UserRoleTable.userId] = userId
                it[UserRoleTable.roleId] = roleId
            }
        }
    }

    private fun insertAssignment(
        userId: UUID,
        branchId: UUID,
        ended: Boolean = false,
    ) {
        val id =
            DatabaseTestHelper.insertTestAssignment(
                userId = userId,
                branchId = branchId,
                slot = 1,
                assignedBy = userId,
                ended = ended,
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
                ExpenseRoutes.register(cfg)
                CompensationRoutes.register(cfg)
                RemittanceRoutes.register(cfg)
                SessionBaseRateRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun expenseBody(branchDayId: UUID): Map<String, String> =
        mapOf(
            "id" to TestFixtures.uuid().toString(),
            "branchDayId" to branchDayId.toString(),
            "amount" to "100.00",
            "category" to "MISCELLANEOUS",
        )

    private fun rateBody(): Map<String, String> =
        mapOf(
            "id" to TestFixtures.uuid().toString(),
            "sessionType" to "REGULAR",
            "rate" to "150.00",
        )

    // --- Coordinator: derived-only grants pass every acceptance surface ---

    @Test
    fun `coordinator creates an expense from role plus assignment alone`() {
        testServer.client.let { client ->
            val response = client.post("/api/expenses", expenseBody(todayAtBranchA), asUser(coordinatorUser))
            assertEquals(201, response.code)
        }
    }

    @Test
    fun `coordinator reads compensations for the assigned branch day`() {
        testServer.client.let { client ->
            val response = client.get("/api/compensations?branchDayId=$todayAtBranchA", asUser(coordinatorUser))
            assertEquals(200, response.code)
        }
    }

    @Test
    fun `coordinator lists remittances for the assigned branch`() {
        testServer.client.let { client ->
            val response = client.get("/api/remittances?branchId=$branchA", asUser(coordinatorUser))
            assertEquals(200, response.code)
        }
    }

    @Test
    fun `coordinator sets a session base rate at the assigned branch`() {
        testServer.client.let { client ->
            val response = client.post("/api/branches/$branchA/rates", rateBody(), asUser(coordinatorUser))
            assertEquals(201, response.code)
        }
    }

    // --- #421: the paying branch carries the charge, so its day must match the work branch ---

    private fun compensationBody(
        workDayId: UUID,
        payingDayId: UUID,
    ): Map<String, String> =
        mapOf(
            "id" to TestFixtures.uuid().toString(),
            "workBranchDayId" to workDayId.toString(),
            "payingBranchDayId" to payingDayId.toString(),
            "userId" to practitionerUser.toString(),
            "amount" to "500.00",
        )

    @Test
    fun `compensation charging another branch's day is rejected as an invalid shape`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client
                    .post(
                        "/api/compensation",
                        compensationBody(todayAtBranchA, todayAtUnrelatedBranch),
                        asUser(coordinatorUser),
                    ).code,
                "work-branch authority alone must not charge a foreign branch's OPEN day",
            )
        }
    }

    @Test
    fun `coordinator creates a same-branch compensation end to end`() {
        testServer.client.let { client ->
            val body = compensationBody(todayAtBranchA, todayAtBranchA)
            assertEquals(201, client.post("/api/compensation", body, asUser(coordinatorUser)).code)
        }
    }

    // --- Scoping + revocation through the same derived rows ---

    @Test
    fun `derived grants stop at the assignment's branch`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/remittances?branchId=$unrelatedBranch", asUser(coordinatorUser)).code,
                "no derivation at branches without an assignment",
            )
            assertEquals(
                403,
                client.post("/api/branches/$unrelatedBranch/rates", rateBody(), asUser(coordinatorUser)).code,
            )
        }
    }

    @Test
    fun `ended assignment revokes reachability`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/remittances?branchId=$branchA", asUser(endedCoordinator)).code,
                "ended_at IS NULL filter must exclude the derived bundle",
            )
        }
    }

    @Test
    fun `practitioner gets expenses but not remittance or compensation surfaces`() {
        testServer.client.let { client ->
            assertEquals(201, client.post("/api/expenses", expenseBody(todayAtBranchA), asUser(practitionerUser)).code)
            assertEquals(403, client.get("/api/remittances?branchId=$branchA", asUser(practitionerUser)).code)
            assertEquals(
                403,
                client.get("/api/compensations?branchDayId=$todayAtBranchA", asUser(practitionerUser)).code,
            )
        }
    }

    @Test
    fun `onboarding stays locked out despite an active assignment`() {
        testServer.client.let { client ->
            assertEquals(403, client.post("/api/expenses", expenseBody(todayAtBranchA), asUser(onboardingUser)).code)
        }
    }
}
