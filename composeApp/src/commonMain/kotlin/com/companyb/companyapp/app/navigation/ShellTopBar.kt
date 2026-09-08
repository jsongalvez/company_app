package com.companyb.companyapp.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.app.drawer.HamburgerWithBadge
import com.companyb.companyapp.notification.NotificationState
import com.companyb.companyapp.ui.theme.InkSubtle

/**
 * #671 — the one compact top bar: menu trigger, parent section title, and viewed
 * branch/date context. Shared by the mobile shell and the narrow-desktop fallback so
 * both stay under one chrome contract.
 *
 * Pushed detail routes keep their single screen-owned back control (nested-Scaffold
 * per #96 Q5) — this bar adds no second back. Closing the modal drawer without
 * navigation leaves focus where the drawer system returns it; destination-heading
 * focus is screen-owned follow-up, not claimed here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellTopBar(
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot by AppSessionState.snapshot.collectAsState()
    val clock = snapshot.clock
    val current = LocalNavHostController.current.currentRoute()
    val unreadCount: Int? by NotificationState.unreadCount.collectAsState()
    val inviteCount: Int? by NotificationState.inviteCount.collectAsState()
    val badgeCount = NotificationState.badgeSum(unreadCount, inviteCount)
    val viewed =
        ShellLayoutPolicy.viewedContext(
            current = current,
            clockBranchId = clock?.branchId,
            clockBranchName = clock?.branchName,
            clockDate = clock?.operationalDate,
        )
    TopAppBar(
        title = {
            Column {
                Text(
                    text = ShellLayoutPolicy.topBarTitle(current),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val contextLine =
                    listOfNotNull(
                        viewed.branchLabel.ifBlank { null },
                        viewed.dateLabel,
                        viewed.shiftLabel,
                    ).joinToString(" · ")
                if (contextLine.isNotBlank()) {
                    Text(
                        text = contextLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSubtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            HamburgerWithBadge(
                onClick = onMenuClick,
                unreadCount = badgeCount,
            )
        },
        modifier = modifier,
    )
}
