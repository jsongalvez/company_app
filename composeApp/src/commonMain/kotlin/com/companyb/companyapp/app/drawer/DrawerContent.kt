package com.companyb.companyapp.app.drawer

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.app.AttendanceViewModel
import com.companyb.companyapp.app.DrawerItem
import com.companyb.companyapp.app.DrawerSection
import com.companyb.companyapp.app.DrawerViewModel
import com.companyb.companyapp.app.navigation.LocalNavHostController
import com.companyb.companyapp.app.navigation.NavigationContextStore
import com.companyb.companyapp.app.navigation.Route
import com.companyb.companyapp.app.navigation.ShellLayoutPolicy
import com.companyb.companyapp.app.navigation.currentRoute
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.workforce.ClockOutRequest
import com.companyb.companyapp.contracts.workforce.ClockOutResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.notification.NotificationBadge
import com.companyb.companyapp.notification.NotificationState
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.PrimaryHover
import com.companyb.companyapp.ui.theme.Spacing

/**
 * #96 Q1 + Q4 + Q7 — stateless presentational composable in commonMain.
 *
 * Per #96 Q7 slot-type seam: declared as a zero-arg `@Composable (Modifier) -> Unit` (Modifier
 * sanctioned by the Q7 follow-up "parametrize DrawerContent's internal Column with a Modifier
 * parameter for caller-supplied chrome"). The Modal slot wraps
 * `ModalDrawerSheet { DrawerContent() }`; the Permanent slot wraps
 * `PermanentNavigationDrawer(drawerContent = { DrawerContent() })`. Owns its internal Column
 * so the composable is previewable / testable without faking a ColumnScope receiver.
 *
 * Reads state directly per #96 Q1: `AppSessionState.snapshot` (user + clock branch name) +
 * `DrawerViewModel.uiState`, plus `LocalNavHostController.current` for currentRoute + on-click
 * navigation. `NotificationState.unreadCount` (live count — #96 Q6 wiring) drives the
 * Notification row badge iff `!= null && > 0` (closure of #96 Q3a gating).
 *
 * #671 — task-grouped sections (Work / Finance / Administration, the last collapsed by
 * default), parent-aware highlight (detail routes light their parent), no-op on the
 * already-active destination, and a footer carrying the signed-in display name
 * (→ Profile) plus the explicit clock-out in the current-shift area. The header shows
 * the viewed branch + operational date with the clocked-in shift as a labeled
 * secondary when different — viewing a branch never implies clocking into it.
 *
 * Caller supplies background + sizing chrome via [modifier] (#96 Q7 follow-up):
 * `PermanentNavigationDrawer`'s bare Row slot applies nothing, so the sidebar caller
 * passes `Modifier.fillMaxHeight().width(224.dp).background(surface)`; the modal
 * caller relies on `ModalDrawerSheet` defaults.
 *
 * DrawerContent remains stateless presentational per #96 Q1 — only modifiers + interaction
 * source for hover/focus tracking; no business state ownership. It does own the clock-out
 * affordance (footer row + confirm dialog) — a session-lifecycle action, wired through an
 * AttendanceViewModel remembered here (the #109 host-pattern: VM created at the composition
 * that needs it; the badge host precedent gates on isPostClockIn, the drawer content is
 * only composed post-clock-in by both shell actuals). [navigationEnabled] is false while the
 * session-create entry is submitting, so drawer navigation cannot destroy its entry-scoped draft.
 */
