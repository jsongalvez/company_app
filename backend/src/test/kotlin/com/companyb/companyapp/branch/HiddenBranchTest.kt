package com.companyb.companyapp.branch

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.audit.AuditLogService
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import io.javalin.Javalin
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.ClassRule
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The latency probe's branch is hidden: people never see it in branch lists, while the probe still reaches it. */
class HiddenBranchTest : BasePostgresTest() {
    private val globalViewer = TestFixtures.uuid()
    private val probeUser = TestFixtures.uuid()
    private val shown = TestFixtures.uuid()
    private val hidden = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(globalViewer, "hidden-global")
        IdentityFixtures.insertTestUser(probeUser, "hidden-probe")
        BranchWorkforceFixtures.insertTestBranch(shown, "Shown Branch")
        BranchWorkforceFixtures.insertTestBranch(hidden, "Hidden Probe Branch")
        transaction { exec("UPDATE branch SET hidden = true WHERE id = '$hidden'") }
        IdentityFixtures.grantCapability(
            userId = globalViewer,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = TestFixtures.uuid(),
        )
        IdentityFixtures.grantCapability(
            userId = probeUser,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = hidden,
            sourceId = TestFixtures.uuid(),
        )
    }

    companion object {
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
                    ctx.attribute("userId", ctx.header("X-Test-User") ?: TestFixtures.uuid().toString())
                }
                BranchRoutes.register(cfg)
            }
        }
    }

    private fun body(
        user: UUID,
        path: String,
    ): String =
        testServer.client
            .get(path) { it.header("X-Test-User", user.toString()) }
            .body
            .string()

    @Test
    fun `a global viewer's branch picker leaves out the hidden branch`() {
        val listed = body(globalViewer, "/api/branches/accessible")
        assertTrue(listed.contains("Shown Branch"), listed)
        assertFalse(listed.contains("Hidden Probe Branch"), listed)
    }

    // Behind GET /api/branches (Branches page), Relief branch options and the all-branches export.
    @Test
    fun `the all-branches lookup leaves out the hidden branch`() {
        assertFalse(BranchService.findAll().any { it.id == hidden })
    }

    @Test
    fun `a global viewer's audit leaves out the hidden branch's rows`() {
        val record = TestFixtures.uuid()
        for (branch in listOf(shown, hidden)) {
            transaction {
                AuditLogTable.insert {
                    it[AuditLogTable.recordId] = record
                    it[AuditLogTable.auditTableName] = "session"
                    it[AuditLogTable.action] = AuditAction.UPDATE
                    it[AuditLogTable.changedBy] = probeUser
                    it[AuditLogTable.branchId] = branch
                }
            }
        }
        assertEquals(
            listOf(shown),
            AuditLogService.findByTableAndRecord(globalViewer, "session", record).map { it.branchId },
        )
        assertEquals(
            listOf(hidden),
            AuditLogService.findByTableAndRecord(probeUser, "session", record).map { it.branchId },
        )
    }

    @Test
    fun `a member of the hidden branch still reaches it`() {
        assertTrue(body(probeUser, "/api/branches/accessible").contains("Hidden Probe Branch"))
    }
}
