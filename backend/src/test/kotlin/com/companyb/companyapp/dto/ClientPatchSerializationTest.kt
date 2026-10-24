package com.companyb.companyapp.dto

import com.companyb.companyapp.contracts.client.ClientPatchField
import com.companyb.companyapp.contracts.client.UpdateClientRequest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Three-state PATCH wire contract (#523): absent means unchanged, a present value means
 * set, a [ClientPatchField] entry in [UpdateClientRequest.clearFields] means clear.
 * Explicit JSON nulls decode the same as absent — only the set clears.
 */
class ClientPatchSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun omitted_fields_decode_to_unchanged_with_empty_clears() {
        val decoded = json.decodeFromString<UpdateClientRequest>("{}")

        assertEquals(UpdateClientRequest(), decoded)
        assertTrue(decoded.clearFields.isEmpty())
    }

    @Test
    fun explicit_null_decodes_as_unchanged_not_clear() {
        val decoded = json.decodeFromString<UpdateClientRequest>("""{"phoneNumber":null}""")

        assertEquals(null, decoded.phoneNumber)
        assertTrue(decoded.clearFields.isEmpty())
    }

    @Test
    fun clear_set_round_trips_and_set_value_survives() {
        val request =
            UpdateClientRequest(
                phoneNumber = null,
                clearFields = setOf(ClientPatchField.PHONE_NUMBER),
            )

        val decoded = json.decodeFromString<UpdateClientRequest>(json.encodeToString(request))

        assertEquals(request, decoded)
        assertEquals(setOf(ClientPatchField.PHONE_NUMBER), decoded.clearFields)
    }

    @Test
    fun bp_pair_clear_names_both_sides() {
        val request =
            UpdateClientRequest(
                clearFields = setOf(ClientPatchField.SYSTOLIC_BP, ClientPatchField.DIASTOLIC_BP),
            )

        val decoded = json.decodeFromString<UpdateClientRequest>(json.encodeToString(request))

        assertEquals(null, decoded.systolicBp)
        assertEquals(null, decoded.diastolicBp)
        assertEquals(
            setOf(ClientPatchField.SYSTOLIC_BP, ClientPatchField.DIASTOLIC_BP),
            decoded.clearFields,
        )
    }

    @Test
    fun set_values_encode_alongside_clears_without_collision() {
        val decoded =
            json.decodeFromString<UpdateClientRequest>(
                """{"firstName":"Ana","clearFields":["middleName"]}""",
            )

        assertEquals("Ana", decoded.firstName)
        assertEquals(setOf(ClientPatchField.MIDDLE_NAME), decoded.clearFields)
    }
}
