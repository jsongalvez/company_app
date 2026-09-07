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
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.app.AttendanceViewModel
import com.companyb.companyapp.app.DrawerItem
import com.companyb.companyapp.app.DrawerViewModel
import com.companyb.companyapp.app.navigation.LocalNavHostController
import com.companyb.companyapp.app.navigation.Route
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
 * Caller supplies background + sizing chrome via [modifier] (#96 Q7 follow-up):
 * `PermanentNavigationDrawer`'s bare Row slot applies nothing, so the desktop caller passes
 * `Modifier.fillMaxHeight().width(360.dp).background(MaterialTheme.colorScheme.surface)`.
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
    val selectedBranchName = snapshot.clock?.branchName
    val attendanceId = snapshot.clock?.attendanceId
    val drawerViewModel: DrawerViewModel = viewModel { DrawerViewModel() }
    val drawerUiState by drawerViewModel.uiState.collectAsState()

    Column(modifier = modifier) {
        DrawerHeader(
            username = currentUser?.username,
            branchName = selectedBranchName,
        )
        // Q4 — hairline divider below header (not above — separates header from items);
        // not boxed in a card (drawer is surface-1, card-in-a-card is the noise ADR-0020 avoids).
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(Spacing.sm))
        DrawerNavItems(
            items = drawerUiState.drawerItems,
            navigationEnabled = navigationEnabled,
            navController = navController,
            onItemNavigated = onItemNavigated,
        )
        ClockOutSection(
            apiClient = apiClient,
            attendanceId = attendanceId,
            branchName = selectedBranchName,
            navigationEnabled = navigationEnabled,
            onClockedOut = {
                AppSessionState.clearClockState()
                NotificationState.clear()
                navController.navigate(Route.BranchSelect) {
                    popUpTo(0) { inclusive = true }
                }
                onItemNavigated()
            },
        )
    }
}

@Composable
private fun DrawerNavItems(
    items: List<DrawerItem>,
    navigationEnabled: Boolean,
    navController: NavHostController,
    onItemNavigated: () -> Unit,
) {
    val selectedRoute = navController.currentRoute()
    val unreadCount: Int? by NotificationState.unreadCount.collectAsState()
    val inviteCount: Int? by NotificationState.inviteCount.collectAsState()
    val badgeCount = NotificationState.badgeSum(unreadCount, inviteCount)
    items
        .filter { it.visible }
        .forEach { item ->
            val isSelected = item.route == selectedRoute
            val notificationBadge: (@Composable () -> Unit)? =
                if (item.route is Route.Notifications && badgeCount != null && badgeCount > 0) {
                    { NotificationBadge(count = badgeCount) }
                } else {
                    null
                }
            DrawerRow(
                item = item,
                isSelected = isSelected,
                enabled = navigationEnabled,
                onItemClicked = { route ->
                    // #389 — section-switch semantics: collapse to the Dashboard root
                    // before pushing, so back from a section returns straight home and
                    // repeated taps never stack duplicates. The Dashboard item itself
                    // pops its existing instance (a relief deep-link panel included)
                    // and pushes a fresh home — popUpTo matches the destination pattern,
                    // not the entry args (SessionCreate landing precedent).
                    if (navigationEnabled) {
                        navController.navigate(route) {
                            popUpTo(Route.Dashboard()) { inclusive = route is Route.Dashboard }
                        }
                        onItemNavigated()
                    }
                },
                badge = notificationBadge,
            )
        }
}

@Composable
private fun DrawerHeader(
    username: String?,
    branchName: String?,
) {
    // Q4 — header: username above branchName (who-then-where), no app name
    // (identity-over-branding axis per Q4a; app name redundant with desktop window chrome
    // + Android launcher label). Typography ladder encodes hierarchy: titleLarge = 22sp
    // semibold (CardTitle slot per LinearTheme.kt:104) over bodyMedium = 14sp regular.
    Column(Modifier.padding(Spacing.lg)) {
        Text(
            text = username ?: "",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // ink-subtle — #96 Q4b; LinearTheme.kt exposes InkSubtle public for this slot.
        Text(
            text = branchName ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSubtle,
        )
    }
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
        Spacer(Modifier.weight(1f))
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
