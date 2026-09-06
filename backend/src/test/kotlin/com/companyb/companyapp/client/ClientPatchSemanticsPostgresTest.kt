package com.companyb.companyapp.client

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.dto.ClientPatchField
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Explicit three-state client PATCH (#523): omitted stays unchanged, present values set,
 * [ClientPatchField] entries in `clearFields` clear. Reload proves the stored result.
 */
class ClientPatchSemanticsPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "patch-caller")
    }

    @Test
    fun `clear phone persists null and leaves other fields`() {
        createClient(phoneNumber = "09171234567", middleName = "Reyes", address = "Cavite")

        val updated = update(clearFields = setOf(ClientPatchField.PHONE_NUMBER))

        assertNull(updated.phoneNumber)
        assertEquals("Reyes", updated.middleName)
        assertEquals("Cavite", updated.address)
        assertNull(persistedPhone())
    }

    @Test
    fun `clear optional name parts and conditions persists nulls`() {
        ClientService.create(
            callerId = callerId,
            id = clientId,
            firstName = "Ana",
            lastName = "Santos",
            middleName = "Reyes",
            suffix = "Jr",
            phoneNumber = "09170000000",
            address = "Cavite",
            gender = Gender.F,
            age = 30,
            systolicBp = null,
            diastolicBp = null,
            medicalConditions = "Asthma",
        )

        update(
            clearFields =
                setOf(
                    ClientPatchField.MIDDLE_NAME,
                    ClientPatchField.SUFFIX,
                    ClientPatchField.MEDICAL_CONDITIONS,
                ),
        )

        transaction {
            val row = ClientTable.selectAll().where { ClientTable.id eq clientId }.single()
            assertNull(row[ClientTable.middleName])
            assertNull(row[ClientTable.suffix])
            assertNull(row[ClientTable.medicalConditions])
        }
    }

    @Test
    fun `clear address resets to the N-A default`() {
        createClient(address = "Bacoor, Cavite")

        val updated = update(clearFields = setOf(ClientPatchField.ADDRESS))

        assertEquals(DEFAULT_CLIENT_ADDRESS, updated.address)
    }

    @Test
    fun `clear BP pair persists nulls`() {
        createClient(systolicBp = 120.toShort(), diastolicBp = 80.toShort())

        val updated =
            update(
                clearFields = setOf(ClientPatchField.SYSTOLIC_BP, ClientPatchField.DIASTOLIC_BP),
            )

        assertNull(updated.systolicBp)
        assertNull(updated.diastolicBp)
    }

    @Test
    fun `half BP clear is rejected and keeps values`() {
        createClient(systolicBp = 120.toShort(), diastolicBp = 80.toShort())

        assertFailsWith<ValidationException> {
            update(clearFields = setOf(ClientPatchField.SYSTOLIC_BP))
        }
        assertFailsWith<ValidationException> {
            update(systolicBp = 130.toShort(), diastolicBp = null)
        }

        transaction {
            val row = ClientTable.selectAll().where { ClientTable.id eq clientId }.single()
            assertEquals(120.toShort(), row[ClientTable.systolicBp])
            assertEquals(80.toShort(), row[ClientTable.diastolicBp])
        }
    }

    @Test
    fun `set and clear on the same field is rejected`() {
        createClient()

        assertFailsWith<ValidationException> {
            update(phoneNumber = "0917", clearFields = setOf(ClientPatchField.PHONE_NUMBER))
        }
        assertFailsWith<ValidationException> {
            update(
                systolicBp = 130.toShort(),
                diastolicBp = 85.toShort(),
                clearFields = setOf(ClientPatchField.SYSTOLIC_BP, ClientPatchField.DIASTOLIC_BP),
            )
        }
    }

    @Test
    fun `unknown and required clears are rejected`() {
        createClient()

        assertFailsWith<ValidationException> {
            update(clearFields = setOf("nickname"))
        }
        assertFailsWith<ValidationException> {
            update(clearFields = setOf("firstName"))
        }
        assertFailsWith<ValidationException> {
            update(clearFields = setOf("gender"))
        }
        assertFailsWith<ValidationException> {
            update(clearFields = setOf("age"))
        }
    }

    @Test
    fun `blank values are rejected without storing whitespace`() {
        createClient(phoneNumber = "09171234567")

        assertFailsWith<ValidationException> {
            update(phoneNumber = "   ")
        }
        assertFailsWith<ValidationException> {
            update(firstName = "  ")
        }

        assertEquals("09171234567", persistedPhone())
    }

    @Test
    fun `omitted fields stay unchanged on a partial PATCH`() {
        createClient(phoneNumber = "09171234567")

        val updated = update(firstName = "Bianca")

        assertEquals("Bianca", updated.firstName)
        assertEquals("09171234567", updated.phoneNumber)
    }

    @Test
    fun `audit reason names cleared fields without their values`() {
        createClient(phoneNumber = "09171234567", middleName = "Reyes")

        update(clearFields = setOf(ClientPatchField.PHONE_NUMBER, ClientPatchField.MIDDLE_NAME))

        val entries = auditEntries()
        val clearEntry = entries.first { it.reason?.startsWith("cleared:") == true }
        assertEquals("cleared: middleName, phoneNumber", clearEntry.reason)
        // #525 — the clear event itself carries markers, never removed values.
        // Earlier SET/CREATE payloads retain history until anonymize redacts it.
        assertFalse(
            (clearEntry.oldValue.orEmpty() + clearEntry.newValue.orEmpty()).contains("09171234567"),
            "Cleared values must not land in the clear event's payloads",
        )
        assertFalse(
            (clearEntry.oldValue.orEmpty() + clearEntry.newValue.orEmpty()).contains("Reyes"),
            "Cleared values must not land in the clear event's payloads",
        )
    }

    @Test
    fun `clear on an anonymized client stays 404 without resurrecting PII`() {
        createClient(phoneNumber = "09171234567")
        ClientService.anonymize(callerId, clientId)

        assertFailsWith<NotFoundException> {
            update(clearFields = setOf(ClientPatchField.PHONE_NUMBER))
        }
        assertNull(persistedPhone())
    }

    private fun createClient(
        phoneNumber: String? = "09170000000",
        middleName: String? = null,
        address: String? = "Cavite",
        systolicBp: Short? = null,
        diastolicBp: Short? = null,
    ) {
        ClientService.create(
            callerId = callerId,
            id = clientId,
            firstName = "Ana",
            lastName = "Santos",
            middleName = middleName,
            suffix = null,
            phoneNumber = phoneNumber,
            address = address,
            gender = Gender.F,
            age = 30,
            systolicBp = systolicBp,
            diastolicBp = diastolicBp,
            medicalConditions = null,
        )
    }

    private fun update(
        firstName: String? = null,
        phoneNumber: String? = null,
        systolicBp: Short? = null,
        diastolicBp: Short? = null,
        clearFields: Set<String> = emptySet(),
    ) = ClientService.update(
        callerId = callerId,
        clientId = clientId,
        firstName = firstName,
        lastName = null,
        middleName = null,
        suffix = null,
        phoneNumber = phoneNumber,
        address = null,
        gender = null,
        age = null,
        systolicBp = systolicBp,
        diastolicBp = diastolicBp,
        medicalConditions = null,
        clearFields = clearFields,
    )

    private fun persistedPhone(): String? =
        transaction {
            ClientTable.selectAll().where { ClientTable.id eq clientId }.single()[ClientTable.phoneNumber]
        }

    private data class AuditRow(
        val reason: String?,
        val oldValue: String?,
        val newValue: String?,
    )

    private fun auditEntries(): List<AuditRow> =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq "client") and
                        (AuditLogTable.recordId eq clientId)
                }.orderBy(AuditLogTable.changedAt to SortOrder.ASC)
                .map { AuditRow(it[AuditLogTable.reason], it[AuditLogTable.oldValue], it[AuditLogTable.newValue]) }
        }
}
