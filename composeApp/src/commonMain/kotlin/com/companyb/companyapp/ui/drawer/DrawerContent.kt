package com.companyb.companyapp.ui.drawer

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.companyb.companyapp.navigation.LocalNavHostController
import com.companyb.companyapp.navigation.Route
import com.companyb.companyapp.navigation.currentRoute
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.viewmodel.DrawerItem
import com.companyb.companyapp.viewmodel.DrawerViewModel

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
 * Reads state directly per #96 Q1: `SessionState.currentUser` + `selectedBranchName` +
 * `DrawerViewModel.uiState`, plus `LocalNavHostController.current` for currentRoute + on-click
 * navigation. `NotificationState.unreadCount` lands in ticket B; null until then, so no badge
 * renders (closure of #96 Q3a "iff > 0" gating).
 *
 * Caller supplies background + sizing chrome via [modifier] (#96 Q7 follow-up):
 * `PermanentNavigationDrawer`'s bare Row slot applies nothing, so the desktop caller passes
 * `Modifier.fillMaxHeight().width(360.dp).background(MaterialTheme.colorScheme.surface)`.
 *
 * DrawerContent remains stateless presentational per #96 Q1 — only modifiers + interaction
 * source for hover/focus tracking; no business state ownership.
 */
@Composable
fun DrawerContent(modifier: Modifier = Modifier) {
    val navController = LocalNavHostController.current
    val currentUser by SessionState.currentUser.collectAsState()
    val selectedBranchName by SessionState.selectedBranchName.collectAsState()
    val drawerViewModel = remember { DrawerViewModel() }
    val drawerUiState by drawerViewModel.uiState.collectAsState()
    // NotificationState.unreadCount wiring lands in ticket B; null until then.
    // Q3a render iff `unreadCount != null && unreadCount > 0` ⟹ false until wired ⟹ no badge.
    val unreadCount: Int? = null
    val selectedRoute = navController.currentRoute()

    Column(modifier = modifier) {
        // Q4 — header: username above selectedBranchName (who-then-where), no app name
        // (identity-over-branding axis per Q4a; app name redundant with desktop window chrome
        // + Android launcher label). Typography ladder encodes hierarchy: titleLarge = 22sp
        // semibold (CardTitle slot per LinearTheme.kt:104) over bodyMedium = 14sp regular.
        Column(Modifier.padding(Spacing.lg)) {
            Text(
                text = currentUser?.username ?: "",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // ink-subtle — #96 Q4b; LinearTheme.kt exposes InkSubtle public for this slot.
            Text(
                text = selectedBranchName ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSubtle,
            )
        }
        // Q4 — hairline divider below header (not above — separates header from items);
        // not boxed in a card (drawer is surface-1, card-in-a-card is the noise ADR-0020 avoids).
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(Spacing.sm))
        drawerUiState.drawerItems
            .filter { it.visible }
            .forEach { item ->
                val isSelected = item.route == selectedRoute
                val notificationBadge: (@Composable () -> Unit)? =
                    if (item.route is Route.Notifications && unreadCount != null && unreadCount > 0) {
                        { NotificationBadge(count = unreadCount) }
                    } else {
                        null
                    }
                DrawerRow(
                    item = item,
                    isSelected = isSelected,
                    onItemClicked = { route -> navController.navigate(route) },
                    badge = notificationBadge,
                )
            }
    }
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
 * - raw `Color(0xFF828FFF)` = primary-hover brighter lavender (selected text/badge) — DESIGN.md:8
 *   names `primary-hover`, but LinearTheme.kt has no slot for it (fog, pending theme hardening;
 *   #107 body explicitly puts PrimaryHover migration out-of-scope for this ticket)
 */
@Composable
private fun DrawerRow(
    item: DrawerItem,
    isSelected: Boolean,
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
    // One raw-literal occurrence per Q2 body ("at the call site"). PrimaryHover token migration
    // to LinearTheme.kt is fog/out-of-scope for #107; raw Color(0xFF828FFF) named arg.
    val primaryHover = Color(0xFF828FFF)

    // intentionally not primary-container: lavender is reserved as single accent, see DESIGN.md:6
    // selected text uses #828FFF (primary-hover tier) to clear AA at 14sp — primary undershoots 4.5:1
    NavigationDrawerItem(
        label = { Text(item.label) },
        selected = isSelected,
        onClick = { onItemClicked(item.route) },
        colors =
            NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                unselectedContainerColor = unselectedBg,
                selectedTextColor = primaryHover,
                unselectedTextColor = unselectedFg,
                selectedBadgeColor = primaryHover,
                unselectedBadgeColor = unselectedFg,
            ),
        interactionSource = interactionSource,
        badge = badge,
    )
}
