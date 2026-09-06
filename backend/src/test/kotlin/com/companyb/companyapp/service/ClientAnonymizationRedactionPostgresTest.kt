package com.companyb.companyapp.service

import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.AuditLogRoutes
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.audit.AuditValues
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import io.javalin.Javalin
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.ClassRule
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #524 — anonymization redacts identifying names from retained client audit
 * payloads instead of letting history resurrect them. Unique markers prove no
 * recoverable name survives in any row or on the authorized HTTP read path,
 * while event count/actor/action and the anonymization marker are retained.
 */
class ClientAnonymizationRedactionPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private var branchDayId: UUID = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "anon-redact-caller")
        DatabaseTestHelper.insertTestBranch(branchId, "Anon Redact Branch $branchId")
        DatabaseTestHelper.grantCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = TestFixtures.uuid(),
        )
        branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
    }

    @Test
    fun `anonymize redacts every retained payload and keeps event identity`() {
        createClient(firstName = MARKER_FIRST_A, lastName = MARKER_LAST_A)
        renameClient(firstName = MARKER_FIRST_B)
        ClientService.anonymize(callerId, clientId)

        val entries = auditEntries()
        assertEquals(3, entries.size, "create + rename + anonymize, no rows added or removed")
        assertEquals(
            listOf(AuditAction.INSERT, AuditAction.UPDATE, AuditAction.UPDATE),
            entries.map { it.action }.sorted(),
        )
        assertTrue(entries.all { it.changedBy == callerId }, "actor preserved on every row")
        val payloadText = entries.joinToString("") { (it.oldValue.orEmpty() + it.newValue.orEmpty()) }
        assertFalse(payloadText.contains(MARKER_FIRST_A), "original first name leaked: $payloadText")
        assertFalse(payloadText.contains(MARKER_LAST_A), "original last name leaked: $payloadText")
        assertFalse(payloadText.contains(MARKER_FIRST_B), "renamed first name leaked: $payloadText")

        val anonymizeEvent = entries.single { it.reason == ANONYMIZED_REASON }
        val anonOld = assertNotNull(anonymizeEvent.oldValue, "anonymize event must carry a before-image")
        val anonNew = assertNotNull(anonymizeEvent.newValue, "anonymize event must carry an after-image")
        assertEquals(AuditValues.REDACTED, DatabaseTestHelper.extractJsonField(anonOld, "firstName"))
        assertEquals(AuditValues.REDACTED, DatabaseTestHelper.extractJsonField(anonOld, "lastName"))
        assertEquals(AuditValues.NULL, DatabaseTestHelper.extractJsonField(anonNew, "firstName"))

        val persisted = persistedClient()
        assertNull(persisted.firstName)
        assertNull(persisted.lastName)
        assertNotNull(persisted.deletedAt)
        assertEquals(Gender.F, persisted.gender)
        assertEquals(RETAINED_AGE, persisted.age)

        val (status, body) = auditHistoryOverHttp()
        assertEquals(200, status)
        assertFalse(body.contains(MARKER_FIRST_A), "HTTP read path leaked original first name")
        assertFalse(body.contains(MARKER_LAST_A), "HTTP read path leaked original last name")
        assertFalse(body.contains(MARKER_FIRST_B), "HTTP read path leaked renamed first name")
        assertTrue(body.contains(ANONYMIZED_REASON), "HTTP read path lost the anonymization marker")
    }

    @Test
    fun `failed anonymize leaves history and PII intact`() {
        createClient(firstName = MARKER_FIRST_A, lastName = MARKER_LAST_A)
        DatabaseTestHelper.insertTestSession(
            id = TestFixtures.uuid(),
            clientId = clientId,
            branchDayId = branchDayId,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.PENDING,
        )

        assertFailsWith<ConflictException> {
            ClientService.anonymize(callerId, clientId)
        }

        val entries = auditEntries()
        assertEquals(1, entries.size, "guard failure writes nothing")
        assertTrue(
            entries
                .single()
                .newValue
                .orEmpty()
                .contains(MARKER_FIRST_A),
            "history rolled back with PII",
        )
        val persisted = persistedClient()
        assertEquals(MARKER_FIRST_A, persisted.firstName)
        assertEquals(MARKER_LAST_A, persisted.lastName)
        assertNull(persisted.deletedAt)
    }

    @Test
    fun `legacy anonymized payloads are redacted without new events`() {
        createClient(firstName = LEGACY_FIRST, lastName = LEGACY_LAST)
        transaction {
            ClientTable.update({ ClientTable.id eq clientId }) {
                it[ClientTable.deletedAt] = CurrentTimestampWithTimeZone
                it[ClientTable.firstName] = null
                it[ClientTable.lastName] = null
                it[ClientTable.phoneNumber] = null
                it[ClientTable.address] = null
                it[ClientTable.medicalConditions] = null
                it[ClientTable.systolicBp] = null
                it[ClientTable.diastolicBp] = null
            }
        }

        val redacted =
            transaction {
                AuditLog.redactClientNamesInTransaction(clientId)
            }

        assertTrue(redacted >= 1, "legacy identifying payloads must be rewritten")
        assertEquals(1, auditEntries().size, "redaction rewrites rows, never adds events")
        val payloadText = auditEntries().joinToString("") { it.oldValue.orEmpty() + it.newValue.orEmpty() }
        assertFalse(payloadText.contains(LEGACY_FIRST), "legacy first name survived: $payloadText")
        assertFalse(payloadText.contains(LEGACY_LAST), "legacy last name survived: $payloadText")
    }

    @Test
    fun `redaction keeps uniform nulls and reasons`() {
        createClient(firstName = LEGACY_FIRST, lastName = LEGACY_LAST)
        transaction {
            AuditLogTable.insert {
                it[AuditLogTable.auditTableName] = ClientTable.tableName
                it[AuditLogTable.recordId] = clientId
                it[AuditLogTable.action] = AuditAction.UPDATE
                it[AuditLogTable.changedBy] = callerId
                it[AuditLogTable.oldValue] =
                    "{\"id\":\"$clientId\",\"firstName\":\"$LEGACY_FIRST\",\"lastName\":\"$LEGACY_LAST\"}"
                it[AuditLogTable.newValue] =
                    "{\"id\":\"$clientId\",\"firstName\":\"${AuditValues.NULL}\"," +
                    "\"lastName\":\"${AuditValues.NULL}\"}"
                it[AuditLogTable.reason] = ANONYMIZED_REASON
            }
        }

        transaction {
            AuditLog.redactClientNamesInTransaction(clientId)
        }

        val anonymizeRow = auditEntries().single { it.reason == ANONYMIZED_REASON }
        val legacyOld = assertNotNull(anonymizeRow.oldValue, "legacy before-image must survive redaction")
        val legacyNew = assertNotNull(anonymizeRow.newValue, "legacy after-image must survive redaction")
        assertEquals(AuditValues.REDACTED, DatabaseTestHelper.extractJsonField(legacyOld, "firstName"))
        assertEquals(AuditValues.NULL, DatabaseTestHelper.extractJsonField(legacyNew, "lastName"))
    }

    /**
     * #548 — the V6 backfill file is retired (fresh databases hold no legacy
     * rows), so this pins the same contract against the live redaction path
     * instead of a migration filename: only client-table name keys become the
     * shared [AuditValues.REDACTED] marker, same-record rows of other tables
     * survive untouched, and no row is ever added or removed.
     */
    @Test
    fun `live redaction scopes to client name keys and never deletes`() {
        createClient(firstName = LEGACY_FIRST, lastName = LEGACY_LAST)
        transaction {
            AuditLogTable.insert {
                it[AuditLogTable.auditTableName] = "branch"
                it[AuditLogTable.recordId] = clientId
                it[AuditLogTable.action] = AuditAction.UPDATE
                it[AuditLogTable.changedBy] = callerId
                it[AuditLogTable.oldValue] =
                    "{\"id\":\"$clientId\",\"firstName\":\"$LEGACY_FIRST\",\"lastName\":\"$LEGACY_LAST\"}"
                it[AuditLogTable.newValue] =
                    "{\"id\":\"$clientId\",\"firstName\":\"$LEGACY_FIRST\",\"lastName\":\"$LEGACY_LAST\"}"
            }
        }
        val idsBefore = auditRowIds()
        val rowsBefore = idsBefore.size

        transaction {
            AuditLog.redactClientNamesInTransaction(clientId)
        }

        assertEquals(idsBefore, auditRowIds(), "redaction rewrites rows in place — same rows, no adds or deletes")
        assertEquals(rowsBefore, auditRowCount(), "redaction never adds or removes events")
        val clientPayloads = auditEntries().joinToString("") { it.oldValue.orEmpty() + it.newValue.orEmpty() }
        assertFalse(clientPayloads.contains(LEGACY_FIRST), "client first name survived: $clientPayloads")
        assertFalse(clientPayloads.contains(LEGACY_LAST), "client last name survived: $clientPayloads")
        assertTrue(clientPayloads.contains(AuditValues.REDACTED), "redaction marker must match AuditValues")
        val outsiderPayloads =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq "branch") and
                            (AuditLogTable.recordId eq clientId)
                    }.map { (it[AuditLogTable.oldValue].orEmpty() + it[AuditLogTable.newValue].orEmpty()) }
                    .joinToString("")
            }
        assertTrue(
            outsiderPayloads.contains(LEGACY_FIRST) && outsiderPayloads.contains(LEGACY_LAST),
            "same-record rows of other tables must survive redaction untouched",
        )
    }

    @Test
    fun `concurrent update and anonymize never leak names or resurrect PII`() {
        createClient(firstName = MARKER_FIRST_A, lastName = MARKER_LAST_A)
        val gate = CountDownLatch(1)
        var updateFailure: Throwable? = null
        var anonymizeFailure: Throwable? = null
        val updater =
            thread {
                gate.await()
                updateFailure = runCatching { renameClient(firstName = MARKER_FIRST_B) }.exceptionOrNull()
            }
        val anonymizer =
            thread {
                gate.await()
                anonymizeFailure = runCatching { ClientService.anonymize(callerId, clientId) }.exceptionOrNull()
            }
        gate.countDown()
        updater.join(JOIN_MILLIS)
        anonymizer.join(JOIN_MILLIS)

        assertEquals(null, anonymizeFailure, "anonymize failed: $anonymizeFailure")
        assertTrue(
            updateFailure == null || updateFailure is NotFoundException,
            "loser must 404, got $updateFailure",
        )
        val persisted = persistedClient()
        assertNull(persisted.firstName)
        assertNull(persisted.lastName)
        val payloadText = auditEntries().joinToString("") { it.oldValue.orEmpty() + it.newValue.orEmpty() }
        assertFalse(payloadText.contains(MARKER_FIRST_A), "race leaked original first name: $payloadText")
        assertFalse(payloadText.contains(MARKER_LAST_A), "race leaked original last name: $payloadText")
        assertFalse(payloadText.contains(MARKER_FIRST_B), "race leaked renamed first name: $payloadText")
        assertTrue(auditEntries().size in 2..3, "create + anonymize, plus the winner's update if it landed")
    }

    private fun createClient(
        firstName: String,
        lastName: String,
    ) {
        ClientService.create(
            callerId = callerId,
            id = clientId,
            firstName = firstName,
            lastName = lastName,
            middleName = null,
            suffix = null,
            phoneNumber = null,
            address = "Cavite",
            gender = Gender.F,
            age = RETAINED_AGE,
            systolicBp = null,
            diastolicBp = null,
            medicalConditions = null,
        )
    }

    private fun renameClient(firstName: String) {
        ClientService.update(
            callerId = callerId,
            clientId = clientId,
            firstName = firstName,
            lastName = null,
            middleName = null,
            suffix = null,
            phoneNumber = null,
            address = null,
            gender = null,
            age = null,
            systolicBp = null,
            diastolicBp = null,
            medicalConditions = null,
        )
    }

    private data class AuditRow(
        val action: AuditAction,
        val changedBy: UUID,
        val reason: String?,
        val oldValue: String?,
        val newValue: String?,
    )

    private fun auditEntries(): List<AuditRow> =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq ClientTable.tableName) and
                        (AuditLogTable.recordId eq clientId)
                }.orderBy(AuditLogTable.changedAt to SortOrder.ASC)
                .map {
                    AuditRow(
                        action = it[AuditLogTable.action],
                        changedBy = it[AuditLogTable.changedBy],
                        reason = it[AuditLogTable.reason],
                        oldValue = it[AuditLogTable.oldValue],
                        newValue = it[AuditLogTable.newValue],
                    )
                }
        }

    private data class PersistedClient(
        val firstName: String?,
        val lastName: String?,
        val deletedAt: OffsetDateTime?,
        val gender: Gender,
        val age: Int,
    )

    private fun auditRowCount(): Int =
        transaction {
            AuditLogTable.selectAll().count().toInt()
        }

    private fun auditRowIds(): Set<UUID> =
        transaction {
            AuditLogTable.selectAll().map { it[AuditLogTable.id] }.toSet()
        }

    private fun persistedClient(): PersistedClient =
        transaction {
            ClientTable
                .selectAll()
                .where { ClientTable.id eq clientId }
                .single()
                .let {
                    PersistedClient(
                        firstName = it[ClientTable.firstName],
                        lastName = it[ClientTable.lastName],
                        deletedAt = it[ClientTable.deletedAt],
                        gender = Gender.valueOf(it[ClientTable.gender]),
                        age = it[ClientTable.age],
                    )
                }
        }

    private fun auditHistoryOverHttp(): Pair<Int, String> {
        var status = 0
        var body = ""
        testServer.client.let { client ->
            val response =
                client.get(
                    "/api/audit-log?tableName=client&recordId=$clientId",
                    Consumer { it.header("X-Test-User", callerId.toString()) },
                )
            status = response.code
            body = response.body.string()
        }
        return status to body
    }

    companion object {
        private const val MARKER_FIRST_A = "ZqxAnonFirstA"
        private const val MARKER_LAST_A = "ZqxAnonLastA"
        private const val MARKER_FIRST_B = "ZqxAnonFirstB"
        private const val LEGACY_FIRST = "ZqxLegacyFirst"
        private const val LEGACY_LAST = "ZqxLegacyLast"
        private const val ANONYMIZED_REASON = "anonymized"
        private const val RETAINED_AGE = 42
        private const val JOIN_MILLIS = 30000L

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
                    ctx.attribute("userId", ctx.header("X-Test-User") ?: TestFixtures.uuid().toString())
                }
                AuditLogRoutes.register(cfg)
            }
        }
    }
}
