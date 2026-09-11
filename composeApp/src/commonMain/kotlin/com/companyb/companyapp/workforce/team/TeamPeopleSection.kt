package com.companyb.companyapp.workforce.team

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.identity.UserAssignmentResponse
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.contracts.identity.UserSummaryResponse
import com.companyb.companyapp.ui.EmptyState
import com.companyb.companyapp.ui.ErrorCard
import com.companyb.companyapp.ui.contract.PrimaryActionButton
import com.companyb.companyapp.ui.contract.operationalFocusRing
import com.companyb.companyapp.ui.contract.operationalTouchTarget
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover
import com.companyb.companyapp.util.formatRelativeTimestamp
import com.companyb.companyapp.util.logWarn

/**
 * #681 — People tab: toolbar, keyed list, and grouped person detail.
 *
 * Reuses the existing member dialogs/flows owned by UserManagementScreen:
 * this file owns only toolbar/list/detail composition. All mutations stay
 * pessimistic through UserViewModel (ADR-0022); the detail never invents a
 * role model or a divergent slot policy. Data holders keep every composable
 * LongParameterList-clean (the UserManagement actions-object precedent).
 *
 * People/branches tab switch. People is the default.
 */
@Composable
internal fun TeamSectionTabs(
    selected: TeamSectionTab,
    onSelect: (TeamSectionTab) -> Unit,
) {
    PrimaryTabRow(selectedTabIndex = if (selected == TeamSectionTab.PEOPLE) 0 else 1) {
        Tab(
            selected = selected == TeamSectionTab.PEOPLE,
            onClick = { onSelect(TeamSectionTab.PEOPLE) },
            text = { Text("People") },
        )
        Tab(
            selected = selected == TeamSectionTab.BRANCHES,
            onClick = { onSelect(TeamSectionTab.BRANCHES) },
            text = { Text("Branches") },
        )
    }
}

internal data class PeopleToolbarState(
    val searchQuery: String,
    val filter: TeamStatusFilter,
    val searchEnabled: Boolean,
    val inviteEnabled: Boolean,
    val refreshEnabled: Boolean,
)

internal data class PeopleToolbarCallbacks(
    val onSearchChange: (String) -> Unit,
    val onFilterChange: (TeamStatusFilter) -> Unit,
    val onInvite: () -> Unit,
    val onRefresh: () -> Unit,
)

/** People toolbar: search, Active/Inactive/All, Invite + Refresh. */
@Composable
internal fun PeopleToolbar(
    state: PeopleToolbarState,
    callbacks: PeopleToolbarCallbacks,
) {
    OutlinedTextField(
        value = state.searchQuery,
        onValueChange = callbacks.onSearchChange,
        label = { Text("Search users") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        enabled = state.searchEnabled,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamFilterChip(
            selected = state.filter == TeamStatusFilter.ACTIVE,
            label = "Active",
            onClick = { callbacks.onFilterChange(TeamStatusFilter.ACTIVE) },
        )
        TeamFilterChip(
            selected = state.filter == TeamStatusFilter.INACTIVE,
            label = "Inactive",
            onClick = { callbacks.onFilterChange(TeamStatusFilter.INACTIVE) },
        )
        TeamFilterChip(
            selected = state.filter == TeamStatusFilter.ALL,
            label = "All",
            onClick = { callbacks.onFilterChange(TeamStatusFilter.ALL) },
        )
        Spacer(Modifier.weight(1f))
        if (state.inviteEnabled) {
            PrimaryActionButton(label = "Invite user", onClick = callbacks.onInvite)
        }
        TextButton(onClick = callbacks.onRefresh, enabled = state.refreshEnabled) {
            Text("Refresh")
        }
    }
}

@Composable
private fun TeamFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.operationalTouchTarget().operationalFocusRing(),
    )
}

internal data class PeopleTabState(
    val users: UiState<List<UserSummaryResponse>>,
    val heldList: List<UserSummaryResponse>?,
    val searchQuery: String,
    val filter: TeamStatusFilter,
    val pinnedId: String?,
    val selectedId: String?,
    val mutationsDisabled: Boolean,
    val actionErrors: Map<String, String>,
    val currentUserId: String?,
)

