package com.companyb.companyapp.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.contracts.notification.NotificationResponse
import com.companyb.companyapp.contracts.workforce.ReliefInviteStatus
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover
import com.companyb.companyapp.util.formatRelativeTimestamp
import com.companyb.companyapp.workforce.relief.ReceivedRow
import com.companyb.companyapp.workforce.relief.currentOperationalDate
import com.companyb.companyapp.workforce.relief.isInviteExpired

/**
 * Stable lazy keys, shared with the refresh anchor restore in [NotificationsScreen].
 * Anchor helpers mirror [NotificationsLazyList]'s section order/counts (headers included) —
 * keep them in sync when the structure changes. Restore is exact-id-or-nothing: a
 * reconciled-away anchor leaves the retained scroll position standing.
 */
internal fun needItemKey(id: String): String = "need-$id"

internal fun queueItemKey(id: String): String = "queue-$id"

internal fun historyItemKey(id: String): String = "history-$id"

/**
 * Resolves a lazy item key back to its row id (null for section headers, which are
 * positional — the retained offset already covers them).
 */
internal fun rowIdForKey(
    key: Any?,
    needs: List<ReceivedRow>,
    queueRows: List<QueueRow>,
    history: List<NotificationResponse>,
): String? {
    val needMatch = needs.firstOrNull { needItemKey(it.invite.id) == key }
    val queueMatch = queueRows.firstOrNull { queueItemKey(it.notification.id) == key }
    val historyMatch = history.firstOrNull { historyItemKey(it.id) == key }
    return when {
        needMatch != null -> needMatch.invite.id
        queueMatch != null -> queueMatch.notification.id
        historyMatch != null -> historyMatch.id
        else -> null
    }
}

/**
 * Index of a row id in the lazy structure (needs → queue → history, first match wins), or
 * null when reconciliation removed it — the retained scroll position then stands.
 */
internal fun anchorIndexForRow(
    rowId: String,
    needs: List<ReceivedRow>,
    queueRows: List<QueueRow>,
    history: List<NotificationResponse>,
): Int? {
    val needsBase = if (needs.isNotEmpty()) 1 else 0
    val queueBase = needsBase + needs.size + (if (queueRows.isNotEmpty()) 1 else 0)
    val historyBase = queueBase + queueRows.size + (if (history.isNotEmpty()) 1 else 0)
    val needAt = needs.indexOfFirst { it.invite.id == rowId }
    val queueAt = queueRows.indexOfFirst { it.notification.id == rowId }
    val historyAt = history.indexOfFirst { it.id == rowId }
    return when {
        needAt >= 0 -> needsBase + needAt
        queueAt >= 0 -> queueBase + queueAt
        historyAt >= 0 -> historyBase + historyAt
        else -> null
    }
}

/**
 * The notification queue as lazy keyed rows (#679): Needs your response (actionable relief
 * invitations) first, then Unread (rows read this visit stay at their positions, visually
 * marked Read), then Earlier (server history, display order). Keyed items plus the
 * caller's retained list state keep the viewport standing across refreshes; the screen
 * additionally re-seats the exact anchor row when it survives reconciliation.
 */
@Composable
internal fun NotificationsLazyList(
    listState: LazyListState,
    needs: NeedsDerived,
    queueRows: List<QueueRow>,
    history: List<NotificationResponse>,
    callbacks: QueueCallbacks,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        needsItems(needs, callbacks)
        queueItems(queueRows, callbacks)
        historyItems(history, callbacks)
    }
}

