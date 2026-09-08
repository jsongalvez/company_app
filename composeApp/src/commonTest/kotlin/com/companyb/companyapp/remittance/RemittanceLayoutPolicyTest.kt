package com.companyb.companyapp.remittance

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * #677 — the remittance desk layout contract: queue + one workspace at >= 1000dp
 * content width (240dp queue), full-width workspace with Back below it; the Undo
 * deadline affordance and branch label helpers.
 */
class RemittanceLayoutPolicyTest {
    @Test
    fun queue_at_and_above_1000dp() {
        assertFalse(RemittanceLayoutPolicy.showQueue(999.dp))
        assertTrue(RemittanceLayoutPolicy.showQueue(1000.dp))
        assertTrue(RemittanceLayoutPolicy.showQueue(1366.dp))
    }

    @Test
    fun queue_width_is_240dp() {
        assertEquals(240.dp, RemittanceLayoutPolicy.queueWidth)
    }

    @Test
    fun undo_deadline_is_48h_after_submission_manila() {
        val detail = submittedDetail(submittedAt = "2026-08-09T01:00:00Z")
        assertEquals("2026-08-11 09:00", remittanceUndoDeadline(detail))
    }

    @Test
    fun undo_deadline_null_without_submission_instant() {
        assertNull(remittanceUndoDeadline(draftDetail()))
    }

    @Test
    fun undo_eligible_inside_window_only() {
        assertTrue(remittanceCanUndo(submittedDetail(submittedAt = clockNowMinusHours(1))))
        assertNotNull(remittanceUndoDeadline(submittedDetail(submittedAt = clockNowMinusHours(1))))
    }

    @Test
    fun branch_label_never_invents_a_name() {
        assertEquals("Branch b1-short", remittanceBranchLabel("b1-short-id"))
        assertEquals("Branch unknown", remittanceBranchLabel(null))
        assertEquals("Branch unknown", remittanceBranchLabel(""))
    }

    private fun clockNowMinusHours(hours: Long): String = (Clock.System.now() - Duration.parse("${hours}h")).toString()

    private fun submittedDetail(submittedAt: String) =
        com.companyb.companyapp.contracts.remittance.RemittanceDetailResponse(
            id = "r1",
            type = com.companyb.companyapp.contracts.remittance.RemittanceType.SESSION,
            status = com.companyb.companyapp.contracts.remittance.RemittanceStatus.SUBMITTED,
            branchId = "b1",
            method = com.companyb.companyapp.contracts.remittance.RemittanceMethod.BANK_TRANSFER,
            submittedDate = "2026-08-09",
            submittedAt = submittedAt,
            submittedBy = "u1",
            dateRangeStart = "2026-08-01",
            dateRangeEnd = "2026-08-09",
            createdAt = "2026-08-01T01:00:00Z",
            version = 4,
            lines = emptyList(),
            totalAmount = "500.00",
            dayBreakdowns = emptyList(),
            snapshot = null,
        )

    private fun draftDetail() =
        submittedDetail(submittedAt = "2026-08-09T01:00:00Z")
            .copy(
                status = com.companyb.companyapp.contracts.remittance.RemittanceStatus.SUBMITTED,
                submittedAt = null,
            )
}
