package com.companyb.companyapp.reporting
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.branch.BranchRoutes
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.branch.BranchResponse
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.identity.RoleTable
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.remittance.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.remittance.RemittanceTable
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
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
 * The multi-branch report (/api/branches/export/report) is JWT-only and
 * includes only the branches the caller may read, so a zero-grant caller gets
 * an empty report (200), never 403; the JWT filter still blocks anonymous calls.
 *
 * Gate-pass is proven by 200/404 (the handler ran); gate-block by 403. This
 * keeps the authz matrix deterministic without seeding financial data.
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
            IdentityFixtures.insertTestUser(id, prefix)
        }

        BranchWorkforceFixtures.insertTestBranch(branchA, "Reports Branch 131 A")
        BranchWorkforceFixtures.insertTestBranch(branchB, "Reports Branch 131 B")

        assignRole(ownerUser, "OWNER")
        BranchWorkforceFixtures.insertTestAssignment(
            userId = ownerUser,
            branchId = branchA,
            slot = 1,
            assignedBy = ownerUser,
        )

        // Branch-scoped VIEW_BRANCH_DATA at branchA only.
        IdentityFixtures.grantCapability(
            userId = viewA,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchA,
            sourceId = sourceId,
        )
        // Non-view grant at branchB — readable in the picker window, NOT view-exportable.
        IdentityFixtures.grantCapability(
            userId = editB,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchB,
            sourceId = sourceId,
        )
        // GLOBAL VIEW_BRANCH_DATA with NO branch grants — the all-branches window.
        IdentityFixtures.grantCapability(
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
            "/api/branches/$branchA/export/report?from=2026-08-01&to=2026-08-10&format=xlsx",
            "/api/branches/$branchA/export/report?from=2026-08-01&to=2026-08-10&format=pdf",
            "/api/branches/$branchA/daily-summary?date=2026-08-10",
            "/api/branches/$branchA/monthly-summary?year=2026&month=8",
        )

    private val branchBExportPaths =
        listOf(
            "/api/branches/$branchB/export/report?from=2026-08-01&to=2026-08-10&format=xlsx",
            "/api/branches/$branchB/export/report?from=2026-08-01&to=2026-08-10&format=pdf",
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
    // Multi-branch report: JWT-only, filtered to readable branches
    // ──────────────────────────────────────────────

    private val multiReportPaths =
        listOf(
            "/api/branches/export/report?from=2026-08-01&to=2026-08-10&format=xlsx",
            "/api/branches/export/report?from=2026-08-01&to=2026-08-10&format=pdf&type=PROVINCIAL_TOUR",
            "/api/branches/export/report?from=2026-08-01&to=2026-08-10&format=xlsx&type=MEDICAL_MISSION",
        )

    @Test
    fun `multi-branch report is reachable by zero-grant user`() {
        // 200 with an empty report: unreadable branches are left out, never refused.
        multiReportPaths.forEach { path ->
            assertEquals(200, getStatus(noneUser, path), "expected 200 on $path")
        }
    }

    @Test
    fun `multi-branch report still requires JWT auth`() {
        testServer.client.let { client ->
            multiReportPaths.forEach { path -> assertEquals(401, client.get(path).code, "expected 401 on $path") }
        }
    }

    // ──────────────────────────────────────────────
    // Gate: branch-scoped VIEW_BRANCH_DATA
    // ──────────────────────────────────────────────

    @Test
    fun `branch-scoped holder passes on granted branch`() {
        branchAExportPaths.forEach { path ->
            assertTrue(getStatus(viewA, path) in GATE_PASS, "expected gate-pass (404) on $path")
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
            assertTrue(
                getStatus(globalViewUser, path) in GATE_PASS,
                "expected gate-pass (404) on $path — GLOBAL VIEW_BRANCH_DATA must be an all-branches window",
            )
        }
    }

    @Test
    fun `global holder passes on any branch`() {
        branchAExportPaths.forEach { path ->
            assertTrue(getStatus(globalViewUser, path) in GATE_PASS, "expected gate-pass (404) on $path")
        }
    }

    @Test
    fun `OWNER role holder passes on unassigned branch`() {
        branchBExportPaths.forEach { path ->
            assertTrue(
                getStatus(ownerUser, path) in GATE_PASS,
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

    // ──────────────────────────────────────────────
    // #744: unknown branch → 404 before the capability gate
    // (branch-scoped-elsewhere + zero-grant legs per #711 precedence)
    // ──────────────────────────────────────────────

    @Test
    fun `unknown branch daily-summary is 404 not 403`() {
        val unknown = TestFixtures.uuid()
        assertEquals(404, getStatus(viewA, "/api/branches/$unknown/daily-summary?date=2026-08-10"))
        assertEquals(404, getStatus(noneUser, "/api/branches/$unknown/daily-summary?date=2026-08-10"))
    }

    @Test
    fun `unknown branch daily-summaries is 404-gate not 403`() {
        val unknown = TestFixtures.uuid()
        // Browse has no 404 data shape — the existence gate 404s before the capability gate.
        assertEquals(404, getStatus(viewA, "/api/branches/$unknown/daily-summaries"))
        assertEquals(404, getStatus(noneUser, "/api/branches/$unknown/daily-summaries"))
    }

    @Test
    fun `unknown branch monthly-summary is 404 not 403`() {
        val unknown = TestFixtures.uuid()
        assertEquals(404, getStatus(viewA, "/api/branches/$unknown/monthly-summary?year=2026&month=8"))
        assertEquals(404, getStatus(noneUser, "/api/branches/$unknown/monthly-summary?year=2026&month=8"))
    }

    @Test
    fun `unknown branch export is 404 not 403`() {
        val unknown = TestFixtures.uuid()
        val path = "/api/branches/$unknown/export/report?from=2026-08-01&to=2026-08-10&format=xlsx"
        assertEquals(404, getStatus(viewA, path))
        assertEquals(404, getStatus(noneUser, path))
    }
}

// Gate-pass: the handler ran (no data = 404 on single reports, 200 zeros on the monthly summary), never 403.
private val GATE_PASS = setOf(200, 404)
