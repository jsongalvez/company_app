package com.companyb.companyapp.notification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.notification.NotificationResponse
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.workforce.relief.ReliefInviteViewModel

/**
 * Queue derivations hoisted out of [NotificationsScreen] for the #462 LongMethod burn-down.
 * Lives here (not same-file) because NotificationsScreen.kt sits at the detekt file-function
 * wall — a same-file helper trips TooManyFunctions. Self-sufficient host: collects the five
 * queue flows itself (duplicate StateFlow subscriptions cheap — LoginNoticeEffect precedent)
 * so the Screen keeps one slim call; `notificationsState` stays collected at the Screen (its
 * status-when + entry log live there). Derivations verbatim (no remember keys in the source —
 * recomputed per composition, identical semantics).
 *
 * 1 param so it stays LongParameterList-clean outside the LPL-excluded Screen file.
 * FunctionNaming ignores @Composable so the camelCase value-returning host is lint-clean.
 */
@Composable
internal fun rememberNotificationsDerived(viewModel: NotificationViewModel): NotificationsDerived {
    val freshestUnread by viewModel.freshestNotifications.collectAsState()
    val readThisSession by viewModel.readThisSession.collectAsState()
    val historyState by viewModel.history.collectAsState()
    val markReadState by viewModel.markReadResult.collectAsState()
    val markAllState by viewModel.markAllResult.collectAsState()

    // Action failures surface inline (#135 round-1 precedent — silent network-failure paths are a
    // bug class, not a design choice): a failed markRead/markAll must be visible, and the next
    // attempt's Loading pre-set clears the line automatically. 404-on-markRead is NOT an error —
    // the VM handles it internally (absent-row defense reload), so it never reaches this state.
    val markReadError = (markReadState as? UiState.Error)?.message
    val markAllError = (markAllState as? UiState.Error)?.message
    val historyError = (historyState as? UiState.Error)?.message
    // ComplexCondition carve-out (#462 burn): the error OR lives in a named val so the
    // render gate stays a single condition. Invite accept/decline errors render in
    // ReliefInvitesSection (it owns those flows); only the queue errors gate here.
    val hasActionError = markAllError != null || markReadError != null
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
    return NotificationsDerived(
        markReadError = markReadError,
        markAllError = markAllError,
        historyError = historyError,
        hasActionError = hasActionError,
        unread = unread,
        readThisSession = readThisSession,
        visibleHistory = visibleHistory,
        hasContent = hasContent,
        markAllBusy = markAllState is UiState.Loading,
    )
}

/**
 * Header + inline error strips hoisted out of [NotificationsScreen] for the #462 LongMethod
 * burn-down. Self-sufficient host: drives markAllRead/loadHistory on the viewModel directly
 * so the Screen keeps one slim call. Plain @Composable (no ColumnScope — the moved block
 * uses no weight/scope members; DrawerHeader precedent). History error re-localed so the
 * non-null smart-cast reaches inside the Row lambda.
 *
 * 2 params so it stays LongParameterList-clean outside the LPL-excluded Screen file.
 */
@Composable
internal fun NotificationsHeaderHost(
    viewModel: NotificationViewModel,
    derived: NotificationsDerived,
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
        if (derived.unread.isNotEmpty()) {
            TextButton(
                onClick = { viewModel.markAllRead() },
                enabled = !derived.markAllBusy,
            ) {
                Text("Mark all")
            }
        }
    }

    if (derived.hasActionError) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            derived.markAllError?.let { ActionErrorLine(it) }
            derived.markReadError?.let { ActionErrorLine(it) }
        }
    }

    // #356 — history load failure is its own inline line with its own retry; it must not
    // masquerade as an unread-queue failure (that queue has the full ErrorCard path).
    // Local (not derived.*) so the non-null smart-cast reaches inside the Row lambda.
    val historyError = derived.historyError
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
}

/**
 * Entry + error-log effects hoisted out of [NotificationsScreen] for the #462 LongMethod
 * burn-down. Effect order/keys, log strings, and early-return behavior verbatim (LoginScreen
 * precedent — LaunchedEffect keyed on the state so a recomposition doesn't re-log).
 *
 * 4 params so it stays LongParameterList-clean outside the LPL-excluded Screen file.
 */
@Composable
internal fun NotificationsEntryEffects(
    viewModel: NotificationViewModel,
    reliefInviteViewModel: ReliefInviteViewModel,
    notificationsState: UiState<List<NotificationResponse>>,
    derived: NotificationsDerived,
) {
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

    LaunchedEffect(derived.markReadError) {
        derived.markReadError?.let { logWarn("NotificationsScreen", "markRead=Error: $it") }
    }
    LaunchedEffect(derived.markAllError) {
        derived.markAllError?.let { logWarn("NotificationsScreen", "markAll=Error: $it") }
    }
    LaunchedEffect(derived.historyError) {
        derived.historyError?.let { logWarn("NotificationsScreen", "history=Error: $it") }
    }
}

/**
 * Queue-list call collapse hoisted out of [NotificationsScreen] for the #462 LongMethod
 * burn-down: the three multi-line NotificationList call args count toward the caller, so
 * they collapse to single-line host calls here (multi-decl Overlays file hosts it —
 * Screen.kt sits at the file-function wall). Success still passes state.data (fresh data,
 * not keep-last derived) at the Screen call site.
 *
 * 3 params so it stays LongParameterList-clean outside the LPL-excluded Screen file.
 */
@Composable
internal fun NotificationsQueueList(
    unread: List<NotificationResponse>,
    derived: NotificationsDerived,
    onNotificationClick: (NotificationResponse) -> Unit,
) {
    NotificationList(
        unread = unread,
        readThisSession = derived.readThisSession,
        history = derived.visibleHistory,
        onNotificationClick = onNotificationClick,
    )
}
