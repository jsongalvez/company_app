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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.dto.NotificationResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.NotificationViewModel
import com.companyb.companyapp.viewmodel.UiState
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

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

    // D5: cold-start spinner only while there's nothing to show. Loading/Error with prior data
    // keep rendering the last successful list (silent-refresh axis from #97 Q5) — the reload
    // markAllRead triggers after a concurrent arrival must not flash a spinner over the list.
    val unread = (notificationsState as? UiState.Success<List<NotificationResponse>>)?.data.orEmpty()
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
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(Spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(CornerRadius.md),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(Spacing.md),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                TextButton(onClick = { viewModel.loadUnreadNotifications() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
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
            text = formatNotificationTimestamp(notification.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (!dimmed) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

// D2 timestamp: relative under 24h ("2m ago", "3h ago"), absolute date past 24h ("Aug 4").
// Absolute format renders in Asia/Manila — the backend writes all domain timestamps in this
// zone (see backend/AGENTS.md), so a device outside Manila still sees the notification's
// business date.
private val notificationDisplayZone = TimeZone.of("Asia/Manila")

private val notificationAbsoluteFormat: DateTimeFormat<LocalDateTime> =
    LocalDateTime.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        dayOfMonth(Padding.NONE)
    }

internal fun formatNotificationTimestamp(
    createdAtIso: String,
    now: Instant = Clock.System.now(),
): String {
    val createdAt = runCatching { Instant.parse(createdAtIso) }.getOrNull()
    if (createdAt == null) {
        // malformed server timestamp = handled data error; blank rather than crash the row
        logWarn("NotificationsScreen", "unparseable createdAt: $createdAtIso")
        return ""
    }
    val elapsed = now - createdAt
    return when {
        elapsed < 1.minutes -> "now"
        elapsed < 1.hours -> "${elapsed.inWholeMinutes}m ago"
        elapsed < 24.hours -> "${elapsed.inWholeHours}h ago"
        else -> createdAt.toLocalDateTime(notificationDisplayZone).format(notificationAbsoluteFormat)
    }
}
