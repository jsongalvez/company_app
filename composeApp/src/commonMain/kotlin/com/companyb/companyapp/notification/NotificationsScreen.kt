package com.companyb.companyapp.notification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.notification.NotificationResponse
import com.companyb.companyapp.contracts.workforce.ReliefInviteResponse
import com.companyb.companyapp.ui.ErrorCard
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.workforce.relief.ReliefInviteViewModel

// #679 — one notification destination: actionable relief invitations under Needs your
// response, then the Unread queue (rows read this visit stay in place, marked Read), then
// Earlier history. Lazy keyed rows; the header actions stay mounted so the toolbar never
// shifts. #410/#508/#611 keep-last errors, bounded reads, idempotency and reconciliation
// are preserved; relief requester names (#666) are untouched (no new notification type,
// no new backend read policy).
@Composable
fun NotificationsScreen(
    viewModel: NotificationViewModel,
    reliefInviteViewModel: ReliefInviteViewModel,
    onNotificationClick: (NotificationResponse) -> Unit,
) {
    val notificationsState by viewModel.notifications.collectAsState()
    val historyState by viewModel.history.collectAsState()
    val derived = rememberNotificationsDerived(viewModel)
    val needs = rememberNeedsResponse(reliefInviteViewModel)

    NotificationsEntryEffects(viewModel, reliefInviteViewModel, notificationsState, derived)

    // #679 — retained across the SessionDetail push/pop round-trip (the entry's saved-state
    // registry survives dispose-restore): the scroll position and the invoking row for focus
    // restoration. Nothing scrolls programmatically on a read mutation.
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var lastOpenedId by rememberSaveable { mutableStateOf<String?>(null) }
    // Hoisted focus registry: fresh per composition — rows register during composition but
    // never request focus themselves, so only rows in the restored viewport can take focus
    // (no scroll pull, no theft when rows scroll into view while browsing).
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    LaunchedEffect(lastOpenedId) {
        lastOpenedId?.let { focusRequesters[it]?.requestFocus() }
    }
    // Explicit-refresh anchor (transient op state — plain remember): first-visible row id +
    // offset captured at refresh click; re-seated once the history leg lands (see below).
    var refreshAnchor by remember { mutableStateOf<RefreshAnchor?>(null) }

    val unreadArg = (notificationsState as? UiState.Success)?.data ?: derived.unread
    val queueRows = mergeQueueRows(unreadArg, derived.readThisSession)
    val callbacks =
        QueueCallbacks(
            onNotificationClick = { notification ->
                lastOpenedId = notification.id
                onNotificationClick(notification)
            },
            onAcceptInvite = { id -> reliefInviteViewModel.acceptInvite(id) },
            onDeclineInvite = { id -> reliefInviteViewModel.declineInvite(id) },
            onRetryReceived = { reliefInviteViewModel.loadReceived() },
            lastOpenedId = lastOpenedId,
            focusRequesters = focusRequesters,
        )

    // #679 — anchor restore for explicit refresh. The loadings converge both legs; the
    // history leg (multi-page loop) lands last in the typical case, so its Success is the
    // settle signal: re-seat the anchor row — matched by id wherever reconciliation moved
    // it — at the captured offset. One-shot; a missing anchor simply leaves the retained
    // scroll position standing. Settles on any terminal leg state: a failed leg clears the
    // anchor without seating (the strips own the failure; no stale jump fires later).
    LaunchedEffect(historyState, notificationsState, queueRows, derived.visibleHistory, needs.rows) {
        val anchor = refreshAnchor
        val historySettled = historyState !is UiState.Loading && historyState !is UiState.Idle
        val queueSettled = notificationsState !is UiState.Loading && notificationsState !is UiState.Idle
        if (anchor != null && historySettled && queueSettled) {
            if (historyState is UiState.Success && notificationsState is UiState.Success) {
                anchorIndexForRow(anchor.rowId, needs.rows, queueRows, derived.visibleHistory)?.let {
                    listState.scrollToItem(it, anchor.offset)
                }
            }
            refreshAnchor = null
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
    ) {
        NotificationsHeaderHost(
            viewModel = viewModel,
            reliefInviteViewModel = reliefInviteViewModel,
            derived = derived,
            onRefresh = {
                val firstVisible = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                refreshAnchor =
                    firstVisible?.let { item ->
                        rowIdForKey(item.key, needs.rows, queueRows, derived.visibleHistory)?.let { id ->
                            RefreshAnchor(rowId = id, offset = listState.firstVisibleItemScrollOffset)
                        }
                    }
                viewModel.refreshQueue()
                reliefInviteViewModel.loadReceived()
            },
        )
        // Invite strips sit fixed above the queue (never lazy items): a cold invites failure
        // still offers its Retry when every list is empty.
        NeedsErrorStrips(needs = needs, onRetry = { reliefInviteViewModel.loadReceived() })

        val content =
            @Composable {
                NotificationsLazyList(
                    listState = listState,
                    needs = needs,
                    queueRows = queueRows,
                    history = derived.visibleHistory,
                    callbacks = callbacks,
                )
            }
        when (val state = notificationsState) {
            is UiState.Idle -> {
                CenteredProgress()
            }

            is UiState.Loading -> {
                if (hasQueueContent(needs, queueRows, derived)) {
                    content()
                } else {
                    CenteredProgress()
                }
            }

            is UiState.Error -> {
                // #410 — with keep-last content on screen the failure degrades to an inline
                // retry strip above the list (the stale rows stay; they must not masquerade
                // as fresh); a failure with nothing to show keeps the in-place error card.
                val error = unreadRefreshErrorLine(state, hasQueueContent(needs, queueRows, derived))
                if (error != null) {
                    NotificationsErrorStrip(message = error, onRetry = { viewModel.loadUnreadNotifications() })
                    content()
                } else {
                    ErrorCard(
                        message = state.message,
                        onRetry = { viewModel.loadUnreadNotifications() },
                    )
                }
            }

            is UiState.Success -> {
                if (hasQueueContent(needs, queueRows, derived)) {
                    content()
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No notifications",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Transient explicit-refresh anchor: the first-visible row id plus its pixel offset. */
private data class RefreshAnchor(
    val rowId: String,
    val offset: Int,
)

/**
 * Whether anything is on screen: pending invitations, queue rows (live or visit-read), or
 * history. A cold empty renders "No notifications" — pending invites alone never hide an
 * empty queue, and an empty invite section never hides history.
 */
private fun hasQueueContent(
    needs: NeedsDerived,
    queueRows: List<QueueRow>,
    derived: NotificationsDerived,
): Boolean =
    needs.rows.isNotEmpty() ||
        queueRows.isNotEmpty() ||
        derived.visibleHistory.isNotEmpty()

@Composable
private fun CenteredProgress() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NotificationsErrorStrip(
    message: String,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionErrorLine(message)
        TextButton(onClick = onRetry) {
            Text("Retry")
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
