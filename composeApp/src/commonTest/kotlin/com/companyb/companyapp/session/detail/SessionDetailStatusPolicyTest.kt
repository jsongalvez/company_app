package com.companyb.companyapp.session.detail

import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val ME = "u1"
private const val OTHER = "u2"

private fun policyRow(
    status: SessionStatus = SessionStatus.PENDING,
    isWalkIn: Boolean = false,
    isVoided: Boolean = false,
) = DashboardSessionResponse(
    id = "s1",
    clientId = "c1",
    clientName = "Test Client",
    sessionType = SessionType.REGULAR,
    isWalkIn = isWalkIn,
    sessionStatus = status,
    basePrice = "2500.00",
    finalPrice = "2500.00",
    remarks = null,
    otherConcerns = null,
    bookedAt = null,
    nextAppointmentDate = null,
    version = 3,
    isVoided = isVoided,
)

private fun model(
    status: SessionStatus = SessionStatus.PENDING,
    isWalkIn: Boolean = false,
    rosterIds: Set<String>? = setOf(OTHER),
    currentUserId: String? = ME,
    canEdit: Boolean = true,
    canCorrectStatus: Boolean = false,
    dayStatus: DayStatus? = DayStatus.OPEN,
    isVoided: Boolean = false,
) = sessionDetailActionModel(
    session = policyRow(status, isWalkIn, isVoided),
    rosterIds = rosterIds,
    currentUserId = currentUserId,
    canEdit = canEdit,
    canCorrectStatus = canCorrectStatus,
    dayStatus = dayStatus,
)

/**
 * #675 — the shared detail action model: join/completion primaries, the menu carrying
 * every other legal target (completion without forced join), fail-closed unknowns,
 * correction gating, and the completed-state line.
 */
class SessionDetailStatusPolicyTest {
    @Test
    fun pending_non_member_gets_add_self_primary_with_completion_in_menu() {
        val result = model()

        assertEquals(SessionDetailPrimary.ADD_SELF, result.primary)
        assertFalse(result.showCompletedState)
        // No forced join: completion stays reachable through the menu.
        assertTrue(SessionStatus.COMPLETED in result.statusMenuOptions)
        assertTrue(SessionStatus.NO_SHOW in result.statusMenuOptions)
        assertTrue(SessionStatus.CANCELLED in result.statusMenuOptions)
    }

    @Test
    fun pending_member_gets_complete_primary_and_menu_drops_completed() {
        val result = model(rosterIds = setOf(ME))

        assertEquals(SessionDetailPrimary.COMPLETE, result.primary)
        assertFalse(SessionStatus.COMPLETED in result.statusMenuOptions)
        assertTrue(SessionStatus.NO_SHOW in result.statusMenuOptions)
        assertTrue(SessionStatus.CANCELLED in result.statusMenuOptions)
    }

    @Test
    fun pending_walk_in_member_completes_with_empty_menu() {
        val result = model(isWalkIn = true, rosterIds = setOf(ME))

        assertEquals(SessionDetailPrimary.COMPLETE, result.primary)
        assertTrue(result.statusMenuOptions.isEmpty(), "walk-ins cannot no-show or cancel")
    }

    @Test
    fun unknown_roster_offers_no_primary_but_menu_stays() {
        val result = model(rosterIds = null)

        assertNull(result.primary, "no provisional shortcut while the roster loads")
        assertTrue(SessionStatus.COMPLETED in result.statusMenuOptions)
    }

    @Test
    fun no_edit_authority_offers_nothing() {
        val result = model(canEdit = false, rosterIds = setOf(ME))

        assertNull(result.primary)
        assertFalse(result.showCompletedState)
        assertTrue(result.statusMenuOptions.isEmpty())
    }

    @Test
    fun unknown_day_state_fails_closed() {
        val member = model(dayStatus = null, rosterIds = setOf(ME))
        assertNull(member.primary)
        assertTrue(member.statusMenuOptions.isEmpty())

        // Joining waits for the day read too — offering against a PAST/REMITTED day the
        // server would 403 is a client inconsistency, not a shortcut.
        val nonMember = model(dayStatus = null, rosterIds = setOf(OTHER))
        assertNull(nonMember.primary)
        assertTrue(nonMember.statusMenuOptions.isEmpty())
    }

    @Test
    fun non_open_day_without_correction_authority_offers_nothing() {
        val result = model(dayStatus = DayStatus.PAST, rosterIds = setOf(ME))

        assertNull(result.primary)
        assertTrue(result.statusMenuOptions.isEmpty())
    }

    @Test
    fun completed_session_shows_state_for_editors_only() {
        val editor = model(status = SessionStatus.COMPLETED)
        assertNull(editor.primary)
        assertTrue(editor.showCompletedState)
        assertTrue(editor.statusMenuOptions.isEmpty(), "completed is immutable through status edits")

        val viewer = model(status = SessionStatus.COMPLETED, canEdit = false)
        assertFalse(viewer.showCompletedState, "read-only rendering keeps badge-only state")
    }

    @Test
    fun correction_reopens_with_authority_only() {
        val allowed =
            model(
                status = SessionStatus.NO_SHOW,
                canCorrectStatus = true,
                dayStatus = DayStatus.PAST,
            )
        assertNull(allowed.primary, "corrections never take the primary slot")
        // PENDING reopens the mis-mark; CANCELLED reclassifies between terminal outcomes
        // (both are corrections under the shared vocabulary, not routine marks).
        assertEquals(setOf(SessionStatus.PENDING, SessionStatus.CANCELLED), allowed.statusMenuOptions.toSet())

        val denied =
            model(
                status = SessionStatus.NO_SHOW,
                canCorrectStatus = false,
                dayStatus = DayStatus.PAST,
            )
        assertTrue(denied.statusMenuOptions.isEmpty())
    }

    @Test
    fun menu_labels_are_textual() {
        assertEquals("Mark completed", SessionStatus.COMPLETED.detailActionLabel())
        assertEquals("Mark no-show", SessionStatus.NO_SHOW.detailActionLabel())
        assertEquals("Cancel session", SessionStatus.CANCELLED.detailActionLabel())
        assertEquals("Reopen as pending", SessionStatus.PENDING.detailActionLabel())
    }

    @Test
    fun voided_session_hides_status_options_but_keeps_unvoid_path() {
        val member = model(rosterIds = setOf(ME), isVoided = true)
        assertNull(member.primary, "void freezes completion shortcut")
        assertTrue(member.statusMenuOptions.isEmpty(), "voided rows offer no status targets")
        assertFalse(member.showCompletedState)

        val nonMember = model(rosterIds = setOf(OTHER), isVoided = true)
        assertEquals(SessionDetailPrimary.ADD_SELF, nonMember.primary, "roster joins stay open (#689)")
        assertTrue(nonMember.statusMenuOptions.isEmpty())
    }

    @Test
    fun voided_completed_session_offers_no_status_targets() {
        val result = model(status = SessionStatus.COMPLETED, isVoided = true)
        assertNull(result.primary)
        assertTrue(result.statusMenuOptions.isEmpty())
    }

    @Test
    fun blank_client_name_reads_unknown() {
        assertEquals(DETAIL_UNKNOWN_CLIENT, detailClientName(null))
        assertEquals(DETAIL_UNKNOWN_CLIENT, detailClientName(""))
        assertEquals(DETAIL_UNKNOWN_CLIENT, detailClientName("   "))
        assertEquals("Test Client", detailClientName("Test Client"))
    }
}
