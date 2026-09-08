package com.companyb.companyapp.workforce.team

import androidx.compose.ui.unit.dp
import com.companyb.companyapp.contracts.identity.UserAssignmentResponse
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.contracts.identity.UserSummaryResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #681 — pure People-tab policy: status filter, concise assignment summary,
 * filter-excluded updated-row pin, and the 1000dp list/detail breakpoint.
 */
class TeamPeoplePolicyTest {
    @Test
    fun statusFilter_defaults_active_and_matches() {
        assertEquals(TeamStatusFilter.ACTIVE, teamStatusFilterFromName(null))
        assertEquals(TeamStatusFilter.ACTIVE, teamStatusFilterFromName("BOGUS"))
        assertEquals(TeamStatusFilter.INACTIVE, teamStatusFilterFromName("INACTIVE"))
        assertEquals(TeamStatusFilter.ALL, teamStatusFilterFromName("ALL"))
        assertTrue(TeamStatusFilter.ACTIVE.matches(UserStatus.ACTIVE))
        assertFalse(TeamStatusFilter.ACTIVE.matches(UserStatus.INACTIVE))
        assertTrue(TeamStatusFilter.ALL.matches(UserStatus.INACTIVE))
    }

    @Test
    fun sectionTab_defaults_people() {
        assertEquals(TeamSectionTab.PEOPLE, teamSectionTabFromName(null))
        assertEquals(TeamSectionTab.PEOPLE, teamSectionTabFromName("BOGUS"))
        assertEquals(TeamSectionTab.BRANCHES, teamSectionTabFromName("BRANCHES"))
    }

    @Test
    fun filterUsersByStatus_splits_active_inactive() {
        val users = listOf(user("u1", UserStatus.ACTIVE), user("u2", UserStatus.INACTIVE))

        assertEquals(listOf("u1"), filterUsersByStatus(users, TeamStatusFilter.ACTIVE).map { it.id })
        assertEquals(listOf("u2"), filterUsersByStatus(users, TeamStatusFilter.INACTIVE).map { it.id })
        assertEquals(listOf("u1", "u2"), filterUsersByStatus(users, TeamStatusFilter.ALL).map { it.id })
    }

    @Test
    fun assignmentSummary_concise_with_overflow() {
        assertEquals("No branch assignments", assignmentSummary(emptyList()))
        assertEquals(
            "Main Branch · slot 1",
            assignmentSummary(listOf(assignment("b1", "Main Branch", 1))),
        )
        assertEquals(
            "A · slot 1, B · slot 2 +1 more",
            assignmentSummary(
                listOf(
                    assignment("b1", "A", 1),
                    assignment("b2", "B", 2),
                    assignment("b3", "C", 3),
                ),
            ),
        )
    }

    @Test
    fun pinnedRow_survives_filter_until_cleared() {
        val users = listOf(user("u1", UserStatus.ACTIVE), user("u2", UserStatus.INACTIVE))
        val filtered = filterUsersByStatus(filterUsers(users, ""), TeamStatusFilter.ACTIVE)

        // u2 deactivated under the Active filter: pin keeps it visible once.
        val pinned = ensurePinnedRowVisible(users, filtered, "u2")
        assertEquals(listOf("u1", "u2"), pinned.map { it.id })
        // No pin, unknown pin, or already-visible pin never duplicates.
        assertEquals(filtered, ensurePinnedRowVisible(users, filtered, null))
        assertEquals(filtered, ensurePinnedRowVisible(users, filtered, "ghost"))
        assertEquals(filtered, ensurePinnedRowVisible(users, filtered, "u1"))
    }

    @Test
    fun filteredPeople_combines_search_status_pin() {
        val users =
            listOf(
                user("u1", UserStatus.ACTIVE, "Ana Cruz"),
                user("u2", UserStatus.INACTIVE, "Ben Diaz"),
            )

        assertEquals(listOf("u1"), filteredPeople(users, "ana", TeamStatusFilter.ACTIVE, null).map { it.id })
        // Pin appends the updated row behind the filtered list (Dashboard precedent).
        assertEquals(
            listOf("u1", "u2"),
            filteredPeople(users, "", TeamStatusFilter.ACTIVE, "u2").map { it.id },
        )
    }

    @Test
    fun layout_shows_side_detail_at_1000dp() {
        assertTrue(TeamLayoutPolicy.showSideDetail(1000.dp))
        assertFalse(TeamLayoutPolicy.showSideDetail(999.dp))
    }

    @Test
    fun detailErrors_keep_person_scope_only() {
        val u1 = user("u1", UserStatus.ACTIVE)
        val errors =
            mapOf(
                "roles:u1" to "Role update failed: 500",
                "roles:u2" to "other",
                "slot:b1:a1" to "Slot update failed: 409",
                "swap:b1:a1:a2" to "Swap failed: 403",
            )
        // u1 holds assignment a1 at b1: its slot error surfaces, swap stays claimed
        // by the Branches slot card, other users' errors stay out.
        val holder = u1.copy(assignments = listOf(assignment("b1", "Main", 1, "a1")))
        assertEquals(
            listOf("Role update failed: 500", "Slot update failed: 409"),
            detailErrors(errors, holder),
        )
    }

    @Test
    fun detailErrors_match_exact_keys_not_suffixes() {
        // "deactivate:abc" must not leak onto user "bc".
        val errors = mapOf("deactivate:abc" to "Deactivate failed: 500")
        assertTrue(detailErrors(errors, user("bc", UserStatus.ACTIVE)).isEmpty())
        assertEquals(
            listOf("Deactivate failed: 500"),
            detailErrors(errors, user("abc", UserStatus.ACTIVE)),
        )
    }

    private fun user(
        id: String,
        status: UserStatus,
        displayName: String = id,
    ): UserSummaryResponse =
        UserSummaryResponse(
            id = id,
            username = id,
            displayName = displayName,
            status = status,
        )

    private fun assignment(
        branchId: String,
        branchName: String,
        slot: Short,
        assignmentId: String = "$branchId-$slot",
    ): UserAssignmentResponse =
        UserAssignmentResponse(
            assignmentId = assignmentId,
            branchId = branchId,
            branchName = branchName,
            slot = slot,
        )
}
