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
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.session.SessionPractitionerService
import com.companyb.companyapp.session.SessionPractitionerTable
import com.companyb.companyapp.session.SessionService
import com.companyb.companyapp.session.SessionTable
import com.companyb.companyapp.session.dashboard.DashboardService
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #908 — anonymize scrubs operator-entered session free text, not just the
 * client row. Unique markers placed in `session.remarks`/`otherConcerns` and
 * `session_practitioner.remarks` must vanish from live rows, session audit
 * payloads, and the dashboard/bearer/HTTP read paths, while event counts and
 * the anonymization marker are retained (the #524 no-new-events precedent).
 */
class ClientAnonymizeSessionTextPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val practitionerId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val sessionId = TestFixtures.uuid()
    private val practitionerRowId = TestFixtures.uuid()
    private var branchDayId: UUID = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "anon-session-caller")
        IdentityFixtures.insertTestUser(practitionerId, "anon-session-practitioner")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Anon Session Branch $branchId")
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
        BranchWorkforceFixtures.insertTestAssignment(
            userId = practitionerId,
            branchId = branchId,
            slot = 1,
            assignedBy = callerId,
        )
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        BranchWorkforceFixtures.insertTestAttendance(branchDayId, callerId)
    }

    @Test
    fun `anonymize scrubs session free text live and in audit without new events`() {
        createClient()
        createCompletedMarkedSession()
        insertOutsiderRow()

        val sessionPayloadBefore = sessionAuditPayloadText()
        assertTrue(
            sessionPayloadBefore.contains(PHONE_MARKER),
            "phone marker must land in session audit",
        )
        assertTrue(
            sessionPayloadBefore.contains(NAME_MARKER),
            "name marker must land in session audit",
        )
        val practitionerPayloadBefore = practitionerAuditPayloadText()
        assertTrue(
            practitionerPayloadBefore.contains(PHONE_MARKER),
            "phone marker must land in practitioner audit",
        )
        val sessionAuditCountBefore = sessionAuditRowCount()
        val practitionerAuditCountBefore = practitionerAuditRowCount()

        ClientService.anonymize(callerId, clientId)

        assertLiveRowsScrubbed()
        assertAuditPayloadsScrubbed(sessionAuditCountBefore, practitionerAuditCountBefore)
        assertAuditIdentityPreserved()
        assertOutsiderRowSurvives()
        assertClientAnonymized()
        assertDashboardServesScrubbed()
        assertBearerDetailServesScrubbed()
        assertAuditHttpServesScrubbed()
    }

    @Test
    fun `anonymize scrubs removed practitioner history`() {
        createClient()
        SessionService.create(
            callerId = callerId,
            id = sessionId,
            clientId = clientId,
            branchId = branchId,
            isWalkIn = false,
            requestedPractitionerId = null,
            finalPrice = BigDecimal("2500.00"),
            remarks = "call $PHONE_MARKER after lunch",
            otherConcerns = null,
            nextAppointmentDate = null,
        )
        SessionPractitionerService.addPractitioner(
            callerId = callerId,
            id = practitionerRowId,
            sessionId = sessionId,
            practitionerId = practitionerId,
            remarks = "practitioner note $PHONE_MARKER",
        )
        SessionPractitionerService.removePractitioner(callerId, sessionId, practitionerId)
        SessionService.updateStatus(callerId, sessionId, SessionStatus.COMPLETED, expectedVersion = 3)

        val payloadBefore = practitionerAuditPayloadText()
        assertTrue(payloadBefore.contains(PHONE_MARKER), "removed practitioner text must be in history")
        val practitionerAuditCountBefore = practitionerAuditRowCount()

        ClientService.anonymize(callerId, clientId)

        val payloadAfter = practitionerAuditPayloadText()
        assertFalse(payloadAfter.contains(PHONE_MARKER), "removed practitioner text leaked: $payloadAfter")
        assertEquals(
            practitionerAuditCountBefore,
            practitionerAuditRowCount(),
            "scrub rewrites rows, never adds events",
        )
    }

    @Test
    fun `failed anonymize leaves session text and history intact`() {
        createClient()
        SessionService.create(
            callerId = callerId,
            id = sessionId,
            clientId = clientId,
            branchId = branchId,
            isWalkIn = false,
            requestedPractitionerId = null,
            finalPrice = BigDecimal("2500.00"),
            remarks = "call $PHONE_MARKER after lunch",
            otherConcerns = "follow up with $NAME_MARKER",
            nextAppointmentDate = null,
        )

        assertFailsWith<ConflictException> {
            ClientService.anonymize(callerId, clientId)
        }

        val liveSession =
            transaction {
                SessionTable.selectAll().where { SessionTable.id eq sessionId }.single()
            }
        assertTrue(
            liveSession[SessionTable.remarks].orEmpty().contains(PHONE_MARKER),
            "guard failure must not scrub live session text",
        )
        assertTrue(
            sessionAuditPayloadText().contains(PHONE_MARKER),
            "guard failure must not rewrite session history",
        )
    }

    private fun createClient() {
        ClientService.create(
            callerId = callerId,
            id = clientId,
            firstName = "ZqxSession",
            lastName = "Client",
            middleName = null,
            suffix = null,
            phoneNumber = PHONE_MARKER,
            address = "Cavite",
            gender = Gender.F,
            age = RETAINED_AGE,
            systolicBp = null,
            diastolicBp = null,
            medicalConditions = null,
        )
    }

    private fun createCompletedMarkedSession() {
        SessionService.create(
            callerId = callerId,
            id = sessionId,
            clientId = clientId,
            branchId = branchId,
            isWalkIn = false,
            requestedPractitionerId = null,
            finalPrice = BigDecimal("2500.00"),
            remarks = "call $PHONE_MARKER after lunch",
            otherConcerns = "follow up with $NAME_MARKER",
            nextAppointmentDate = null,
        )
        SessionPractitionerService.addPractitioner(
            callerId = callerId,
            id = practitionerRowId,
            sessionId = sessionId,
            practitionerId = practitionerId,
            remarks = "practitioner note $PHONE_MARKER",
        )
        SessionService.updateStatus(callerId, sessionId, SessionStatus.COMPLETED, expectedVersion = 2)
    }

    private fun assertLiveRowsScrubbed() {
        val liveSession =
            transaction {
                SessionTable.selectAll().where { SessionTable.id eq sessionId }.single()
            }
        assertNull(liveSession[SessionTable.remarks], "session remarks must be scrubbed live")
        assertNull(liveSession[SessionTable.otherConcerns], "session otherConcerns must be scrubbed live")
        val livePractitioner =
            transaction {
                SessionPractitionerTable.selectAll().where { SessionPractitionerTable.id eq practitionerRowId }.single()
            }
        assertNull(livePractitioner[SessionPractitionerTable.remarks], "practitioner remarks must be scrubbed live")
    }

    private fun assertAuditPayloadsScrubbed(
        sessionAuditCountBefore: Int,
        practitionerAuditCountBefore: Int,
    ) {
        val sessionPayloadAfter = sessionAuditPayloadText()
        assertFalse(
            sessionPayloadAfter.contains(PHONE_MARKER),
            "phone marker leaked in session audit",
        )
        assertFalse(
            sessionPayloadAfter.contains(NAME_MARKER),
            "name marker leaked in session audit",
        )
        val practitionerPayloadAfter = practitionerAuditPayloadText()
        assertFalse(
            practitionerPayloadAfter.contains(PHONE_MARKER),
            "phone marker leaked in practitioner audit",
        )
        assertEquals(
            sessionAuditCountBefore,
            sessionAuditRowCount(),
            "scrub rewrites rows, never adds events",
        )
        assertEquals(
            practitionerAuditCountBefore,
            practitionerAuditRowCount(),
            "scrub rewrites rows, never adds events",
        )
    }

    private fun assertAuditIdentityPreserved() {
        val identities =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq SessionTable.tableName) and
                            (AuditLogTable.recordId eq sessionId)
                    }.map { it[AuditLogTable.action] to it[AuditLogTable.changedBy] }
            }
        assertEquals(
            setOf(AuditAction.INSERT, AuditAction.UPDATE),
            identities.map { it.first }.toSet(),
            "scrub must preserve event actions",
        )
        assertTrue(identities.all { it.second == callerId }, "scrub must preserve event actors")
    }

    private fun assertOutsiderRowSurvives() {
        val outsiderPayload =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq OUTSIDER_TABLE) and
                            (AuditLogTable.recordId eq sessionId)
                    }.map { it[AuditLogTable.oldValue].orEmpty() + it[AuditLogTable.newValue].orEmpty() }
                    .joinToString("")
            }
        assertTrue(
            outsiderPayload.contains(PHONE_MARKER) && outsiderPayload.contains(NAME_MARKER),
            "same-record rows of other tables must survive redaction untouched",
        )
    }

    private fun assertClientAnonymized() {
        val persisted =
            transaction {
                ClientTable.selectAll().where { ClientTable.id eq clientId }.single()
            }
        assertNull(persisted[ClientTable.phoneNumber])
        assertNotNull(persisted[ClientTable.deletedAt])
    }

    private fun assertDashboardServesScrubbed() {
        val dashboard = DashboardService.getToday(callerId, branchId)
        val listed = dashboard.sessions.single { it.id == sessionId }
        assertNull(listed.remarks, "dashboard list leaked session remarks")
        assertNull(listed.otherConcerns, "dashboard list leaked session concerns")
        assertTrue(
            dashboard.practitioners.none { it.remarks?.contains(PHONE_MARKER) == true },
            "dashboard list leaked practitioner remarks",
        )
    }

    private fun assertBearerDetailServesScrubbed() {
        SessionClientFixtures.insertTestNotification(sessionId = sessionId, userId = callerId, branchId = branchId)
        val detail = DashboardService.getSessionDetail(callerId, sessionId)
        assertNull(detail.session.remarks, "bearer detail leaked session remarks")
        assertNull(detail.session.otherConcerns, "bearer detail leaked session concerns")
    }

    private fun assertAuditHttpServesScrubbed() {
        val (sessionStatus, sessionBody) = auditHistoryOverHttp(SessionTable.tableName, sessionId)
        assertEquals(200, sessionStatus)
        assertFalse(sessionBody.contains(PHONE_MARKER), "HTTP audit path leaked phone marker")
        assertFalse(sessionBody.contains(NAME_MARKER), "HTTP audit path leaked name marker")
        assertTrue(sessionBody.contains(AuditValues.REDACTED), "HTTP audit path lost the redaction marker")

        val (practitionerStatus, practitionerBody) =
            auditHistoryOverHttp(SessionPractitionerTable.tableName, practitionerRowId)
        assertEquals(200, practitionerStatus)
        assertFalse(practitionerBody.contains(PHONE_MARKER), "HTTP practitioner audit leaked phone marker")
        assertTrue(practitionerBody.contains(AuditValues.REDACTED), "HTTP practitioner audit lost the marker")
    }

    private fun sessionAuditPayloadText(): String =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq SessionTable.tableName) and
                        (AuditLogTable.recordId eq sessionId)
                }.joinToString("") { (it[AuditLogTable.oldValue].orEmpty() + it[AuditLogTable.newValue].orEmpty()) }
        }

    private fun practitionerAuditPayloadText(): String =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq SessionPractitionerTable.tableName) and
                        (AuditLogTable.recordId eq practitionerRowId)
                }.joinToString("") { (it[AuditLogTable.oldValue].orEmpty() + it[AuditLogTable.newValue].orEmpty()) }
        }

    private fun sessionAuditRowCount(): Int {
        val count =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq SessionTable.tableName) and
                            (AuditLogTable.recordId eq sessionId)
                    }.count()
            }
        return count.toInt()
    }

    private fun practitionerAuditRowCount(): Int {
        val count =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq SessionPractitionerTable.tableName) and
                            (AuditLogTable.recordId eq practitionerRowId)
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
                it[AuditLogTable.recordId] = sessionId
                it[AuditLogTable.action] = AuditAction.UPDATE
                it[AuditLogTable.changedBy] = callerId
                it[AuditLogTable.oldValue] = "{\"id\":\"$sessionId\",\"remarks\":\"call $PHONE_MARKER\"}"
                it[AuditLogTable.newValue] = "{\"id\":\"$sessionId\",\"otherConcerns\":\"$NAME_MARKER\"}"
            }
        }
    }

    companion object {
        private const val PHONE_MARKER = "Zqx908Phone"
        private const val NAME_MARKER = "Zqx908Name"
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
