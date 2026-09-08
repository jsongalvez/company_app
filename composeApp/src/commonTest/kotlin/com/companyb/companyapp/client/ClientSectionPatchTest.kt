package com.companyb.companyapp.client

import com.companyb.companyapp.contracts.client.ClientPatchField
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.contracts.client.Gender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #673 — section saves emit one request with only changed fields (existing null/clear
 * semantics via [patchFor]); BP validates as its existing paired value; unchanged
 * sections exit silently; validation failures surface per field with no dispatch.
 */
class ClientSectionPatchTest {
    @Test
    fun `identity save merges only changed fields into one request`() {
        val client = testClient()
        val drafts =
            mapOf(
                ClientField.FIRST_NAME to "Ana",
                ClientField.MIDDLE_NAME to "",
                ClientField.LAST_NAME to "Reyes",
                ClientField.SUFFIX to "",
                ClientField.GENDER to Gender.F.name,
                ClientField.AGE to "30",
            )
        val result = buildSectionPatch(ClientSection.IDENTITY, drafts, "", "", client)
        assertTrue(result.fieldErrors.isEmpty())
        assertTrue(result.hasChanges)
        val request = assertNotNull(result.request)
        assertEquals("Reyes", request.lastName)
        assertNull(request.firstName)
        assertNull(request.age)
        assertTrue(request.clearFields.isEmpty())
    }

    @Test
    fun `unchanged section exits silently with no request`() {
        val client = testClient()
        val drafts =
            mapOf(
                ClientField.PHONE to "09171234567",
                ClientField.ADDRESS to "Cavite",
            )
        val result = buildSectionPatch(ClientSection.CONTACT, drafts, "", "", client)
        assertTrue(result.fieldErrors.isEmpty())
        assertFalse(result.hasChanges)
        assertNull(result.request)
    }

    @Test
    fun `blank optional contact field dispatches a clear in the same request`() {
        val client = testClient(phone = "09171234567")
        val drafts = mapOf(ClientField.PHONE to "", ClientField.ADDRESS to "Cavite")
        val result = buildSectionPatch(ClientSection.CONTACT, drafts, "", "", client)
        assertTrue(result.fieldErrors.isEmpty())
        val request = assertNotNull(result.request)
        assertEquals(setOf(ClientPatchField.PHONE_NUMBER), request.clearFields)
    }

    @Test
    fun `blank required name errors with no request`() {
        val client = testClient()
        val drafts =
            mapOf(
                ClientField.FIRST_NAME to "",
                ClientField.MIDDLE_NAME to "",
                ClientField.LAST_NAME to "Santos",
                ClientField.SUFFIX to "",
                ClientField.GENDER to Gender.F.name,
                ClientField.AGE to "30",
            )
        val result = buildSectionPatch(ClientSection.IDENTITY, drafts, "", "", client)
        assertNotNull(result.fieldErrors[ClientField.FIRST_NAME])
        assertNull(result.request)
    }

    @Test
    fun `health save pairs bp and carries medical conditions together`() {
        val client = testClient(sys = null, dia = null)
        val drafts = mapOf(ClientField.MEDICAL_CONDITIONS to "asthma")
        val result = buildSectionPatch(ClientSection.HEALTH, drafts, "120", "80", client)
        assertTrue(result.fieldErrors.isEmpty())
        val request = assertNotNull(result.request)
        assertEquals(120.toShort(), request.systolicBp)
        assertEquals(80.toShort(), request.diastolicBp)
        assertEquals("asthma", request.medicalConditions)
    }

    @Test
    fun `half-blank bp pair errors without dispatching`() {
        val client = testClient(sys = 120, dia = 80)
        val drafts = mapOf(ClientField.MEDICAL_CONDITIONS to "")
        val result = buildSectionPatch(ClientSection.HEALTH, drafts, "", "80", client)
        assertEquals("Both BP fields are required", result.fieldErrors[ClientField.BP_PAIR])
        assertNull(result.request)
    }

    @Test
    fun `fully-blanked seeded bp pair clears as a pair`() {
        val client = testClient(sys = 120, dia = 80)
        val drafts = mapOf(ClientField.MEDICAL_CONDITIONS to "")
        val result = buildSectionPatch(ClientSection.HEALTH, drafts, "", "", client)
        assertTrue(result.fieldErrors.isEmpty())
        val request = assertNotNull(result.request)
        assertTrue(request.clearFields.contains(ClientPatchField.SYSTOLIC_BP))
        assertTrue(request.clearFields.contains(ClientPatchField.DIASTOLIC_BP))
    }

    @Test
    fun `unavailable detection uses the stable revoked-access contract`() {
        assertTrue(isUnavailableError(CLIENT_DETAIL_UNAVAILABLE))
        assertFalse(isUnavailableError("loadClient failed: 500"))
        assertFalse(isUnavailableError("loadClient failed: 403"))
    }

    private fun testClient(
        phone: String? = "09171234567",
        sys: Short? = 120,
        dia: Short? = 80,
    ): ClientResponse =
        ClientResponse(
            id = "client-1",
            firstName = "Ana",
            lastName = "Santos",
            middleName = null,
            suffix = null,
            phoneNumber = phone,
            address = "Cavite",
            gender = Gender.F,
            age = 30,
            systolicBp = sys,
            diastolicBp = dia,
            medicalConditions = null,
            sessionCount = 0,
        )
}
