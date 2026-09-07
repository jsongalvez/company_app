package com.companyb.companyapp.domain

import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.commerce.InventoryMovementReason
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.workforce.ReliefAccessStatus
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
        assertEquals("\"UPDATE\"", json.encodeToString(AuditAction.UPDATE))
        assertEquals("\"BRANCH_DAY\"", json.encodeToString(CapabilityContextType.BRANCH_DAY))
        assertEquals("\"GRANTED\"", json.encodeToString(ReliefAccessStatus.GRANTED))
        assertEquals("\"MISSING\"", json.encodeToString(InventoryMovementReason.MISSING))
    }

    @Test
    fun unknown_finite_value_fails_decoding() {
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<SessionStatus>("\"ARCHIVED\"")
        }
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<ReliefAccessStatus>("\"ACCEPTED\"")
        }
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<InventoryMovementReason>("\"DISPOSED\"")
        }
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<AuditAction>("\"ARCHIVE\"")
        }
    }
}
