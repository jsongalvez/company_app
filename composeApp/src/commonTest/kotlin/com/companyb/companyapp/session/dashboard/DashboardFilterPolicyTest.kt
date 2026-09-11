package com.companyb.companyapp.session.dashboard

import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #672 — the workspace filter: All / Pending / Completed membership plus the
 * Hide-voided toggle. Filtering hides rows in stable backend order; it never moves
 * them.
 */
class DashboardFilterPolicyTest {
    private fun row(
        id: String,
        status: SessionStatus,
        voided: Boolean = false,
    ) = DashboardSessionResponse(
        id = id,
        clientId = "c1",
        clientName = "Client $id",
        sessionType = SessionType.REGULAR,
        isWalkIn = false,
        sessionStatus = status,
        basePrice = "2500.00",
        finalPrice = "2500.00",
        remarks = null,
        otherConcerns = null,
        bookedAt = null,
        nextAppointmentDate = null,
        version = 1,
        isVoided = voided,
    )

    @Test
    fun all_matches_every_status() {
        SessionStatus.entries.forEach { status ->
            assertTrue(DashboardFilter.ALL.matches(status), "$status must show under All")
        }
    }

    @Test
    fun pending_matches_only_pending() {
        assertTrue(DashboardFilter.PENDING.matches(SessionStatus.PENDING))
        assertTrue(!DashboardFilter.PENDING.matches(SessionStatus.COMPLETED))
        assertTrue(!DashboardFilter.PENDING.matches(SessionStatus.NO_SHOW))
        assertTrue(!DashboardFilter.PENDING.matches(SessionStatus.CANCELLED))
        // #876 — the sentinel is filter-exclusive: degraded rows surface under All only.
        assertTrue(!DashboardFilter.PENDING.matches(SessionStatus.UNKNOWN))
    }

    @Test
    fun completed_covers_every_terminal_outcome() {
        assertTrue(!DashboardFilter.COMPLETED.matches(SessionStatus.PENDING))
        assertTrue(DashboardFilter.COMPLETED.matches(SessionStatus.COMPLETED))
        assertTrue(DashboardFilter.COMPLETED.matches(SessionStatus.NO_SHOW))
        assertTrue(DashboardFilter.COMPLETED.matches(SessionStatus.CANCELLED))
        // #876 — the sentinel is filter-exclusive: degraded rows surface under All only.
        assertTrue(!DashboardFilter.COMPLETED.matches(SessionStatus.UNKNOWN))
    }

    @Test
    fun pending_plus_completed_partition_the_day() {
        val sessions =
            listOf(
                row("s1", SessionStatus.PENDING),
                row("s2", SessionStatus.COMPLETED),
                row("s3", SessionStatus.NO_SHOW),
                row("s4", SessionStatus.CANCELLED),
            )
        val pending = applyDashboardFilter(sessions, DashboardFilter.PENDING, hideVoided = false)
        val completed = applyDashboardFilter(sessions, DashboardFilter.COMPLETED, hideVoided = false)

        assertEquals(listOf("s1"), pending.map { it.id })
        assertEquals(listOf("s2", "s3", "s4"), completed.map { it.id })
    }

    @Test
    fun hide_voided_hides_voided_rows_in_every_filter() {
        val sessions =
            listOf(
                row("s1", SessionStatus.PENDING),
                row("s2", SessionStatus.PENDING, voided = true),
                row("s3", SessionStatus.COMPLETED, voided = true),
            )

        assertEquals(
            listOf("s1"),
            applyDashboardFilter(sessions, DashboardFilter.ALL, hideVoided = true).map { it.id },
        )
        assertEquals(
            listOf("s1"),
            applyDashboardFilter(sessions, DashboardFilter.PENDING, hideVoided = true).map { it.id },
        )
        assertEquals(
            emptyList(),
            applyDashboardFilter(sessions, DashboardFilter.COMPLETED, hideVoided = true).map { it.id },
        )
    }

    @Test
    fun voided_rows_stay_visible_by_default() {
        val sessions = listOf(row("s1", SessionStatus.COMPLETED, voided = true))

        assertEquals(1, applyDashboardFilter(sessions, DashboardFilter.ALL, hideVoided = false).size)
    }

    @Test
    fun filtering_preserves_backend_order() {
        val sessions =
            listOf(
                row("s1", SessionStatus.COMPLETED),
                row("s2", SessionStatus.PENDING),
                row("s3", SessionStatus.COMPLETED),
            )

        assertEquals(
            listOf("s1", "s3"),
            applyDashboardFilter(sessions, DashboardFilter.COMPLETED, hideVoided = false).map { it.id },
        )
    }

    @Test
    fun empty_kind_names_the_day_before_the_filter() {
        assertEquals(DashboardEmptyKind.NO_SESSIONS_FOR_DAY, dashboardEmptyKind(0))
        assertEquals(DashboardEmptyKind.NO_FILTER_MATCHES, dashboardEmptyKind(4))
    }

    @Test
    fun restore_filter_fails_closed_to_all() {
        assertEquals(DashboardFilter.ALL, restoredDashboardFilter(null))
        assertEquals(DashboardFilter.PENDING, restoredDashboardFilter("PENDING"))
        assertEquals(DashboardFilter.COMPLETED, restoredDashboardFilter("COMPLETED"))
        assertEquals(DashboardFilter.ALL, restoredDashboardFilter("GRAND_NEW_TAB"))
    }

    @Test
    fun edited_row_stays_mounted_when_the_filter_hides_it() {
        val sessions =
            listOf(
                row("s1", SessionStatus.PENDING),
                row("s2", SessionStatus.COMPLETED),
                row("s3", SessionStatus.COMPLETED),
            )
        val filtered = applyDashboardFilter(sessions, DashboardFilter.PENDING, hideVoided = false)

        val visible = ensureEditedRowVisible(sessions, filtered, editSessionId = "s2")

        assertEquals(listOf("s1", "s2"), visible.map { it.id })
    }

    @Test
    fun edited_row_reinserts_at_its_backend_relative_position() {
        val sessions =
            listOf(
                row("s1", SessionStatus.PENDING),
                row("s2", SessionStatus.COMPLETED),
                row("s3", SessionStatus.COMPLETED),
                row("s4", SessionStatus.PENDING),
            )
        val filtered = applyDashboardFilter(sessions, DashboardFilter.PENDING, hideVoided = false)

        val visible = ensureEditedRowVisible(sessions, filtered, editSessionId = "s3")

        assertEquals(listOf("s1", "s3", "s4"), visible.map { it.id })
    }

    @Test
    fun edited_row_exemption_leaves_visible_and_missing_rows_alone() {
        val sessions = listOf(row("s1", SessionStatus.PENDING))
        val filtered = applyDashboardFilter(sessions, DashboardFilter.PENDING, hideVoided = false)

        assertEquals(filtered, ensureEditedRowVisible(sessions, filtered, editSessionId = null))
        assertEquals(filtered, ensureEditedRowVisible(sessions, filtered, editSessionId = "s1"))
        assertEquals(filtered, ensureEditedRowVisible(sessions, filtered, editSessionId = "gone"))
    }
}