@Composable
fun DrawerContent(
    apiClient: ApiClient,
    modifier: Modifier = Modifier,
    navigationEnabled: Boolean = true,
    // #389 — host hook fired after every drawer-initiated navigation (item tap, clock-out
    // landing). Mobile closes its modal drawer; the desktop permanent drawer no-ops.
    onItemNavigated: () -> Unit = {},
) {
    val navController = LocalNavHostController.current
    val snapshot by AppSessionState.snapshot.collectAsState()
    val currentUser = snapshot.user
    val clock = snapshot.clock
    val attendanceId = clock?.attendanceId
    val drawerViewModel: DrawerViewModel = viewModel { DrawerViewModel() }
    val drawerUiState by drawerViewModel.uiState.collectAsState()
    val selectedRoute = navController.currentRoute()
    // #671 — Administration stays collapsed until the user opens it; the drawer must
    // not crowd working space at 1024-wide or compact viewports.
    var adminExpanded by remember { mutableStateOf(false) }
    val viewed =
        ShellLayoutPolicy.viewedContext(
            current = selectedRoute,
            clockBranchId = clock?.branchId,
            clockBranchName = clock?.branchName,
            clockDate = clock?.operationalDate,
        )

    Column(modifier = modifier) {
        DrawerHeader(
            viewedBranchLabel = viewed.branchLabel,
            viewedDateLabel = viewed.dateLabel,
            shiftLabel = viewed.shiftLabel,
        )
        // Q4 — hairline divider below header (not above — separates header from items);
        // not boxed in a card (drawer is surface-1, card-in-a-card is the noise ADR-0020 avoids).
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        // #671 P4 — the section list scrolls under a pinned header/footer: an expanded
        // Administration group at 200% text (or a short landscape window) must never
        // clip the footer holding the only shell Clock-out affordance.
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(Spacing.sm))
            DrawerNavSections(
                items = drawerUiState.drawerItems,
                selectedRoute = selectedRoute,
                navigationEnabled = navigationEnabled,
                navController = navController,
                adminExpanded = adminExpanded,
                onAdminToggle = { adminExpanded = !adminExpanded },
                onItemNavigated = onItemNavigated,
            )
        }
        DrawerFooter(
            apiClient = apiClient,
            displayName = currentUser?.displayName ?: currentUser?.username,
            selectedRoute = selectedRoute,
            navigationEnabled = navigationEnabled,
            attendanceId = attendanceId,
            branchName = clock?.branchName,
            navController = navController,
            onItemNavigated = onItemNavigated,
        )
    }
}

@Composable
// #671 — 7-param section fan-out stays whole (declarative-UI signature per #535:
// one leg per shell-owned input; bundling would manufacture a DTO).
private fun DrawerNavSections(
    items: List<DrawerItem>,
    selectedRoute: Route?,
    navigationEnabled: Boolean,
    navController: NavHostController,
    adminExpanded: Boolean,
    onAdminToggle: () -> Unit,
    onItemNavigated: () -> Unit,
) {
    val unreadCount: Int? by NotificationState.unreadCount.collectAsState()
    val inviteCount: Int? by NotificationState.inviteCount.collectAsState()
    val badgeCount = NotificationState.badgeSum(unreadCount, inviteCount)
    val navigate: (DrawerItem) -> Unit = { item ->
        // #671 — selecting the already-active destination is a true no-op: no navigate,
        // no drawer close, no state reset. Deep-linked variants (Dashboard(branchId,
        // date) vs Dashboard()) are distinct destinations, so home-from-relief-day
        // still navigates.
        if (!ShellLayoutPolicy.isSameDestination(selectedRoute, item.route) && navigationEnabled) {
            // #389 — section-switch semantics: collapse to the Dashboard root
            // before pushing, so back from a section returns straight home and
            // repeated taps never stack duplicates. The Dashboard item itself
            // pops its existing instance (a relief deep-link panel included)
            // and pushes a fresh home — popUpTo matches the destination pattern,
            // not the entry args (SessionCreate landing precedent).
            navController.navigate(item.route) {
                popUpTo(Route.Dashboard()) { inclusive = item.route is Route.Dashboard }
            }
            onItemNavigated()
        }
    }
    DrawerSectionGroup(
        heading = DrawerSection.WORK.heading,
        items = items.filter { it.visible && it.section == DrawerSection.WORK },
        selectedRoute = selectedRoute,
        navigationEnabled = navigationEnabled,
        badgeCount = badgeCount,
        onItemClicked = navigate,
    )
    DrawerSectionGroup(
        heading = DrawerSection.FINANCE.heading,
        items = items.filter { it.visible && it.section == DrawerSection.FINANCE },
        selectedRoute = selectedRoute,
        navigationEnabled = navigationEnabled,
        badgeCount = badgeCount,
        onItemClicked = navigate,
    )
    AdministrationGroup(
        items = items.filter { it.visible && it.section == DrawerSection.ADMINISTRATION },
        selectedRoute = selectedRoute,
        navigationEnabled = navigationEnabled,
        badgeCount = badgeCount,
        expanded = adminExpanded,
        onToggle = onAdminToggle,
        onItemClicked = navigate,
    )
}

