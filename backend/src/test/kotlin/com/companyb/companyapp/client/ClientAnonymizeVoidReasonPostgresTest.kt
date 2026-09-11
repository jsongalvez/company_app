package com.companyb.companyapp.client

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.audit.AuditLogRoutes
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.audit.AuditValues
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.client.Gender
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.session.SessionService
import com.companyb.companyapp.session.SessionVoidTable
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import io.javalin.Javalin
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.ClassRule
import java.math.BigDecimal
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #909 — anonymize must scrub session void/unvoid operator free text, not just
 * the #908 session columns. Unique markers placed in `voidReason` /
 * `unvoidedReason` must vanish from live `session_void` rows, void audit
 * payloads, and the duplicated `audit_log.reason` column, while event counts
 * are retained (the #524 no-new-events precedent).
 */
class ClientAnonymizeVoidReasonPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val sessionId = TestFixtures.uuid()
    private val voidedOnlySessionId = TestFixtures.uuid()
    private val voidId = TestFixtures.uuid()
    private val voidedOnlyVoidId = TestFixtures.uuid()
    private var branchDayId: UUID = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "anon-void-caller")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Anon Void Branch $branchId")
        IdentityFixtures.grantCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = TestFixtures.uuid(),
        )
        IdentityFixtures.grantCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = TestFixtures.uuid(),
        )
        SessionClientFixtures.insertTestBaseRate(TestFixtures.uuid(), branchId, callerId)
        SessionClientFixtures.insertTestBaseRate(
            TestFixtures.uuid(),
            branchId,
            callerId,
            SessionType.SECOND_SESSION,
        )
        SessionClientFixtures.insertTestBaseRate(
            TestFixtures.uuid(),
            branchId,
            callerId,
            SessionType.SUBSEQUENT,
        )
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        BranchWorkforceFixtures.insertTestAttendance(branchDayId, callerId)
    }

    @Test
    fun `anonymize scrubs void reason live payload and reason column without new events`() {
        createClient()
        createCompletedSession(sessionId)
        SessionService.voidSession(callerId, sessionId, voidId, "client $VOID_MARKER called to cancel")
        SessionService.unvoidSession(callerId, sessionId, "rebook $UNVOID_MARKER after review")
        createCompletedSession(voidedOnlySessionId)
        SessionService.voidSession(callerId, voidedOnlySessionId, voidedOnlyVoidId, "duplicate $VOID_MARKER entry")
        insertOutsiderRow()

        assertTrue(voidPayloadText(voidId).contains(VOID_MARKER), "void marker must land in void audit")
        assertTrue(voidPayloadText(voidId).contains(UNVOID_MARKER), "unvoid marker must land in void audit")
        assertTrue(voidReasonColumns(voidId).any { it.contains(VOID_MARKER) }, "void marker must land in reason column")
        val voidAuditCountBefore = voidAuditRowCount(voidId) + voidAuditRowCount(voidedOnlyVoidId)

        ClientService.anonymize(callerId, clientId)

        assertLiveRowsScrubbed()
        assertVoidHistoryScrubbed(voidAuditCountBefore)
        assertOutsiderRowSurvives()
    }

    @Test
    fun `failed anonymize leaves void text and history intact`() {
        createClient()
        createCompletedSession(sessionId)
        SessionService.voidSession(callerId, sessionId, voidId, "client $VOID_MARKER called to cancel")
        createPendingSession()

        assertFailsWith<ConflictException> {
            ClientService.anonymize(callerId, clientId)
        }

        val liveVoid =
            transaction {
                SessionVoidTable.selectAll().where { SessionVoidTable.id eq voidId }.single()
            }
        assertTrue(
            liveVoid[SessionVoidTable.voidReason].contains(VOID_MARKER),
            "guard failure must not scrub the live void reason",
        )
        assertTrue(
            voidPayloadText(voidId).contains(VOID_MARKER),
            "guard failure must not rewrite void history",
        )
        assertTrue(
            voidReasonColumns(voidId).any { it.contains(VOID_MARKER) },
            "guard failure must not rewrite the reason column",
        )
    }

    private fun createClient() {
        ClientService.create(
            callerId = callerId,
            id = clientId,
            firstName = "ZqxVoid",
            lastName = "Client",
            middleName = null,
            suffix = null,
            phoneNumber = "09170000000",
            address = "Cavite",
            gender = Gender.F,
            age = RETAINED_AGE,
            systolicBp = null,
            diastolicBp = null,
            medicalConditions = null,
        )
    }

    private fun createCompletedSession(id: UUID) {
        SessionService.create(
            callerId = callerId,
            id = id,
            clientId = clientId,
            branchId = branchId,
            isWalkIn = false,
            requestedPractitionerId = null,
            finalPrice = BigDecimal("2500.00"),
            remarks = null,
            otherConcerns = null,
            nextAppointmentDate = null,
        )
        SessionService.updateStatus(callerId, id, SessionStatus.COMPLETED, expectedVersion = 1)
    }

    private fun createPendingSession() {
        SessionService.create(
            callerId = callerId,
            id = TestFixtures.uuid(),
            clientId = clientId,
            branchId = branchId,
            isWalkIn = false,
            requestedPractitionerId = null,
            finalPrice = BigDecimal("2500.00"),
            remarks = null,
            otherConcerns = null,
            nextAppointmentDate = null,
        )
    }

    private fun assertLiveRowsScrubbed() {
        val unvoided =
            transaction {
                SessionVoidTable.selectAll().where { SessionVoidTable.id eq voidId }.single()
            }
        assertEquals(
            AuditValues.REDACTED,
            unvoided[SessionVoidTable.voidReason],
            "live void reason must be scrubbed",
        )
        assertEquals(
            AuditValues.REDACTED,
            unvoided[SessionVoidTable.unvoidedReason],
            "live unvoid reason must be scrubbed",
        )
        val voidedOnly =
            transaction {
                SessionVoidTable.selectAll().where { SessionVoidTable.id eq voidedOnlyVoidId }.single()
            }
        assertEquals(
            AuditValues.REDACTED,
            voidedOnly[SessionVoidTable.voidReason],
            "live void-only reason must be scrubbed",
        )
        assertNull(
            voidedOnly[SessionVoidTable.unvoidedReason],
            "never-unvoided rows keep their null shape",
        )
    }

    private fun assertVoidHistoryScrubbed(voidAuditCountBefore: Int) {
        for (id in listOf(voidId, voidedOnlyVoidId)) {
            val payload = voidPayloadText(id)
            assertFalse(payload.contains(VOID_MARKER), "void marker leaked in void audit for $id")
            assertFalse(payload.contains(UNVOID_MARKER), "unvoid marker leaked in void audit for $id")
            assertFalse(
                voidReasonColumns(id).any { it.contains(VOID_MARKER) || it.contains(UNVOID_MARKER) },
                "reason column leaked for $id",
            )
        }
        assertEquals(
            voidAuditCountBefore,
            voidAuditRowCount(voidId) + voidAuditRowCount(voidedOnlyVoidId),
            "scrub rewrites rows, never adds events",
        )
        val (status, body) = auditHistoryOverHttp(SessionVoidTable.tableName, voidId)
        assertEquals(200, status)
        assertFalse(body.contains(VOID_MARKER), "HTTP audit path leaked void marker")
        assertFalse(body.contains(UNVOID_MARKER), "HTTP audit path leaked unvoid marker")
        assertTrue(body.contains(AuditValues.REDACTED), "HTTP audit path lost the redaction marker")
    }

    private fun assertOutsiderRowSurvives() {
        val outsiderPayload =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq OUTSIDER_TABLE) and
                            (AuditLogTable.recordId eq voidId)
                    }.map { it[AuditLogTable.oldValue].orEmpty() + it[AuditLogTable.newValue].orEmpty() }
                    .joinToString("")
            }
        assertTrue(
            outsiderPayload.contains(VOID_MARKER),
            "same-record rows of other tables must survive redaction untouched",
        )
    }

    private fun voidPayloadText(id: UUID): String =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq SessionVoidTable.tableName) and
                        (AuditLogTable.recordId eq id)
                }.joinToString("") { (it[AuditLogTable.oldValue].orEmpty() + it[AuditLogTable.newValue].orEmpty()) }
        }

    private fun voidReasonColumns(id: UUID): List<String> =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq SessionVoidTable.tableName) and
                        (AuditLogTable.recordId eq id)
                }.map { it[AuditLogTable.reason].orEmpty() }
        }

    private fun voidAuditRowCount(id: UUID): Int {
        val count =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq SessionVoidTable.tableName) and
                            (AuditLogTable.recordId eq id)
                    }.count()
            }
        return count.toInt()
    }

    private fun auditHistoryOverHttp(
        tableName: String,
        recordId: UUID,
    ): Pair<Int, String> {
        var status = 0
        var body = ""
        testServer.client.let { client ->
            val response =
                client.get(
                    "/api/audit-log?tableName=$tableName&recordId=$recordId",
                    Consumer { it.header("X-Test-User", callerId.toString()) },
                )
            status = response.code
            body = response.body.string()
        }
        return status to body
    }

    private fun insertOutsiderRow() {
        transaction {
            AuditLogTable.insert {
                it[AuditLogTable.auditTableName] = OUTSIDER_TABLE
                it[AuditLogTable.recordId] = voidId
                it[AuditLogTable.action] = AuditAction.UPDATE
                it[AuditLogTable.changedBy] = callerId
                it[AuditLogTable.oldValue] = "{\"id\":\"$voidId\",\"voidReason\":\"$VOID_MARKER\"}"
                it[AuditLogTable.newValue] = "{\"id\":\"$voidId\",\"unvoidedReason\":\"$UNVOID_MARKER\"}"
            }
        }
    }

    companion object {
        private const val VOID_MARKER = "Zqx909Void"
        private const val UNVOID_MARKER = "Zqx909Unvoid"
        private const val RETAINED_AGE = 42
        private const val OUTSIDER_TABLE = "branch"

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
                AuditLogRoutes.register(cfg)
            }
        }
    }
}
