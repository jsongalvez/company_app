package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.dto.NotificationResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.formatRelativeTimestamp
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.NotificationViewModel
import com.companyb.companyapp.viewmodel.UiState

// D1/D3: unread queue (locked #102). Screen renders unread rows at full emphasis + a dimmed,
// in-memory Read section (rows marked read this session). Platform tap behavior differs only in
// whether the tap navigates (mobile) or not (desktop) — the split lives at the NavHost call
// site (ADR-0020 smallest-divergent-subtree), this composable stays platform-agnostic.
@Composable
fun NotificationsScreen(
    viewModel: NotificationViewModel,
    onNotificationClick: (NotificationResponse) -> Unit,
) {
    val notificationsState by viewModel.notifications.collectAsState()
    val readThisSession by viewModel.readThisSession.collectAsState()
    val markReadState by viewModel.markReadResult.collectAsState()
    val markAllState by viewModel.markAllResult.collectAsState()

    LaunchedEffect(Unit) {
        logInfo("NotificationsScreen", "composable entered (first composition)")
        viewModel.loadUnreadNotifications()
    }

    // Log state changes, not composition passes (LoginScreen precedent — LaunchedEffect keyed on
    // the state, so a recomposition doesn't re-log an unchanged Error).
    LaunchedEffect(notificationsState) {
        val error = notificationsState as? UiState.Error ?: return@LaunchedEffect
        logWarn("NotificationsScreen", "notificationsState=Error: ${error.message}")
    }

    // Action failures surface inline (#135 round-1 precedent — silent network-failure paths are a
    // bug class, not a design choice): a failed markRead/markAll must be visible, and the next
    // attempt's Loading pre-set clears the line automatically. 404-on-markRead is NOT an error —
    // the VM handles it internally (absent-row defense reload), so it never reaches this state.
    val markReadError = (markReadState as? UiState.Error)?.message
    val markAllError = (markAllState as? UiState.Error)?.message
    LaunchedEffect(markReadError) {
        markReadError?.let { logWarn("NotificationsScreen", "markRead=Error: $it") }
    }
    LaunchedEffect(markAllError) {
        markAllError?.let { logWarn("NotificationsScreen", "markAll=Error: $it") }
    }

    // D5 + #97 Q5 silent-refresh: cold-start spinner only while there's nothing to show; once a
    // list has loaded, a reload (re-entry, post-markAll arrival) must not flash a spinner over
    // it. Screen-side last-results cache, the ClientsScreen precedent (#113 D2 keep-last-results
    // — assigned on Success, rendered during Loading/Error). Cold start keeps the spinner:
    // cache is null until the first Success lands.
    var lastUnread by remember { mutableStateOf<List<NotificationResponse>?>(null) }
    (notificationsState as? UiState.Success<List<NotificationResponse>>)?.let { lastUnread = it.data }
    val unread = lastUnread.orEmpty()
    val hasContent = unread.isNotEmpty() || readThisSession.isNotEmpty()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.titleLarge,
            )
            // D3: Mark all visible iff unread > 0; disabled while in-flight.
            if (unread.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.markAllRead() },
                    enabled = markAllState !is UiState.Loading,
                ) {
                    Text("Mark all")
                }
            }
        }

        if (markAllError != null || markReadError != null) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                markAllError?.let { ActionErrorLine(it) }
                markReadError?.let { ActionErrorLine(it) }
            }
        }

        when (val state = notificationsState) {
            is UiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Loading -> {
                if (hasContent) {
                    NotificationList(
                        unread = unread,
                        readThisSession = readThisSession,
                        onNotificationClick = onNotificationClick,
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                if (hasContent) {
                    NotificationList(
                        unread = unread,
                        readThisSession = readThisSession,
                        onNotificationClick = onNotificationClick,
                    )
                } else {
                    // D5: in-place error card + retry.
                    ErrorCard(
                        message = state.message,
                        onRetry = { viewModel.loadUnreadNotifications() },
                    )
                }
            }

            is UiState.Success -> {
                // D4: zero-state when nothing unread and nothing marked read this session.
                if (hasContent) {
                    NotificationList(
                        unread = state.data,
                        readThisSession = readThisSession,
                        onNotificationClick = onNotificationClick,
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "All caught up",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationList(
    unread: List<NotificationResponse>,
    readThisSession: List<NotificationResponse>,
    onNotificationClick: (NotificationResponse) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        if (unread.isNotEmpty()) {
            SectionLabel("Unread (${unread.size})")
            unread.forEach { notification ->
                NotificationRow(
                    notification = notification,
                    dimmed = false,
                    onClick = { onNotificationClick(notification) },
                )
            }
        }
        if (readThisSession.isNotEmpty()) {
            SectionLabel("Read (${readThisSession.size})")
            readThisSession.forEach { notification ->
                NotificationRow(
                    notification = notification,
                    dimmed = true,
                    onClick = null,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
    )
}

@Composable
private fun ActionErrorLine(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

// D2: message (primary) + timestamp (secondary); whole row is the tap target. Unread rows at
// full emphasis; Read rows dimmed (ink-muted text + surface-1 row bg) and non-interactive
// (already read — re-marking is a no-op PATCH and the session nav adds no new information).
@Composable
private fun NotificationRow(
    notification: NotificationResponse,
    dimmed: Boolean,
    onClick: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(CornerRadius.sm)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xxs)
                .then(
                    if (dimmed) {
                        Modifier.background(MaterialTheme.colorScheme.surface, shape)
                    } else {
                        Modifier
                    },
                ).then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ).padding(
                    horizontal = Spacing.sm,
                    vertical = Spacing.xs,
                ),
    ) {
        Text(
            text = notification.message,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (dimmed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
        Text(
            text = formatRelativeTimestamp(notification.createdAt, logTag = "NotificationsScreen"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (!dimmed) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}
