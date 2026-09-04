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
    val derived = rememberNotificationsDerived(viewModel)

    NotificationsEntryEffects(viewModel, reliefInviteViewModel, notificationsState, derived)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
    ) {
        NotificationsHeaderHost(viewModel, derived)

        // #160 — the invites section renders above the unread list (self-sufficient host: it
        // collects the received/accept/decline flows internally so the Screen stays lean).
        ReliefInvitesSection(reliefInviteViewModel = reliefInviteViewModel)

        when (val state = notificationsState) {
            is UiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Loading -> {
                if (derived.hasContent) {
                    NotificationList(
                        unread = derived.unread,
                        readThisSession = derived.readThisSession,
                        history = derived.visibleHistory,
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
                    refreshError = unreadRefreshErrorLine(state, derived.hasContent),
                    message = state.message,
                    onRetry = { viewModel.loadUnreadNotifications() },
                    content = {
                        NotificationList(
                            unread = derived.unread,
                            readThisSession = derived.readThisSession,
                            history = derived.visibleHistory,
                            onNotificationClick = onNotificationClick,
                        )
                    },
                )
            }

            is UiState.Success -> {
                // D4: zero-state when nothing unread, nothing marked read this session, and no
                // history rows — "All caught up" must not hide a populated history (#356).
                if (derived.hasContent) {
                    NotificationList(
                        unread = state.data,
                        readThisSession = derived.readThisSession,
                        history = derived.visibleHistory,
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

/**
 * Queue derivations owned by [rememberNotificationsDerived] for the #462 LongMethod burn-down.
 * Data class so LongParameterList/TooManyFunctions-free; lives in this multi-decl file so
 * MatchingDeclarationName stays green.
 */
internal data class NotificationsDerived(
    val markReadError: String?,
    val markAllError: String?,
    val historyError: String?,
    val hasActionError: Boolean,
    val unread: List<NotificationResponse>,
    val readThisSession: List<NotificationResponse>,
    val visibleHistory: List<NotificationResponse>,
    val hasContent: Boolean,
    val markAllBusy: Boolean,
)

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
 *
 * Self-sufficient host (#462 burn): collects the received/accept/decline flows internally
 * (c0fb842e slimming precedent) so the Screen keeps one slim call instead of four collects
 * plus derivations. #410 — a failed load stays visible with its own Retry above keep-last
 * rows (never a silent collapse); the next load's Loading pre-set clears the strip.
 * Invite action failures (accept/decline) surface here too — same flows already collected
 * for the busy gate — so the Screen owns only the queue errors.
 */
@Composable
private fun ReliefInvitesSection(reliefInviteViewModel: ReliefInviteViewModel) {
    val receivedState by reliefInviteViewModel.received.collectAsState()
    val freshestReceived by reliefInviteViewModel.freshestReceived.collectAsState()
    val acceptState by reliefInviteViewModel.acceptResult.collectAsState()
    val declineState by reliefInviteViewModel.declineResult.collectAsState()
    val receivedError = receivedInvitesErrorLine(receivedState)
    LaunchedEffect(receivedError) {
        receivedError?.let { logWarn("NotificationsScreen", "receivedInvites=Error: $it") }
    }
    // Invite action failures surface inline (moved from the Screen under the #462 burn —
    // the flows are already collected for the busy gate; a failed accept/decline stays
    // visible, and the next attempt's Loading pre-set clears the line automatically).
    val acceptError = (acceptState as? UiState.Error)?.message
    val declineError = (declineState as? UiState.Error)?.message
    LaunchedEffect(acceptError) {
        acceptError?.let { logWarn("NotificationsScreen", "inviteAccept=Error: $it") }
    }
    LaunchedEffect(declineError) {
        declineError?.let { logWarn("NotificationsScreen", "inviteDecline=Error: $it") }
    }
    val received = freshestReceived.orEmpty()
    val inviteActionsBusy = acceptState is UiState.Loading || declineState is UiState.Loading
    // #410 — the strip sits above keep-last rows so cached rows never masquerade as fresh.
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
    if (acceptError != null || declineError != null) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            acceptError?.let { ActionErrorLine(it) }
            declineError?.let { ActionErrorLine(it) }
        }
    }
    if (received.isNotEmpty()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionLabel("Relief invites (${received.size})")
            received.forEach { invite ->
                ReliefInviteRow(
                    invite = invite,
                    today = currentOperationalDate(),
                    busy = inviteActionsBusy,
                    onAccept = { id -> reliefInviteViewModel.acceptInvite(id) },
                    onDecline = { id -> reliefInviteViewModel.declineInvite(id) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
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
internal fun ActionErrorLine(message: String) {
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
