package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.dto.MemberAttendanceResponse
import com.companyb.companyapp.viewmodel.UiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #404 — the pure attendance-roster presentation rules: the present count/summary line and
 * the can-toggle predicate (mark controls for others only; self attendance stays with the
 * drawer's Clock in / Clock out).
 */
class AttendanceRosterLogicTest {
    private fun row(
        userId: String,
        present: Boolean,
        slot: Short = 1,
        assignmentId: String = "assignment-$userId",
    ) = MemberAttendanceResponse(
        assignmentId = assignmentId,
        userId = userId,
        displayName = userId,
        slot = slot,
        present = present,
    )

    @Test
    fun `presentCount handles empty all-present and mixed rosters`() {
        assertEquals(0, AttendanceRosterLogic.presentCount(emptyList()))
        assertEquals(2, AttendanceRosterLogic.presentCount(listOf(row("a", true), row("b", true))))
        assertEquals(1, AttendanceRosterLogic.presentCount(listOf(row("a", true), row("b", false))))
    }

    @Test
    fun `summaryLine is null for an empty roster and counts otherwise`() {
        assertNull(AttendanceRosterLogic.summaryLine(emptyList()))
        assertEquals("1 of 2 present", AttendanceRosterLogic.summaryLine(listOf(row("a", true), row("b", false))))
    }

    @Test
    fun `canToggle allows marking others only`() {
        val other = row("target-user", false)
        assertTrue(AttendanceRosterLogic.canToggle(other, "caller-id"))
        assertFalse(AttendanceRosterLogic.canToggle(other, "target-user"), "no self mark controls")
    }

    @Test
    fun `canToggle is false without a known caller id`() {
        assertFalse(AttendanceRosterLogic.canToggle(row("target-user", true), null))
    }

    @Test
    fun `toggleLabel mirrors the row state`() {
        assertEquals("Mark absent", AttendanceRosterLogic.toggleLabel(row("a", true)))
        assertEquals("Mark present", AttendanceRosterLogic.toggleLabel(row("a", false)))
    }

    @Test
    fun `mutationsDisabled locks on any busy leg including roster error`() {
        val idle = UiState.Idle as UiState<Unit>
        val loading = UiState.Loading as UiState<Unit>
        val error = UiState.Error("boom") as UiState<Unit>
        val success = UiState.Success(Unit) as UiState<Unit>
        assertEquals(false, AttendanceRosterLogic.mutationsDisabled(idle, idle, idle, idle))
        assertEquals(true, AttendanceRosterLogic.mutationsDisabled(loading, idle, idle, idle))
        assertEquals(true, AttendanceRosterLogic.mutationsDisabled(error, idle, idle, idle))
        assertEquals(true, AttendanceRosterLogic.mutationsDisabled(success, loading, idle, idle))
        assertEquals(true, AttendanceRosterLogic.mutationsDisabled(success, success, success, loading))
        assertEquals(false, AttendanceRosterLogic.mutationsDisabled(success, success, success, success))
    }

    @Test
    fun `dialogDismissEnabled mirrors busy legs without the error leg`() {
        val idle = UiState.Idle as UiState<Unit>
        val loading = UiState.Loading as UiState<Unit>
        val error = UiState.Error("boom") as UiState<Unit>
        val success = UiState.Success(Unit) as UiState<Unit>
        assertEquals(true, AttendanceRosterLogic.dialogDismissEnabled(idle, idle, idle, idle))
        assertEquals(true, AttendanceRosterLogic.dialogDismissEnabled(error, idle, idle, idle))
        assertEquals(false, AttendanceRosterLogic.dialogDismissEnabled(loading, idle, idle, idle))
        assertEquals(false, AttendanceRosterLogic.dialogDismissEnabled(success, success, loading, success))
    }
}
