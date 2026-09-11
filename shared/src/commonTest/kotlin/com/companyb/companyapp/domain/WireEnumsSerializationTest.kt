package com.companyb.companyapp.domain

import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.audit.AuditLogEntryResponse
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.CapabilitySourceType
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.contracts.branch.BranchClockInStatus
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.branch.MeBranchResponse
import com.companyb.companyapp.contracts.branchday.BranchDayTodayResponse
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.contracts.client.Gender
import com.companyb.companyapp.contracts.commerce.InventoryMovementReason
import com.companyb.companyapp.contracts.commerce.InventoryMovementResponse
import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.contracts.finance.ExpenseResponse
import com.companyb.companyapp.contracts.identity.MeResponse
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.contracts.incident.IncidentPacket
import com.companyb.companyapp.contracts.incident.IncidentSource
import com.companyb.companyapp.contracts.incident.PoolSnapshot
import com.companyb.companyapp.contracts.remittance.RemittanceLineResponse
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceResponse
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.contracts.session.isRoutineStatusMark
import com.companyb.companyapp.contracts.session.isStatusCorrection
import com.companyb.companyapp.contracts.session.isStatusTransitionAllowed
import com.companyb.companyapp.contracts.workforce.ReliefAccessResponse
import com.companyb.companyapp.contracts.workforce.ReliefAccessStatus
import com.companyb.companyapp.contracts.workforce.ReliefInviteResponse
import com.companyb.companyapp.contracts.workforce.ReliefInviteStatus
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #876 — forward-compat policy for shared wire enums. Every wire enum carries an UNKNOWN
 * sentinel and every response DTO defaults its enum properties to it; the client transport
 * ([clientJson] mirrors `ApiClient`) sets `coerceInputValues = true` so a newer server
 * value degrades one row instead of failing the whole response. The backend transport
 * ([backendJson] mirrors `KotlinxSerializationMapper`) stays strict: unknown input still
 * throws, which the backend maps to 400.
 */
class WireEnumsSerializationTest {
    private val json = Json