@Composable
private fun DrawerSectionGroup(
    heading: String,
    items: List<DrawerItem>,
    selectedRoute: Route?,
    navigationEnabled: Boolean,
    badgeCount: Int?,
    onItemClicked: (DrawerItem) -> Unit,
) {
    if (items.isEmpty()) return
    SectionHeading(text = heading)
    val activeParent = selectedRoute?.let { ShellLayoutPolicy.parentFor(it) }
    items.forEach { item ->
        DrawerRow(
            item = item,
            // #671 — parent-aware highlight: detail/pushed routes light their parent
            // (exact route equality stranded deep-linked details with no selection).
            isSelected = ShellLayoutPolicy.parentFor(item.route) == activeParent,
            enabled = navigationEnabled,
            onItemClicked = { onItemClicked(item) },
            badge =
                if (item.route is Route.Notifications && badgeCount != null && badgeCount > 0) {
                    { NotificationBadge(count = badgeCount) }
                } else {
                    null
                },
        )
    }
}

@Composable
// #671 — 7-param admin group stays whole (same #535 declarative-UI rationale as above).
private fun AdministrationGroup(
    items: List<DrawerItem>,
    selectedRoute: Route?,
    navigationEnabled: Boolean,
    badgeCount: Int?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onItemClicked: (DrawerItem) -> Unit,
) {
    if (items.isEmpty()) return
    // #671 — collapsed by default so low-frequency admin surfaces never crowd the
    // daily workspace; a focusable toggle (keyboard traversal included).
    TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (expanded) "Administration — hide" else "Administration — show",
            style = MaterialTheme.typography.labelMedium,
            color = InkSubtle,
        )
    }
    if (expanded) {
        DrawerSectionGroup(
            heading = DrawerSection.ADMINISTRATION.heading,
            items = items,
            selectedRoute = selectedRoute,
            navigationEnabled = navigationEnabled,
            badgeCount = badgeCount,
            onItemClicked = onItemClicked,
        )
    } else {
        // A detail opened from history/deep link keeps its parent visible even while
        // the group is collapsed — the highlight, not the row, carries orientation.
        val activeParent = selectedRoute?.let { ShellLayoutPolicy.parentFor(it) }
        items.firstOrNull { ShellLayoutPolicy.parentFor(it.route) == activeParent }?.let { active ->
            DrawerRow(
                item = active,
                isSelected = true,
                enabled = navigationEnabled,
                onItemClicked = { onItemClicked(active) },
                badge = null,
            )
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = InkSubtle,
        modifier = Modifier.semantics { heading() }.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
    )
}

