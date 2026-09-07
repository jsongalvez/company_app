package com.companyb.companyapp.client

import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.dto.ClientPatchField
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.dto.UpdateClientRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Explicit three-state PATCH decisions (#523): blank drafts on genuinely optional fields
 * dispatch clears, required names still error, BP clears only as a pair, and the
 * supersede gate tracks clear dispatches.
 */
class ClientPatchLogicTest {
    @Test
    fun `blank optional fields dispatch clears`() {
        val cases =
            mapOf(
                ClientField.MIDDLE_NAME to ClientPatchField.MIDDLE_NAME,
                ClientField.SUFFIX to ClientPatchField.SUFFIX,
                ClientField.PHONE to ClientPatchField.PHONE_NUMBER,
                ClientField.ADDRESS to ClientPatchField.ADDRESS,
                ClientField.MEDICAL_CONDITIONS to ClientPatchField.MEDICAL_CONDITIONS,
            )
        for ((field, expected) in cases) {
            var error: String? = null
            val patch = patchFor(field, "") { error = it }

            assertNull(error, "$field blank must not error")
            assertNotNull(patch, "$field blank must dispatch")
            assertEquals(setOf(expected), patch.clearFields, "$field blank must clear $expected")
        }
    }

    @Test
    fun `non-blank optional fields dispatch sets`() {
        var error: String? = null
        val patch = patchFor(ClientField.PHONE, "09171234567") { error = it }

        assertNull(error)
        assertNotNull(patch)
        assertEquals("09171234567", patch.phoneNumber)
        assertTrue(patch.clearFields.isEmpty())
    }

    @Test
    fun `blank required names still error without dispatching`() {
        for (field in listOf(ClientField.FIRST_NAME, ClientField.LAST_NAME)) {
            var error: String? = null
            val patch = patchFor(field, "") { error = it }

            assertNull(patch, "$field blank must not dispatch")
            assertNotNull(error, "$field blank must error")
        }
    }

    @Test
    fun `commitEdit on a cleared phone dispatches the clear`() {
        val session = ClientEditSession()
        val client = testClient(phone = "09171234567")
        var dispatched: UpdateClientRequest? = null
        val deps = testDeps(onPatch = { dispatched = it })

        session.startEdit(ClientField.PHONE, client, deps)
        session.handleDraftChange("")
        assertTrue(session.commitEdit(ClientField.PHONE, client, deps))

        assertNotNull(dispatched)
        assertEquals(setOf(ClientPatchField.PHONE_NUMBER), dispatched.clearFields)
        assertEquals(ClientField.PHONE, session.pendingEditField)
    }

    @Test
    fun `commitBpDrafts on a fully-blanked seeded pair dispatches the pair clear`() {
        val session = ClientEditSession()
        val client = testClient(sys = 120, dia = 80)
        var dispatched: UpdateClientRequest? = null
        val deps = testDeps(onPatch = { dispatched = it })

        session.startEdit(ClientField.BP_PAIR, client, deps)
        session.bpDraft.seed(client.systolicBp, client.diastolicBp)
        session.bpDraft.systolic = ""
        session.bpDraft.diastolic = ""
        assertTrue(session.commitBpDrafts(client, deps))

        assertNotNull(dispatched)
        assertEquals(
            setOf(ClientPatchField.SYSTOLIC_BP, ClientPatchField.DIASTOLIC_BP),
            dispatched.clearFields,
        )
    }

    @Test
    fun `commitBpDrafts on a half-blanked pair errors without dispatching`() {
        val session = ClientEditSession()
        val client = testClient(sys = 120, dia = 80)
        var dispatched: UpdateClientRequest? = null
        val deps = testDeps(onPatch = { dispatched = it })

        session.startEdit(ClientField.BP_PAIR, client, deps)
        session.bpDraft.seed(client.systolicBp, client.diastolicBp)
        session.bpDraft.systolic = ""
        session.bpDraft.diastolic = "80"
        assertFalse(session.commitBpDrafts(client, deps))

        assertNull(dispatched)
        assertEquals("Both BP fields are required", session.fieldError)
    }

    @Test
    fun `commitBpDrafts on an untouched null pair exits silently`() {
        val session = ClientEditSession()
        val client = testClient(sys = null, dia = null)
        var dispatched: UpdateClientRequest? = null
        val deps = testDeps(onPatch = { dispatched = it })

        session.startEdit(ClientField.BP_PAIR, client, deps)
        session.bpDraft.seed(null, null)
        assertTrue(session.commitBpDrafts(client, deps))

        assertNull(dispatched)
        assertNull(session.editingField)
    }

    @Test
    fun `cleared dispatch record matches only the empty draft`() {
        val record = DispatchedDraft(field = ClientField.PHONE, value = "", cleared = true)

        assertTrue(record.matches(draftValue = "", bpSystolic = "", bpDiastolic = ""))
        assertTrue(record.matches(draftValue = "   ", bpSystolic = "", bpDiastolic = ""))
        assertFalse(record.matches(draftValue = "0917", bpSystolic = "", bpDiastolic = ""))
    }

    @Test
    fun `cleared bp record matches only the fully-blank pair`() {
        val record = DispatchedDraft(field = ClientField.BP_PAIR, value = "", cleared = true)

        assertTrue(record.matches(draftValue = "", bpSystolic = "", bpDiastolic = ""))
        assertFalse(record.matches(draftValue = "", bpSystolic = "120", bpDiastolic = "80"))
        assertFalse(record.matches(draftValue = "", bpSystolic = "", bpDiastolic = "80"))
    }

    private fun testClient(
        phone: String? = null,
        sys: Short? = null,
        dia: Short? = null,
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

    private fun testDeps(onPatch: (UpdateClientRequest) -> Unit): ClientEditDeps =
        ClientEditDeps(
            navigationLocked = false,
            updateState = UiState.Idle,
            anonymizeState = UiState.Idle,
            liveDetailState = { UiState.Idle },
            liveUpdateState = { UiState.Idle },
            onPatch = onPatch,
        )
}
