package com.companyb.companyapp.client

import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.contracts.client.Gender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #673 — one identity presentation: primary/secondary, em dash for missing,
 * "Anonymized client" for husks, age/address only to disambiguate, never clinical
 * concerns or raw IDs; keyboard focus moves with no default selection.
 */
class ClientIdentityTest {
    @Test
    fun `same-name clients disambiguate with phone plus age and address`() {
        val a = client(id = "a", first = "Ana", last = "Santos", phone = "09170000001", address = "Cavite")
        val b = client(id = "b", first = "Ana", last = "Santos", phone = "09170000002", address = "Manila")
        val siblings = listOf(a, b)
        assertEquals("Ana Santos", clientPrimaryName(a))
        assertTrue(clientSecondaryLine(a, siblings).contains("09170000001"))
        assertTrue(clientSecondaryLine(a, siblings).contains("Cavite"))
        assertTrue(clientSecondaryLine(b, siblings).contains("Manila"))
    }

    @Test
    fun `unique names show phone only without age noise`() {
        val a = client(id = "a", first = "Ana", last = "Santos", phone = "09170000001")
        assertEquals("09170000001", clientSecondaryLine(a, listOf(a)))
    }

    @Test
    fun `missing phone reads as an em dash`() {
        val a = client(id = "a", first = "Ana", last = "Santos", phone = null)
        assertEquals(CLIENT_MISSING_VALUE, clientPhoneLine(a))
        assertEquals(CLIENT_MISSING_VALUE, clientSecondaryLine(a, listOf(a)))
    }

    @Test
    fun `anonymized records read as anonymized client`() {
        val anon =
            client(id = "x", first = "Ana", last = "Santos").copy(firstName = null, lastName = null)
        assertTrue(isAnonymizedClient(anon))
        assertEquals(CLIENT_ANONYMIZED_LABEL, clientPrimaryName(anon))
        assertEquals(CLIENT_MISSING_VALUE, clientSecondaryLine(anon))
        assertNull(clientDisambiguator(anon))
        assertFalse(needsDisambiguation(anon, listOf(anon)))
    }

    @Test
    fun `search rows never expose clinical concerns or raw ids`() {
        val a = client(id = "secret-id-1", first = "Ana", last = "Santos", phone = "0917")
        val line = clientPrimaryName(a) + " " + clientSecondaryLine(a, listOf(a))
        assertFalse(line.contains("secret-id-1"))
        assertFalse(line.contains("hypertension", ignoreCase = true))
        assertFalse(line.contains("120"))
    }

    @Test
    fun `picker focus starts unselected and clamps at bounds`() {
        assertEquals(-1, movePickerFocus(-99, 0, 0))
        assertEquals(0, movePickerFocus(-1, 1, 3))
        assertEquals(2, movePickerFocus(-1, -1, 3))
        assertEquals(2, movePickerFocus(2, 1, 3))
        assertEquals(0, movePickerFocus(0, -1, 3))
        assertEquals(1, movePickerFocus(0, 1, 3))
    }

    private fun client(
        id: String,
        first: String,
        last: String,
        phone: String? = "09170000000",
        address: String? = null,
    ): ClientResponse =
        ClientResponse(
            id = id,
            firstName = first,
            lastName = last,
            middleName = null,
            suffix = null,
            phoneNumber = phone,
            address = address,
            gender = Gender.F,
            age = 30,
            systolicBp = 120,
            diastolicBp = 80,
            medicalConditions = "hypertension",
            sessionCount = 0,
        )
}