@Composable
private fun DrawerHeader(
    viewedBranchLabel: String,
    viewedDateLabel: String?,
    shiftLabel: String?,
) {
    // #671 — who-then-where stays, but the subject is the VIEWED branch + operational
    // date (server/domain-authoritative, never device midnight). The clocked-in shift
    // appears only as an explicitly labeled secondary when it differs — viewing a
    // branch never implies clocking into it.
    Column(Modifier.padding(Spacing.lg)) {
        // #671 P4 — long branch names (and 200% text on the 224dp rail) ellipsize
        // instead of pushing the date/shift lines out; the header never blanks (the
        // drawer only composes post-clock-in, but the clock-null recomposition window
        // still passes through here).
        if (viewedBranchLabel.isNotBlank()) {
            Text(
                text = viewedBranchLabel,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (viewedDateLabel != null) {
            Text(
                text = viewedDateLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = InkSubtle,
            )
        }
        if (shiftLabel != null) {
            Text(
                text = shiftLabel,
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        }
    }
}

@Composable
// #671 — 8-param footer stays whole (same #535 declarative-UI rationale as above).
private fun ColumnScope.DrawerFooter(
    apiClient: ApiClient,
    displayName: String?,
    selectedRoute: Route?,
    navigationEnabled: Boolean,
    attendanceId: String?,
    branchName: String?,
    navController: NavHostController,
    onItemNavigated: () -> Unit,
) {
    Spacer(Modifier.weight(1f))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    // #671 — footer identity: the signed-in display name opens Profile (no-op
    // guarded like every other destination).
    NavigationDrawerItem(
        label = { Text(displayName?.ifBlank { null } ?: "") },
        selected = selectedRoute?.let { ShellLayoutPolicy.parentFor(it) == Route.Profile } == true,
        modifier =
            Modifier
                .alpha(if (navigationEnabled) 1f else 0.5f)
                .semantics { if (!navigationEnabled) disabled() },
        onClick = {
            // #671 — same-destination taps are a true no-op (not even a drawer close).
            if (!ShellLayoutPolicy.isSameDestination(selectedRoute, Route.Profile) && navigationEnabled) {
                navController.navigate(Route.Profile) {
                    popUpTo(Route.Dashboard()) { inclusive = false }
                }
                onItemNavigated()
            }
        },
        colors =
            NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                unselectedContainerColor = MaterialTheme.colorScheme.surface,
                selectedTextColor = PrimaryHover,
                unselectedTextColor = InkSubtle,
                selectedBadgeColor = PrimaryHover,
                unselectedBadgeColor = InkSubtle,
            ),
    )
    ClockOutSection(
        apiClient = apiClient,
        attendanceId = attendanceId,
        branchName = branchName,
        navigationEnabled = navigationEnabled,
        onClockedOut = {
            AppSessionState.clearClockState()
            NotificationState.clear()
            // #671 — clock-out is access loss for branch data: retained section
            // anchors leave with the shift (fresh key on next clock-in anyway).
            NavigationContextStore.clear()
            navController.navigate(Route.BranchSelect) {
                popUpTo(0) { inclusive = true }
            }
            onItemNavigated()
        },
    )
}

@Composable
private fun ColumnScope.ClockOutSection(
    apiClient: ApiClient,
    attendanceId: String?,
    branchName: String?,
    navigationEnabled: Boolean,
    onClockedOut: () -> Unit,
) {
    val attendanceViewModel = remember { AttendanceViewModel(apiClient) }
    val clockOutState by attendanceViewModel.clockOutState.collectAsState()
    var showClockOutDialog by remember { mutableStateOf(false) }

    // #147 — successful clock-out clears session state and lands on BranchSelect.
    LaunchedEffect(clockOutState) {
        if (clockOutState is UiState.Success) onClockedOut()
    }

    // Hide footer when attendance is absent; fail closed.
    if (attendanceId != null) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        NavigationDrawerItem(
            label = { Text("Clock out") },
            selected = false,
            modifier =
                Modifier
                    .alpha(if (navigationEnabled) 1f else 0.5f)
                    .semantics { if (!navigationEnabled) disabled() },
            onClick = {
                if (navigationEnabled) {
                    attendanceViewModel.resetClockOut()
                    showClockOutDialog = true
                }
            },
            colors =
                NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                    unselectedContainerColor = MaterialTheme.colorScheme.surface,
                    selectedTextColor = InkSubtle,
                    unselectedTextColor = InkSubtle,
                    selectedBadgeColor = InkSubtle,
                    unselectedBadgeColor = InkSubtle,
                ),
        )
    }

    if (showClockOutDialog) {
        ClockOutDialog(
            branchName = branchName,
            clockOutState = clockOutState,
            navigationEnabled = navigationEnabled,
            onConfirm = {
                if (!navigationEnabled || attendanceViewModel.clockOutState.value is UiState.Loading) {
                    return@ClockOutDialog
                }
                val id = attendanceId
                if (id != null) attendanceViewModel.clockOut(ClockOutRequest(attendanceId = id))
            },
            onDismiss = {
                if (attendanceViewModel.clockOutState.value !is UiState.Loading) {
                    showClockOutDialog = false
                }
            },
        )
    }
}

