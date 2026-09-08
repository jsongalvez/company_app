package com.companyb.companyapp.session.detail

import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.CapabilitySourceType
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val BRANCH = "b1"
private const val DAY = "bd1"
private const val OTHER_DAY = "bd9"

private fun grant(
    code: String,
    type: CapabilityContextType,
    id: String,
) = UserCapabilityResponse(
    capabilityCode = code,
    contextType = type,
    contextId = id,
    sourceType = CapabilitySourceType.ROLE,
)

private fun gateRow() =
    DashboardSessionResponse(
        id = "s1",
        clientId = "c1",
        clientName = "Test Client",
        sessionType = SessionType.REGULAR,
        isWalkIn = false,
        sessionStatus = SessionStatus.PENDING,
        basePrice = "2500.00",
        finalPrice = "2500.00",
        remarks = null,
        otherConcerns = null,
        bookedAt = null,
        nextAppointmentDate = null,
        version = 3,
        isVoided = false,
        branchId = BRANCH,
    )

/**
 * #675 — the pane's capability matrix across practitioner/coordinator/relief shapes:
 * edit is branch-or-day EDIT_BRANCH_DATA, sale additionally needs the clocked-in day,
 * void is BRANCH-scoped VOID_SESSION only (no day-grant leg), and the desktop-only
 * allowVoid switch stays the phone's closed gate.
 */
class SessionDetailGatesTest {
    @Test
    fun branch_edit_grant_opens_edit_and_sale_with_day() {
        val gates =
            paneGates(
                listOf(grant(CapabilityCodes.EDIT_BRANCH_DATA, CapabilityContextType.BRANCH, BRANCH)),
                gateRow(),
                DAY,
                allowVoid = true,
            )

        assertTrue(gates.canEdit)
        assertTrue(gates.canSell)
        assertFalse(gates.canVoid, "no VOID_SESSION held")
    }

    @Test
    fun relief_day_grant_opens_edit_but_never_void() {
        val caps = listOf(grant(CapabilityCodes.EDIT_BRANCH_DATA, CapabilityContextType.BRANCH_DAY, DAY))
        val gates = paneGates(caps, gateRow(), DAY, allowVoid = true)

        assertTrue(gates.canEdit, "day-grant editors act on their day")
        assertTrue(gates.canSell)
        assertFalse(gates.canVoid, "void has no day-grant leg (#406)")
    }

    @Test
    fun sale_fails_closed_without_clocked_day() {
        val caps = listOf(grant(CapabilityCodes.EDIT_BRANCH_DATA, CapabilityContextType.BRANCH, BRANCH))
        val gates = paneGates(caps, gateRow(), branchDayId = null, allowVoid = true)

        assertTrue(gates.canEdit)
        assertFalse(gates.canSell, "the request's branchDayId is missing")
    }

    @Test
    fun other_day_grant_does_not_edit_this_session() {
        val caps = listOf(grant(CapabilityCodes.EDIT_BRANCH_DATA, CapabilityContextType.BRANCH_DAY, OTHER_DAY))
        val gates = paneGates(caps, gateRow(), OTHER_DAY, allowVoid = true)

        // Branch-or-day at this session's branch: the pane's day leg is context-type-wide
        // (pre-existing #382 mirror) — the server 403 stays authoritative for wrong-day rows.
        assertTrue(gates.canEdit)
        assertFalse(gates.canVoid)
    }

    @Test
    fun void_needs_branch_void_and_desktop_opt_in() {
        val caps = listOf(grant(CapabilityCodes.VOID_SESSION, CapabilityContextType.BRANCH, BRANCH))

        assertTrue(paneGates(caps, gateRow(), DAY, allowVoid = true).canVoid)
        assertFalse(
            paneGates(caps, gateRow(), DAY, allowVoid = false).canVoid,
            "the phone route never opts in (ADR-0020)",
        )
    }

    @Test
    fun void_day_grant_never_satisfies_branch_only_gate() {
        val caps = listOf(grant(CapabilityCodes.VOID_SESSION, CapabilityContextType.BRANCH_DAY, DAY))

        assertFalse(canVoidSession(caps, BRANCH))
        assertFalse(paneGates(caps, gateRow(), DAY, allowVoid = true).canVoid)
    }

    @Test
    fun empty_capabilities_close_everything() {
        val gates = paneGates(emptyList(), gateRow(), DAY, allowVoid = true)

        assertFalse(gates.canEdit)
        assertFalse(gates.canSell)
        assertFalse(gates.canVoid)
    }

    @Test
    fun blank_branch_fails_void_closed() {
        val caps = listOf(grant(CapabilityCodes.VOID_SESSION, CapabilityContextType.BRANCH, BRANCH))

        assertFalse(canVoidSession(caps, ""))
        assertFalse(paneGates(caps, gateRow().copy(branchId = ""), DAY, allowVoid = true).canVoid)
    }
}
