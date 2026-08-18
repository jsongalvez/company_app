package com.companyb.companyapp.domain

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WireEnumsSerializationTest {
    private val json = Json

    @Test
    fun finite_values_keep_uppercase_wire_names() {
        assertEquals("\"SESSION\"", json.encodeToString(RemittanceType.SESSION))
        assertEquals("\"PRODUCT_SALE\"", json.encodeToString(RemittanceLineType.PRODUCT_SALE))
        assertEquals("\"REMITTED\"", json.encodeToString(DayStatus.REMITTED))
        assertEquals("\"INACTIVE\"", json.encodeToString(UserStatus.INACTIVE))
        assertEquals("\"BRANCH_DAY\"", json.encodeToString(CapabilityContextType.BRANCH_DAY))
    }

    @Test
    fun unknown_finite_value_fails_decoding() {
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<SessionStatus>("\"ARCHIVED\"")
        }
    }
}
