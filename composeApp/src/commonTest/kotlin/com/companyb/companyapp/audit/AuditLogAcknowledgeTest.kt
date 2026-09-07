package com.companyb.companyapp.audit

import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.dto.AuditLogEntryResponse
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [canAcknowledgeEntry] (#144 pass-3 SOFT-2): the backend leaves `isFlagged` true when
 * acknowledging (it sets only `acknowledgedAt`), so a row acknowledged by another reviewer would
 * 404 on tap — the affordance must also require `acknowledgedAt == null`.
 */
class AuditLogAcknowledgeTest {
    private fun flaggedEntry(
        id: String = "e1",
        isFlagged: Boolean = true,
        acknowledgedAt: String? = null,
        changedBy: String = "u1",
    ): AuditLogEntryResponse =
        AuditLogEntryResponse(
            id = id,
            tableName = "session",
            recordId = "r1",
            action = AuditAction.UPDATE,
            changedBy = changedBy,
            changedByName = "Ana",
            changedAt = "2026-08-05T06:00:00+08:00",
            oldValue = null,
            newValue = null,
            isFlagged = isFlagged,
            reason = null,
            acknowledgedAt = acknowledgedAt,
        )

    @Test
    fun flagged_unacknowledged_other_reviewer_is_acknowledgeable() {
        assertTrue(canAcknowledgeEntry(showAcknowledge = true, entry = flaggedEntry(), currentUserId = "u2"))
    }

    @Test
    fun own_row_is_hidden() {
        assertFalse(canAcknowledgeEntry(showAcknowledge = true, entry = flaggedEntry(), currentUserId = "u1"))
    }

    @Test
    fun already_acknowledged_row_is_hidden() {
        val acked = flaggedEntry(acknowledgedAt = "2026-08-05T07:00:00+08:00")
        assertFalse(canAcknowledgeEntry(showAcknowledge = true, entry = acked, currentUserId = "u2"))
    }

    @Test
    fun unflagged_row_is_hidden() {
        assertFalse(
            canAcknowledgeEntry(showAcknowledge = true, entry = flaggedEntry(isFlagged = false), currentUserId = "u2"),
        )
    }

    @Test
    fun show_acknowledge_false_is_hidden() {
        assertFalse(canAcknowledgeEntry(showAcknowledge = false, entry = flaggedEntry(), currentUserId = "u2"))
    }
}
