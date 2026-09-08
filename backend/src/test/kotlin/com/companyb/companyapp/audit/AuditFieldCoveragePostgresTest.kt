package com.companyb.companyapp.audit

import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.audit.AuditValues
import com.companyb.companyapp.client.ClientService
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.client.ClientPatchField
import com.companyb.companyapp.contracts.client.Gender
import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.finance.ExpenseService
import com.companyb.companyapp.finance.ExpenseTable
import com.companyb.companyapp.session.SessionBaseRateTable
import com.companyb.companyapp.session.SessionService
import com.companyb.companyapp.session.SessionTable
import com.companyb.companyapp.session.SessionVoidTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import com.companyb.companyapp.workforce.UserBranchAssignmentCreateParams
import com.companyb.companyapp.workforce.UserBranchAssignmentRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * #525 — audit field coverage and real-null representation. Each affected public
 * mutation pins one meaningful changed field (not merely row existence);
 * literal "null" text stays distinct from JSON null; historical string payloads
 * stay readable.
 */
class AuditFieldCoveragePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val practitionerId = TestFixtures.uuid()
    private val sessionId = TestFixtures.uuid()
    private val rateId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "audit-coverage-caller")
        IdentityFixtures.insertTestUser(practitionerId, "audit-coverage-practitioner")
        BranchWorkforceFixtures.insertTestBranch(branchId)
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())
        IdentityFixtures.grantVoidSession(callerId, TestFixtures.uuid())
        IdentityFixtures.grantCapability(
            userId = callerId,
            capabilityCode = com.companyb.companyapp.contracts.authorization.CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = com.companyb.companyapp.contracts.authorization.CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = TestFixtures.uuid(),
        )
        IdentityFixtures.grantCapability(
            userId = callerId,
            capabilityCode = com.companyb.companyapp.contracts.authorization.CapabilityCodes.VOID_SESSION,
            contextType = com.companyb.companyapp.contracts.authorization.CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = TestFixtures.uuid(),
        )
        transaction {
            UserBranchAssignmentRepository.createInTransaction(
                UserBranchAssignmentCreateParams(
                    id = TestFixtures.uuid(),
                    userId = practitionerId,
                    branchId = branchId,
                    slot = 1,
                    assignedBy = callerId,
                ),
            )
        }
        insertSessionBaseRate()
    }

    private fun insertSessionBaseRate(
        id: UUID = rateId,
        branchId: UUID = this.branchId,
        sessionType: SessionType = SessionType.REGULAR,
    ) {
        transaction {
            SessionBaseRateTable.insert {
                it[SessionBaseRateTable.id] = id
                it[SessionBaseRateTable.setBy] = callerId
                it[SessionBaseRateTable.branchId] = branchId
                it[SessionBaseRateTable.sessionType] = sessionType
                it[SessionBaseRateTable.rate] = BigDecimal("2500.00")
                it[SessionBaseRateTable.effectiveFrom] = TestFixtures.now.minusDays(1)
                it[SessionBaseRateTable.effectiveUntil] = TestFixtures.now.plusDays(365)
            }
        }
    }

    @Test
    fun `phone-only edit records a phone diff instead of an empty diff`() {
        createClient(phoneNumber = "09170000001")
        ClientService.update(
            callerId = callerId,
            clientId = clientId,
            firstName = null,
            lastName = null,
            middleName = null,
            suffix = null,
            phoneNumber = "09179999999",
            address = null,
            gender = null,
            age = null,
            systolicBp = null,
            diastolicBp = null,
            medicalConditions = null,
        )

        val update = latestUpdate(ClientTable.tableName, clientId)
        val oldPhone = Json.parseToJsonElement(update.oldValue.orEmpty()).jsonObject["phoneNumber"]
        val newPhone = Json.parseToJsonElement(update.newValue.orEmpty()).jsonObject["phoneNumber"]
        assertEquals("09170000001", oldPhone?.toString()?.trim('"'))
        assertEquals("09179999999", newPhone?.toString()?.trim('"'))
    }

    @Test
    fun `demographic edit records gender and age`() {
        createClient(gender = Gender.F, age = 30)
        ClientService.update(
            callerId = callerId,
            clientId = clientId,
            firstName = null,
            lastName = null,
            middleName = null,
            suffix = null,
            phoneNumber = null,
            address = null,
            gender = Gender.M,
            age = 31,
            systolicBp = null,
            diastolicBp = null,
            medicalConditions = null,
        )

        val update = latestUpdate(ClientTable.tableName, clientId)
        val oldJson = Json.parseToJsonElement(update.oldValue.orEmpty()).jsonObject
        val newJson = Json.parseToJsonElement(update.newValue.orEmpty()).jsonObject
        assertEquals("F", oldJson["gender"]?.toString()?.trim('"'))
        assertEquals("M", newJson["gender"]?.toString()?.trim('"'))
        assertEquals("30", oldJson["age"]?.toString()?.trim('"'))
        assertEquals("31", newJson["age"]?.toString()?.trim('"'))
    }

    @Test
    fun `cleared phone values never land in payloads`() {
        createClient(phoneNumber = "09171234567", middleName = "Reyes")
        ClientService.update(
            callerId = callerId,
            clientId = clientId,
            firstName = null,
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
            clearFields = setOf(ClientPatchField.PHONE_NUMBER),
        )

        val entries = clientEntries()
        val clear = entries.first { it.reason == "cleared: phoneNumber" }
        assertTrue(entries.any { it.reason == "cleared: phoneNumber" })
        // #525 — the clear event carries markers, never removed values. Earlier
        // SET/CREATE payloads retain history until anonymize redacts it.
        val clearText = clear.oldValue.orEmpty() + clear.newValue.orEmpty()
        assertFalse(clearText.contains("09171234567"), "cleared PII leaked in clear event: $clearText")
        assertTrue(
            clear.oldValue.orEmpty().contains(AuditValues.REDACTED),
            "cleared old value must be the redaction marker: ${clear.oldValue}",
        )
    }

    @Test
    fun `anonymize redacts extended PII keys and keeps demographics out of the diff`() {
        val id = TestFixtures.uuid()
        ClientService.create(
            callerId = callerId,
            id = id,
            firstName = "ZqxCovFirst",
            lastName = "ZqxCovLast",
            middleName = "ZqxCovMid",
            suffix = "Jr",
            phoneNumber = "09170001111",
            address = "ZqxCovAddr",
            gender = Gender.F,
            age = 42,
            systolicBp = 120.toShort(),
            diastolicBp = 80.toShort(),
            medicalConditions = "ZqxCovCond",
        )
        ClientService.anonymize(callerId, id)

        val entries =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq ClientTable.tableName) and
                            (AuditLogTable.recordId eq id)
                    }.orderBy(AuditLogTable.changedAt to SortOrder.ASC)
                    .map {
                        Triple(it[AuditLogTable.action], it[AuditLogTable.oldValue], it[AuditLogTable.newValue])
                    }
            }
        val payloadText = entries.joinToString("") { (it.second.orEmpty() + it.third.orEmpty()) }
        assertFalse(payloadText.contains("ZqxCovFirst"))
        assertFalse(payloadText.contains("ZqxCovLast"))
        assertFalse(payloadText.contains("ZqxCovMid"))
        assertFalse(payloadText.contains("09170001111"))
        assertFalse(payloadText.contains("ZqxCovAddr"))
        assertFalse(payloadText.contains("ZqxCovCond"))
        assertFalse(payloadText.contains("120"), "BP value leaked: $payloadText")
        assertTrue(payloadText.contains(AuditValues.REDACTED))
    }

    @Test
    fun `literal null text stays distinct from real null`() {
        assertEquals("""{"notes":"null"}""", AuditLog.jsonFields(mapOf("notes" to "null")))
        assertEquals("""{"notes":null}""", AuditLog.jsonFields(mapOf("notes" to null)))

        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val expenseId = TestFixtures.uuid()
        ExpenseService.create(
            callerId = callerId,
            id = expenseId,
            branchDayId = branchDayId,
            amount = BigDecimal("100.00"),
            category = ExpenseCategory.MISCELLANEOUS,
            notes = null,
        )
        val created = ExpenseService.findByBranchDayId(branchDayId).single { it.id == expenseId }
        ExpenseService.update(
            callerId = callerId,
            expenseId = expenseId,
            amount = created.amount,
            category = created.category,
            notes = "null",
            expectedVersion = created.version,
        )

        val update = latestUpdate(ExpenseTable.tableName, expenseId)
        val oldNotes = Json.parseToJsonElement(update.oldValue.orEmpty()).jsonObject["notes"]
        val newNotes = Json.parseToJsonElement(update.newValue.orEmpty()).jsonObject["notes"]
        assertTrue(oldNotes is JsonNull, "real null must encode as JSON null, got $oldNotes")
        assertEquals("null", newNotes?.toString()?.trim('"'))
    }

    @Test
    fun `session creation audit carries practitioner booking and appointment context`() {
        SessionClientFixtures.insertTestClient(clientId)
        val nextDate = TestFixtures.today.plusDays(5)
        val result =
            SessionService.create(
                callerId = callerId,
                id = sessionId,
                clientId = clientId,
                branchId = branchId,
                isWalkIn = false,
                requestedPractitionerId = practitionerId,
                finalPrice = BigDecimal("2500.00"),
                remarks = "CovRemark",
                otherConcerns = "CovConcern",
                nextAppointmentDate = nextDate,
            )
        assertTrue(result.created)

        val insert =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq SessionTable.tableName) and
                            (AuditLogTable.recordId eq sessionId) and
                            (AuditLogTable.action eq com.companyb.companyapp.contracts.audit.AuditAction.INSERT)
                    }.single()
            }
        val payload = Json.parseToJsonElement(insert[AuditLogTable.newValue].orEmpty()).jsonObject
        assertEquals(practitionerId.toString(), payload["requestedPractitionerId"]?.toString()?.trim('"'))
        assertEquals("CovRemark", payload["remarks"]?.toString()?.trim('"'))
        assertEquals("CovConcern", payload["otherConcerns"]?.toString()?.trim('"'))
        assertEquals(nextDate.toString(), payload["nextAppointmentDate"]?.toString()?.trim('"'))
        assertNotNull(payload["bookedAt"], "booking context must be audited")
    }

    @Test
    fun `void and unvoid audits carry the full lifecycle`() {
        SessionClientFixtures.insertTestClient(clientId)
        SessionService.create(
            callerId = callerId,
            id = sessionId,
            clientId = clientId,
            branchId = branchId,
            isWalkIn = true,
            requestedPractitionerId = null,
            finalPrice = BigDecimal("2500.00"),
            remarks = null,
            otherConcerns = null,
            nextAppointmentDate = null,
        )
        val voidId = TestFixtures.uuid()
        SessionService.voidSession(callerId, sessionId, voidId, "CovVoidReason")
        SessionService.unvoidSession(callerId, sessionId, "CovUnvoidReason")

        val voidInsert =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq SessionVoidTable.tableName) and
                            (AuditLogTable.action eq AuditAction.INSERT)
                    }.single()
            }
        val voidPayload = Json.parseToJsonElement(voidInsert[AuditLogTable.newValue].orEmpty()).jsonObject
        assertEquals("CovVoidReason", voidPayload["voidReason"]?.toString()?.trim('"'))
        assertEquals(callerId.toString(), voidPayload["voidedBy"]?.toString()?.trim('"'))
        assertNotNull(voidPayload["voidedAt"])

        val voidUpdate = latestUpdate(SessionVoidTable.tableName, voidId)
        val unvoidNew = Json.parseToJsonElement(voidUpdate.newValue.orEmpty()).jsonObject
        assertEquals("CovUnvoidReason", unvoidNew["unvoidedReason"]?.toString()?.trim('"'))
        assertEquals(callerId.toString(), unvoidNew["unvoidedBy"]?.toString()?.trim('"'))
        assertNotNull(unvoidNew["unvoidedAt"])
    }

    @Test
    fun `historical string-null payloads stay readable alongside JSON null`() {
        val legacy = Json.parseToJsonElement("""{"notes":"null","reason":"x"}""").jsonObject
        assertEquals("null", legacy["notes"]?.toString()?.trim('"'))
        val current = Json.parseToJsonElement("""{"notes":null,"reason":"x"}""").jsonObject
        assertTrue(current["notes"] is JsonNull)

        val redacted =
            transaction {
                AuditLog.redactClientNamesInTransaction(clientId)
            }
        assertEquals(0, redacted)
    }

    private fun createClient(
        phoneNumber: String? = "09170000000",
        gender: Gender = Gender.F,
        age: Int = 30,
        middleName: String? = null,
    ) {
        ClientService.create(
            callerId = callerId,
            id = clientId,
            firstName = "Ana",
            lastName = "Santos",
            middleName = middleName,
            suffix = null,
            phoneNumber = phoneNumber,
            address = "Cavite",
            gender = gender,
            age = age,
            systolicBp = null,
            diastolicBp = null,
            medicalConditions = null,
        )
    }

    private data class AuditRow(
        val reason: String?,
        val oldValue: String?,
        val newValue: String?,
    )

    private fun clientEntries(): List<AuditRow> =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq ClientTable.tableName) and
                        (AuditLogTable.recordId eq clientId)
                }.orderBy(AuditLogTable.changedAt to SortOrder.ASC)
                .map {
                    AuditRow(it[AuditLogTable.reason], it[AuditLogTable.oldValue], it[AuditLogTable.newValue])
                }
        }

    private fun latestUpdate(
        tableName: String,
        recordId: UUID,
    ): AuditRow =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq tableName) and
                        (AuditLogTable.recordId eq recordId) and
                        (AuditLogTable.action eq AuditAction.UPDATE)
                }.orderBy(AuditLogTable.changedAt to SortOrder.DESC)
                .limit(1)
                .map {
                    AuditRow(it[AuditLogTable.reason], it[AuditLogTable.oldValue], it[AuditLogTable.newValue])
                }.single()
        }
}
