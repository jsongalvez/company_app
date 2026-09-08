package com.companyb.companyapp.session.detail

import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.CapabilitySourceType
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #406 — the void affordance: gate off renders nothing; a voided row flips to Unvoid; and
 * the client gate mirrors `SessionAuthz.requireBranchCapabilityForSession(VOID_SESSION)` — a BRANCH-scoped
 * VOID_SESSION grant at the session's branch, with NO day-grant leg.
 */
class SessionVoidAffordanceTest {
    @Test
    fun `closed gate offers no affordance regardless of void state`() {
        assertNull(sessionVoidAffordance(canVoid = false, isVoided = false))
        assertNull(sessionVoidAffordance(canVoid = false, isVoided = true))
    }

    @Test
    fun `open gate on an active row offers Void`() {
        assertEquals(SessionVoidAffordance.VOID, sessionVoidAffordance(canVoid = true, isVoided = false))
    }

    @Test
    fun `open gate on a voided row offers Unvoid`() {
        assertEquals(SessionVoidAffordance.UNVOID, sessionVoidAffordance(canVoid = true, isVoided = true))
    }

    @Test
    fun `branch-scoped void session grant at the session branch opens the gate`() {
        val caps = listOf(branchCap(CapabilityCodes.VOID_SESSION, "branch-1"))

        assertTrue(canVoidSession(caps, "branch-1"))
    }

    @Test
    fun `edit capability or a different branch does not open the gate`() {
        val caps =
            listOf(
                branchCap(CapabilityCodes.EDIT_BRANCH_DATA, "branch-1"),
                branchCap(CapabilityCodes.VOID_SESSION, "branch-2"),
            )

        // The backend's void routes have no EDIT_BRANCH_DATA leg — neither does the mirror.
        assertFalse(canVoidSession(caps, "branch-1"))
        // Fail-closed on a null context (pre-clock-in).
        assertFalse(canVoidSession(caps, null))
    }

    private fun branchCap(
        code: String,
        contextId: String,
    ) = UserCapabilityResponse(
        capabilityCode = code,
        contextType = CapabilityContextType.BRANCH,
        contextId = contextId,
        sourceType = CapabilitySourceType.ROLE,
    )
}