internal data class PeopleTabCallbacks(
    val onSearchChange: (String) -> Unit,
    val onFilterChange: (TeamStatusFilter) -> Unit,
    val onSelect: (String) -> Unit,
    val onBack: () -> Unit,
    val onInvite: () -> Unit,
    val onRefresh: () -> Unit,
    val onClearFilters: () -> Unit,
    val onRetry: () -> Unit,
    val onEditRoles: (UserSummaryResponse) -> Unit,
    val onDeactivate: (UserSummaryResponse) -> Unit,
    val onReactivate: (String) -> Unit,
    val onEditSlot: (UserSummaryResponse, UserAssignmentResponse) -> Unit,
    val onRemoveAssignment: (UserSummaryResponse, UserAssignmentResponse) -> Unit,
)

/**
 * People tab content: toolbar + list/detail (side-by-side at >=1000dp via
 * [TeamLayoutPolicy], full-width detail with Back below it). Search/filter
 * empties offer Clear filters; true first use offers the invite action.
 */
@Composable
internal fun PeopleTabContent(
    state: PeopleTabState,
    callbacks: PeopleTabCallbacks,
    listState: LazyListState = rememberLazyListState(),
) {
    val held = state.heldList
    if (held == null) {
        PeopleColdStart(users = state.users, onRetry = callbacks.onRetry)
        return
    }
    val visible = filteredPeople(held, state.searchQuery, state.filter, state.pinnedId)
    val selectedUser = held.firstOrNull { it.id == state.selectedId }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PeopleToolbar(
            state =
                PeopleToolbarState(
                    searchQuery = state.searchQuery,
                    filter = state.filter,
                    searchEnabled = true,
                    inviteEnabled = !state.mutationsDisabled,
                    refreshEnabled = !state.mutationsDisabled,
                ),
            callbacks =
                PeopleToolbarCallbacks(
                    onSearchChange = callbacks.onSearchChange,
                    onFilterChange = callbacks.onFilterChange,
                    onInvite = callbacks.onInvite,
                    onRefresh = callbacks.onRefresh,
                ),
        )
        if (state.users is UiState.Error) {
            PeopleReloadStrip(
                message = state.users.message,
                enabled = !state.mutationsDisabled,
                onRetry = callbacks.onRetry,
            )
        }
        if (visible.isEmpty()) {
            PeopleEmpty(
                hasAnyUsers = held.isNotEmpty(),
                searchQuery = state.searchQuery,
                inviteEnabled = !state.mutationsDisabled,
                onInvite = callbacks.onInvite,
                onClearFilters = callbacks.onClearFilters,
            )
            return
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (TeamLayoutPolicy.showSideDetail(maxWidth)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    PeopleList(
                        users = visible,
                        selectedId = state.selectedId,
                        onSelect = callbacks.onSelect,
                        actionErrors = state.actionErrors,
                        listState = listState,
                        modifier = Modifier.weight(1f),
                    )
                    if (selectedUser != null) {
                        PersonDetail(
                            state =
                                PersonDetailState(
                                    user = selectedUser,
                                    currentUserId = state.currentUserId,
                                    mutationsDisabled = state.mutationsDisabled,
                                    errors = detailErrors(state.actionErrors, selectedUser),
                                    showBack = false,
                                ),
                            callbacks =
                                personDetailCallbacks(callbacks, selectedUser),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                if (selectedUser != null) {
                    PersonDetail(
                        state =
                            PersonDetailState(
                                user = selectedUser,
                                currentUserId = state.currentUserId,
                                mutationsDisabled = state.mutationsDisabled,
                                errors = detailErrors(state.actionErrors, selectedUser),
                                showBack = true,
                            ),
                        callbacks = personDetailCallbacks(callbacks, selectedUser),
                    )
                } else {
                    PeopleList(
                        users = visible,
                        selectedId = state.selectedId,
                        onSelect = callbacks.onSelect,
                        actionErrors = state.actionErrors,
                        listState = listState,
                    )
                }
            }
        }
    }
}

private fun personDetailCallbacks(
    callbacks: PeopleTabCallbacks,
    selectedUser: UserSummaryResponse,
): PersonDetailCallbacks =
    PersonDetailCallbacks(
        onEditRoles = { callbacks.onEditRoles(selectedUser) },
        onDeactivate = { callbacks.onDeactivate(selectedUser) },
        onReactivate = { callbacks.onReactivate(selectedUser.id) },
        onEditSlot = { assignment -> callbacks.onEditSlot(selectedUser, assignment) },
        onRemoveAssignment = { assignment -> callbacks.onRemoveAssignment(selectedUser, assignment) },
        onBack = callbacks.onBack,
    )

/** Row/detail inline errors for one person (the pre-#681 row-filter shape). */
internal fun detailErrors(
    actionErrors: Map<String, String>,
    user: UserSummaryResponse,
): List<String> {
    // Exact-key match: the old endsWith(":$userId") shape cross-wired users whose
    // ids are string-suffixes of one another (deactivate:abc vs user bc).
    val keys =
        buildSet {
            add("deactivate:${user.id}")
            add("reactivate:${user.id}")
            add("roles:${user.id}")
            user.assignments.forEach { assignment ->
                add("slot:${assignment.branchId}:${assignment.assignmentId}")
            }
        }
    return actionErrors.filterKeys { it in keys }.values.toList()
}

@Composable
private fun PeopleColdStart(
    users: UiState<List<UserSummaryResponse>>,
    onRetry: () -> Unit,
) {
    val error = users as? UiState.Error
    if (error != null) {
        logWarn("UserManagementScreen", "usersState=Error: ${error.message}")
        ErrorCard(message = error.message, onRetry = onRetry)
    } else {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.CircularProgressIndicator()
        }
    }
}