@Composable
private fun ClockOutDialog(
    branchName: String?,
    clockOutState: UiState<ClockOutResponse>,
    navigationEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clock out?") },
        text = {
            Column {
                Text(
                    text =
                        "End your shift at ${branchName ?: "this branch"}? " +
                            "You'll return to the branch list.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (clockOutState is UiState.Error) {
                    Text(
                        text = clockOutState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = navigationEnabled && clockOutState !is UiState.Loading,
            ) {
                Text("Clock out")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/**
 * #96 Q2 — Drawer row with the item-styling triad (default / hover=focus-visible / selected).
 *
 * Material3 `NavigationDrawerItemDefaults.colors()` exposes only `selected` vs `unselected`; the
 * "hover = focus-visible (inactive only)" state (#96 Q2) requires hoisting an
 * `interactionSource` and recomputing the unselected colors at site when hovered or focused.
 * Selected wins over focus (selected-and-focused keeps selected styling — #96 Q2 final guard).
 *
 * Color-slot mappings (LinearTheme.kt → DESIGN.md tokens):
 * - `MaterialTheme.colorScheme.secondary`        = surface-3 / #18191A   (selected container)
 * - `MaterialTheme.colorScheme.surfaceVariant`   = surface-2 / #141516   (hover/focus container)
 * - `MaterialTheme.colorScheme.surface`          = surface-1 / #0F1011   (default container)
 * - `MaterialTheme.colorScheme.onSurfaceVariant` = ink-muted / #D0D6E0  (hover/focus text/badge)
 * - theme `InkSubtle`                            = ink-subtle / #8A8F98  (default text/badge)
 * - theme `PrimaryHover`                         = primary-hover / #828FFF (selected text/badge —
 *   DESIGN.md:8 names the token; LinearTheme exposes it since #398, replacing the raw literal
 *   that #107 deferred to theme hardening)
 */
@Composable
private fun DrawerRow(
    item: DrawerItem,
    isSelected: Boolean,
    enabled: Boolean,
    onItemClicked: (Route) -> Unit,
    badge: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHighlighted = !isSelected && (isHovered || isFocused)
    // Hoist the unselected triad once (Q2 review feedback) — repeated `if (isHighlighted)`
    // for the same three-rail container/text/badge triad duplicated the computation trace.
    val unselectedFg =
        if (isHighlighted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            InkSubtle
        }
    val unselectedBg =
        if (isHighlighted) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        }
    // intentionally not primary-container: lavender is reserved as single accent, see DESIGN.md:6
    // selected text uses the primary-hover tier (#828FFF, LinearTheme.PrimaryHover) to clear AA
    // at 14sp — primary undershoots 4.5:1
    NavigationDrawerItem(
        label = { Text(item.label) },
        selected = isSelected,
        modifier =
            Modifier
                .alpha(if (enabled) 1f else 0.5f)
                .semantics { if (!enabled) disabled() },
        onClick = { if (enabled) onItemClicked(item.route) },
        colors =
            NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                unselectedContainerColor = unselectedBg,
                selectedTextColor = PrimaryHover,
                unselectedTextColor = unselectedFg,
                selectedBadgeColor = PrimaryHover,
                unselectedBadgeColor = unselectedFg,
            ),
        interactionSource = interactionSource,
        badge = badge,
    )
}
