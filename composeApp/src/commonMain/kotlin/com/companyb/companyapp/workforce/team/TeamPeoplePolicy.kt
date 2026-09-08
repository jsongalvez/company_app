package com.companyb.companyapp.workforce.team

import com.companyb.companyapp.contracts.identity.UserAssignmentResponse
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.contracts.identity.UserSummaryResponse

/**
 * #681 — pure People-tab policy: status filter, concise assignment summary, and
 * the filter-excluded updated-row pin.
 *
 * Composition-free so the decisions are unit-testable without a Compose runtime
 * (the #670 contract shape, DashboardLayoutPolicy precedent).
 *
 * Team & branches tabs. People is the default.
 */
enum class TeamSectionTab {
    PEOPLE,
    BRANCHES,
}

/** People status filter. Active is the default. */
enum class TeamStatusFilter {
    ACTIVE,
    INACTIVE,
    ALL,
}

/** Fail-closed to ACTIVE so an unknown persisted filter never widens the roster silently. */
fun teamStatusFilterFromName(name: String?): TeamStatusFilter =
    when (name) {
        TeamStatusFilter.INACTIVE.name -> TeamStatusFilter.INACTIVE
        TeamStatusFilter.ALL.name -> TeamStatusFilter.ALL
        else -> TeamStatusFilter.ACTIVE
    }

/** Fail-closed to PEOPLE so an unknown persisted tab never lands on branch admin. */
fun teamSectionTabFromName(name: String?): TeamSectionTab =
    when (name) {
        TeamSectionTab.BRANCHES.name -> TeamSectionTab.BRANCHES
        else -> TeamSectionTab.PEOPLE
    }

fun TeamStatusFilter.matches(status: UserStatus): Boolean =
    when (this) {
        TeamStatusFilter.ACTIVE -> status == UserStatus.ACTIVE
        TeamStatusFilter.INACTIVE -> status == UserStatus.INACTIVE
        TeamStatusFilter.ALL -> true
    }

/** Status-filter leg of the People list (search rides the existing [filterUsers]). */
fun filterUsersByStatus(
    users: List<UserSummaryResponse>,
    filter: TeamStatusFilter,
): List<UserSummaryResponse> = if (filter == TeamStatusFilter.ALL) users else users.filter { filter.matches(it.status) }

/**
 * Concise branch-assignment summary for a collapsed People row.
 *
 * - No assignments → "No branch assignments" (the expanded-body precedent).
 * - Up to [maxShown] entries render "Branch — slot N", joined with ", ".
 * - Beyond that renders "+M more" so long assignment lists stay one line
 *   (the UI ellipsizes to one line; validation covers long assignments).
 */
fun assignmentSummary(
    assignments: List<UserAssignmentResponse>,
    maxShown: Int = 2,
): String {
    if (assignments.isEmpty()) return "No branch assignments"
    val shown = assignments.take(maxShown).joinToString(", ") { "${it.branchName} · slot ${it.slot}" }
    val remaining = assignments.size - assignments.take(maxShown).size
    return if (remaining > 0) "$shown +$remaining more" else shown
}

/**
 * Filter-excluded updated-row pin (#681): a status mutation that moves its row
 * outside the active filter (e.g. deactivate under Active) keeps the row visible
 * with its changed status until the next interaction closes it.
 *
 * Pure list leg of that contract (the Dashboard `ensureEditedRowVisible`
 * precedent): when [pinnedId] names a user present in [all] but absent from
 * [filtered], the pinned row appends once. Callers clear the pin on
 * search/filter/tab/selection change.
 */
fun ensurePinnedRowVisible(
    all: List<UserSummaryResponse>,
    filtered: List<UserSummaryResponse>,
    pinnedId: String?,
): List<UserSummaryResponse> {
    if (pinnedId == null) return filtered
    if (filtered.any { it.id == pinnedId }) return filtered
    val pinned = all.firstOrNull { it.id == pinnedId } ?: return filtered
    return filtered + pinned
}

/**
 * One combined People derivation for the screen and tests: search, then status,
 * then the updated-row pin.
 */
fun filteredPeople(
    users: List<UserSummaryResponse>,
    query: String,
    filter: TeamStatusFilter,
    pinnedId: String?,
): List<UserSummaryResponse> {
    val searched = filterUsers(users, query)
    val statusFiltered = filterUsersByStatus(searched, filter)
    return ensurePinnedRowVisible(searched, statusFiltered, pinnedId)
}
