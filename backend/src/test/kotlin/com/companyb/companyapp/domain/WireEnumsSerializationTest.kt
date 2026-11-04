package com.companyb.companyapp.domain

import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.commerce.InventoryMovementReason
import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.contracts.finance.ExpenseResponse
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.isRoutineStatusMark
import com.companyb.companyapp.contracts.session.isStatusTransitionAllowed
import com.companyb.companyapp.contracts.workforce.ReliefAccessStatus
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Wire enums as the backend transport (`KotlinxSerializationMapper`) reads and writes them. */
class WireEnumsSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun values_keep_uppercase_wire_names() {
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
    fun missing_enum_field_fails_decode() {
        val body =
            """{"id":"e1","branchDayId":"b1","amount":"10.00","notes":null,""" +
                """"createdBy":"u1","createdAt":"t","deletedBy":null,"deletedAt":null,"version":1}"""
        assertFailsWith<SerializationException> { json.decodeFromString<ExpenseResponse>(body) }
    }

    @Test
    fun unlisted_values_fail_decode() {
        for (raw in listOf("\"UNKNOWN\"", "\"ARCHIVED\"")) {
            assertFailsWith<SerializationException> { json.decodeFromString<SessionStatus>(raw) }
        }
        assertFailsWith<SerializationException> { json.decodeFromString<ExpenseCategory>("\"UNKNOWN\"") }
        assertFailsWith<SerializationException> { json.decodeFromString<ReliefAccessStatus>("\"ACCEPTED\"") }
        assertFailsWith<SerializationException> { json.decodeFromString<InventoryMovementReason>("\"DISPOSED\"") }
        assertFailsWith<SerializationException> { json.decodeFromString<AuditAction>("\"ARCHIVE\"") }
    }

    @Test
    fun transition_matrix() {
        assertTrue(isRoutineStatusMark(SessionStatus.PENDING, SessionStatus.COMPLETED))
        assertTrue(isStatusTransitionAllowed(SessionStatus.PENDING, SessionStatus.COMPLETED, false))
    }
}
