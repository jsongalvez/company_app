package com.companyb.companyapp.workforce.attendance

import androidx.compose.runtime.saveable.SaverScope
import com.companyb.companyapp.dto.MemberAttendanceResponse
import com.companyb.companyapp.workforce.team.SlotEditTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * #416 — the dashboard self-service slot affordance: the overflow menu renders on the
 * caller's own row only (every other row stays read-only display), swap candidates are the
 * other roster members, and a missing caller identity fail-closes both.
 */
class AttendanceRosterSelfSlotTest {
    private fun row(
        userId: String,
        slot: Short,
        present: Boolean = true,
        assignmentId: String = "assignment-$userId",
    ) = MemberAttendanceResponse(
        assignmentId = assignmentId,
        userId = userId,
        displayName = "Member $userId",
        slot = slot,
        present = present,
    )

    @Test
    fun `own row offers the self-service affordance`() {
        assertTrue(AttendanceRosterLogic.canEditOwnSlot(row("me", 1), currentUserId = "me"))
    }

    @Test
    fun `another member's row stays read-only`() {
        assertFalse(AttendanceRosterLogic.canEditOwnSlot(row("other", 2), currentUserId = "me"))
    }

    @Test
    fun `missing caller identity fail-closes the own-row gate`() {
        assertFalse(AttendanceRosterLogic.canEditOwnSlot(row("me", 1), currentUserId = null))
    }

    @Test
    fun `swap candidates are every other roster member`() {
        val rows = listOf(row("me", 1), row("a", 2), row("b", 3))

        assertEquals(listOf("a", "b"), AttendanceRosterLogic.swapCandidates(rows, "me").map { it.userId })
    }

    @Test
    fun `no caller identity means no swap targets`() {
        assertTrue(AttendanceRosterLogic.swapCandidates(listOf(row("a", 1)), null).isEmpty())
    }

    @Test
    fun `solo roster leaves no one to trade with`() {
        assertTrue(AttendanceRosterLogic.swapCandidates(listOf(row("me", 1)), "me").isEmpty())
    }

    @Test
    fun `candidate label names the member and the slot it trades with`() {
        assertEquals("Member a · slot 2", AttendanceRosterLogic.swapCandidateLabel(row("a", 2)))
    }

    @Test
    fun `captured assignment identities survive process state restoration`() {
        val original = RosterSelfSlotState()
        original.editTarget =
            SlotEditTarget(
                branchId = "b1",
                branchName = "Main",
                assignmentId = "assignment-me-old",
                displayName = "Member me",
                currentSlot = 1,
            )
        original.swapTarget =
            RosterSwapTarget(
                branchId = "b1",
                ownAssignmentId = "assignment-me-old",
                candidateAssignmentIds = listOf("assignment-other-old"),
            )

        // #469 — NestedScopeFunctions: single scope call via helper (was with{with{}}).
        val saved = checkNotNull(saveWithEverything(original))
        val restored = assertNotNull(RosterSelfSlotStateSaver.restore(saved))

        assertEquals("assignment-me-old", restored.editTarget?.assignmentId)
        assertEquals("assignment-me-old", restored.swapTarget?.ownAssignmentId)
        assertEquals(listOf("assignment-other-old"), restored.swapTarget?.candidateAssignmentIds)
    }
}

private object SaveEverythingScope : SaverScope {
    override fun canBeSaved(value: Any): Boolean = true
}

// #469 — NestedScopeFunctions helper: one scope-function deep; the save call
// keeps its original receivers (dispatch = saver, extension = scope).
private fun saveWithEverything(original: RosterSelfSlotState): List<String>? =
    with(RosterSelfSlotStateSaver) { SaveEverythingScope.save(original) }