private fun LazyListScope.needsItems(
    needs: NeedsDerived,
    callbacks: QueueCallbacks,
) {
    if (needs.rows.isNotEmpty()) {
        item(key = "needs-header") {
            SectionLabel("Needs your response (${needs.rows.size})")
        }
        items(needs.rows, key = { needItemKey(it.invite.id) }) { row ->
            NeedsInviteRow(
                row = row,
                busy = needs.actionsBusy,
                onAccept = callbacks.onAcceptInvite,
                onDecline = callbacks.onDeclineInvite,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

/**
 * Invite error strips (#679): fixed above the queue — never lazy items — so a cold
 * invites failure still offers its Retry when every list is empty (the time-bound
 * Accept/Decline actions must not vanish silently). Renders nothing when clean.
 */
@Composable
internal fun NeedsErrorStrips(
    needs: NeedsDerived,
    onRetry: () -> Unit,
) {
    if (needs.receivedError != null) {
        val error = needs.receivedError
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionErrorLine(error)
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
    if (needs.acceptError != null || needs.declineError != null) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            needs.acceptError?.let { ActionErrorLine(it) }
            needs.declineError?.let { ActionErrorLine(it) }
        }
    }
}

private fun LazyListScope.queueItems(
    rows: List<QueueRow>,
    callbacks: QueueCallbacks,
) {
    if (rows.isNotEmpty()) {
        val unreadCount = rows.count { !it.readThisVisit }
        item(key = "queue-header") {
            SectionLabel("Unread ($unreadCount)")
        }
        items(rows, key = { queueItemKey(it.notification.id) }) { row ->
            val read = row.readThisVisit || row.notification.isRead
            QueueNotificationRow(
                notification = row.notification,
                read = read,
                readTag = row.readThisVisit,
                isLastOpened = row.notification.id == callbacks.lastOpenedId,
                focusRequesters = callbacks.focusRequesters,
                onOpen = callbacks.onNotificationClick,
            )
            if (!read) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

private fun LazyListScope.historyItems(
    history: List<NotificationResponse>,
    callbacks: QueueCallbacks,
) {
    if (history.isNotEmpty()) {
        item(key = "history-header") {
            SectionLabel("Earlier (${history.size})")
        }
        items(history, key = { historyItemKey(it.id) }) { notification ->
            QueueNotificationRow(
                notification = notification,
                read = notification.isRead,
                readTag = false,
                isLastOpened = notification.id == callbacks.lastOpenedId,
                focusRequesters = callbacks.focusRequesters,
                onOpen = callbacks.onNotificationClick,
            )
            if (!notification.isRead) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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
 * One queue row (#679): message first, then the branch/day/event secondary line, then the
 * relative timestamp. Unread carries a marker dot plus message weight — never a different
 * navigation capability: every row with a destination opens it, read or unread, while rows
 * without one render informational (no tap target, no hover).
 */
@Composable
private fun QueueNotificationRow(
    notification: NotificationResponse,
    read: Boolean,
    readTag: Boolean,
    isLastOpened: Boolean,
    focusRequesters: MutableMap<String, FocusRequester>,
    onOpen: (NotificationResponse) -> Unit,
) {
    val canOpen = notification.hasDestination()
    // Hoisted registry (screen-owned): the row registers its requester during composition but
    // never requests focus itself — the screen fires once per fresh composition, so scrolling
    // new rows into view cannot steal focus and off-screen rows never pull the viewport.
    val focusRequester =
        if (isLastOpened && canOpen) {
            focusRequesters.getOrPut(notification.id) { FocusRequester() }
        } else {
            null
        }
    val shape = RoundedCornerShape(CornerRadius.sm)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xxs)
                .then(
                    if (read) {
                        Modifier.background(MaterialTheme.colorScheme.surface, shape)
                    } else {
                        Modifier
                    },
                ).then(
                    if (canOpen) {
                        Modifier.clickable(onClickLabel = "Open ${notification.eventLabel()}") {
                            onOpen(notification)
                        }
                    } else {
                        Modifier
                    },
                ).then(
                    if (focusRequester != null) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    },
                ).rowHover(enabled = canOpen, shape = shape)
                .padding(
                    horizontal = Spacing.sm,
                    vertical = Spacing.xs,
                ),
        verticalAlignment = Alignment.Top,
    ) {
        UnreadSlot(visible = !read)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (read) FontWeight.Normal else FontWeight.Bold,
                color =
                    if (read) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
            Text(
                text = secondaryLine(notification, readTag),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatRelativeTimestamp(notification.createdAt, logTag = "NotificationsScreen"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun secondaryLine(
    notification: NotificationResponse,
    readTag: Boolean,
): String {
    val base =
        "${notification.eventLabel()} · Branch ${branchShort(notification.branchId)} · " +
            notification.dayLabel()
    return if (readTag) "$base · Read" else base
}

/**
 * The unread marker slot (#679): fixed geometry so a row keeps its bounds when it flips to
 * read — only the dot paints. The dot names itself for screen readers.
 */
@Composable
private fun UnreadSlot(visible: Boolean) {
    Box(
        modifier =
            Modifier
                .padding(top = Spacing.xs, end = Spacing.xs)
                .size(8.dp)
                .then(
                    if (visible) {
                        Modifier
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .semantics { contentDescription = "Unread" }
                    } else {
                        Modifier
                    },
                ),
    )
}

/**
 * One Needs-your-response row (#679): branch/day plus inviter stay visible; Accept/Decline
 * are explicit labeled actions that disable (bounds kept) while a mutation is in flight. A
 * confirmed resolution renders its outcome in place for the visit; expired or otherwise
 * resolved rows show their real state with no actions.
 */
@Composable
private fun NeedsInviteRow(
    row: ReceivedRow,
    busy: Boolean,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
) {
    val invite = row.invite
    val outcome = row.outcome
    val expired = outcome == null && isInviteExpired(invite, currentOperationalDate())
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
        when {
            outcome != null -> {
                Text(
                    text = inviteOutcomeLabel(outcome),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            expired || invite.status != ReliefInviteStatus.PENDING -> {
                Text(
                    text = if (expired) "Expired" else invite.status.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
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
}

private fun inviteOutcomeLabel(outcome: ReliefInviteStatus): String =
    when (outcome) {
        ReliefInviteStatus.ACCEPTED -> "Accepted"
        ReliefInviteStatus.DECLINED -> "Declined"
        else -> outcome.name
    }
