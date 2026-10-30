package com.companyb.companyapp.session

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.audit.AuditLogRoutes
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.finance.ExpenseRoutes
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.identity.RoleTable
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import io.javalin.Javalin
import io.javalin.testtools.Request
import io.javalin.testtools.Response
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import kotlin.test.assertTrue

/**
 * Owed #20 (user 12904): everyone belonging to a branch, relief workers aside, gets the same edit
 * access. Membership is an active branch assignment, whatever the role (ONBOARDING alone is not
 * working yet). On a day that is not remitted, every member adds, edits, deletes and voids
 * sessions and expenses and corrects a mark, with a reason on a past day. A remitted day stays
 * with EDIT_PAST_DAY holders. Relief (a day grant, no assignment) keeps only what it had.
 */
class BranchMemberEditAuthzTest : BasePostgresTest() {
    private val branchId = TestFixtures.uuid()
    private val members = MEMBER_ROLES.associateWith { TestFixtures.uuid() }
    private val coordinator = TestFixtures.uuid()
    private val relief = TestFixtures.uuid()
    private val onboarding = TestFixtures.uuid()
    private lateinit var todayId: UUID
    private lateinit var yesterdayId: UUID
    private lateinit var remittedId: UUID

    override fun initTestData() {
        BranchWorkforceFixtures.insertTestBranch(branchId)
        todayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, TestFixtures.today)
        yesterdayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, TestFixtures.today.minusDays(1))
        remittedId = BranchWorkforceFixtures.createRemittedBranchDay(branchId, TestFixtures.today.minusDays(2))
        (members + ("COORDINATOR" to coordinator)).forEach { (role, id) ->
            IdentityFixtures.insertTestUser(id, role.lowercase())
            assignRole(id, role)
            BranchWorkforceFixtures.insertTestAssignment(userId = id, branchId = branchId, slot = 1, assignedBy = id)
        }
        IdentityFixtures.insertTestUser(onboarding, "onboarding")
        assignRole(onboarding, "ONBOARDING")
        BranchWorkforceFixtures.insertTestAssignment(
            userId = onboarding,
            branchId = branchId,
            slot = 2,
            assignedBy = onboarding,
        )
        IdentityFixtures.insertTestUser(relief, "relief")
        assignRole(relief, "PRACTITIONER")
        for (day in listOf(todayId, yesterdayId)) {
            IdentityFixtures.grantCapability(
                relief,
                CapabilityCodes.EDIT_BRANCH_DATA,
                CapabilityContextType.BRANCH_DAY,
                day,
                TestFixtures.uuid(),
            )
        }
    }

    @Test
    fun `every member voids a session today`() =
        forEachMember { who ->
            val s = session(todayId)
            assertCode(201, post("/api/sessions/$s/void", voidBody(), who), who)
        }

    @Test
    fun `every member corrects a no-show today`() =
        forEachMember { who ->
            val s = session(todayId)
            assertCode(200, mark(s, "NO_SHOW", 1, null, who), who)
            assertCode(200, mark(s, "CANCELLED", 2, "wrong tap", who), who)
        }

    @Test
    fun `every member adds, edits and deletes an expense today`() =
        forEachMember { who ->
            val (id, version) = addExpense(todayId, who)
            assertCode(200, editExpense(id, version, null, who), who)
            assertCode(200, deleteExpense(id, version + 1, "double", who), who)
        }

    @Test
    fun `every member fixes a past unremitted day, with a reason`() =
        forEachMember { who ->
            val (id, version) = addExpense(yesterdayId, who)
            assertCode(400, editExpense(id, version, null, who), who)
            assertCode(200, editExpense(id, version, "typo", who), who)
            assertCode(200, deleteExpense(id, version + 1, "double", who), who)
            val s = session(yesterdayId)
            assertCode(200, price(s, 1, "typo", who), who)
            assertCode(200, mark(s, "NO_SHOW", 2, "did not come", who), who)
            assertCode(200, mark(s, "CANCELLED", 3, "called ahead", who), who)
            assertCode(201, post("/api/sessions/$s/void", voidBody(), who), who)
        }

    @Test
    fun `a remitted day stays locked for members, open to Coordinators`() {
        forEachMember { who ->
            val s = session(remittedId)
            assertCode(403, price(s, 1, "typo", who), who)
            assertCode(403, post("/api/sessions/$s/void", voidBody(), who), who)
            assertCode(403, post(ApiRoutes.EXPENSES, expenseBody(remittedId, "late"), who), who)
        }
        val s = session(remittedId)
        assertCode(200, price(s, 1, "typo", coordinator), "COORDINATOR")
        assertCode(201, post("/api/sessions/$s/void", voidBody(), coordinator), "COORDINATOR")
    }

    @Test
    fun `relief has a member's rights for its day, void and mark correction included`() {
        // Owed #23: "relief has same access as branch members only for that granted day."
        assertCode(201, post("/api/sessions/${session(todayId)}/void", voidBody(), relief), "relief")
        val s = session(todayId)
        assertCode(200, mark(s, "NO_SHOW", 1, null, relief), "relief")
        assertCode(200, mark(s, "CANCELLED", 2, "wrong tap", relief), "relief")
    }

    @Test
    fun `someone still onboarding stays out though assigned`() {
        val s = session(todayId)
        assertCode(403, post("/api/sessions/$s/void", voidBody(), onboarding), "onboarding")
        assertCode(403, post(ApiRoutes.EXPENSES, expenseBody(todayId), onboarding), "onboarding")
    }

    @Test
    fun `the session's change history shows each member edit with its reason, newest first`() {
        val owner = members.getValue("OWNER")
        val s = session(yesterdayId)
        assertCode(200, price(s, 1, "typo", owner), "OWNER")
        assertCode(201, post("/api/sessions/$s/void", voidBody(), owner), "OWNER")
        val r = testServer.client.get("${ApiRoutes.AUDIT_LOG}?tableName=session&recordId=$s", asUser(owner))
        assertCode(200, r, "OWNER")
        val rows = Json.parseToJsonElement(r.body.string()).jsonArray.map { it.jsonObject }
        val tables = rows.map { it["tableName"]?.jsonPrimitive?.content }
        assertEquals("session_void", tables.first(), "void is newest: $tables")
        val price = rows.first { it["tableName"]?.jsonPrimitive?.content == "session" && it["reason"] != null }
        assertEquals("typo", price["reason"]?.jsonPrimitive?.content)
        assertEquals(owner.toString(), price["changedBy"]?.jsonPrimitive?.content)
    }

    private fun forEachMember(block: (UUID) -> Unit) {
        val failures =
            members.mapNotNull { (role, id) ->
                runCatching { block(id) }.exceptionOrNull()?.let { "$role: ${it.message}" }
            }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun assertCode(
        expected: Int,
        r: Response,
        who: Any,
    ) = assertEquals(expected, r.code, "$who: ${r.body.string()}")

    private fun session(dayId: UUID): UUID {
        val client = TestFixtures.uuid()
        val id = TestFixtures.uuid()
        SessionClientFixtures.insertTestClient(client)
        SessionClientFixtures.insertTestSession(id, client, dayId)
        return id
    }

    private fun mark(
        s: UUID,
        status: String,
        version: Int,
        reason: String?,
        who: UUID,
    ) = testServer.client.patch(
        "/api/sessions/$s/status",
        mapOf("status" to status, "version" to version, "reason" to reason),
        asUser(who),
    )

    private fun price(
        s: UUID,
        version: Int,
        reason: String,
        who: UUID,
    ) = testServer.client.patch(
        "/api/sessions/$s/final-price",
        mapOf("finalPrice" to "1.00", "version" to version, "reason" to reason),
        asUser(who),
    )

    private fun voidBody() = mapOf("id" to TestFixtures.uuid().toString(), "voidReason" to "double entry")

    private fun expenseBody(
        dayId: UUID,
        reason: String? = null,
    ) = mapOf(
        "id" to TestFixtures.uuid().toString(),
        "branchDayId" to dayId.toString(),
        "amount" to "50.00",
        "category" to "PANTRY",
        "reason" to reason,
    )

    private fun addExpense(
        dayId: UUID,
        who: UUID,
    ): Pair<String, Int> {
        val r = post(ApiRoutes.EXPENSES, expenseBody(dayId), who)
        val body = r.body.string()
        assertEquals(201, r.code, "add expense: $body")
        val json = Json.parseToJsonElement(body).jsonObject
        return json.getValue("id").jsonPrimitive.content to json.getValue("version").jsonPrimitive.int
    }

    private fun editExpense(
        id: String,
        version: Int,
        reason: String?,
        who: UUID,
    ) = testServer.client.patch(
        "${ApiRoutes.EXPENSES}/$id",
        mapOf("amount" to "60.00", "category" to "PANTRY", "expectedVersion" to version, "reason" to reason),
        asUser(who),
    )

    private fun deleteExpense(
        id: String,
        version: Int,
        reason: String,
        who: UUID,
    ) = testServer.client.delete(
        "${ApiRoutes.EXPENSES}/$id",
        mapOf("reason" to reason, "expectedVersion" to version),
        asUser(who),
    )

    private fun post(
        path: String,
        body: Any,
        who: UUID,
    ) = testServer.client.post(path, body, asUser(who))

    private fun asUser(id: UUID) = Consumer<Request.Builder> { it.header("X-Test-User", id.toString()) }

    private fun assignRole(
        userId: UUID,
        roleName: String,
    ) {
        val roleId = transaction { RoleTable.selectAll().where { RoleTable.name eq roleName }.single()[RoleTable.id] }
        transaction {
            UserRoleTable.insert {
                it[UserRoleTable.userId] = userId
                it[UserRoleTable.roleId] = roleId
            }
        }
    }

    companion object {
        private val MEMBER_ROLES = listOf("OWNER", "ACCOUNTANT", "PRACTITIONER")
        private val defaultUser = TestFixtures.uuid()

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
                    ctx.attribute("userId", ctx.header("X-Test-User") ?: defaultUser.toString())
                }
                cfg.routes.exception(ValidationException::class.java) { e, ctx ->
                    ctx.status(400).json(mapOf("error" to e.message))
                }
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(mapOf("error" to e.message))
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(mapOf("error" to e.message))
                }
                cfg.routes.exception(ConflictException::class.java) { e, ctx ->
                    ctx.status(409).json(mapOf("error" to e.message))
                }
                SessionRoutes.register(cfg)
                ExpenseRoutes.register(cfg)
                AuditLogRoutes.register(cfg)
            }
        }
    }
}