@Composable
private fun PeopleReloadStrip(
    message: String,
    enabled: Boolean,
    onRetry: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry, enabled = enabled) {
            Text("Retry")
        }
    }
}

/** Keyed People list; selection opens the grouped detail. */
@Composable
internal fun PeopleList(
    users: List<UserSummaryResponse>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    actionErrors: Map<String, String>,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(users, key = { it.id }) { user ->
            // #681 — every row renders its own errors (the pre-#681 discoverability):
            // the selected user's errors live in the detail instead, so wide mode
            // never double-renders.
            PeopleRow(
                user = user,
                selected = user.id == selectedId,
                onSelect = { onSelect(user.id) },
                errors =
                    if (user.id == selectedId) {
                        emptyList()
                    } else {
                        detailErrors(actionErrors, user)
                    },
            )
        }
    }
}

/**
 * Collapsed People row: identity + status text + concise assignment summary.
 * Long names/summaries ellipsize to one line; deactivated rows dim (D2).
 */
@Composable
internal fun PeopleRow(
    user: UserSummaryResponse,
    selected: Boolean,
    onSelect: () -> Unit,
    errors: List<String> = emptyList(),
) {
    val isDeactivated = user.status == UserStatus.INACTIVE
    Surface(
        shape = RoundedCornerShape(CornerRadius.md),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSelect)
                    .rowHover()
                    .alpha(if (isDeactivated) DEACTIVATED_ROW_ALPHA else 1f)
                    .padding(Spacing.md),
        ) {
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = user.username,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = peopleStatusLine(user),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = assignmentSummary(user.assignments),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            errors.forEach { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

internal fun peopleStatusLine(user: UserSummaryResponse): String =
    when (user.status) {
        UserStatus.ACTIVE -> "Active"

        UserStatus.INACTIVE -> "Inactive"

        // #876 — forward-compat sentinel: degrades the line, never the row.
        UserStatus.UNKNOWN -> "Unknown"
    }

/**
 * People empty states: true first use (no users at all) offers the authorized
 * create action; search/filter empties offer Clear filters.
 */
@Composable
internal fun PeopleEmpty(
    hasAnyUsers: Boolean,
    searchQuery: String,
    inviteEnabled: Boolean,
    onInvite: () -> Unit,
    onClearFilters: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (!hasAnyUsers && searchQuery.isBlank()) {
            EmptyState(message = "No users")
            if (inviteEnabled) {
                PrimaryActionButton(label = "Invite user", onClick = onInvite)
            }
        } else {
            val message =
                if (searchQuery.isBlank()) {
                    "No users match this filter"
                } else {
                    "No users match \"$searchQuery\""
                }
            EmptyState(message = message)
            TextButton(onClick = onClearFilters) {
                Text("Clear filters")
            }
        }
    }
}

internal data class PersonDetailState(
    val user: UserSummaryResponse,
    val currentUserId: String?,
    val mutationsDisabled: Boolean,
    val errors: List<String>,
    val showBack: Boolean,
)

internal data class PersonDetailCallbacks(
    val onEditRoles: () -> Unit,
    val onDeactivate: () -> Unit,
    val onReactivate: () -> Unit,
    val onEditSlot: (UserAssignmentResponse) -> Unit,
    val onRemoveAssignment: (UserAssignmentResponse) -> Unit,
    val onBack: () -> Unit,
)

/** Grouped person detail: Account, Roles, Branch assignments. */
@Composable
internal fun PersonDetail(
    state: PersonDetailState,
    callbacks: PersonDetailCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (state.showBack) {
            TextButton(onClick = callbacks.onBack) {
                Text("Back")
            }
        }
        PersonAccountGroup(
            user = state.user,
            currentUserId = state.currentUserId,
            mutationsDisabled = state.mutationsDisabled,
            onDeactivate = callbacks.onDeactivate,
            onReactivate = callbacks.onReactivate,
        )
        PersonRolesGroup(
            user = state.user,
            mutationsDisabled = state.mutationsDisabled,
            onEditRoles = callbacks.onEditRoles,
        )
        PersonAssignmentsGroup(
            user = state.user,
            mutationsDisabled = state.mutationsDisabled,
            onEditSlot = callbacks.onEditSlot,
            onRemoveAssignment = callbacks.onRemoveAssignment,
        )
        state.errors.forEach { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun PersonAccountGroup(
    user: UserSummaryResponse,
    currentUserId: String?,
    mutationsDisabled: Boolean,
    onDeactivate: () -> Unit,
    onReactivate: () -> Unit,
) {
    var menuOpen by remember(user.id) { mutableStateOf(false) }
    val isDeactivated = user.status == UserStatus.INACTIVE
    // Deactivate/Reactivate lives in the account More menu (#681), not on every
    // collapsed row: the row stays identity-only, the detail owns the lifecycle.
    val canShowLifecycle =
        if (isDeactivated) {
            true
        } else {
            user.id != currentUserId
        }
    Surface(
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Account",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (canShowLifecycle) {
                    TextButton(onClick = { menuOpen = true }, enabled = !mutationsDisabled) {
                        Text("More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (!isDeactivated) {
                            DropdownMenuItem(
                                text = { Text("Deactivate") },
                                onClick = {
                                    menuOpen = false
                                    onDeactivate()
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Reactivate") },
                                onClick = {
                                    menuOpen = false
                                    onReactivate()
                                },
                            )
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = user.username,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = peopleStatusLine(user),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            user.deactivatedAt?.let {
                Text(
                    text = "deactivated ${formatRelativeTimestamp(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isDeactivated && user.id == currentUserId) {
                Text(
                    text = "You can't deactivate your own account",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PersonRolesGroup(
    user: UserSummaryResponse,
    mutationsDisabled: Boolean,
    onEditRoles: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Roles",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                // Actions gate visibly while loads/mutations are in flight; the
                // bundle still renders (no silent-fail tap). Screen-level access
                // rides the MANAGE_USERS route gate; backend denials surface
                // inline via the action errors below.
                TextButton(onClick = onEditRoles, enabled = !mutationsDisabled) {
                    Text("Edit")
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
            Text(
                text = if (user.roles.isEmpty()) "No roles" else user.roles.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PersonAssignmentsGroup(
    user: UserSummaryResponse,
    mutationsDisabled: Boolean,
    onEditSlot: (UserAssignmentResponse) -> Unit,
    onRemoveAssignment: (UserAssignmentResponse) -> Unit,
) {
    val isDeactivated = user.status == UserStatus.INACTIVE
    Surface(
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
            Text(text = "Branch assignments", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
            if (user.assignments.isEmpty()) {
                Text(
                    text = "No branch assignments",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                user.assignments.forEach { assignment ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${assignment.branchName} — slot ${assignment.slot}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        TextButton(
                            onClick = { onEditSlot(assignment) },
                            enabled = !isDeactivated && !mutationsDisabled,
                        ) {
                            Text("Edit slot")
                        }
                        TextButton(
                            onClick = { onRemoveAssignment(assignment) },
                            enabled = !mutationsDisabled,
                        ) {
                            Text("Remove")
                        }
                    }
                }
            }
        }
    }
}
