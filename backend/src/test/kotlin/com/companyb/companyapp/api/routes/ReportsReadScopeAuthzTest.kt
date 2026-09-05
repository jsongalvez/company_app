@file:Suppress("LargeClass")

package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceStatus
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.repository.model.RoleTable
import com.companyb.companyapp.repository.model.UserRoleTable
import com.companyb.companyapp.service.CapabilityService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import io.javalin.Javalin
import io.javalin.http.UnauthorizedResponse
import io.javalin.testtools.Request
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.ClassRule
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #131 Reports access scope: GLOBAL VIEW_BRANCH_DATA = all-branches window on
 * the export + daily/monthly-summary routes, plus the accessible-branches
 * picker endpoint (read window = distinct BRANCH grants, or all branches for
 * a GLOBAL VIEW_BRANCH_DATA holder).
 *
 * #128 Public branch-type exports: /api/branches/export/provincial +
 * /medical-mission are JWT-only (no capability gate). The 3-segment GLOBAL
 * filter that used to sit at /api/branches/export never fired on the 4-segment
 * routes (the #114 exact-segment lesson), so the target state already held —
 * these tests lock it: zero-grant user passes (404/200, never 403), and the
 * real JWT filter still blocks unauthenticated callers.
 *
 * Gate-pass is proven by 404 (the handlers 404 on missing data AFTER the gate
 * passes); gate-block by 403. This keeps the authz matrix deterministic
 * without seeding financial data — except one #128 test that seeds two
 * minimal submitted remittances to prove the public download path returns
 * 200 with data for a zero-grant user.
 */
class ReportsReadScopeAuthzTest : BasePostgresTest() {
    private val viewA = TestFixtures.uuid()
    private val editB = TestFixtures.uuid()
    private val globalViewUser = TestFixtures.uuid()
    private val ownerUser = TestFixtures.uuid()
    private val noneUser = TestFixtures.uuid()
    private val branchA = TestFixtures.uuid()
    private val branchB = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    override fun initTestData() {
        listOf(
            viewA to "reports-view-a",
            editB to "reports-edit-b",
            globalViewUser to "reports-global",
            ownerUser to "reports-owner",
            noneUser to "no-caps",
        ).forEach { (id, prefix) ->
            DatabaseTestHelper.insertTestUser(id, prefix)
        }

        DatabaseTestHelper.insertTestBranch(branchA, "Reports Branch 131 A")
        DatabaseTestHelper.insertTestBranch(branchB, "Reports Branch 131 B")

        assignRole(ownerUser, "OWNER")
        DatabaseTestHelper.insertTestAssignment(
            userId = ownerUser,
            branchId = branchA,
            slot = 1,
            assignedBy = ownerUser,
        )

        // Branch-scoped VIEW_BRANCH_DATA at branchA only.
        DatabaseTestHelper.grantCapability(
            userId = viewA,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchA,
            sourceId = sourceId,
        )
        // Non-view grant at branchB — readable in the picker window, NOT view-exportable.
        DatabaseTestHelper.grantCapability(
            userId = editB,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchB,
            sourceId = sourceId,
        )
        // GLOBAL VIEW_BRANCH_DATA with NO branch grants — the all-branches window.
        DatabaseTestHelper.grantCapability(
            userId = globalViewUser,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
        listOf(viewA, editB, globalViewUser).forEach {
        }
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
                    ctx.status(403).json(mapOf("error" to (e.message ?: "Forbidden")))
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(mapOf("error" to (e.message ?: "Not Found")))
                }
                ExportRoutes.register(cfg)
                DailySalesSummaryRoutes.register(cfg)
                MonthlyRemittanceSummaryRoutes.register(cfg)
                BranchRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun getStatus(
        user: UUID,
        path: String,
    ): Int {
        var status = 0
        testServer.client.let { client ->
            status = client.get(path, asUser(user)).code
        }
        return status
    }

    private fun accessibleBranches(user: UUID): Pair<Int, List<BranchResponse>> {
        var status = 0
        var body: List<BranchResponse>? = null
        testServer.client.let { client ->
            val response = client.get("/api/branches/accessible", asUser(user))
            status = response.code
            body =
                response.body
                    .string()
                    .takeIf { it.isNotBlank() }
                    ?.let { json.decodeFromString<List<BranchResponse>>(it) }
        }
        return status to (body ?: emptyList())
    }

    private val branchAExportPaths =
        listOf(
            "/api/branches/$branchA/export/daily?date=2026-08-10&format=csv",
            "/api/branches/$branchA/export/monthly?year=2026&month=8&format=csv",
            "/api/branches/$branchA/export/all-time?format=csv",
            "/api/branches/$branchA/export/range?from=2026-08-01&to=2026-08-10&format=csv",
            "/api/branches/$branchA/daily-summary?date=2026-08-10",
            "/api/branches/$branchA/monthly-summary?year=2026&month=8",
        )

    private val branchBExportPaths =
        listOf(
            "/api/branches/$branchB/export/daily?date=2026-08-10&format=csv",
            "/api/branches/$branchB/export/monthly?year=2026&month=8&format=csv",
            "/api/branches/$branchB/export/all-time?format=csv",
            "/api/branches/$branchB/export/range?from=2026-08-01&to=2026-08-10&format=csv",
            "/api/branches/$branchB/daily-summary?date=2026-08-10",
            "/api/branches/$branchB/monthly-summary?year=2026&month=8",
        )

    // ──────────────────────────────────────────────
    // Gate: no grants → 403 everywhere
    // ──────────────────────────────────────────────

    @Test
    fun `no-grant user is blocked on every read route`() {
        (branchAExportPaths + branchBExportPaths).forEach { path ->
            assertEquals(403, getStatus(noneUser, path), "expected 403 on $path")
        }
    }

    // ──────────────────────────────────────────────
    // #128: branch-type exports are public (JWT-only)
    // ──────────────────────────────────────────────

    private val branchTypeExportPaths =
        listOf(
            "/api/branches/export/provincial?format=csv",
            "/api/branches/export/provincial?year=2026&month=8&format=csv",
            "/api/branches/export/medical-mission?format=csv",
            "/api/branches/export/medical-mission?year=2026&month=8&format=csv",
        )

    @Test
    fun `branch-type exports are reachable by zero-grant user`() {
        // 404 = gate-pass (handler ran, no data), never 403 — public reports.
        branchTypeExportPaths.forEach { path ->
            assertEquals(404, getStatus(noneUser, path), "expected gate-pass (404) on $path")
        }
    }

    @Test
    fun `branch-type export returns 200 with data for zero-grant user`() {
        val provincialBranch = TestFixtures.uuid()
        val missionBranch = TestFixtures.uuid()
        seedSubmittedRemittance(
            branchId = provincialBranch,
            branchName = "Provincial Export Branch",
            branchType = BranchType.PROVINCIAL_TOUR,
            snapshot =
                FinancialSnapshot(
                    grossIncome = BigDecimal("2000.00"),
                    totalCompensation = BigDecimal("400.00"),
                    totalExpenses = BigDecimal("100.00"),
                ),
        )
        seedSubmittedRemittance(
            branchId = missionBranch,
            branchName = "Mission Export Branch",
            branchType = BranchType.MEDICAL_MISSION,
            snapshot =
                FinancialSnapshot(
                    grossIncome = BigDecimal("1000.00"),
                    totalCompensation = BigDecimal("200.00"),
                    totalExpenses = BigDecimal("50.00"),
                ),
        )
        testServer.client.let { client ->
            val provincial = client.get("/api/branches/export/provincial?format=csv", asUser(noneUser))
            assertEquals(200, provincial.code)
            assertTrue(
                provincial
                    .headers()
                    .get("Content-Type")
                    .orEmpty()
                    .contains("text/csv"),
            )
            assertTrue(provincial.body.string().contains("Provincial Export Branch"))
            assertTrue(provincial.body.string().contains("1500.00"))

            val mission = client.get("/api/branches/export/medical-mission?format=csv", asUser(noneUser))
            assertEquals(200, mission.code)
            assertTrue(
                mission
                    .headers()
                    .get("Content-Type")
                    .orEmpty()
                    .contains("text/csv"),
            )
            assertTrue(mission.body.string().contains("Mission Export Branch"))
            assertTrue(mission.body.string().contains("750.00"))
        }
    }

    @Test
    fun `branch-type exports still require JWT auth`() {
        testServer.client.let { client ->
            assertEquals(401, client.get("/api/branches/export/provincial?format=csv").code)
            assertEquals(401, client.get("/api/branches/export/medical-mission?format=csv").code)
        }
    }

    @Test
    fun `branch-type exports pass with a valid JWT`() {
        val token = JwtService.generateToken(noneUser.toString())
        testServer.client.let { client ->
            val response =
                client.get(
                    "/api/branches/export/provincial?format=csv",
                    Consumer { it.header("Authorization", "Bearer $token") },
                )
            assertEquals(404, response.code, "expected gate-pass (404) — JWT accepted, no data")
        }
    }

    private data class FinancialSnapshot(
        val grossIncome: BigDecimal,
        val totalCompensation: BigDecimal,
        val totalExpenses: BigDecimal,
    )

    private fun seedSubmittedRemittance(
        branchId: UUID,
        branchName: String,
        branchType: BranchType,
        snapshot: FinancialSnapshot,
    ) {
        DatabaseTestHelper.insertTestBranch(branchId, branchName, branchType)
        val remittanceId = TestFixtures.uuid()
        val today = TestFixtures.today
        transaction {
            RemittanceTable.insert {
                it[RemittanceTable.id] = remittanceId
                it[RemittanceTable.branchId] = branchId
                it[RemittanceTable.type] = RemittanceType.SESSION
                it[RemittanceTable.method] = RemittanceMethod.BANK_TRANSFER
                it[RemittanceTable.status] = RemittanceStatus.SUBMITTED
                it[RemittanceTable.version] = 2
                it[RemittanceTable.submittedDate] = today
                it[RemittanceTable.submittedBy] = noneUser
                it[RemittanceTable.dateRangeStart] = today
                it[RemittanceTable.dateRangeEnd] = today
            }
            RemittanceFinancialSnapshotTable.insert {
                it[RemittanceFinancialSnapshotTable.remittanceId] = remittanceId
                it[RemittanceFinancialSnapshotTable.grossIncome] = snapshot.grossIncome
                it[RemittanceFinancialSnapshotTable.totalCompensation] = snapshot.totalCompensation
                it[RemittanceFinancialSnapshotTable.totalExpenses] = snapshot.totalExpenses
                it[RemittanceFinancialSnapshotTable.netIncome] =
                    snapshot.grossIncome - snapshot.totalCompensation - snapshot.totalExpenses
            }
        }
    }

    // ──────────────────────────────────────────────
    // Gate: branch-scoped VIEW_BRANCH_DATA
    // ──────────────────────────────────────────────

    @Test
    fun `branch-scoped holder passes on granted branch`() {
        branchAExportPaths.forEach { path ->
            assertEquals(404, getStatus(viewA, path), "expected gate-pass (404) on $path")
        }
    }

    @Test
    fun `branch-scoped holder is blocked on other branch`() {
        branchBExportPaths.forEach { path ->
            assertEquals(403, getStatus(viewA, path), "expected 403 on $path")
        }
    }

    // ──────────────────────────────────────────────
    // Gate: GLOBAL VIEW_BRANCH_DATA = all-branches window
    // ──────────────────────────────────────────────

    @Test
    fun `global holder passes on a branch with no grants`() {
        branchBExportPaths.forEach { path ->
            assertEquals(
                404,
                getStatus(globalViewUser, path),
                "expected gate-pass (404) on $path — GLOBAL VIEW_BRANCH_DATA must be an all-branches window",
            )
        }
    }

    @Test
    fun `global holder passes on any branch`() {
        branchAExportPaths.forEach { path ->
            assertEquals(404, getStatus(globalViewUser, path), "expected gate-pass (404) on $path")
        }
    }

    @Test
    fun `OWNER role holder passes on unassigned branch`() {
        branchBExportPaths.forEach { path ->
            assertEquals(
                404,
                getStatus(ownerUser, path),
                "OWNER GLOBAL VIEW_BRANCH_DATA must pass on unassigned branch for $path",
            )
        }
    }

    // ──────────────────────────────────────────────
    // Gate: paged daily-summaries feed
    // (gate-pass = 200 empty feed, NOT 404 — a browse feed is 200 when
    // the branch has no days, unlike the single-date 404-on-missing-data)
    // ──────────────────────────────────────────────

    private val dailySummariesPaths =
        listOf(
            "/api/branches/$branchA/daily-summaries",
            "/api/branches/$branchB/daily-summaries",
        )

    @Test
    fun `no-grant user is blocked on daily-summaries`() {
        dailySummariesPaths.forEach { path ->
            assertEquals(403, getStatus(noneUser, path), "expected 403 on $path")
        }
    }

    @Test
    fun `branch-scoped holder passes on granted branch daily-summaries`() {
        assertEquals(200, getStatus(viewA, "/api/branches/$branchA/daily-summaries"))
    }

    @Test
    fun `branch-scoped holder is blocked on other branch daily-summaries`() {
        assertEquals(403, getStatus(viewA, "/api/branches/$branchB/daily-summaries"))
    }

    @Test
    fun `global holder passes on zero-grant branch daily-summaries`() {
        assertEquals(200, getStatus(globalViewUser, "/api/branches/$branchB/daily-summaries"))
    }

    @Test
    fun `OWNER role holder passes on zero-grant branch daily-summaries`() {
        assertEquals(200, getStatus(ownerUser, "/api/branches/$branchB/daily-summaries"))
    }

    // ──────────────────────────────────────────────
    // GET /api/branches/accessible — the picker window
    // ──────────────────────────────────────────────

    @Test
    fun `accessible branches returns the caller window - branch-scoped`() {
        val (status, branches) = accessibleBranches(viewA)
        assertEquals(200, status)
        assertEquals(listOf(branchA.toString()), branches.map { it.id })
    }

    @Test
    fun `accessible branches excludes other-branch grants`() {
        val (_, branches) = accessibleBranches(viewA)
        assertTrue(branchB.toString() !in branches.map { it.id })
    }

    @Test
    fun `accessible branches lists non-view grants too`() {
        val (status, branches) = accessibleBranches(editB)
        assertEquals(200, status)
        assertEquals(listOf(branchB.toString()), branches.map { it.id })
    }

    @Test
    fun `accessible branches global holder sees all branches`() {
        val (status, branches) = accessibleBranches(globalViewUser)
        assertEquals(200, status)
        assertEquals(setOf(branchA.toString(), branchB.toString()), branches.map { it.id }.toSet())
        assertEquals("Reports Branch 131 A", branches.first { it.id == branchA.toString() }.name)
    }

    @Test
    fun `accessible branches OWNER role holder sees all branches`() {
        val (status, branches) = accessibleBranches(ownerUser)
        assertEquals(200, status)
        assertEquals(setOf(branchA.toString(), branchB.toString()), branches.map { it.id }.toSet())
    }

    @Test
    fun `accessible branches zero-grant user gets empty list not 403`() {
        val (status, branches) = accessibleBranches(noneUser)
        assertEquals(200, status)
        assertTrue(branches.isEmpty())
    }

    @Test
    fun `accessible branches is not MANAGE_USERS-gated`() {
        // The 3-segment /accessible path must not be caught by the 2-segment
        // MANAGE_USERS before-filter in BranchRoutes (exact-segment matching).
        val (status, _) = accessibleBranches(noneUser)
        assertEquals(200, status)
    }

    // ──────────────────────────────────────────────
    // Regression: GET /api/branches stays MANAGE_USERS-gated
    // ──────────────────────────────────────────────

    @Test
    fun `GET branches stays MANAGE_USERS-gated`() {
        assertEquals(403, getStatus(viewA, "/api/branches"))
        assertEquals(403, getStatus(noneUser, "/api/branches"))
    }
}
