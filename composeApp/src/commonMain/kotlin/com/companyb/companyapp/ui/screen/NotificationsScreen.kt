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
import com.companyb.companyapp.domain.ReliefInviteStatus
import com.companyb.companyapp.dto.NotificationResponse
import com.companyb.companyapp.dto.ReliefInviteResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover
import com.companyb.companyapp.util.formatRelativeTimestamp
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.NotificationViewModel
import com.companyb.companyapp.viewmodel.ReliefInviteViewModel
import com.companyb.companyapp.viewmodel.UiState

// D1/D3: unread queue (locked #102). Screen renders unread rows at full emphasis + a dimmed,
// in-memory Read section (rows marked read this session). Platform tap behavior differs only in
// whether the tap navigates (mobile) or not (desktop) — the split lives at the NavHost call
// site (ADR-0020 smallest-divergent-subtree), this composable stays platform-agnostic.
//
// #160 — a "Relief invites" section (#159 Q5, Option A): branch-initiated invites render above
// the unread list until RESOLVED, not until read (read semantics never fight action semantics;
// the notification table stays untouched). Pending rows carry Accept/Decline; a pending invite
// whose day is past renders "expired" (day-state is the expiry — no cron). The badge poller
// counts the same actionable rows.
@Composable
fun NotificationsScreen(
    viewModel: NotificationViewModel,
    reliefInviteViewModel: ReliefInviteViewModel,
    onNotificationClick: (NotificationResponse) -> Unit,
) {
    val notificationsState by viewModel.notifications.collectAsState()
    val freshestUnread by viewModel.freshestNotifications.collectAsState()
    val readThisSession by viewModel.readThisSession.collectAsState()
    val historyState by viewModel.history.collectAsState()
    val markReadState by viewModel.markReadResult.collectAsState()
    val markAllState by viewModel.markAllResult.collectAsState()

    val receivedState by reliefInviteViewModel.received.collectAsState()
    val freshestReceived by reliefInviteViewModel.freshestReceived.collectAsState()
    val acceptState by reliefInviteViewModel.acceptResult.collectAsState()
    val declineState by reliefInviteViewModel.declineResult.collectAsState()

    LaunchedEffect(Unit) {
        logInfo("NotificationsScreen", "composable entered (first composition)")
        viewModel.loadUnreadNotifications()
        viewModel.loadHistory()
        reliefInviteViewModel.loadReceived()
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
    val acceptError = (acceptState as? UiState.Error)?.message
    val declineError = (declineState as? UiState.Error)?.message
    val historyError = (historyState as? UiState.Error)?.message
    val receivedError = receivedInvitesErrorLine(receivedState)
    // ComplexCondition carve-out (#462 burn): the 4-way error OR lives in a named val so the
    // render gate below stays a single condition.
    val hasActionError = markAllError != null || markReadError != null || acceptError != null || declineError != null
    LaunchedEffect(markReadError) {
        markReadError?.let { logWarn("NotificationsScreen", "markRead=Error: $it") }
    }
    LaunchedEffect(markAllError) {
        markAllError?.let { logWarn("NotificationsScreen", "markAll=Error: $it") }
    }
    LaunchedEffect(acceptError) {
        acceptError?.let { logWarn("NotificationsScreen", "inviteAccept=Error: $it") }
    }
    LaunchedEffect(declineError) {
        declineError?.let { logWarn("NotificationsScreen", "inviteDecline=Error: $it") }
    }
    LaunchedEffect(historyError) {
        historyError?.let { logWarn("NotificationsScreen", "history=Error: $it") }
    }
    LaunchedEffect(receivedError) {
        receivedError?.let { logWarn("NotificationsScreen", "receivedInvites=Error: $it") }
    }

    // D5 + #97 Q5 silent-refresh: cold-start spinner only while there's nothing to show; once a
    // list has content, a reload (re-entry, post-markAll arrival) must not flash a spinner over
    // it. `unread` derives from the VM's freshest flow (keep-last-results — the #162 KeepLast
    // unifier; VM-side so it survives composition re-entries; audit #141 pass-5). Cold start
    // keeps the spinner: the freshest flow is null until the first Success lands. The
    // empty-list reload case (All caught up → re-entry → reload) still flashes the spinner —
    // nothing is on screen, so D5's "nothing to show → spinner" clause covers it.
    val unread = freshestUnread.orEmpty()
    // #356 — server-backed history: every past row, read + unread, newest first. Rows the live
    // sections already render are filtered out so nothing appears twice; what remains is
    // display-only (tap-to-read stays the unread queue's job).
    val historyRows = (historyState as? UiState.Success)?.data.orEmpty()
    val visibleHistory =
        historyRows.filterNot { row ->
            unread.any { it.id == row.id } || readThisSession.any { it.id == row.id }
        }
    val hasContent = unread.isNotEmpty() || readThisSession.isNotEmpty() || visibleHistory.isNotEmpty()

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

        if (hasActionError) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                markAllError?.let { ActionErrorLine(it) }
                markReadError?.let { ActionErrorLine(it) }
                acceptError?.let { ActionErrorLine(it) }
                declineError?.let { ActionErrorLine(it) }
            }
        }

        // #356 — history load failure is its own inline line with its own retry; it must not
        // masquerade as an unread-queue failure (that queue has the full ErrorCard path).
        if (historyError != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionErrorLine(historyError)
                TextButton(onClick = { viewModel.loadHistory() }) {
                    Text("Retry")
                }
            }
        }

        // #160 — the invites section renders above the unread list: invites are time-bound
        // actions (Accept/Decline), the unread queue is reading material. Keep-last (VM-side,
        // the #143 shape): the section survives reloads; resolved rows leave it (the row
        // renders until resolved, not until read).
        //
        // #410 — a failed received-invites load is always visible with its own Retry (the
        // historyError row shape): with no cached rows the strip is the section's only trace
        // (the old code collapsed it silently — Accept/Decline vanished without signal); with
        // cached rows it sits above them so keep-last never masquerades as fresh. The next
        // load's Loading pre-set clears the strip automatically.
        val received = freshestReceived.orEmpty()
        val today = currentOperationalDate()
        val inviteActionsBusy = acceptState is UiState.Loading || declineState is UiState.Loading
        if (receivedError != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionErrorLine(receivedError)
                TextButton(onClick = { reliefInviteViewModel.loadReceived() }) {
                    Text("Retry")
                }
            }
        }
        if (received.isNotEmpty()) {
            ReliefInvitesSection(
                invites = received,
                today = today,
                busy = inviteActionsBusy,
                onAccept = { id -> reliefInviteViewModel.acceptInvite(id) },
                onDecline = { id -> reliefInviteViewModel.declineInvite(id) },
            )
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
                        history = visibleHistory,
                        onNotificationClick = onNotificationClick,
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                // #410 — with keep-last content on screen the failure degrades to an inline
                // retry strip above the list (the stale rows stay; they must not masquerade
                // as fresh); a failure with nothing to show keeps the in-place error card.
                NotificationsErrorBody(
                    refreshError = unreadRefreshErrorLine(state, hasContent),
                    message = state.message,
                    onRetry = { viewModel.loadUnreadNotifications() },
                    content = {
                        NotificationList(
                            unread = unread,
                            readThisSession = readThisSession,
                            history = visibleHistory,
                            onNotificationClick = onNotificationClick,
                        )
                    },
                )
            }

            is UiState.Success -> {
                // D4: zero-state when nothing unread, nothing marked read this session, and no
                // history rows — "All caught up" must not hide a populated history (#356).
                if (hasContent) {
                    NotificationList(
                        unread = state.data,
                        readThisSession = readThisSession,
                        history = visibleHistory,
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
private fun NotificationsErrorBody(
    refreshError: String?,
    message: String,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (refreshError != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionErrorLine(refreshError)
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
        content()
    } else {
        // D5: in-place error card + retry.
        ErrorCard(
            message = message,
            onRetry = onRetry,
        )
    }
}

@Composable
private fun NotificationList(
    unread: List<NotificationResponse>,
    readThisSession: List<NotificationResponse>,
    history: List<NotificationResponse>,
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
        // #356 — earlier rows stay findable indefinitely (read rows are never deleted server-
        // side; they carry session access). Display-only: dimmed, no tap target.
        if (history.isNotEmpty()) {
            SectionLabel("Earlier (${history.size})")
            history.forEach { notification ->
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

/**
 * #160 — the received-invites section (#159 Q5, Option A). Rows render until RESOLVED, not
 * until read: the server serves PENDING rows only (resolved rows never arrive — a re-entry
 * cannot resurrect an answered invite), PENDING rows carry Accept/Decline, and a pending
 * invite whose day is past renders "expired" (day-state is the expiry — no cron, no actions
 * on a stale row). The `else` branch is defensive against a future status-returning server.
 */
@Composable
private fun ReliefInvitesSection(
    invites: List<ReliefInviteResponse>,
    today: kotlinx.datetime.LocalDate,
    busy: Boolean,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel("Relief invites (${invites.size})")
        invites.forEach { invite ->
            ReliefInviteRow(
                invite = invite,
                today = today,
                busy = busy,
                onAccept = onAccept,
                onDecline = onDecline,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ReliefInviteRow(
    invite: ReliefInviteResponse,
    today: kotlinx.datetime.LocalDate,
    busy: Boolean,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
) {
    val expired = isInviteExpired(invite, today)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${invite.branchName} · ${invite.date}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Invited by ${invite.inviterName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expired || invite.status != ReliefInviteStatus.PENDING) {
            Text(
                text = if (expired) "Expired" else invite.status.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                TextButton(
                    onClick = { onAccept(invite.id) },
                    enabled = !busy,
                ) {
                    Text("Accept")
                }
                TextButton(
                    onClick = { onDecline(invite.id) },
                    enabled = !busy,
                ) {
                    Text("Decline")
                }
            }
        }
    }
}

@Composable
private fun ActionErrorLine(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

/**
 * #410 — the received-invites leg's error line: the failure message when the invites read
 * failed, else null. Rendered with an inline Retry on every cache state — keep-last rows stay
 * up, and an empty cache must not hide the time-bound Accept/Decline actions silently.
 */
internal fun receivedInvitesErrorLine(state: UiState<List<ReliefInviteResponse>>): String? =
    (state as? UiState.Error)?.message

/**
 * #410 — the unread leg's refresh-failure strip message, only when content is on screen
 * ([hasContent]): keep-last shows the stale list, so the strip above it keeps the failure
 * truthful. A failure with nothing to show returns null — the in-place ErrorCard path owns it.
 */
internal fun unreadRefreshErrorLine(
    state: UiState<List<NotificationResponse>>,
    hasContent: Boolean,
): String? = (state as? UiState.Error)?.takeIf { hasContent }?.message

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
                ).rowHover(enabled = onClick != null, shape = shape)
                .padding(
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
