package com.companyb.companyapp.workforce.relief

import com.companyb.companyapp.contracts.workforce.ReliefAccessResponse
import com.companyb.companyapp.contracts.workforce.ReliefAccessStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #666 — the deep-link panel labels each request row with the requester display name
 * when the day read populated it; null-name rows (mine list / legacy) fall back to the id.
 */
class ReliefDayRowLabelTest {
    private fun row(
        requestedBy: String,
        requesterName: String?,
    ) = ReliefAccessResponse(
        id = "req-1",
        branchDayId = "bd1",
        requestedBy = requestedBy,
        requestStatus = ReliefAccessStatus.PENDING,
        requesterName = requesterName,
    )

    @Test
    fun `named row shows the display name, not the id`() {
        assertEquals("Alice", reliefRequestRowLabel(row("uuid-alice", "Alice")))
    }

    @Test
    fun `null-name row falls back to the id`() {
        assertEquals("uuid-alice", reliefRequestRowLabel(row("uuid-alice", null)))
    }
}
