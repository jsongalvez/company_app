package com.companyb.companyapp.session.rates

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.CapabilitySourceType
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.dto.RateResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #418 — the base-rate admin screen's pure decision surface: exact-scope gate predicate,
 * canonical five-row display merge, and the route-400-mirroring input validation.
 *
 * #573 — moved with the rates owner from `ui/screen` to `session/rates`.
 */
class BaseRatesLogicTest {
    private val branchId = "11111111-1111-1111-1111-111111111111"

    private fun capability(
        code: String,
        contextType: CapabilityContextType,
        contextId: String?,
    ) = UserCapabilityResponse(
        capabilityCode = code,
        contextType = contextType,
        contextId = contextId.orEmpty(),
        sourceType = CapabilitySourceType.ROLE,
    )

    // --- canManageRates (mirror of SessionBaseRateRoutes' requireBranchCapabilityForBranchId) ---

    @Test
    fun `rate admin needs MANAGE_PRODUCTS at BRANCH context for the branch`() {
        val caps =
            listOf(
                capability(CapabilityCodes.MANAGE_PRODUCTS, CapabilityContextType.BRANCH, branchId),
            )
        assertTrue(canManageRates(caps, branchId))
    }

    @Test
    fun `global and day-scoped grants never satisfy the rate-admin gate`() {
        val global =
            listOf(
                capability(
                    CapabilityCodes.MANAGE_PRODUCTS,
                    CapabilityContextType.GLOBAL,
                    "00000000-0000-0000-0000-000000000000",
                ),
            )
        val dayGrant =
            listOf(
                capability(
                    CapabilityCodes.MANAGE_PRODUCTS,
                    CapabilityContextType.BRANCH_DAY,
                    "22222222-2222-2222-2222-222222222222",
                ),
            )
        assertFalse(canManageRates(global, branchId))
        assertFalse(canManageRates(dayGrant, branchId))
    }

    @Test
    fun `a different branch or a null scope fails closed`() {
        val other = "33333333-3333-3333-3333-333333333333"
        val caps =
            listOf(
                capability(CapabilityCodes.MANAGE_PRODUCTS, CapabilityContextType.BRANCH, other),
            )
        assertFalse(canManageRates(caps, branchId))
        assertFalse(canManageRates(emptyList(), branchId))
        assertFalse(canManageRates(caps, null))
    }

    // --- toRateDisplayRows (canonical five-row display merge) ---

    private fun rate(type: SessionType) =
        RateResponse(
            id = "44444444-4444-4444-4444-444444444444",
            branchId = branchId,
            sessionType = type,
            rate = "2500.00",
            effectiveFrom = "2026-01-01T00:00:00Z",
            effectiveUntil = "9999-12-31T23:59:59Z",
        )

    @Test
    fun `rows render all five types in BR display order even with no rates`() {
        val rows = toRateDisplayRows(emptyList())

        assertEquals(5, rows.size)
        assertEquals(BASE_RATE_ROWSPECS.map { it.sessionType }, rows.map { it.spec.sessionType })
        rows.forEach { row ->
            assertNull(row.rateId)
            assertNull(row.rateText)
        }
    }

    @Test
    fun `an open rate row binds to its type row and mission stays locked`() {
        val rows = toRateDisplayRows(listOf(rate(SessionType.REGULAR), rate(SessionType.MEDICAL_MISSION)))

        assertEquals(5, rows.size)
        val regular = rows.first { it.spec.sessionType == SessionType.REGULAR }
        assertEquals("2500.00", regular.rateText)
        assertFalse(regular.missionLocked)
        val mission = rows.first { it.spec.sessionType == SessionType.MEDICAL_MISSION }
        assertTrue(mission.missionLocked)
    }

    // --- rateInputError (the route's parseNonNegativeBigDecimal 400s mirrored) ---

    @Test
    fun `blank unparseable negative and non-finite amounts are rejected inline`() {
        assertEquals("Enter a rate amount", rateInputError(""))
        assertEquals("Enter a rate amount", rateInputError("   "))
        assertEquals("Invalid rate amount: abc", rateInputError("abc"))
        assertEquals("Invalid rate amount: NaN", rateInputError("NaN"))
        assertEquals("Invalid rate amount: Infinity", rateInputError("Infinity"))
        assertEquals("rate must be non-negative", rateInputError("-500"))
    }

    @Test
    fun `zero and positive amounts pass`() {
        assertNull(rateInputError("0"))
        assertNull(rateInputError("2500"))
        assertNull(rateInputError("2500.50"))
    }
}