    /** Mirrors the client API transport (`ApiClient`): tolerant reads, strict writes. */
    private val clientJson =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            coerceInputValues = true
        }

    /** Mirrors the backend transport (`KotlinxSerializationMapper`): strict on input. */
    private val backendJson =
        Json {
            ignoreUnknownKeys = true
        }

    /** Rewrites one `"key":"known"` pair to `"key":"unknown"`, simulating a newer server value. */
    private fun spoil(
        encoded: String,
        key: String,
        known: String,
        unknown: String,
    ): String = encoded.replace("\"$key\":\"$known\"", "\"$key\":\"$unknown\"")

    private inline fun <reified T> spoiled(
        value: T,
        key: String,
        known: String,
        unknown: String,
    ): String = spoil(clientJson.encodeToString(value), key, known, unknown)

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
    fun sentinel_keeps_uppercase_wire_name() {
        assertEquals("\"UNKNOWN\"", json.encodeToString(SessionStatus.UNKNOWN))
        assertEquals("\"UNKNOWN\"", json.encodeToString(ExpenseCategory.UNKNOWN))
    }

    @Test
    fun unknown_expense_category_coerces_on_client_transport() {
        val response =
            ExpenseResponse(
                id = "e1",
                branchDayId = "b1",
                amount = "10.00",
                category = ExpenseCategory.MISCELLANEOUS,
                notes = null,
                createdBy = "u1",
                createdAt = "t",
                deletedBy = null,
                deletedAt = null,
                version = 1,
            )
        val decoded =
            clientJson.decodeFromString<ExpenseResponse>(
                spoiled(response, "category", "MISCELLANEOUS", "FUEL"),
            )
        assertEquals(ExpenseCategory.UNKNOWN, decoded.category)
        assertEquals("e1", decoded.id)
    }

    @Test
    fun unknown_value_in_list_degrades_only_that_row() {
        val first =
            ExpenseResponse(
                id = "e1",
                branchDayId = "b1",
                amount = "10.00",
                category = ExpenseCategory.MISCELLANEOUS,
                notes = null,
                createdBy = "u1",
                createdAt = "t",
                deletedBy = null,
                deletedAt = null,
                version = 1,
            )
        val second = first.copy(id = "e2", amount = "5.00", category = ExpenseCategory.PANTRY)
        val body = spoil(clientJson.encodeToString(listOf(first, second)), "category", "PANTRY", "FUEL")
        val decoded = clientJson.decodeFromString<List<ExpenseResponse>>(body)
        assertEquals(2, decoded.size)
        assertEquals(ExpenseCategory.MISCELLANEOUS, decoded[0].category)
        assertEquals(ExpenseCategory.UNKNOWN, decoded[1].category)
        assertEquals("e2", decoded[1].id)
    }

    @Test
    fun unknown_session_enums_coerce_on_client_transport() {
        val response =
            DashboardSessionResponse(
                id = "s1",
                clientId = "c1",
                clientName = null,
                sessionType = SessionType.REGULAR,
                isWalkIn = false,
                sessionStatus = SessionStatus.PENDING,
                basePrice = "10.00",
                finalPrice = "10.00",
                remarks = null,
                otherConcerns = null,
                bookedAt = null,
                nextAppointmentDate = null,
                version = 1,
                isVoided = false,
            )
        val statusDecoded =
            clientJson.decodeFromString<DashboardSessionResponse>(
                spoiled(response, "sessionStatus", "PENDING", "ARCHIVED"),
            )
        assertEquals(SessionStatus.UNKNOWN, statusDecoded.sessionStatus)
        assertEquals(SessionType.REGULAR, statusDecoded.sessionType)
        val typeDecoded =
            clientJson.decodeFromString<DashboardSessionResponse>(
                spoiled(response, "sessionType", "REGULAR", "FUTURE_TYPE"),
            )
        assertEquals(SessionType.UNKNOWN, typeDecoded.sessionType)
        assertEquals(SessionStatus.PENDING, typeDecoded.sessionStatus)
    }

    @Test
    fun unknown_audit_and_inventory_values_coerce_on_client_transport() {
        val audit =
            AuditLogEntryResponse(
                id = "a1",
                tableName = "t",
                recordId = "r",
                action = AuditAction.UPDATE,
                changedBy = "u",
                changedAt = "t",
                isFlagged = false,
            )
        val auditDecoded =
            clientJson.decodeFromString<AuditLogEntryResponse>(
                spoiled(audit, "action", "UPDATE", "ARCHIVE"),
            )
        assertEquals(AuditAction.UNKNOWN, auditDecoded.action)
        val movement =
            InventoryMovementResponse(
                id = "m1",
                productId = "p1",
                branchId = "b1",
                branchDayId = "d1",
                reason = InventoryMovementReason.SALE,
                quantityChange = -1,
                movedBy = "u",
                movedAt = "t",
                notes = null,
            )
        val movementDecoded =
            clientJson.decodeFromString<InventoryMovementResponse>(
                spoiled(movement, "reason", "SALE", "DISPOSED"),
            )
        assertEquals(InventoryMovementReason.UNKNOWN, movementDecoded.reason)
    }

    @Test
    fun unknown_relief_values_coerce_on_client_transport() {
        val access =
            ReliefAccessResponse(
                id = "r1",
                branchDayId = "b1",
                requestedBy = "u1",
                requestStatus = ReliefAccessStatus.PENDING,
            )
        val accessDecoded =
            clientJson.decodeFromString<ReliefAccessResponse>(
                spoiled(access, "requestStatus", "PENDING", "ACCEPTED"),
            )
        assertEquals(ReliefAccessStatus.UNKNOWN, accessDecoded.requestStatus)
        val invite =
            ReliefInviteResponse(
                id = "i1",
                branchId = "b1",
                branchName = "n",
                branchDayId = "d1",
                date = "2026-01-01",
                invitedBy = "u1",
                inviterName = "n1",
                invitee = "u2",
                inviteeName = "n2",
                status = ReliefInviteStatus.PENDING,
                createdAt = "t",
            )
        val inviteDecoded =
            clientJson.decodeFromString<ReliefInviteResponse>(
                spoiled(invite, "status", "PENDING", "WITHDRAWN"),
            )
        assertEquals(ReliefInviteStatus.UNKNOWN, inviteDecoded.status)
    }

    @Test
    fun unknown_identity_branch_day_client_values_coerce_on_client_transport() {
        val me = MeResponse(id = "u1", username = "u", displayName = "n", status = UserStatus.ACTIVE, createdAt = "t")
        assertEquals(
            UserStatus.UNKNOWN,
            clientJson.decodeFromString<MeResponse>(spoiled(me, "status", "ACTIVE", "SUSPENDED")).status,
        )
        val capability =
            UserCapabilityResponse(
                capabilityCode = "VIEW_BRANCH_DATA",
                contextType = CapabilityContextType.BRANCH,
                contextId = "b1",
                sourceType = CapabilitySourceType.ROLE,
            )
        val capabilityDecoded =
            clientJson.decodeFromString<UserCapabilityResponse>(
                spoil(spoiled(capability, "contextType", "BRANCH", "REGION"), "sourceType", "ROLE", "BADGE"),
            )
        assertEquals(CapabilityContextType.UNKNOWN, capabilityDecoded.contextType)
        assertEquals(CapabilitySourceType.UNKNOWN, capabilityDecoded.sourceType)
        val branch =
            MeBranchResponse(
                branchId = "b1",
                branchName = "n",
                branchType = BranchType.CLINIC,
                clockInStatus = BranchClockInStatus.NOT_CLOCKED_IN,
                isRelief = false,
            )
        val branchDecoded =
            clientJson.decodeFromString<MeBranchResponse>(
                spoil(spoiled(branch, "branchType", "CLINIC", "POPUP"), "clockInStatus", "NOT_CLOCKED_IN", "ELSEWHERE"),
            )
        assertEquals(BranchType.UNKNOWN, branchDecoded.branchType)
        assertEquals(BranchClockInStatus.UNKNOWN, branchDecoded.clockInStatus)
        val day = BranchDayTodayResponse(branchDayId = "d1", status = DayStatus.OPEN)
        assertEquals(
            DayStatus.UNKNOWN,
            clientJson.decodeFromString<BranchDayTodayResponse>(spoiled(day, "status", "OPEN", "LOCKED")).status,
        )
        val client =
            ClientResponse(
                id = "c1",
                firstName = null,
                lastName = null,
                middleName = null,
                suffix = null,
                phoneNumber = null,
                address = null,
                gender = Gender.M,
                age = 30,
                systolicBp = null,
                diastolicBp = null,
                medicalConditions = null,
                sessionCount = 0,
            )
        assertEquals(
            Gender.UNKNOWN,
            clientJson.decodeFromString<ClientResponse>(spoiled(client, "gender", "M", "X")).gender,
        )
    }

    @Test
    fun unknown_remittance_values_coerce_on_client_transport() {
        val response =
            RemittanceResponse(
                id = "r1",
                type = RemittanceType.SESSION,
                status = RemittanceStatus.DRAFT,
                branchId = "b1",
                method = RemittanceMethod.BANK_TRANSFER,
                submittedDate = "2026-01-01",
                submittedBy = "u1",
                dateRangeStart = "2026-01-01",
                dateRangeEnd = "2026-01-31",
                createdAt = "t",
                version = 1,
            )
        val encoded = clientJson.encodeToString(response)
        assertEquals(
            RemittanceType.UNKNOWN,
            clientJson.decodeFromString<RemittanceResponse>(spoil(encoded, "type", "SESSION", "CRYPTO")).type,
        )
        assertEquals(
            RemittanceStatus.UNKNOWN,
            clientJson.decodeFromString<RemittanceResponse>(spoil(encoded, "status", "DRAFT", "APPROVED")).status,
        )
        assertEquals(
            RemittanceMethod.UNKNOWN,
            clientJson.decodeFromString<RemittanceResponse>(spoil(encoded, "method", "BANK_TRANSFER", "CASH")).method,
        )
        val line =
            RemittanceLineResponse(
                id = "l1",
                remittanceId = "r1",
                type = RemittanceLineType.SESSION,
                sessionId = "s1",
                productSaleId = null,
                createdBy = "u1",
                createdAt = "t",
                deletedBy = null,
                deletedAt = null,
                amount = "5.00",
            )
        assertEquals(
            RemittanceLineType.UNKNOWN,
            clientJson.decodeFromString<RemittanceLineResponse>(spoiled(line, "type", "SESSION", "TIP")).type,
        )
    }

    @Test
    fun unknown_incident_source_coerces_on_client_transport() {
        val packet =
            IncidentPacket(
                traceId = "t",
                method = "GET",
                route = "/api/x",
                status = 500,
                elapsedMs = null,
                appVersion = "1",
                timestamp = "t",
                pool = PoolSnapshot(active = 1, idle = 1, awaiting = 0, total = 2),
                reporter = "u",
                source = IncidentSource.USER_REPORT,
            )
        assertEquals(
            IncidentSource.UNKNOWN,
            clientJson.decodeFromString<IncidentPacket>(spoiled(packet, "source", "USER_REPORT", "CRON")).source,
        )
    }

    @Test
    fun missing_enum_field_defaults_to_unknown_sentinel() {
        val body =
            """{"id":"e1","branchDayId":"b1","amount":"10.00","notes":null,""" +
                """"createdBy":"u1","createdAt":"t","deletedBy":null,"deletedAt":null,"version":1}"""
        assertEquals(ExpenseCategory.UNKNOWN, clientJson.decodeFromString<ExpenseResponse>(body).category)
        assertEquals(ExpenseCategory.UNKNOWN, backendJson.decodeFromString<ExpenseResponse>(body).category)
    }

    @Test
    fun sentinel_round_trips_on_both_transports() {
        assertEquals(SessionStatus.UNKNOWN, clientJson.decodeFromString<SessionStatus>("\"UNKNOWN\""))
        assertEquals(SessionStatus.UNKNOWN, backendJson.decodeFromString<SessionStatus>("\"UNKNOWN\""))
        assertEquals("\"UNKNOWN\"", backendJson.encodeToString(SessionStatus.UNKNOWN))
    }

    @Test
    fun unknown_is_never_a_legal_transition_endpoint() {
        assertFalse(isRoutineStatusMark(SessionStatus.PENDING, SessionStatus.UNKNOWN))
        assertFalse(isStatusCorrection(SessionStatus.NO_SHOW, SessionStatus.UNKNOWN))
        assertFalse(isStatusCorrection(SessionStatus.UNKNOWN, SessionStatus.PENDING))
        assertFalse(isStatusTransitionAllowed(SessionStatus.PENDING, SessionStatus.UNKNOWN, false))
        assertFalse(isStatusTransitionAllowed(SessionStatus.UNKNOWN, SessionStatus.PENDING, false))
        // Sanity: the classic matrix is untouched by the sentinel.
        assertTrue(isRoutineStatusMark(SessionStatus.PENDING, SessionStatus.COMPLETED))
        assertTrue(isStatusTransitionAllowed(SessionStatus.PENDING, SessionStatus.COMPLETED, false))
    }

    @Test
    fun unknown_values_fail_closed_on_backend_transport() {
        val response =
            ExpenseResponse(
                id = "e1",
                branchDayId = "b1",
                amount = "10.00",
                category = ExpenseCategory.MISCELLANEOUS,
                notes = null,
                createdBy = "u1",
                createdAt = "t",
                deletedBy = null,
                deletedAt = null,
                version = 1,
            )
        val body = spoil(backendJson.encodeToString(response), "category", "MISCELLANEOUS", "FUEL")
        assertFailsWith<SerializationException> {
            backendJson.decodeFromString<ExpenseResponse>(body)
        }
        assertFailsWith<SerializationException> {
            backendJson.decodeFromString<SessionStatus>("\"ARCHIVED\"")
        }
        assertFailsWith<SerializationException> {
            clientJson.decodeFromString<SessionStatus>("\"ARCHIVED\"")
        }
        assertFailsWith<SerializationException> {
            backendJson.decodeFromString<ReliefAccessStatus>("\"ACCEPTED\"")
        }
        assertFailsWith<SerializationException> {
            backendJson.decodeFromString<InventoryMovementReason>("\"DISPOSED\"")
        }
        assertFailsWith<SerializationException> {
            backendJson.decodeFromString<AuditAction>("\"ARCHIVE\"")
        }
    }
}
